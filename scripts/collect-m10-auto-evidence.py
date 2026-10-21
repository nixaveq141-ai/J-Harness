"""Capture the settings increment independently of historical M10 acceptance."""

# Historical acceptance is immutable; retained code below is reference only.
_FROZEN_MESSAGE = 'HISTORICAL_EVIDENCE_FROZEN: historical collector; retained implementation is reference only and cannot be invoked by CLI, import, or runpy. Preserve original M10 acceptance bytes. Reproduction requires its original recorded revision and isolated evidence copy; current M10.5.9 evidence cannot prove M10. For new M10.5.9 evidence only, use scripts/collect-m1059-evidence.py and scripts/archive-m1059-local.py.'
if __name__ == '__main__':
    import sys
    sys.stderr.write(_FROZEN_MESSAGE + '\n')
    raise SystemExit(2)
raise RuntimeError(_FROZEN_MESSAGE)
import datetime
import hashlib
import json
from pathlib import Path
import platform
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.build-cache'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load(path):
    return json.loads(path.read_bytes())


def main():
    log = CACHE / 'M10-auto-verify.log'
    require('[INFO] BUILD SUCCESS' in log.read_text(encoding='utf-8', errors='replace'), 'No full verify success')
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    modules = [e.text for e in ET.parse(ROOT / 'pom.xml').findall('m:modules/m:module', ns)]
    sources, suites = [ROOT / 'pom.xml'], []
    for module in modules:
        sources += [ROOT / module / 'pom.xml'] + sorted((ROOT / module / 'src').rglob('*.java'))
        for file in sorted((ROOT / module / 'target/surefire-reports').glob('TEST-*.xml')):
            require(file.stat().st_mtime <= log.stat().st_mtime, 'Reports newer than verify')
            xml = ET.parse(file).getroot()
            suites.append(dict(module=module, name=xml.get('name'), sha256=sha(file),
                               cases=sorted(e.get('name') for e in xml.findall('testcase')),
                               **{k: int(xml.get(k, '0')) for k in ['tests', 'failures', 'errors', 'skipped']}))
    totals = {k: sum(s[k] for s in suites) for k in ['tests', 'failures', 'errors', 'skipped']}
    require(totals == dict(tests=314, failures=0, errors=0, skipped=0), 'Incomplete Java verification')
    require(all(p.stat().st_mtime <= log.stat().st_mtime for p in sources), 'Java/POM changed after verify')
    client = CACHE / 'M10-auto-client.log'
    text = client.read_text(encoding='utf-8', errors='replace')
    require(all(re.search(pattern, text) for pattern in [r'pass\s+9\b', r'fail\s+0\b', r'skipped\s+0\b']), 'Client tests failed')
    jar = ROOT / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
    jar_hash = sha(jar)
    records = {}
    for name, expected in [('M10-settings-browser-results.json', 21), ('M10-browser-results.json', 28), ('M10-launcher-results.json', 8)]:
        file = CACHE / name
        data = load(file)
        require(data.get('jarSha256') == jar_hash, 'Acceptance tested different JAR')
        require(len(data['checks']) == expected, 'Incomplete acceptance checks')
        require(all(c is not None and (isinstance(c, str) or c.get('passed')) for c in data['checks']), 'Failed acceptance')
        if name.startswith('M10-settings'):
            require(data['paidRequests'] == 0 and data['fixtureCredentialsCleaned'], 'Fixture cleanup/request mismatch')
        else:
            require(data['status'] == 'PASS', 'Acceptance did not pass')
        for path, digest in data.get('sourceSha256', {}).items():
            require(sha(ROOT / path) == digest, 'Acceptance input drift')
        for image in data.get('screenshots', []):
            require(sha(ROOT / image['path'].replace('\\', '/')) == image['sha256'], 'Screenshot drift')
        records[name] = sha(file)
    require(load(CACHE / 'M10-browser-results.json')['runtime']['paidRequests'] == 0, 'Paid model request')
    require(load(CACHE / 'M10-launcher-results.json')['paidRequests'] == 0, 'Launcher made paid request')
    upstream = load(ROOT / 'frontend/upstream.json')
    for item in upstream['files']:
        require(sha(ROOT / 'frontend' / item['destination']) == item['sha256'], 'Upstream file changed')
    with zipfile.ZipFile(jar) as archive:
        files = archive.read('ui/ui-files.txt').decode().splitlines()
        ui = {p: hashlib.sha256(archive.read('ui/' + p)).hexdigest() for p in files}
        require(all(sha(ROOT / 'frontend/dist' / p) == digest for p, digest in ui.items()), 'UI/JAR drift')
        require('thinking: disabled' in archive.read('ui/' + next(p for p in files if p.endswith('.js'))).decode(), 'Settings missing from bundle')
        require('io/jharness/cli/WorkbenchSettings.class' in archive.namelist() and 'io/jharness/cli/SystemCredentials.class' in archive.namelist(), 'Settings missing from runtime')
        legal = archive.read('ui/ui-license.txt').decode()
        require('Apache License' in legal and 'Copyright (c) 2026 DeepSeek' in legal, 'License missing')
    frontend = sorted((ROOT / 'frontend/src').rglob('*'))
    frontend += [ROOT / 'frontend' / p for p in ['package.json', 'package-lock.json', 'tests/settings.mjs', 'tests/browser.mjs', 'tests/client.test.ts', 'scripts/package-ui.mjs']]
    inputs = {p.relative_to(ROOT).as_posix(): sha(p) for p in frontend if p.is_file()}
    evidence = dict(milestone='M10-auto', status='PASS', capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    platform=platform.platform(), totals=totals, suites=suites, sourceSha256={p.relative_to(ROOT).as_posix(): sha(p) for p in sources},
                    frontendInputsSha256=inputs, verificationLogSha256=sha(log), clientTests=dict(tests=9, failures=0, skipped=0, logSha256=sha(client)),
                    acceptanceSha256=records, jarSha256=jar_hash, uiFilesSha256=ui, paidRequests=0,
                    collectionToolSha256=sha(Path(__file__)),
                    limits=['Local Windows11 Chrome; Windows Credential Manager persistence', 'Other OS credential stores unsupported; environment references still available',
                            'Remote M10 CI not run; M9 and base M10 receipts remain historical', 'No plugin marketplace/JAR install/hot load or paid model requests'])
    dest = ROOT / 'docs/testing'
    (dest / 'M10-auto-results.json').write_bytes((json.dumps(evidence, indent=2) + '\n').encode())
    for name in records:
        target = {'M10-settings-browser-results.json': 'M10-auto-settings-results.json', 'M10-browser-results.json': 'M10-auto-workbench-results.json', 'M10-launcher-results.json': 'M10-auto-launcher-results.json'}[name]
        (dest / target).write_bytes((CACHE / name).read_bytes())
    print(f'PASS: Java314/client9/settings21/workbench28/launcher8; {len(sources)} Java/POM hashes; identical final JAR/UI')


if __name__ == '__main__':
    main()
