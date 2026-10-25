"""Independently bind a candidate's exact UI resources to current frontend build inputs."""
import hashlib
from pathlib import Path
import zipfile
from evidence_io import bounded_path, read_json_snapshot

def verify(root, jar):
    root=Path(root).resolve()
    record=read_json_snapshot(root,'frontend/dist/ui-build.json',max_bytes=1024*1024).data
    if record.get('schemaVersion')!=1:raise ValueError('UI build record required')
    paths=['LICENSE']+['frontend/'+name for name in ['package.json','package-lock.json','tsconfig.json','vite.config.ts','index.html','upstream.json','LICENSE-DeepSeek-Harness.txt']]
    for directory in ['src','public','scripts']:
        for path in (root/'frontend'/directory).rglob('*'):
            name=path.relative_to(root).as_posix();checked=bounded_path(root,name)
            if checked.is_file():paths.append(name)
    def read(name,limit=2*1024*1024):
        path=bounded_path(root,name)
        if not path.is_file() or path.stat().st_size>limit:raise ValueError('UI input limit/type')
        with path.open('rb') as stream:data=stream.read(limit+1)
        if len(data)>limit:raise ValueError('UI input limit')
        return data
    digest=lambda data:hashlib.sha256(data).hexdigest()
    if record.get('sourceSha256')!={p:digest(read(p)) for p in sorted(paths)}:raise ValueError('Stale frontend build inputs')
    files=record.get('files')
    if not isinstance(files,dict) or not {'index.html','ui-license.txt','ui-files.txt'}<=files.keys():raise ValueError('Incomplete UI build')
    actual={p.relative_to(root/'frontend/dist').as_posix() for p in (root/'frontend/dist').rglob('*') if p.is_file()}
    if actual!=files.keys()|{'ui-build.json'}:raise ValueError('UI output set changed')
    snapshots={p:read('frontend/dist/'+p) for p in sorted(actual)}
    if any(digest(snapshots[p])!=expected for p,expected in files.items()):raise ValueError('UI output checksum mismatch')
    manifest=snapshots['ui-files.txt']
    if len(manifest)>16384 or set(manifest.decode().splitlines())!=files.keys()-{'ui-files.txt'}:raise ValueError('UI serving manifest mismatch')
    with zipfile.ZipFile(jar) as archive:
        entries=[i for i in archive.infolist() if i.filename.startswith('ui/') and not i.is_dir()]
        if len(entries)!=len(actual) or {i.filename[3:] for i in entries}!=actual:raise ValueError('Packaged UI set mismatch')
        for entry in entries:
            if entry.file_size>2*1024*1024 or archive.read(entry)!=snapshots[entry.filename[3:]]:raise ValueError('Packaged UI bytes mismatch')
    return {p:digest(data) for p,data in snapshots.items()}
