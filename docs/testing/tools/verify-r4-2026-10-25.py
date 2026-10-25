"""Collect/verify this R4 increment; never closes all 115 opinions or a remote platform gate."""
import copy
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'scripts'))
from evidence_run import inputs
from evidence_checks import suite_snapshot, validate_java_suites, names, CONTRACT

RESULT = 'docs/testing/OCR-R4-remediation-2026-10-25-results.json'
LEDGER = 'docs/development/OCR-R4-scope-2026-10-25.json'
GATES = '.build-cache/R4-2026-10-25/final/results.json'
JAR = 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'


def read(name):
    return json.loads((ROOT / name).read_bytes())


def sha(name):
    return hashlib.sha256((ROOT / name).read_bytes()).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def scopes():
    plan = read('docs/development/ocr-verification-2026-10-20/plan.json')
    return {item for task in plan['tasks'] if task['phase'] == 'R4' for item in task['opinionIds']}


def verify(record):
    require(record['status'] == 'LOCAL_INCREMENT_VALIDATED_R4_OPEN' and record['m11Eligible'] is False, 'Wrong acceptance boundary')
    require(record['sourceSha256'] == inputs(ROOT), 'Current source drift')
    require(record['jarSha256'] == sha(JAR), 'JAR drift')
    require(record['ledgerSha256'] == sha(LEDGER), 'Ledger drift')
    require({row['id'] for row in read(LEDGER)['items']} == scopes() and len(read(LEDGER)['items']) == 115, 'Missing/duplicate R4 scope')
    require(record['totals'] == dict(java=677, javaSuites=110, client=42, componentPerEngine=29, historyPerEngine=19, sdk=66), 'Wrong exact case totals')
    for path, expected in record['artifactSha256'].items():
        require(sha(path) == expected, 'Artifact drift: ' + path)
    spec = importlib.util.spec_from_file_location('r4_regressions', ROOT / 'scripts/run-r3-validation.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    gates = module.common.verify_report(GATES)
    require(gates['withProduct'] is True and len(gates['commands']) == 18, 'Missing final Java/product/gates')
    contract = read('scripts/contracts/m1059-java-cases.json')
    suites = [suite_snapshot(ROOT / suite['module'] / 'target/surefire-reports' / ('TEST-' + suite['name'] + '.xml'), suite['module']) for suite in contract['suites']]
    require(validate_java_suites(suites, contract) == 677 and record['javaSuites'] == suites, 'Java case identity mismatch')
    with zipfile.ZipFile(ROOT / JAR) as jar:
        for path, digest in record['packagedUiSha256'].items():
            require(hashlib.sha256(jar.read('ui/' + path)).hexdigest() == digest == sha('frontend/dist/' + path), 'Packaged UI drift')
    for engine in ['chromium', 'webkit']:
        report = read('.build-cache/R4-2026-10-25/browser-' + engine + '.json')
        require(report['status'] == 'PASS' and len(report['checks']) == 29 and all(c['passed'] is True for c in report['checks']) and not report['errors'], 'Component failure')
        for path, digest in report['sourceSha256'].items():
            require(sha('frontend/' + path) == digest, 'Component source drift')
        history = read('.build-cache/R4-2026-10-25/history-' + engine + '.json')
        require(history['status'] == 'PASS' and len(history['checks']) == 19 and all(c['passed'] is True for c in history['checks']), 'History failure')
        names([c['name'] for c in history['checks']], read('scripts/workbench-case-contract.json')['fixtures']['history'], 'History case identities missing')
        require(history['jarSha256'] == record['jarSha256'] and history['paidRequests'] == 0 and history['hostStopped'] is True, 'History artifact/cleanup mismatch')
        for screenshot in history['screenshots']:
            frozen = '.build-cache/R4-2026-10-25/history-' + engine + '-' + Path(screenshot['path']).name
            require(sha(frozen) == screenshot['sha256'], 'History screenshot mismatch')
    sdk = read('.build-cache/R4-2026-10-25/sdk.json')
    require(sdk['status'] == 'PASS' and sdk['jarSha256'] == record['jarSha256'], 'SDK failure')
    require(len(sdk['results']) == 2 and {r['language'] for r in sdk['results']} == {'typescript', 'python'}, 'SDK languages missing')
    for result in sdk['results']:
        require(result['passed'] is True, 'SDK case failure')
        names(result['checks'], CONTRACT['sdk'][result['language']], 'SDK cases missing')
    require(sdk['allAuthorized'] is True and sdk['modelContextRedacted'] is True and sdk['paidRequests'] == 0, 'SDK traffic facts missing')
    scan = read('target/source-secrets.json')
    require(scan['status'] == 'PASS' and scan['findings'] == [] and scan['sourceFiles'] > 0, 'Source pattern findings')
    return True


def collect():
    record = dict(schemaVersion=1, status='LOCAL_INCREMENT_VALIDATED_R4_OPEN', capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  baseline='11ea9112da3d976f4336eda60569a4262082404d', m11Eligible=False, paidRequests=0,
                  sourceSha256=inputs(ROOT), jarSha256=sha(JAR), ledgerSha256=sha(LEDGER),
                  totals=dict(java=677, javaSuites=110, client=42, componentPerEngine=29, historyPerEngine=19, sdk=66))
    contract = read('scripts/contracts/m1059-java-cases.json')
    record['javaSuites'] = [suite_snapshot(ROOT / suite['module'] / 'target/surefire-reports' / ('TEST-' + suite['name'] + '.xml'), suite['module']) for suite in contract['suites']]
    record['packagedUiSha256'] = {p.relative_to(ROOT / 'frontend/dist').as_posix(): sha(p.relative_to(ROOT).as_posix()) for p in (ROOT / 'frontend/dist').rglob('*') if p.is_file()}
    artifacts = [GATES, LEDGER, 'target/source-secrets.json', 'docs/testing/tools/verify-r4-2026-10-25.py',
                 'docs/development/.gitattributes', 'docs/testing/.gitattributes',
                 '.build-cache/R4-2026-10-25-build-closure.log', '.build-cache/R4-2026-10-25/sdk.json',
                 '.build-cache/R4-2026-10-25-design-detector.json', '.build-cache/R4-2026-10-25-design-final-targets.json']
    for engine in ['chromium', 'webkit']:
        artifacts.extend(['.build-cache/R4-2026-10-25/browser-' + engine + '.json', '.build-cache/R4-2026-10-25/history-' + engine + '.json'])
        artifacts.append(Path(read('.build-cache/R4-2026-10-25/browser-' + engine + '.json')['screenshot']['path']).relative_to(ROOT).as_posix())
        for screenshot in read('.build-cache/R4-2026-10-25/history-' + engine + '.json')['screenshots']:
            artifacts.append('.build-cache/R4-2026-10-25/history-' + engine + '-' + Path(screenshot['path']).name)
    record['artifactSha256'] = {path: sha(path) for path in sorted(set(artifacts))}
    record['limitations'] = ['Original 115 opinions are indexed, not 115 independent reproductions or blanket closure.',
                             'No local Firefox, new POSIX/remote CI, Docker, native credential rerun, distribution install or paid model calls.',
                             'Presentation callbacks cannot change execution facts; UNKNOWN and original ownership checks remain blocking.',
                             'Scope ledger distinguishes this increment from retained earlier implementation; exact fault windows and full platform acceptance remain open.']
    verify(record)
    (ROOT / RESULT).write_bytes((json.dumps(record, ensure_ascii=False, indent=2) + '\n').encode())
    return record


if __name__ == '__main__':
    record = collect() if '--collect' in sys.argv else read(RESULT)
    verify(record)
    if '--negative' in sys.argv:
        for field, value in [('status', 'COMPLETE'), ('jarSha256', '0' * 64), ('sourceSha256', {}), ('totals', {})]:
            changed = copy.deepcopy(record)
            changed[field] = value
            try:
                verify(changed)
            except ValueError:
                continue
            raise ValueError('Mutation accepted: ' + field)
        print('PASS: four independent status/artifact/source/count mutations rejected')
    print('PASS: R4 increment bindings; original R4/M10.5.9/M11 remain open')
