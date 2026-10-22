"""Check R1 implementation coverage separately from immutable original review data."""
import argparse
import importlib.util
from pathlib import Path
from evidence_io import bounded_path, read_json_snapshot

ROOT = Path(__file__).resolve().parents[1]
LEDGER = 'docs/development/OCR-R1-implementation-2026-10-21.json'


def require(value, message):
    if not value:
        raise ValueError(message)


def validate_ledger(data, expected, *, root=ROOT, passed=None, complete=False):
    require(isinstance(data, dict) and type(data.get('schemaVersion')) is int and data['schemaVersion'] == 1
            and data.get('phase') == 'R1', 'Invalid R1 implementation schema')
    rows = data.get('opinions')
    require(isinstance(rows, list) and rows and all(isinstance(r, dict) for r in rows), 'Missing R1 implementation rows')
    names = [r.get('id') for r in rows]
    require(all(isinstance(n, str) for n in names) and len(set(names)) == len(names), 'Duplicate R1 implementation opinion')
    require(set(names) == set(expected), 'R1 implementation coverage mismatch')
    states = {'IN_PROGRESS', 'IMPLEMENTED_LOCAL_VALIDATED', 'LOCAL_PARTIAL'}
    for row in rows:
        original = expected[row['id']]
        require(row.get('task') == original['task'] and row.get('originalVerdict') == original['verdict'],
                'Original R1 assignment/verdict changed: ' + row['id'])
        require(row.get('status') in states, 'Invalid R1 implementation state')
        if complete:
            require(row['status'] != 'IN_PROGRESS', 'R1 implementation remains incomplete: ' + row['id'])
        if row['status'] == 'IN_PROGRESS':
            continue
        require(isinstance(row.get('implementation'), str) and len(row['implementation'].strip()) >= 8,
                'Missing R1 implementation explanation: ' + row['id'])
        files = row.get('files')
        require(isinstance(files, list) and files and all(isinstance(p, str) for p in files)
                and len(files) == len(set(files)), 'Missing R1 implementation files: ' + row['id'])
        for name in files:
            require(not name.startswith(('.build-cache/', '.dsh-tmp/')) and bounded_path(root, name).is_file(),
                    'Invalid R1 implementation source')
        tests = row.get('tests')
        require(isinstance(tests, list) and tests and all(isinstance(t, str) and t.strip() for t in tests)
                and len(tests) == len(set(tests)), 'Missing R1 verification identities: ' + row['id'])
        if passed is not None:
            require(set(tests) <= passed, 'R1 verification case did not pass: ' + row['id'])
        if row['status'] == 'LOCAL_PARTIAL':
            require(isinstance(row.get('limitations'), list) and row['limitations']
                    and all(isinstance(t, str) and t.strip() for t in row['limitations']), 'Unexplained R1 partial verification')
    return {state: sum(r['status'] == state for r in rows) for state in sorted(states)}


def passing_cases(report):
    result = set()
    for entry in report['commands']:
        name = entry['name']
        snapshot = read_json_snapshot(ROOT, entry['cases']) if name not in ('workbench-evidence', 'ocr-progress', 'client') else None
        if name == 'java':
            for suite in snapshot.data['suites']:
                result.add(suite['name'])
                for case in suite['cases']:
                    result.add(suite['name'] + '#' + case)
                    result.add(suite['module'] + '/src/test/java/' + suite['name'].replace('.', '/') + '.java#' + case)
        elif snapshot is not None:
            result.update(c['caseId'] for c in snapshot.data['cases'] if c['outcome'] == 'PASS')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-complete', action='store_true')
    args = parser.parse_args()
    base = 'docs/development/ocr-verification-2026-10-20/'
    plan = read_json_snapshot(ROOT, base + 'plan.json').data
    verdicts = {}
    for name in ['core', 'application', 'docs', 'delivery', 'delivery-extra']:
        verdicts.update({r['id']: r for r in read_json_snapshot(ROOT, base + 'verdicts-' + name + '.json').data})
    expected = {i: dict(task=t['id'], verdict=verdicts[i]['status']) for t in plan['tasks'] if t['phase'] == 'R1' for i in t['opinionIds']}
    require(len(expected) == 237, 'Original R1 scope drift')
    ledger = read_json_snapshot(ROOT, LEDGER).data
    passed = None
    if args.require_complete:
        validation = ledger.get('validation')
        require(isinstance(validation, dict) and isinstance(validation.get('rawResults'), str), 'Missing fresh R1 validation')
        spec = importlib.util.spec_from_file_location('r1_validation_verify', ROOT / 'scripts/run-r1-validation.py')
        runner = importlib.util.module_from_spec(spec); spec.loader.exec_module(runner)
        report = runner.common.verify_report(validation['rawResults'])
        require(report['withProduct'] is True, 'R1 completion requires full local Java/client evidence')
        passed = passing_cases(report)
        supplemental = validation.get('supplementalResults')
        require(isinstance(supplemental, str), 'Missing fresh R1 supplemental validation')
        supplement_spec = importlib.util.spec_from_file_location('r1_supplemental_verify', ROOT / 'scripts/run-r1-supplemental.py')
        supplement_runner = importlib.util.module_from_spec(supplement_spec); supplement_spec.loader.exec_module(supplement_runner)
        passed.update(supplement_runner.verify(supplemental, run_id=report['runId'], source=report['sourceSha256']))
    counts = validate_ledger(ledger, expected, root=ROOT, passed=passed, complete=args.require_complete)
    print('PASS R1 implementation coverage', len(expected), counts, 'complete' if args.require_complete else 'coverage only')


if __name__ == '__main__':
    main()
