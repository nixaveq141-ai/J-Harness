"""Collect fresh R0 regression receipts; never promotes the product or remote CI gate.

The checked-in contract contains reviewed test identities, not an automatically
accepted count. Use --with-product for the frontend and Java baseline as well.
"""
import argparse
import importlib.util
import os
import re
from pathlib import Path
import sys
import unittest
import uuid

from evidence_io import atomic_write_json, bounded_path, read_json_snapshot, strict_json_loads, stream_sha256
from evidence_run import inputs, run, valid_run_id, validate as validate_command

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / 'scripts/contracts/r0-validation-cases.json'


def require(ok, message):
    if not ok:
        raise ValueError(message)



def validate_contract(contract):
    require(type(contract) is dict and type(contract.get('schemaVersion')) is int
            and contract['schemaVersion'] == 1, 'Invalid validation contract schema')
    groups = contract.get('groups')
    require(type(groups) is list and bool(groups), 'Missing validation contract groups')
    names, paths = set(), set()
    for group in groups:
        require(type(group) is dict, 'Invalid validation contract group')
        name = group.get('name')
        require(type(name) is str and re.fullmatch('[a-z][a-z0-9-]{0,63}', name)
                and name != 'java', 'Invalid or reserved validation group name')
        require(name not in names, 'Duplicate validation group name'); names.add(name)
        require(group.get('role') in ('gate', 'product'), 'Invalid validation group role')
        require(group.get('kind') in ('python', 'node'), 'Invalid validation group kind')
        cwd = group.get('cwd', '.')
        require(type(cwd) is str, 'Invalid validation group cwd')
        directory = ROOT.resolve() if cwd == '.' else bounded_path(ROOT, cwd)
        require(directory.is_dir(), 'Validation group cwd is not a directory')
        relative = group.get('path')
        path = bounded_path(directory, relative)
        require(path.is_file(), 'Validation group path is not a file')
        key = path.relative_to(ROOT.resolve()).as_posix().casefold()
        require(key not in paths, 'Duplicate validation group test path'); paths.add(key)
        args = group.get('nodeArgs', [])
        require(type(args) is list and (args == [] or args == ['--import', 'tsx']), 'Invalid validation Node arguments')
        if group['kind'] == 'python':
            require(cwd == '.' and path.name.startswith('test-') and path.suffix == '.py' and not args,
                    'Invalid Python validation test path or cwd')
        else:
            require(path.suffix in ('.mjs', '.js', '.ts', '.mts', '.cts')
                    and (path.name.startswith('test-') or '.test.' in path.name), 'Invalid Node validation test path')
        expected = group.get('caseIds')
        require(type(expected) is list and expected and all(type(x) is str and x.strip() and len(x) <= 1024 for x in expected)
                and len(expected) == len(set(expected)), 'Invalid expected case contract')
    require(any(g['role'] == 'gate' for g in groups), 'Missing gate validation groups')
    return contract


def command_for(group, cases_path):
    cwd = ROOT.resolve() if group.get('cwd', '.') == '.' else bounded_path(ROOT, group['cwd'])
    if group['kind'] == 'python':
        command = [sys.executable, 'scripts/run-r0-validation.py', '--python-child', group['path'], '--cases-output', str(cases_path)]
    else:
        reporter = (ROOT / 'scripts/test-evidence-reporter.mjs').resolve().as_uri()
        command = ['node', *group.get('nodeArgs', []), '--test', '--test-reporter=tap', '--test-reporter-destination=stdout',
                   '--test-reporter=' + reporter, '--test-reporter-destination=' + str(cases_path), group['path']]
    return command, cwd


def java_command():
    return ([str(ROOT / 'mvnw.cmd')] if os.name == 'nt' else ['sh', './mvnw']) + ['-B', '-o', 'test']

def validate_cases(data, expected):
    require(isinstance(expected, list) and expected and all(isinstance(x, str) and x for x in expected)
            and len(expected) == len(set(expected)), 'Invalid expected case contract')
    require(type(data) is dict and type(data.get('schemaVersion')) is int and data['schemaVersion'] == 1, 'Invalid test case schema')
    require(data.get('status') == 'PASS', 'Test outcome report did not pass')
    cases = data.get('cases')
    require(type(cases) is list and cases and all(type(c) is dict and set(c) == {'caseId', 'outcome'} for c in cases), 'Missing or invalid terminal cases')
    names = [c.get('caseId') for c in cases]
    require(all(isinstance(n, str) and n for n in names) and len(names) == len(set(names)), 'Duplicate or invalid terminal case')
    require(set(names) == set(expected), 'Terminal case contract mismatch')
    require(all(c.get('outcome') == 'PASS' for c in cases), 'Failed, skipped, or pending test case')
    return len(cases)


