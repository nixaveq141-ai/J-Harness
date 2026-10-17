"""Collect the native-credentials increment; does not close all of M10.5.4."""
import datetime
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
cache = root / '.build-cache'
destination = root / 'docs/testing'


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path):
    return json.loads(path.read_bytes())


log = cache / 'M10.5.4-native-final-verify.log'
require('BUILD SUCCESS' in log.read_text(encoding='utf-8'), 'No final clean verification')
sources = [root / 'pom.xml'] + sorted(root.glob('harness-*/pom.xml')) + sorted(root.glob('harness-*/src/**/*.java'))
require(all(p.stat().st_mtime <= log.stat().st_mtime for p in sources), 'Java source changed after verify')
manifest = {p.relative_to(root).as_posix(): sha(p) for p in sources}
suites = []
for file in sorted(root.glob('harness-*/target/surefire-reports/TEST-*.xml')):
    xml = ET.parse(file).getroot()
    suites.append({'name': xml.get('name'), 'cases': sorted(e.get('name') for e in xml.findall('testcase')),
                   **{k: int(xml.get(k, '0')) for k in ['tests', 'failures', 'errors', 'skipped']}})
totals = {k: sum(s[k] for s in suites) for k in ['tests', 'failures', 'errors', 'skipped']}
require(totals == dict(tests=338, failures=0, errors=0, skipped=0), 'Incomplete final Java tests')
client_log = cache / 'M10.5.4-native-client.log'
client_text = client_log.read_text(encoding='utf-8')
require(all(re.search(pattern, client_text) for pattern in [r'pass\s+16\b', r'fail\s+0\b', r'skipped\s+0\b']), 'Client regression failed')
jar = root / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
jar_hash = sha(jar)
records = {}
for label in ['windows-credentials', 'linux-credentials', 'linux-missing-service', 'linux-locked-service']:
    path = cache / f'M10.5.4-native-{label}.json'
    data = load(path)
    require(data['jarSha256'] == jar_hash and data['sourceSha256'] == manifest, 'Native source/JAR drift')
    require(data['probeScriptSha256'] == sha(root / 'scripts/test-native-credentials.py'), 'Native probe drift')
    require(data['expectationMet'], 'Unexpected native result')
    if label.endswith('-credentials'):
        require(data['passed'] and len(data['checks']) == 7 and all(data['checks'].values()), 'Native persistence failed')
    else:
        require(data['passed'] is False and data['expectedUnavailable'], 'Unavailable counted as persistence')
    records[label] = data
browser = load(cache / 'M10-settings-browser-results.json')
require(browser['jarSha256'] == jar_hash and len(browser['checks']) == 26
        and all(c['passed'] for c in browser['checks']) and browser['fixtureCredentialsCleaned']
        and browser['paidRequests'] == 0, 'Chrome settings incomplete')
with zipfile.ZipFile(jar) as archive:
    files = archive.read('ui/ui-files.txt').decode().splitlines()
    require({p for p in archive.namelist() if p.startswith('ui/') and not p.endswith('/')}
            == {'ui/' + p for p in files} | {'ui/ui-files.txt'}, 'Stale UI in JAR')
    require(all(hashlib.sha256(archive.read('ui/' + p)).hexdigest() == sha(root / 'frontend/dist' / p)
                for p in files), 'UI/JAR mismatch')
    require('io/jharness/cli/NativeCredentials.class' in archive.namelist(), 'Native worker missing')
report = dict(milestone='M10.5.4', scope='native-credentials increment', status='IN_PROGRESS',
              capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
              java=totals, suites=suites, sourceSha256=manifest, jarSha256=jar_hash,
              native=records, chromeSettings=browser, paidRequests=0, remoteRun=False,
              client=dict(tests=16, failures=0, skipped=0, logSha256=sha(client_log)),
              frontendInputsSha256={p.relative_to(root).as_posix(): sha(p) for p in
                  sorted((root / 'frontend/src').rglob('*')) + [root / 'frontend/tests/settings.mjs'] if p.is_file()},
              workflowSha256=sha(root / '.github/workflows/native-credentials.yml'),
              verificationLogSha256=sha(log), collectionToolSha256=sha(Path(__file__)),
              pending=['macOS native validation on GitHub-hosted runner',
                       'Composer model selection, per-turn snapshot and per-connection capabilities',
                       'Responses/Anthropic protocol adapters and their full acceptance matrix'])
(destination / 'M10.5.4-native-results.json').write_bytes((json.dumps(report, indent=2) + '\n').encode())
print('Native increment: Java338/Chrome settings26; Windows/Linux persistence7 each; Linux missing/locked expected unavailable; macOS pending. M10.5.4 remains in progress.')
