"""Assemble explicitly scoped native previews without changing Java/product inputs.

This does not certify a product. Each replacement must be independently accepted
before assembly; this tool binds those exact bytes and preserves every other ZIP
entry. Never overwrite the accepted input JAR or its verification receipts.
"""
import hashlib
import json
from pathlib import Path
import zipfile


MANIFEST = 'META-INF/jharness/source-native-preview.json'


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def assemble(base, target, replacements, expected_base, inventory, scope):
    base, target = Path(base), Path(target)
    if base.resolve() == target.resolve() or target.exists():
        raise ValueError('Fresh preview path required; never overwrite input/evidence')
    if not replacements or not scope.strip() or digest(base.read_bytes()) != expected_base:
        raise ValueError('Explicit scope, replacements and accepted base required')
    payload = {}
    for name, item in replacements.items():
        if name not in inventory or not name.endswith(('.dll', '.exe', '.so', '.dylib', 'pty4j-unix-spawn-helper')):
            raise ValueError('Only inventoried native resources may be replaced')
        source = Path(item['path'])
        if source.is_symlink() or not source.is_file() or source.stat().st_size > 32 * 1024 * 1024:
            raise ValueError('Bounded regular source output required')
        raw = source.read_bytes()
        if digest(raw) != item['sha256'] or not item.get('origin'):
            raise ValueError('Exact independently reviewed source bytes/origin required')
        payload[name] = raw
    with zipfile.ZipFile(base) as original:
        names = original.namelist()
        if len(names) != len(set(names)) or MANIFEST in names or not set(payload).issubset(names):
            raise ValueError('Duplicate, pre-existing preview or absent resource rejected')
        for name in payload:
            if digest(original.read(name)) != inventory[name]:
                raise ValueError('Original native inventory drift rejected')
        manifest = dict(scope=scope, baseJarSha256=expected_base, productReplacement=False,
                        replacements={name:dict(sha256=digest(raw), origin=replacements[name]['origin'])
                                      for name, raw in payload.items()})
        # All preflight checks precede opening the fresh output file.
        with zipfile.ZipFile(target, 'x') as preview:
            for info in original.infolist():
                preview.writestr(info, payload.get(info.filename, original.read(info)))
            preview.writestr(MANIFEST, (json.dumps(manifest, sort_keys=True) + '\n').encode())
    with zipfile.ZipFile(base) as original, zipfile.ZipFile(target) as preview:
        if set(preview.namelist()) != set(original.namelist()) | {MANIFEST}:
            raise ValueError('Preview entry set drift rejected')
        for name in original.namelist():
            if preview.read(name) != payload.get(name, original.read(name)):
                raise ValueError('Preview byte drift rejected')
    return dict(manifest=manifest, previewJarSha256=digest(target.read_bytes()),
                preservedEntries=len(names)-len(payload), replacedEntries=len(payload),
                status='NATIVE_PREVIEW_ASSEMBLED_NOT_PRODUCT_ACCEPTANCE')