def node_cases(raw):
    require(len(raw) <= 16 * 1024 * 1024, 'Test event report too large')
    events = [strict_json_loads(line) for line in raw.splitlines() if line.strip()]
    require(events and all(type(e) is dict and e.get('type') in ('case', 'summary') for e in events), 'Missing or invalid test events')
    require(all(set(e) == ({'type', 'counts'} if e['type'] == 'summary' else {'type', 'caseId', 'outcome'}) for e in events), 'Invalid Node event schema')
    summaries = [e for e in events if e.get('type') == 'summary']
    require(len(summaries) == 1, 'Missing unique Node test summary')
    require(events[-1] is summaries[0], 'Node summary must follow terminal cases')
    counts = summaries[0].get('counts', {})
    require(type(counts) is dict and all(type(v) is int and v >= 0 for v in counts.values()), 'Invalid Node count schema')
    cases = [dict(caseId=e.get('caseId'), outcome=e.get('outcome')) for e in events if e.get('type') == 'case']
    require(type(counts.get('tests')) is int and counts['tests'] == len(cases) and len(cases) > 0, 'Node test total mismatch')
    for key in ('failed', 'cancelled', 'skipped', 'todo'):
        require(type(counts.get(key)) is int and counts[key] == 0, 'Node test did not fully pass: ' + key)
    require(type(counts.get('passed')) is int and counts['passed'] == len(cases), 'Node passing count mismatch')
    result = dict(schemaVersion=1, status='PASS', cases=cases)
    validate_cases(result, [c['caseId'] for c in cases])
    return result


def case_bytes(path):
    with path.open('rb') as stream:
        raw = stream.read(16 * 1024 * 1024 + 1)
    require(len(raw) <= 16 * 1024 * 1024, 'Test case report too large')
    return raw


def flatten(suite):
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            yield from flatten(item)
        else:
            yield item


def load_python_tests(relative):
    path = bounded_path(ROOT, relative)
    require(path.name.startswith('test-') and path.suffix == '.py', 'Not a validation test module')
    name = 'r0_' + path.stem.replace('-', '_')
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return unittest.defaultTestLoader.loadTestsFromModule(module)


def python_child(relative, output):
    report = dict(schemaVersion=1, status='INCOMPLETE', cases=[])
    atomic_write_json(output, report)
    try:
        suite = load_python_tests(relative)
        declared = [case.id() for case in flatten(suite)]
        outcomes = {}

        class RecordedResult(unittest.TextTestResult):
            def addSuccess(self, case):
                outcomes[case.id()] = 'PASS'; super().addSuccess(case)
            def addFailure(self, case, error):
                outcomes[case.id()] = 'FAIL'; super().addFailure(case, error)
            def addError(self, case, error):
                outcomes[case.id()] = 'ERROR'; super().addError(case, error)
            def addSkip(self, case, reason):
                outcomes[case.id()] = 'SKIP'; super().addSkip(case, reason)
            def addExpectedFailure(self, case, error):
                outcomes[case.id()] = 'EXPECTED_FAILURE'; super().addExpectedFailure(case, error)
            def addUnexpectedSuccess(self, case):
                outcomes[case.id()] = 'UNEXPECTED_SUCCESS'; super().addUnexpectedSuccess(case)
            def addSubTest(self, case, subtest, error):
                if error is not None:
                    outcomes[case.id()] = 'FAIL'
                super().addSubTest(case, subtest, error)

        result = unittest.TextTestRunner(verbosity=2, resultclass=RecordedResult).run(suite)
        report.update(cases=[dict(caseId=name, outcome=outcome) for name, outcome in sorted(outcomes.items())])
        require(result.wasSuccessful() and result.testsRun == len(declared) and not result.skipped, 'Python test failure or skip')
        validate_cases(dict(report, status='PASS'), declared)
        report['status'] = 'PASS'
        return 0
    finally:
        atomic_write_json(output, report)


