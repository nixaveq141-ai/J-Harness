"""Inventory pinned native bytes without loading or executing them. Distribution is not build provenance."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import time
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[3]
PTY_COMMIT = 'b7554d649f6374183040e6554942b6d5b028b5e5'
JNA_COMMIT = 'c366bc4fd0c50a30ac8d2475603ec564f6611462'
SUFFIXES = ('.dll', '.exe', '.so', '.dylib', '.jnilib')


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def versions(raw):
    """Embedded VS_FIXEDFILEINFO candidates are clues, not signed source attestations."""
    found = set()
    if raw[:2] == b'MZ':
        signature = struct.pack('<II', 0xFEEF04BD, 0x10000)
        start = 0
        while True:
            start = raw.find(signature, start)
            if start < 0:
                break
            if start + 52 <= len(raw):
                ms, ls = struct.unpack_from('<II', raw, start + 8)
                found.add('.'.join(map(str, (ms >> 16, ms & 65535, ls >> 16, ls & 65535))))
            start += 1
    return sorted(found)


def codeview_candidates(raw):
    """Embedded build clues only; a matching public PDB/source map is still required."""
    rows=[];start=0
    if raw[:2]!=b'MZ':return rows
    while len(rows)<16:
        start=raw.find(b'RSDS',start)
        if start<0:break
        if start+24<=len(raw):
            tail=raw[start+24:start+1048];end=tail.find(b'\0')
            if end>=0:
                path=tail[:end].decode('utf-8',errors='replace')
                rows.append(dict(guid=uuid.UUID(bytes_le=raw[start+4:start+20]).hex.upper(),
                    age=struct.unpack_from('<I',raw,start+20)[0],upstreamBuildPath=path,
                    status='EMBEDDED_CLUE_NOT_BUILD_ATTESTATION'))
        start+=4
    return rows


def audit(repository, upstream=False, fetch=None, jna_upstream=False):
    rows = json.loads((ROOT / 'third-party/inventory.json').read_bytes())
    result = dict(status='BUILD_PROVENANCE_INCOMPLETE', binaries=[],
                  scope='Exact artifact/native bytes and optional pinned upstream blob comparison; not an OS/CPE scan or build attestation.')
    deadline = time.monotonic() + 120
    def download(url):
        if time.monotonic()>deadline:raise TimeoutError('Upstream verification deadline')
        if fetch is not None:
            raw=fetch(url)
        else:
            with urllib.request.urlopen(url,timeout=15) as response:raw=response.read(32*1024*1024+1)
        if len(raw)>32*1024*1024:raise ValueError('Upstream byte bound')
        return raw
    for row in rows:
        if row['usage'] != 'runtime':
            continue
        group, artifact, version = row['coordinate'].split(':')
        if any(not p or '/' in p or '\\' in p or p in ('.', '..') for p in (group, artifact, version)):
            raise ValueError('Invalid inventory coordinate')
        path = repository / group.replace('.', '/') / artifact / version / (artifact + '-' + version + '.jar')
        if path.stat().st_size > 128 * 1024 * 1024:
            raise ValueError('Artifact byte bound')
        raw = path.read_bytes()
        if sha(raw) != row['jarSha256']:
            raise ValueError('Artifact does not match pinned legal inventory: ' + row['coordinate'])
        # Inspect the exact bytes whose hash was checked, not a second path open.
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            for entry in archive.infolist():
                if not entry.filename.lower().endswith(SUFFIXES):
                    continue
                if len(result['binaries']) >= 128 or entry.file_size > 32 * 1024 * 1024:
                    raise ValueError('Native resource bound')
                data = archive.read(entry)
                item = dict(coordinate=row['coordinate'], artifactSha256=row['jarSha256'],
                            resource=entry.filename, bytes=len(data), sha256=sha(data),
                            embeddedFixedVersionCandidates=versions(data),
                            embeddedCodeViewCandidates=codeview_candidates(data),
                            exactBuildRevision='UNRESOLVED', cpeAssessment='NOT_ASSESSED')
                prefix = 'resources/com/pty4j/native/'
                if upstream and artifact == 'pty4j' and entry.filename.startswith(prefix):
                    relative = entry.filename[len(prefix):]
                    if any(part in ('', '.', '..') for part in relative.split('/')):
                        raise ValueError('Invalid upstream resource path')
                    url = 'https://raw.githubusercontent.com/JetBrains/pty4j/' + PTY_COMMIT + '/os/' + relative
                    original = download(url)
                    item.update(upstreamUrl=url, upstreamCommit=PTY_COMMIT,
                                upstreamSha256=sha(original), distributionBytesMatch=original == data)
                    if original != data:
                        raise ValueError('Pinned upstream native resource mismatch: ' + entry.filename)
                if jna_upstream and artifact=='jna' and entry.filename.startswith('com/sun/jna/'):
                    platform,filename=entry.filename.removeprefix('com/sun/jna/').split('/')
                    if any(part in ('','.','..') for part in [platform,filename]):raise ValueError('Invalid JNA resource')
                    url='https://raw.githubusercontent.com/java-native-access/jna/'+JNA_COMMIT+'/lib/native/'+platform+'.jar'
                    original_jar=download(url)
                    with zipfile.ZipFile(io.BytesIO(original_jar)) as native:
                        info=native.getinfo(filename)
                        if info.file_size>32*1024*1024:raise ValueError('Upstream native byte bound')
                        original=native.read(info)
                    item.update(upstreamUrl=url,upstreamCommit=JNA_COMMIT,upstreamJarSha256=sha(original_jar),
                                upstreamSha256=sha(original),distributionBytesMatch=original==data)
                    if original!=data:raise ValueError('Pinned JNA upstream native resource mismatch: '+entry.filename)
                result['binaries'].append(item)
    result['nativeCount'] = len(result['binaries'])
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', type=Path, default=Path.home() / '.m2/repository')
    parser.add_argument('--verify-pty-upstream', action='store_true')
    parser.add_argument('--verify-jna-upstream', action='store_true')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.repository, args.verify_pty_upstream, jna_upstream=args.verify_jna_upstream)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(result, stream, indent=2)
        stream.write('\n')
    print(json.dumps(dict(status=result['status'], nativeCount=result['nativeCount'])))


if __name__ == '__main__':
    main()
