"""Export already-audited original CI ZIPs without executing, extracting or rewriting them."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile
from evidence_io import bounded_path, stream_sha256

ROOT = Path(__file__).resolve().parents[1]
NAMES = {'test-results-ubuntu-latest', 'test-results-windows-latest', 'test-results-macos-latest'}


def export(root, result_path, source_directory, destination):
    root = Path(root).resolve(strict=True)
    source_directory = bounded_path(root, source_directory)
    result_path = bounded_path(root, result_path)
    destination = bounded_path(root, destination, must_exist=False)
    if result_path.stat().st_size > 8 * 1024 * 1024:
        raise ValueError('Result manifest size bound')
    original = result_path.read_bytes()
    report = json.loads(original)
    rows = report.get('acquisition')
    if (report.get('originalArtifactsIndependentlyAudited') is not True
            or report.get('remoteAcceptanceComplete') is not True
            or not isinstance(rows, list) or len(rows) != 3):
        raise ValueError('Require the audited three-platform original acquisition manifest')
    if (not isinstance(report.get('commit'), str) or not re.fullmatch('[0-9a-f]{40}', report['commit'])
            or not re.fullmatch('[1-9][0-9]{0,19}', str(report.get('runId')))):
        raise ValueError('Invalid accepted commit or run identity')
    members = []
    seen = set()
    for row in rows:
        name, file, digest = row.get('name'), row.get('file'), row.get('sha256')
        if (name not in NAMES or name in seen or not isinstance(file, str)
                or not re.fullmatch(r'[0-9]+\.zip', file) or not isinstance(digest, str)
                or not re.fullmatch('[0-9a-f]{64}', digest)
                or row.get('githubDigest') != 'sha256:' + digest
                or row.get('commit') != report.get('commit')
                or str(row.get('runId')) != str(report.get('runId'))):
            raise ValueError('Mixed, duplicate or invalid original artifact identity')
        seen.add(name)
        source = bounded_path(root, (source_directory / file).relative_to(root).as_posix())
        if source.stat().st_size > 256 * 1024 * 1024 or stream_sha256(source) != digest:
            raise ValueError('Original artifact changed')
        members.append((source, 'artifacts/' + file, digest))
    if seen != NAMES:
        raise ValueError('Incomplete platform originals')
    manifest = dict(schemaVersion=1, scope='Historical accepted run; not current-code acceptance or offsite backup',
                    runId=str(report['runId']), commit=report['commit'], artifacts=rows,
                    resultSha256=hashlib.sha256(original).hexdigest())
    destination.parent.mkdir(parents=True, exist_ok=True)
    created = False
    try:
        with zipfile.ZipFile(destination, 'x', compression=zipfile.ZIP_STORED) as archive:
            created = True
            for source, member, _digest in members:
                archive.write(source, member)
            archive.writestr('accepted-result.json', original)
            archive.writestr('manifest.json', json.dumps(manifest, ensure_ascii=False, indent=2).encode())
        with zipfile.ZipFile(destination) as archive:
            for _source, member, digest in members:
                actual = hashlib.sha256()
                with archive.open(member) as stream:
                    for block in iter(lambda: stream.read(1024 * 1024), b''):
                        actual.update(block)
                if actual.hexdigest() != digest:
                    raise ValueError('Export copy changed')
            if archive.read('accepted-result.json') != original:
                raise ValueError('Export result changed')
        return dict(path=destination.relative_to(root).as_posix(), sha256=stream_sha256(destination),
                    bytes=destination.stat().st_size, runId=manifest['runId'], commit=manifest['commit'])
    except Exception:
        if created:
            destination.unlink(missing_ok=True)
        raise


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--result', required=True)
    parser.add_argument('--originals', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    print(json.dumps(export(ROOT, args.result, args.originals, args.output)))