def collect(output, run_id, *, with_product=False):
    require(valid_run_id(run_id), 'Invalid validation run identity')
    require(type(with_product) is bool, 'Invalid withProduct flag')
    output = bounded_path(ROOT, output, must_exist=False)
    require(not output.exists(), 'Validation destination must be fresh')
    output.mkdir(parents=True)
    report_path = output / 'results.json'
    source = inputs(ROOT)
    report = dict(schemaVersion=1, status='INCOMPLETE', runId=run_id, m11Eligible=False,
                  scope='Fresh local regression evidence, not release or remote CI acceptance', withProduct=with_product,
                  sourceSha256=source, commands=[])
    atomic_write_json(report_path, report)
    try:
        contract = validate_contract(read_json_snapshot(ROOT, CONTRACT.relative_to(ROOT).as_posix()).data)
        for group in contract['groups']:
            if group['role'] == 'product' and not with_product:
                continue
            name = group['name']; cases_path = output / (name + '.cases.json')
            command, cwd = command_for(group, cases_path)
            receipt = output / (name + '.receipt.json'); log = output / (name + '.log')
            run(command, name=name, log=log, receipt=receipt, run_id=run_id, cwd=cwd, outputs=(cases_path,))
            raw = case_bytes(cases_path)
            cases = node_cases(raw) if group['kind'] == 'node' else strict_json_loads(raw)
            count = validate_cases(cases, group['caseIds'])
            require(inputs(ROOT) == source, 'Build inputs changed during validation')
            report['commands'].append(dict(name=name, command=command, cwd=cwd.relative_to(ROOT).as_posix(), tests=count,
                receipt=receipt.relative_to(ROOT).as_posix(), receiptSha256=stream_sha256(receipt),
                log=log.relative_to(ROOT).as_posix(), logSha256=stream_sha256(log),
                cases=cases_path.relative_to(ROOT).as_posix(), casesSha256=stream_sha256(cases_path)))
            atomic_write_json(report_path, report)
            print('PASS', name, count, flush=True)
        if with_product:
            java_contract = read_json_snapshot(ROOT, 'scripts/contracts/m1059-java-cases.json').data
            xml_paths = tuple(ROOT / s['module'] / 'target/surefire-reports' / ('TEST-' + s['name'] + '.xml') for s in java_contract['suites'])
            command = java_command()
            receipt = output / 'java.receipt.json'; log = output / 'java.log'
            run(command, name='java', log=log, receipt=receipt, run_id=run_id, outputs=xml_paths)
            from evidence_checks import suite_snapshot
            suites = [suite_snapshot(p, p.relative_to(ROOT).parts[0]) for p in xml_paths]
            actual = {(s['module'], s['name']): set(s['cases']) for s in suites}
            expected = {(s['module'], s['name']): set(s['cases']) for s in java_contract['suites']}
            require(actual == expected and all(s[k] == 0 for s in suites for k in ('failures', 'errors', 'skipped')), 'Java case contract mismatch')
            cases_path = output / 'java.cases.json'
            atomic_write_json(cases_path, dict(schemaVersion=1, status='PASS', suites=suites))
            report['commands'].append(dict(name='java', command=command, cwd='.', tests=sum(s['tests'] for s in suites),
                receipt=receipt.relative_to(ROOT).as_posix(), receiptSha256=stream_sha256(receipt),
                log=log.relative_to(ROOT).as_posix(), logSha256=stream_sha256(log),
                cases=cases_path.relative_to(ROOT).as_posix(), casesSha256=stream_sha256(cases_path)))
        require(report['commands'] and inputs(ROOT) == source, 'Incomplete or changed validation source')
        report['status'] = 'LOCAL_REGRESSION_PASS'
        return report
    except BaseException as error:
        report['failureType'] = type(error).__name__
        raise
    finally:
        atomic_write_json(report_path, report)


