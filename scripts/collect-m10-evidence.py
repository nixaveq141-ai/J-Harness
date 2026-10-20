"""Bind full Java verify, client tests and actual packaged browser checks to current sources."""

# Historical acceptance is immutable; retained code is reference only.
if __name__ == '__main__':
    import sys
    sys.stderr.write('HISTORICAL_EVIDENCE_FROZEN: this collector cannot regenerate historical acceptance. Use scripts/collect-m1059-evidence.py with fresh evidence and scripts/archive-m1059-local.py for a new archive.\n')
    raise SystemExit(2)
import datetime
import hashlib
import json
from pathlib import Path
import platform
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path):
    return json.loads(path.read_bytes())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    log = ROOT / (sys.argv[1] if len(sys.argv) > 1 else '.build-cache/M10-full-verify.log')
    require('[INFO] BUILD SUCCESS' in log.read_text(encoding='utf-8', errors='replace'), 'No successful verify')
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    modules = [e.text for e in ET.parse(ROOT / 'pom.xml').findall('m:modules/m:module', ns)]
    suites, java, sources = [], set(), [ROOT / 'pom.xml']
    for module in modules:
        sources += [ROOT / module / 'pom.xml'] + sorted((ROOT / module / 'src').rglob('*.java'))
        for p in sorted((ROOT / module / 'target/surefire-reports').glob('TEST-*.xml')):
            require(p.stat().st_mtime <= log.stat().st_mtime, 'Report newer than verification log')
            xml = ET.parse(p).getroot()
            row = {k: int(xml.get(k, '0')) for k in ['tests', 'failures', 'errors', 'skipped']}
            row.update(module=module, name=xml.get('name'), cases=sorted(e.get('name') for e in xml.findall('testcase')), sha256=sha(p))
            suites.append(row)
            java.update(e.get('value') for e in xml.findall('properties/property') if e.get('name') == 'java.version')
    totals = {k: sum(s[k] for s in suites) for k in ['tests', 'failures', 'errors', 'skipped']}
    require(totals == dict(tests=307, failures=0, errors=0, skipped=0), 'Incomplete Java results')
    require(all(p.stat().st_mtime <= log.stat().st_mtime for p in sources), 'Java/POM inputs changed after verification')
    browser_file = ROOT / '.build-cache/M10-browser-results.json'
    browser = load(browser_file)
    require(browser['status'] == 'PASS' and len(browser['checks']) >= 28 and all(c['passed'] for c in browser['checks']), 'Incomplete browser acceptance')
    require(browser['runtime']['paidRequests'] == 0 and browser['runtime']['writeExists'], 'Invalid runtime evidence')
    require(not browser['browserErrors'], 'Browser exceptions')
    require(all(sha(ROOT / p) == h for p, h in browser['sourceSha256'].items()), 'Browser inputs changed')
    jar = ROOT / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
    require(sha(jar) == browser['jarSha256'], 'Browser tested a different JAR')
    upstream = load(ROOT / 'frontend/upstream.json')
    require(len(upstream['files']) == 21, 'Incomplete upstream copy record')
    for item in upstream['files']:
        require(sha(ROOT / 'frontend' / item['destination']) == item['sha256'], 'Copied source bytes changed')
    with zipfile.ZipFile(jar) as z:
        files = z.read('ui/ui-files.txt').decode().splitlines()
        ui_hashes = {f: hashlib.sha256(z.read('ui/' + f)).hexdigest() for f in files}
        require(all(sha(ROOT / 'frontend/dist' / p) == h for p, h in ui_hashes.items()), 'Packaged UI drift')
        license_text = z.read('ui/ui-license.txt').decode()
        require('Apache License' in license_text and 'Copyright (c) 2026 DeepSeek' in license_text, 'Incomplete UI licenses')
        require(all(n in license_text for n in ['react@18.3.1', 'react-dom@18.3.1', 'scheduler@', 'clsx@2.1.1']), 'Missing browser dependency license')
    client_log = ROOT / '.build-cache/M10-client-tests.log'
    content = client_log.read_text(encoding='utf-8', errors='replace')
    require(bool(re.search(r'pass\s+8\b', content)) and bool(re.search(r'fail\s+0\b', content)) and bool(re.search(r'skipped\s+0\b', content)), 'Client tests not passed')
    audit_file = ROOT / '.build-cache/M10-npm-audit.json'
    audit = load(audit_file)
    require(audit['metadata']['vulnerabilities']['total'] == 0 and not audit['vulnerabilities'], 'npm audit has findings')
    bom_file = ROOT / '.build-cache/M10-frontend-sbom.json'
    bom = load(bom_file)
    require(bom['bomFormat'] == 'CycloneDX' and len(bom['components']) >= 6, 'No runtime npm BOM')
    lock = load(ROOT / 'frontend/package-lock.json')
    expected = {(p.rsplit('node_modules/', 1)[1], v['version']) for p, v in lock['packages'].items() if p and not v.get('dev', False)}
    require({(c['name'], c['version']) for c in bom['components']} == expected, 'Runtime BOM closure incomplete')
    require(any(p['name'] == 'jharness:npm-lock-sha256' and p['value'] == sha(ROOT / 'frontend/package-lock.json') for p in bom['metadata']['properties']), 'Runtime BOM / lock mismatch')
    for image in browser['screenshots']:
        require(sha(ROOT / image['path'].replace('\\', '/')) == image['sha256'], 'Screenshot hash mismatch')
    evidence = {'milestone': 'M10', 'status': 'PASS', 'capturedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                'platform': platform.platform(), 'architecture': platform.machine(), 'javaVersions': sorted(java),
                'baseCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip(),
                'workingTreeDirtyAtCapture': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT)),
                'verificationLog': log.relative_to(ROOT).as_posix(), 'logSha256': sha(log), 'totals': totals,
                'suites': suites, 'sourceSha256': {p.relative_to(ROOT).as_posix(): sha(p) for p in sources},
                'browserEvidenceSha256': sha(browser_file), 'clientTests': {'tests': 8, 'failures': 0, 'skipped': 0, 'logSha256': sha(client_log)},
                'npmAuditSha256': sha(audit_file), 'npmBomSha256': sha(bom_file), 'npmLockSha256': sha(ROOT / 'frontend/package-lock.json'),
                'uiFilesSha256': ui_hashes, 'jarSha256': sha(jar), 'paidRequests': 0,
                'limits': ['Local Windows11 Chrome only; M10 remote matrix has not been run', 'M9 results remain historical',
                           'No public hosting, TLS, multi-user, Desktop or new Docker acceptance']}
    out = ROOT / 'docs/testing'
    (out / 'M10-results.json').write_bytes((json.dumps(evidence, indent=2) + '\n').encode())
    for src, name in [(browser_file, 'M10-browser-results.json'), (audit_file, 'M10-npm-audit.json'), (bom_file, 'M10-frontend-sbom.json')]:
        (out / name).write_bytes(src.read_bytes())
    print('PASS: Java307, client8, browser' + str(len(browser['checks'])) + ', source/ZIP/UI/license/audit/BOM/screenshot hashes bound')


if __name__ == '__main__':
    main()
