"""Check R1-R3 follow-up coverage without rewriting the immutable review."""
from pathlib import Path
import hashlib
import json
from evidence_io import bounded_path, read_json_snapshot

ROOT = Path(__file__).resolve().parents[1]
LEDGER = 'docs/development/OCR-R1-R3-implementation-2026-10-24.json'
RESULTS = 'docs/testing/OCR-R1-R3-implementation-2026-10-24-results.json'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_rows(rows, originals, passing, root=ROOT):
    require(isinstance(rows, list) and len(rows) == len(originals), 'Incomplete follow-up coverage')
    expected = {row['id']: row for row in originals}
    require(len(expected) == len(originals), 'Duplicate original opinion')
    require(len({row['id'] for row in rows}) == len(rows) and {row['id'] for row in rows} == set(expected),
            'Duplicate or foreign follow-up opinion')
    for row in rows:
        original = expected[row['id']]
        require(row.get('verdict') == original['verdict'] and row.get('path') == original['path'], 'Original verdict or path changed')
        if original['verdict'] == '不采纳':
            require(row.get('status') == 'OUT_OF_SCOPE', 'Rejected opinion was promoted')
            continue
        require(row.get('status') in {'IMPLEMENTED_LOCAL_VALIDATED', 'EXISTING_FIX_CONFIRMED', 'CONTRACT_CLARIFIED'},
                'Follow-up remains incomplete: ' + row['id'])
        require(isinstance(row.get('implementation'), str) and len(row['implementation'].strip()) >= 8, 'Missing implementation explanation')
        files, tests = row.get('files'), row.get('tests')
        require(isinstance(files, list) and files and len(set(files)) == len(files), 'Missing implementation files')
        for name in files:
            require(not name.startswith(('.build-cache/', '.dsh-tmp/')) and bounded_path(root, name).is_file(), 'Invalid source reference')
        require(isinstance(tests, list) and tests and len(set(tests)) == len(tests) and set(tests) <= passing,
                'Referenced verification did not pass: ' + row['id'])
        require(isinstance(row.get('limitations'), list), 'Missing verification limitations')


def main():
    ledger = read_json_snapshot(ROOT, LEDGER).data
    review = read_json_snapshot(ROOT, ledger['reviewLedger']['path'])
    require(hashlib.sha256(review.raw).hexdigest() == ledger['reviewLedger']['sha256'], 'Review bytes changed')
    require(len(review.data['opinions']) == 174, 'Review count changed')
    result = read_json_snapshot(ROOT, RESULTS).data
    require(ledger['status'] == 'LOCAL_IMPLEMENTATION_VALIDATED' and result['status'] == 'LOCAL_IMPLEMENTATION_VALIDATED', 'Not locally validated')
    require(result['m11Eligible'] is False, 'Follow-up cannot promote M11')
    passing = set(result['passingVerificationIds'])
    require(len(passing) == len(result['passingVerificationIds']), 'Duplicate verification identity')
    suites = result['java']['suites']
    require(suites and all(s['failures'] == s['errors'] == s['skipped'] == 0 and s['tests'] == len(s['cases']) for s in suites), 'Java did not fully pass')
    java_ids = {'java:' + s['name'] + '#' + case for s in suites for case in s['cases']}
    require(java_ids <= passing and result['java']['tests'] == len(java_ids), 'Java case count drift')
    for name, digest in result['implementationSourceSha256'].items():
        require(hashlib.sha256(bounded_path(ROOT, name).read_bytes()).hexdigest() == digest, 'Implementation source drift: ' + name)
    validate_rows(ledger['opinions'], review.data['opinions'], passing)
    print(json.dumps(dict(opinions=174, adopted=155, rejected=19, javaCases=len(java_ids), m11Eligible=False)))


if __name__ == '__main__':
    main()