def verify_report(relative):
    report = read_json_snapshot(ROOT, relative).data
    require(type(report) is dict, 'Invalid regression report schema')
    require(type(report.get('schemaVersion')) is int and report['schemaVersion'] == 1, 'Invalid regression report schema')
    require(type(report.get('withProduct')) is bool, 'Invalid withProduct flag')
    require(report.get('status') == 'LOCAL_REGRESSION_PASS' and report.get('m11Eligible') is False,
            'Not a complete local regression record')
    require(valid_run_id(report.get('runId')), 'Missing regression run identity')
    source = inputs(ROOT)
    require(report.get('sourceSha256') == source, 'Regression source drift')
    contract = validate_contract(read_json_snapshot(ROOT, CONTRACT.relative_to(ROOT).as_posix()).data)
    groups = {g['name']: g for g in contract['groups']}
    entries = report.get('commands')
    require(isinstance(entries, list) and entries and all(isinstance(e, dict) for e in entries), 'Missing regression commands')
    names = [e.get('name') for e in entries]
    require(all(type(n) is str for n in names) and len(names) == len(set(names)), 'Duplicate or invalid regression command')
    required = {g['name'] for g in groups.values() if g['role'] == 'gate' or report['withProduct']}
    if report['withProduct']:required.add('java')
    require(required == set(names), 'Incomplete or unexpected regression groups')
    destination = bounded_path(ROOT, relative).parent
    for entry in entries:
        name = entry['name']
        cases_path = destination / (name + '.cases.json')
        receipt_path = destination / (name + '.receipt.json')
        log_path = destination / (name + '.log')
        for field, expected_path in [('cases', cases_path), ('receipt', receipt_path), ('log', log_path)]:
            require(entry.get(field) == expected_path.relative_to(ROOT).as_posix(), 'Wrong regression artifact path: ' + field)
        expected_command, expected_cwd = (java_command(), ROOT.resolve()) if name == 'java' else command_for(groups[name], cases_path)
        require(entry.get('command') == expected_command, 'Wrong regression command: ' + name)
        require(entry.get('cwd') == expected_cwd.relative_to(ROOT.resolve()).as_posix(), 'Wrong regression working directory: ' + name)
        receipt = read_json_snapshot(ROOT, entry['receipt'])
        require(receipt.sha256 == entry['receiptSha256'], 'Regression receipt drift')
        log = bounded_path(ROOT, entry['log']); outcome = bounded_path(ROOT, entry['cases'])
        require(stream_sha256(log) == entry['logSha256'] and stream_sha256(outcome) == entry['casesSha256'], 'Regression log/case drift')
        if name == 'java':
            data = strict_json_loads(case_bytes(outcome))
            java_contract = read_json_snapshot(ROOT, 'scripts/contracts/m1059-java-cases.json').data
            from evidence_checks import suite_snapshot
            actual = [suite_snapshot(ROOT / s['module'] / 'target/surefire-reports' / ('TEST-' + s['name'] + '.xml'), s['module']) for s in java_contract['suites']]
            require(type(data.get('schemaVersion')) is int and data['schemaVersion'] == 1 and data.get('status') == 'PASS' and data.get('suites') == actual, 'Java regression output drift')
            require({(s['module'], s['name']): set(s['cases']) for s in actual} == {(s['module'], s['name']): set(s['cases']) for s in java_contract['suites']}, 'Java regression case identity mismatch')
            require(all(s[k] == 0 for s in actual for k in ('failures', 'errors', 'skipped')), 'Java regression did not pass')
            count = sum(s['tests'] for s in actual)
            outputs = {f"{s['module']}/target/surefire-reports/TEST-{s['name']}.xml": s['sha256'] for s in actual}
        else:
            raw = case_bytes(outcome)
            group = groups[name]
            data = node_cases(raw) if group['kind'] == 'node' else strict_json_loads(raw)
            count = validate_cases(data, group['caseIds'])
            outputs = {entry['cases']: entry['casesSha256']}
        require(type(entry.get('tests')) is int and entry['tests'] == count, 'Regression count mismatch')
        validate_command(receipt.data, name=name, log=log, run_id=report['runId'], source=source,
                         command=expected_command, outputs=outputs, cwd=expected_cwd.relative_to(ROOT.resolve()).as_posix())
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', default=os.environ.get('JHARNESS_EVIDENCE_RUN_ID'))
    parser.add_argument('--output')
    parser.add_argument('--with-product', action='store_true')
    parser.add_argument('--verify', help='Repository-relative fresh results.json to verify')
    parser.add_argument('--python-child')
    parser.add_argument('--cases-output', type=Path)
    args = parser.parse_args()
    if args.verify:
        report = verify_report(args.verify)
        print('PASS verified local regression', report['runId'], len(report['commands']), 'commands')
        return 0
    if args.python_child:
        require(args.cases_output is not None, 'Missing child outcome destination')
        output = bounded_path(ROOT, args.cases_output.absolute().relative_to(ROOT).as_posix(), must_exist=False)
        return python_child(args.python_child, output)
    run_id = args.run_id or 'r0-' + uuid.uuid4().hex
    collect(args.output or '.build-cache/R0/' + run_id, run_id, with_product=args.with_product)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
