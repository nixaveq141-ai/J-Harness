"""Bounded snapshots for owned, stable local directories; no history rewriting.

Callers must own source/destination directories for the duration of a read/write.
Link/reparse checks reject existing escapes; they do not defend against an active
process swapping ancestor directories between the check and open on all platforms.
"""
from dataclasses import dataclass
import hashlib
import json
import math
import os
from pathlib import Path, PureWindowsPath
import re
import shutil
import stat
import tempfile

MAX_JSON_BYTES = 16 * 1024 * 1024


def stream_sha256(path, *, max_bytes=None):
    """Hash with bounded memory; optional hard byte budget rejects oversized logs."""
    if max_bytes is not None and (type(max_bytes) is not int or max_bytes < 0):
        raise ValueError('Invalid evidence byte budget')
    digest = hashlib.sha256()
    total = 0
    with Path(path).open('rb') as stream:
        while True:
            chunk = stream.read(64 * 1024 if max_bytes is None else min(64 * 1024, max_bytes - total + 1))
            if not chunk:
                break
            total += len(chunk)
            if max_bytes is not None and total > max_bytes:
                raise ValueError('Evidence file exceeds byte budget')
            digest.update(chunk)
    return digest.hexdigest()


def strict_json_loads(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('Duplicate JSON key: ' + key)
            result[key] = value
        return result
    def constant(value):
        raise ValueError('Non-finite JSON number: ' + value)
    def number(value):
        result = float(value)
        if not math.isfinite(result):
            raise ValueError('Non-finite JSON number: ' + value)
        return result
    return json.loads(raw, object_pairs_hook=pairs, parse_constant=constant, parse_float=number)


def bounded_path(root, relative, *, must_exist=True):
    root = Path(root).resolve(strict=True)
    if not isinstance(relative, str) or not relative or '\x00' in relative:
        raise ValueError('Evidence path must be a nonempty relative string')
    win = PureWindowsPath(relative)
    parts = relative.replace('\\', '/').split('/')
    if (win.drive or win.root or relative.startswith('/') or
            any(p in ('', '.', '..') or ':' in p or p.endswith((' ', '.')) or
                re.fullmatch(r'(?i:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\..*)?', p)
                for p in parts)):
        raise ValueError('Unsafe evidence path: ' + relative)
    current = root
    for part in parts:
        current = current / part
        # Reject all links/reparse points, even if their current target is inside root.
        if current.is_symlink() or (hasattr(current, 'is_junction') and current.is_junction()):
            raise ValueError('Linked evidence path: ' + relative)
        if current.exists() and getattr(current.lstat(), 'st_file_attributes', 0) & 0x400:
            raise ValueError('Reparse evidence path: ' + relative)
    resolved = current.resolve(strict=must_exist)
    if not resolved.is_relative_to(root):
        raise ValueError('Evidence path escapes root: ' + relative)
    return resolved


@dataclass(frozen=True)
class JsonSnapshot:
    path: Path
    raw: bytes
    data: object
    sha256: str


def read_json_snapshot(root, relative, *, max_bytes=MAX_JSON_BYTES):
    path = bounded_path(root, relative)
    if not stat.S_ISREG(path.stat().st_mode):
        raise ValueError('Evidence JSON is not a regular file: ' + relative)
    with path.open('rb') as stream:
        raw = stream.read(max_bytes + 1)
    if len(raw) > max_bytes:
        raise ValueError('Evidence JSON exceeds byte limit: ' + relative)
    if not raw:
        raise ValueError('Empty evidence JSON: ' + relative)
    return JsonSnapshot(path, raw, strict_json_loads(raw), hashlib.sha256(raw).hexdigest())


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n').encode('utf-8')


def atomic_write_json(path, value):
    path = Path(path)
    raw = json_bytes(value)  # Serialization must succeed before touching the destination.
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.is_symlink() or (hasattr(path, 'is_junction') and path.is_junction()):
        raise ValueError('Linked JSON destination')
    fd, temporary = tempfile.mkstemp(prefix='.' + path.name + '-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def require_sha256(value):
    if not isinstance(value, str) or not re.fullmatch('[0-9a-f]{64}', value):
        raise ValueError('Invalid SHA-256')
    return value


def publish_archive(destination, files, manifest, *, manifest_name='manifest.json'):
    """Publish a fresh directory in one rename; bytes are already validated snapshots.

    Files is an iterable, so duplicate/case-colliding destinations can be rejected.
    The manifest is written last in staging. Failed validation or writes never expose
    a partial destination; an existing archive is never replaced.
    """
    def valid_name(name):
        return (isinstance(name, str) and re.fullmatch('[A-Za-z0-9][A-Za-z0-9_.-]*', name)
                and not name.endswith('.') and not re.fullmatch(r'(?i:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\..*)?', name))
    if not valid_name(manifest_name):
        raise ValueError('Unsafe archive manifest name')
    destination = Path(destination)
    if destination.exists() or destination.is_symlink():
        raise ValueError('Archive destination already exists')
    destination.parent.mkdir(parents=True, exist_ok=True)
    entries = list(files)
    seen = {manifest_name.casefold()}
    for name, raw in entries:
        # Archive names are flat and generated by the trusted caller.
        if not valid_name(name):
            raise ValueError('Unsafe archive destination name')
        if name.casefold() in seen:
            raise ValueError('Duplicate archive destination')
        seen.add(name.casefold())
        if not isinstance(raw, bytes) or not raw:
            raise ValueError('Empty archive snapshot')
    manifest_raw = json_bytes(manifest)
    stage = Path(tempfile.mkdtemp(prefix='.' + destination.name + '-', dir=destination.parent))
    try:
        for name, raw in entries:
            (stage / name).write_bytes(raw)
        (stage / manifest_name).write_bytes(manifest_raw)
        # rename does not replace a nonempty archive; concurrent publication fails.
        os.rename(stage, destination)
    finally:
        if stage.exists():
            shutil.rmtree(stage)
