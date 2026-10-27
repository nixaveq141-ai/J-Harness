"""Frozen identities and source-bound checks for the supplementary R4 browser fault matrix."""
import argparse
from pathlib import Path
import re
from evidence_io import read_json_snapshot, bounded_path, stream_sha256

ROOT = Path(__file__).resolve().parents[1]


def frontend_sources(root):
    frontend = root / 'frontend'
    files = [p for p in (frontend / 'src').rglob('*') if p.is_file()]
    files += [frontend / 'tests' / name for name in ('r4.mjs', 'r4-fixture.tsx', 'runtime.mjs')]
    return {p.relative_to(frontend).as_posix(): stream_sha256(p) for p in sorted(files)}


def validate_record(record, expected, *, engine, run_id, sources):
    if (not isinstance(record, dict) or record.get('status') != 'PASS'
            or record.get('runId') != run_id or record.get('engine') != engine
            or record.get('errors') != [] or record.get('sourceSha256') != sources):
        raise ValueError('R4 run/engine/source mismatch or page error')
    checks = record.get('checks')
    if (not isinstance(expected, list) or not expected or len(set(expected)) != len(expected)
            or not isinstance(checks, list) or any(not isinstance(c, dict) or c.get('passed') is not True for c in checks)
            or [c.get('name') for c in checks] != expected):
        raise ValueError('R4 missing, duplicate, reordered or failed fault case')
    screenshot = record.get('screenshot')
    if (not isinstance(screenshot, dict) or not isinstance(screenshot.get('sha256'), str)
            or not re.fullmatch('[0-9a-f]{64}', screenshot['sha256'])):
        raise ValueError('R4 screenshot digest missing')
    return len(checks)


def validate_file(root, path, engine, run_id):
    record = read_json_snapshot(root, path).data
    contract = read_json_snapshot(root, 'scripts/contracts/r4-fault-cases.json').data
    count = validate_record(record, contract['cases'], engine=engine, run_id=run_id, sources=frontend_sources(root))
    shot = record['screenshot']['path']
    if not isinstance(shot, str) or not shot.startswith('.build-cache/R4/' + run_id + '/'):
        raise ValueError('R4 screenshot outside its run')
    if stream_sha256(bounded_path(root, shot)) != record['screenshot']['sha256']:
        raise ValueError('R4 screenshot changed')
    return count


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--record', required=True)
    parser.add_argument('--engine', choices=('chromium', 'firefox', 'webkit'), required=True)
    parser.add_argument('--run-id', required=True)
    args = parser.parse_args()
    print('PASS R4', args.engine, validate_file(ROOT, args.record, args.engine, args.run_id))
