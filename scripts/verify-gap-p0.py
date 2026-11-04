"""Verify the frozen P0 inventory against actual source receipts and Git inputs.

This is a decomposition/evidence check, not a product or security clearance.
Use --local-evidence to also re-read the retained local backup archives.
"""
from pathlib import Path
import argparse
import collections
import hashlib
import json
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/testing/gap-p0-2026-11-04'


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def read(name):
    return json.loads((OUT / name).read_text(encoding='utf-8'))


def verify(local=False):
    accepted = read('acceptance.json')
    for name, expected in accepted['outputs'].items():
        assert digest(OUT / name) == expected, ('output drift', name)
    tasks = read('native-tasks.json')['tasks']
    coverage = json.loads((ROOT / 'docs/testing/M10-unified14-final-native-coverage-2026-11-01.json').read_text())
    expected = {r['resource']: r for r in coverage['resources'] if r['provenance'] != 'REVIEWED_SOURCE_BUILD'}
    assert len(tasks) == len(expected) == 36
    assert len({t['id'] for t in tasks}) == 36
    assert {t['resource'] for t in tasks} == set(expected)
    assert collections.Counter(t['batch'] for t in tasks) == {
        'JNA-LINUX': 9, 'JNA-WINDOWS': 2, 'JNA-UNIX': 10, 'PTY-POSIX': 7, 'PTY-WINDOWS': 8}
    for task in tasks:
        source = expected[task['resource']]
        assert task['sha256'] == source['sha256'] and task['coordinate'] == source['coordinate']
        assert task['status'] == 'OPEN'
        assert all(task[k] for k in ['os', 'cpu', 'originalDistribution', 'missingProof',
                                    'availableToolchainEvidence', 'requiredToolchain',
                                    'verification', 'blockers', 'nextAction', 'exitCriteria'])
    environment = read('environment-scope.json')
    assert len(environment['primaryTargets']) == 7
    assert len({e['id'] for e in environment['primaryTargets']}) == 7
    for entry in environment['primaryTargets']:
        assert (ROOT / entry['receipt']).is_file()
        assert entry['productJarSha256'] == coverage['jarSha256']
        if entry['kind'] == 'HOSTED_BUILD_AND_PRODUCT_TEST':
            source = json.loads((ROOT / entry['receipt']).read_text())
            assert entry['artifact'] == source['artifact']
            assert entry['observedProof']['consistentSuites'] == 124
            assert entry['observedExactJdkBuild'] == entry['observedJvmAndOs']['java.runtime.version']
    history = read('historical-evidence.json')
    assert {r['gap'] for r in history['records']} == {'MAC-HISTORY', 'WINPTY-LOCAL', 'DEVELOPMENT-UNKNOWN'}
    for record in history['records']:
        assert record['status'] == 'OPEN' and record['missingEvidence'] and record['exitCriteria']
        for name in record['availableEvidence']:
            assert (ROOT / name).is_file(), name
    delta = read('revalidation-delta.json')
    raw = subprocess.check_output(['git', 'diff', '--name-status', delta['fromCommit'], delta['toCommit']], cwd=ROOT)
    actual = dict(line.split('\t')[::-1] for line in raw.decode().splitlines())
    assert {d['path']: d['change'] for d in delta['changes']} == actual
    for change in delta['changes']:
        blob = subprocess.check_output(['git', 'show', delta['toCommit'] + ':' + change['path']], cwd=ROOT)
        assert hashlib.sha256(blob).hexdigest() == change['sha256']
        assert change['category'] and change['requiredRevalidation']
    assert all(s == 'OPEN' for s in accepted['gapStatuses'].values())
    assert len(accepted['gapStatuses']) == 5
    if local:
        for backup in [*accepted['verifiedLocalBackups'], accepted['p0LocalBackup']]:
            path = ROOT / backup['path']
            assert digest(path) == backup['sha256'], ('backup digest', str(path))
            with zipfile.ZipFile(path) as archive:
                assert len(archive.namelist()) == backup['members'] and archive.testzip() is None
                hashes = backup.get('memberSha256', {})
                for name, expected_hash in hashes.items():
                    assert hashlib.sha256(archive.read(name)).hexdigest() == expected_hash
    return {'status': 'P0_VERIFIED_NOT_TECHNICAL_GAP_CLOSURE', 'nativeTasks': len(tasks),
            'primaryEnvironments': 7, 'historicalCases': 3, 'baselineDeltaFiles': len(delta['changes']),
            'localBackupsRead': 6 if local else 0, 'technicalGapsStillOpen': 5}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--local-evidence', action='store_true')
    args = parser.parse_args()
    print(json.dumps(verify(args.local_evidence), ensure_ascii=False))
