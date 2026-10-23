"""Require complete matching CI evidence. This validates reports, not GitHub authenticity."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys

CONTRACT_PATH = Path(__file__).with_name('contracts') / 'm1059-java-cases.json'
MAX_REPORT_BYTES = 2 * 1024 * 1024
HEX256 = re.compile(r'[0-9a-f]{64}\Z')
HEX_COMMIT = re.compile(r'[0-9a-f]{40}\Z')


def require(value, message):
    if not value:
        raise ValueError(message)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate JSON key: ' + key)
        result[key] = value
    return result


def load_json(raw):
    return json.loads(raw, object_pairs_hook=unique_object,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError('Invalid JSON constant: ' + value)))


def relative_name(value):
    return (type(value) is str and bool(value) and '\\' not in value and ':' not in value
            and not value.startswith('/') and all(p not in ('', '.', '..') for p in value.split('/'))
            and PurePosixPath(value).as_posix() == value)


def fingerprint(value, label, required=()):
    require(type(value) is dict and bool(value), label + ': empty fingerprint')
    require(all(relative_name(p) and type(h) is str and HEX256.fullmatch(h) for p, h in value.items()),
            label + ': invalid path or SHA256')
    require(set(required) <= value.keys(), label + ': missing required source paths')


def exact_count(value, expected, label):
    require(type(value) is int and value == expected, label + ': incorrect integer count')


def validate_report(report, contract):
    require(type(report) is dict, 'Runner report must be an object')
    require(report.get('runnerOs') in ('Windows', 'Linux', 'macOS'), 'Invalid runner OS')
    require(report.get('status') == 'RUNNER_PASS' and report.get('remoteExecution') is True
            and report.get('workingTreeDirty') is False and report.get('m11Eligible') is False,
            'Not actual clean passing CI')
    exact_count(report.get('paidRequests'), 0, 'paidRequests')
    require(type(report.get('commit')) is str and HEX_COMMIT.fullmatch(report['commit']), 'Invalid commit identity')
    require(type(report.get('ciRunId')) is str and re.fullmatch(r'[1-9][0-9]{0,19}', report['ciRunId']),
            'Invalid CI run identity')
    require(type(report.get('runId')) is str and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}', report['runId']),
            'Invalid evidence run identity')
    fingerprint(report.get('sourceSha256'), 'sourceSha256', contract['requiredSourcePaths'])
    fingerprint(report.get('uiSha256'), 'uiSha256', ('index.html', 'ui-license.txt'))
    require(any(p.startswith('assets/') and p.endswith('.js') for p in report['uiSha256'])
            and any(p.startswith('assets/') and p.endswith('.css') for p in report['uiSha256']), 'Incomplete UI asset set')
    require(type(report.get('jarSha256')) is str and HEX256.fullmatch(report['jarSha256']), 'Invalid JAR fingerprint')
    expected = {(s['module'], s['name']): set(s['cases']) for s in contract['suites']}
    suites = report.get('suites')
    require(type(suites) is list and bool(suites), 'Empty Java suites')
    seen = set()
    total = 0
    for suite in suites:
        require(type(suite) is dict, 'Invalid Java suite')
        key = (suite.get('module'), suite.get('name'))
        require(all(type(v) is str for v in key), 'Invalid Java suite identity')
        require(key not in seen, 'Duplicate Java suite')
        require(key in expected, 'Unexpected Java suite')
        seen.add(key)
        cases = suite.get('cases')
        require(type(cases) is list and bool(cases) and all(type(c) is str and bool(c.strip()) for c in cases),
                'Invalid or empty Java cases')
        require(len(cases) == len(set(cases)), 'Duplicate Java case')
        require(set(cases) == expected[key], 'Incomplete Java case identities')
        exact_count(suite.get('tests'), len(cases), 'Suite tests')
        for field in ('failures', 'errors', 'skipped'):
            exact_count(suite.get(field), 0, 'Suite ' + field)
        require(type(suite.get('sha256')) is str and HEX256.fullmatch(suite['sha256']), 'Invalid suite XML fingerprint')
        total += len(cases)
    require(seen == expected.keys(), 'Missing Java suite')
    java = report.get('java')
    require(type(java) is dict, 'Missing Java totals')
    exact_count(java.get('tests'), total, 'Java tests')
    for field in ('failures', 'errors', 'skipped'):
        exact_count(java.get(field), 0, 'Java ' + field)
    for field, count in [('browserChecks', 813), ('sdkChecks', 66), ('frontendClientChecks', 38)]:
        exact_count(report.get(field), count, field)


def aggregate(directory):
    root = Path(directory).resolve(strict=True)
    require(root.is_dir(), 'Report root must be a directory')
    contract = load_json(CONTRACT_PATH.read_bytes())
    reports = []
    for path in sorted(root.rglob('runner.json')):
        require(not any(p.is_symlink() for p in (path, *path.parents) if p != root and root in p.parents),
                'Linked runner report')
        require(path.resolve(strict=True).is_relative_to(root), 'Runner report outside root')
        with path.open('rb') as stream:
            raw = stream.read(MAX_REPORT_BYTES + 1)
        require(len(raw) <= MAX_REPORT_BYTES, 'Runner report exceeds size limit')
        data = load_json(raw)
        require(type(data) is dict, 'Runner report must be an object')
        if data.get('milestone') == 'M10.5.9':
            validate_report(data, contract)
            reports.append((path, data, hashlib.sha256(raw).hexdigest()))
    require(len(reports) == 3, 'Require exactly three current runner reports')
    values = [r for _, r, _ in reports]
    require({r['runnerOs'] for r in values} == {'Windows', 'Linux', 'macOS'}, 'Missing or duplicate runner OS')
    for field in ('commit', 'ciRunId', 'runId', 'sourceSha256', 'uiSha256'):
        require(all(r[field] == values[0][field] for r in values), 'Different CI/source/UI: ' + field)
    # JARs may differ legitimately across OS builds; each report still needs its own valid hash.
    return dict(milestone='M10.5.9', status='PASS', m11Eligible=True, commit=values[0]['commit'],
                ciRunId=values[0]['ciRunId'], runId=values[0]['runId'],
                runnerReports={str(p): digest for p, _, digest in reports}, paidRequests=0)


if __name__ == '__main__':
    print(json.dumps(aggregate(sys.argv[1]), indent=2))
