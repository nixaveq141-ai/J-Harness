"""Verify this R5 local increment against current source, exact cases and artifact bytes."""
import copy
from collections import Counter
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

BASE = '.build-cache/R5-2026-10-25/'
RESULT = 'docs/testing/OCR-R5-remediation-2026-10-25-results.json'
LEDGER = 'docs/development/OCR-R5-scope-2026-10-25.json'
JAR = 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'

def read(path):
    return json.loads((ROOT / path).read_bytes())

def sha(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()

def require(ok, message):
    if not ok:
        raise ValueError(message)

def java_suites():
    contract = read('scripts/contracts/m1059-java-cases.json')
    suites = [suite_snapshot(ROOT / s['module'] / 'target/surefire-reports' / ('TEST-' + s['name'] + '.xml'), s['module']) for s in contract['suites']]
    require(validate_java_suites(suites, contract) == 700 and len(suites) == 117, 'Wrong Java cases')
    return suites

def verify(record):
    require(record['status'] == 'LOCAL_INCREMENT_VALIDATED_R5_OPEN' and record['m11Eligible'] is False, 'Wrong acceptance boundary')
    require(record['sourceSha256'] == inputs(ROOT), 'Source drift')
    require(record['jarSha256'] == sha(JAR), 'JAR drift')
    require(record['totals'] == dict(java=700, javaSuites=117, client=42, gates=184, sdk=66, searchPerEngine=28, contentPerEngine=45), 'Wrong totals')
    ledger = read(LEDGER)
    expected = {v['id'] for v in read('docs/development/ocr-verification-2026-10-20/review.json')['verdicts'] if v.get('phase') == 'R5'}
    require(len(ledger['items']) == 64 and {r['id'] for r in ledger['items']} == expected, 'Missing/duplicate R5 scope')
    require(not any(r['individualAcceptanceClosed'] for r in ledger['items']), 'Overclaimed scope')
    require(record['scopeCounts'] == ledger['counts'] == dict(Counter(r['state'] for r in ledger['items'])), 'Scope counts drift')
    for path, digest in record['artifactSha256'].items():
        require(sha(path) == digest, 'Artifact drift: ' + path)
    require(record['javaSuites'] == java_suites(), 'Java report drift')
    spec = importlib.util.spec_from_file_location('r5_regressions', ROOT / 'scripts/run-r3-validation.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    gates = module.common.verify_report(BASE + 'final-frozen2/results.json')
    require(gates['withProduct'] is True and len(gates['commands']) == 18, 'Missing final groups')
    require(sum(c['tests'] for c in gates['commands']) == 926, 'Wrong group total')
    with zipfile.ZipFile(ROOT / JAR) as jar:
        for path, digest in record['packagedUiSha256'].items():
            require(hashlib.sha256(jar.read('ui/' + path)).hexdigest() == digest == sha('frontend/dist/' + path), 'UI drift')
    require(read(BASE + 'browser-source-before.json') == read(BASE + 'browser-source-after.json') == inputs(ROOT), 'Browser source drift')
    fixtures = read('scripts/workbench-case-contract.json')
    for engine in ['chromium', 'webkit']:
        for kind, total in [('search', 28), ('content', 45)]:
            report = read(BASE + kind + '-' + engine + '.json')
            require(report['status'] == 'PASS' and len(report['checks']) == total and all(c['passed'] is True for c in report['checks']), 'Browser case failure')
            names([c['name'] for c in report['checks']], fixtures['fixtures'][kind], 'Browser exact cases missing')
            require(report['jarSha256'] == record['jarSha256'] and report['paidRequests'] == 0, 'Browser JAR/traffic mismatch')
            if kind == 'content':
                require(report['hostStopped'] is True, 'Content host not stopped')
            for screenshot in report['screenshots']:
                path = BASE + kind + '-' + engine + '-' + Path(screenshot['path']).name
                require(sha(path) == screenshot['sha256'], 'Screenshot drift')
    sdk = read(BASE + 'sdk.json')
    require(sdk['status'] == 'PASS' and sdk['jarSha256'] == record['jarSha256'], 'SDK failure')
    require(len(sdk['results']) == 2 and {r['language'] for r in sdk['results']} == {'typescript', 'python'}, 'SDK languages missing')
    for result in sdk['results']:
        require(result['passed'] is True, 'SDK case failure')
        names(result['checks'], CONTRACT['sdk'][result['language']], 'SDK case identities missing')
    require(sdk['allAuthorized'] is True and sdk['modelContextRedacted'] is True and sdk['paidRequests'] == 0, 'SDK traffic mismatch')
    scan = read('target/source-secrets.json')
    require(scan['status'] == 'PASS' and not scan['findings'], 'Source pattern findings')

def collect():
    record = dict(schemaVersion=1, status='LOCAL_INCREMENT_VALIDATED_R5_OPEN', capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  baseline='7f5b9bf66f7c63d6960e2f50cd05afe652ba4136', m11Eligible=False, paidRequests=0,
                  sourceSha256=inputs(ROOT), jarSha256=sha(JAR), javaSuites=java_suites(),
                  totals=dict(java=700, javaSuites=117, client=42, gates=184, sdk=66, searchPerEngine=28, contentPerEngine=45))
    record['packagedUiSha256'] = {p.relative_to(ROOT / 'frontend/dist').as_posix(): sha(p.relative_to(ROOT).as_posix()) for p in (ROOT / 'frontend/dist').rglob('*') if p.is_file()}
    artifacts = [LEDGER, 'target/source-secrets.json', 'docs/testing/tools/verify-r5-2026-10-25.py', 'docs/development/.gitattributes', 'docs/testing/.gitattributes', BASE + 'sdk.json', BASE + 'browser-source-before.json', BASE + 'browser-source-after.json']
    artifacts += [p.relative_to(ROOT).as_posix() for p in (ROOT / BASE / 'final-frozen2').rglob('*') if p.is_file()]
    for engine in ['chromium', 'webkit']:
        for kind in ['search', 'content']:
            report = BASE + kind + '-' + engine + '.json'
            artifacts += [report, BASE + kind + '-' + engine + '.log']
            artifacts += [BASE + kind + '-' + engine + '-' + Path(s['path']).name for s in read(report)['screenshots']]
    record['artifactSha256'] = {path: sha(path) for path in sorted(set(artifacts))}
    record['scopeCounts'] = read(LEDGER)['counts']
    record['limitations'] = ['64-opinion ledger: 51 implemented/strengthened, two partial, 11 pending; no individual blanket closure.', 'Unique exact source associations only; ambiguous or synthetic messages remain unmapped.', 'Directory identity boundary checks do not eliminate parent-directory replacement races; no SecureDirectoryStream implementation.', 'No peak-heap or production performance measurement; no local Firefox, new remote POSIX/platform matrix, Docker, paid calls, push or publication.']
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
            raise AssertionError('Mutation accepted: ' + field)
    print('PASS R5 exact local source/cases/artifact/UI/SDK/browser/scope verification')
