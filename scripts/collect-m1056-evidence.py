"""Bind M10.5.6 completion evidence to the final local source, JAR and UI."""

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
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.build-cache'
DEST = ROOT / 'docs/testing'


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path):
    return json.loads(path.read_bytes())


log = ROOT / '.dsh-tmp/m1056-final-verify.log'
require('BUILD SUCCESS' in log.read_text(encoding='utf-8', errors='replace'), 'No full verify success')
sources = [ROOT / 'pom.xml'] + sorted(ROOT.glob('harness-*/pom.xml')) + sorted(ROOT.glob('harness-*/src/**/*.java'))
suites = []
for file in sorted(ROOT.glob('harness-*/target/surefire-reports/TEST-*.xml')):
    xml = ET.parse(file).getroot()
    require(file.stat().st_mtime <= log.stat().st_mtime, 'Newer test report')
    suites.append(dict(path=file.relative_to(ROOT).as_posix(), sha256=sha(file),
                       cases=[e.get('name') for e in xml.findall('testcase')],
                       **{k: int(xml.get(k, '0')) for k in ['tests', 'failures', 'errors', 'skipped']}))
totals = {k: sum(s[k] for s in suites) for k in ['tests', 'failures', 'errors', 'skipped']}
require(totals == dict(tests=388, failures=0, errors=0, skipped=0), 'Incomplete Java verification')
require(all(p.stat().st_mtime <= log.stat().st_mtime for p in sources), 'Source changed since verification')
jar = ROOT / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
jar_hash = sha(jar)
client = ROOT / '.dsh-tmp/m1056-client.log'
text = client.read_text(encoding='utf-8', errors='replace')
require(all(re.search(p, text) for p in [r'pass\s+27\b', r'fail\s+0\b', r'skipped\s+0\b']), 'Client failure')
records = {}
for name, expected in [('M10.5.6-content-results.json', 45), ('M10.5.5-questions-results.json', 26), ('M10.5.4-models-results.json', 22), ('M10-settings-browser-results.json', 26),
                       ('M10.5.3-turns-results.json', 15), ('M10.5.2-history-results.json', 19),
                       ('M10.5.1-diagnosis-results.json', 13), ('M10-browser-results.json', 28), ('M10-launcher-results.json', 8)]:
    data = load(CACHE / name)
    require(data['jarSha256'] == jar_hash, 'Different acceptance JAR: ' + name)
    require(len(data['checks']) == expected and all(isinstance(c, str) or c.get('passed') for c in data['checks']), 'Incomplete browser checks: ' + name)
    require(data.get('paidRequests', data.get('runtime', {}).get('paidRequests')) == 0, 'Paid provider request')
    if name.startswith('M10-settings'):
        require(data.get('fixtureCredentialsCleaned'), 'Credential fixture not cleaned')
    else:
        require(data.get('status') == 'PASS', 'Browser did not pass')
    for image in data.get('screenshots', []):
        require(sha(ROOT / image['path'].replace(chr(92), '/')) == image['sha256'], 'Screenshot drift')
    for path, digest in data.get('sourceSha256', {}).items():
        require(sha(ROOT / path) == digest, 'Acceptance source drift')
    output_name = re.sub(r'^M10(?:\.5\.[1-6])?-', 'M10.5.6-', name)
    target = DEST / output_name
    target.write_bytes((CACHE / name).read_bytes())
    records[output_name] = dict(sha256=sha(target), checks=expected)
native = load(CACHE / 'M10.5.4-native-credentials.json')
require(native.get('expectationMet') and native.get('passed') and native['jarSha256'] == jar_hash, 'Native current-JAR failure')
require(native['sourceSha256'] == {p.relative_to(ROOT).as_posix(): sha(p) for p in sources}, 'Native source drift')
(DEST / 'M10.5.6-current-native-results.json').write_bytes((CACHE / 'M10.5.4-native-credentials.json').read_bytes())
# The previous three-OS probe validates unchanged native sources only, never this new full release.
prior = load(DEST / 'M10.5.4-native-ci-results.json')
native_paths = ['harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java',
                'harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java']
# Locate recorded manifests recursively because the historical collector schema is independent.
def manifests(value):
    if isinstance(value, dict):
        for k, v in value.items():
            if k == 'sourceSha256' and isinstance(v, dict):
                yield v
            yield from manifests(v)
    elif isinstance(value, list):
        for item in value:
            yield from manifests(item)
historical = list(manifests(prior))
require(historical, 'No historical native source identity')
for name in native_paths:
    require(any(m.get(name) == sha(ROOT / name) for m in historical), 'Native implementation changed since platform CI')
with zipfile.ZipFile(jar) as archive:
    files = archive.read('ui/ui-files.txt').decode().splitlines()
    require({n for n in archive.namelist() if n.startswith('ui/') and not n.endswith('/')} == {'ui/' + p for p in files} | {'ui/ui-files.txt'}, 'Stale UI')
    ui = {p: hashlib.sha256(archive.read('ui/' + p)).hexdigest() for p in files}
    require(all(sha(ROOT / 'frontend/dist' / p) == digest for p, digest in ui.items()), 'UI/JAR mismatch')
    bundle = archive.read('ui/' + next(p for p in files if p.endswith('.js'))).decode()
    require(all(s in bundle for s in ['对话与执行', '临时流式输出', '当前文件内容', '任务产物']), 'New UI missing')
    require(all(n in archive.namelist() for n in ['io/jharness/ecosystem/ModelDisplayBuffer.class', 'io/jharness/api/ContentInspection.class', 'io/jharness/ecosystem/WorkspaceContentInspection.class']), 'New adapters missing')
    legal = archive.read('ui/ui-license.txt').decode()
    require('Apache License' in legal and 'Copyright (c) 2026 DeepSeek' in legal, 'Missing attribution')
upstream = load(ROOT / 'frontend/upstream.json')
for item in upstream['files']:
    require(sha(ROOT / 'frontend' / item['destination']) == item['sha256'], 'Copied upstream file changed')
frontend = sorted(p for p in (ROOT / 'frontend/src').rglob('*') if p.is_file()) + sorted((ROOT / 'frontend/tests').glob('*'))
result = dict(milestone='M10.5.6', status='PASS', capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
              scope='Local conversation, streaming and scoped content acceptance; historical three-OS evidence for unchanged native worker',
              apiProtocol='1.12', jsonlVersion=1, webCatalogVersion=4, totals=totals, suites=suites,
              sourceSha256={p.relative_to(ROOT).as_posix(): sha(p) for p in sources},
              frontendSha256={p.relative_to(ROOT).as_posix(): sha(p) for p in frontend if p.is_file()},
              jarSha256=jar_hash, uiSha256=ui, records=records,
              logs={p.relative_to(ROOT).as_posix(): sha(p) for p in [log,client]},
              currentNative=dict(sha256=sha(DEST / 'M10.5.6-current-native-results.json'), checks=native['checks']),
              historicalNativeCI=dict(path='docs/testing/M10.5.4-native-ci-results.json',sha256=sha(DEST / 'M10.5.4-native-ci-results.json'),unchangedSources=native_paths),
              paidRequests=0, pushed=False, referenceProjectModified=False)
(DEST / 'M10.5.6-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + chr(10), encoding='utf-8')
print(json.dumps(dict(totals=totals,jarSha256=jar_hash,browserChecks=records,sourceFiles=len(sources)),ensure_ascii=False))
