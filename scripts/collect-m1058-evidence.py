"""Bind M10.5.8 local acceptance to the current Java, frontend, SDK and final JAR."""

# Historical acceptance is immutable; retained code is reference only.
if __name__ == '__main__':
    import sys
    sys.stderr.write('HISTORICAL_EVIDENCE_FROZEN: this collector cannot regenerate historical acceptance. Use scripts/collect-m1059-evidence.py with fresh evidence and scripts/archive-m1059-local.py for a new archive.\n')
    raise SystemExit(2)
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

log = ROOT / '.dsh-tmp/m1058-final-verify2.log'
require('BUILD SUCCESS' in log.read_text(encoding='utf-8', errors='replace'), 'No full verify success')
sources = [ROOT / 'pom.xml'] + sorted(ROOT.glob('harness-*/pom.xml')) + sorted(ROOT.glob('harness-*/src/**/*.java'))
suites = []
for file in sorted(ROOT.glob('harness-*/target/surefire-reports/TEST-*.xml')):
    xml = ET.parse(file).getroot()
    require(file.stat().st_mtime <= log.stat().st_mtime, 'Newer test report')
    suites.append(dict(path=file.relative_to(ROOT).as_posix(), sha256=sha(file), cases=[e.get('name') for e in xml.findall('testcase')],
                       **{k: int(xml.get(k, '0')) for k in ['tests', 'failures', 'errors', 'skipped']}))
totals = {k: sum(s[k] for s in suites) for k in ['tests', 'failures', 'errors', 'skipped']}
require(totals == dict(tests=410, failures=0, errors=0, skipped=0), 'Incomplete Java verification')
require(all(p.stat().st_mtime <= log.stat().st_mtime for p in sources), 'Source changed since verification')
jar = ROOT / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
jar_hash = sha(jar)
client = ROOT / '.dsh-tmp/m1058-final-client.log'
text = client.read_text(encoding='utf-8', errors='replace')
require(all(re.search(p, text) for p in [r'pass\s+31\b', r'fail\s+0\b', r'skipped\s+0\b']), 'Client failure')
require(not (ROOT / '.dsh-tmp/m1058-sdk-typecheck.log').read_text(encoding='utf-8').strip(), 'SDK typecheck failure')
records = {}
fixtures = [('M10.5.8-search-results.json', 28), ('M10.5.7-extensions-results.json',35), ('M10.5.6-content-results.json',45),
            ('M10.5.5-questions-results.json',26), ('M10.5.4-models-results.json',22), ('M10-settings-browser-results.json',26),
            ('M10.5.3-turns-results.json',15), ('M10.5.2-history-results.json',19), ('M10.5.1-diagnosis-results.json',13),
            ('M10-browser-results.json',28), ('M10-launcher-results.json',8), ('M10.5.8-examples-results.json',2)]
for name, expected in fixtures:
    data = load(CACHE / name)
    require(data['jarSha256'] == jar_hash, 'Different acceptance JAR: ' + name)
    checks = data['checks']
    require((expected is None or len(checks) == expected) and checks and all(isinstance(c,str) or c.get('passed') for c in checks), 'Failed checks: '+name)
    require(data.get('paidRequests', data.get('runtime',{}).get('paidRequests')) == 0, 'Paid provider request')
    require(data.get('fixtureCredentialsCleaned') if name.startswith('M10-settings') else data.get('status') == 'PASS', 'Fixture failed')
    for image in data.get('screenshots',[]):
        require(sha(ROOT / image['path'].replace(chr(92), '/')) == image['sha256'], 'Screenshot drift')
    for path, digest in data.get('sourceSha256',{}).items():
        require(sha(ROOT / path) == digest, 'Acceptance source drift')
    output = re.sub(r'^M10(?:\.5\.[1-8])?-','M10.5.8-',name)
    (DEST / output).write_bytes((CACHE / name).read_bytes())
    records[output] = dict(sha256=sha(DEST / output), checks=len(checks))
sdk = load(CACHE / 'M10.5.8-sdk-results.json')
require(sdk['status'] == 'PASS' and sdk['jarSha256'] == jar_hash and sdk['paidRequests'] == 0 and sdk['allAuthorized'] and sdk['modelContextRedacted'], 'SDK failure')
require(len(sdk['results']) == 2 and all(len(r['checks']) == 29 for r in sdk['results']), 'Incomplete SDK')
(DEST / 'M10.5.8-sdk-results.json').write_bytes((CACHE / 'M10.5.8-sdk-results.json').read_bytes())
records['M10.5.8-sdk-results.json'] = dict(sha256=sha(DEST / 'M10.5.8-sdk-results.json'), checks=58)
native = load(CACHE / 'M10.5.4-native-credentials.json')
require(native.get('expectationMet') and native.get('passed') and native['jarSha256'] == jar_hash, 'Native current-JAR failure')
require(native['sourceSha256'] == {p.relative_to(ROOT).as_posix():sha(p) for p in sources}, 'Native source drift')
(DEST / 'M10.5.8-current-native-results.json').write_bytes((CACHE / 'M10.5.4-native-credentials.json').read_bytes())
native_paths = ['harness-cli/src/main/java/io/jharness/cli/SystemCredentials.java', 'harness-cli/src/main/java/io/jharness/cli/NativeCredentials.java']
prior = load(DEST / 'M10.5.7-results.json')
require(all(prior['sourceSha256'][p] == sha(ROOT / p) for p in native_paths), 'Native sources changed')
with zipfile.ZipFile(jar) as archive:
    files = archive.read('ui/ui-files.txt').decode().splitlines()
    require({n for n in archive.namelist() if n.startswith('ui/') and not n.endswith('/')} == {'ui/'+p for p in files}|{'ui/ui-files.txt'}, 'Stale UI')
    ui = {p:hashlib.sha256(archive.read('ui/'+p)).hexdigest() for p in files}
    require(all(sha(ROOT/'frontend/dist'/p) == digest for p,digest in ui.items()), 'UI/JAR mismatch')
    require(all(n in archive.namelist() for n in ['io/jharness/cli/StdioSdkServer.class','io/jharness/session/TokenSessionSearch.class']), 'Missing new services')
    require('Copyright (c) 2026 DeepSeek' in archive.read('ui/ui-license.txt').decode(), 'Missing attribution')
frontend = sorted(p for p in (ROOT/'frontend/src').rglob('*') if p.is_file()) + sorted((ROOT/'frontend/tests').glob('*'))
sdk_files = sorted(p for p in (ROOT/'sdk').rglob('*') if p.is_file() and '__pycache__' not in p.parts)
result = dict(milestone='M10.5.8',status='PASS',capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
              scope='Local authorized content search, independent stdio 1.0, TypeScript/Python SDK and examples; M10.5.9 remains open',
              apiProtocol='1.14',stdioProtocol='1.0',jsonlVersion=1,webCatalogVersion=4,totals=totals,suites=suites,
              sourceSha256={p.relative_to(ROOT).as_posix():sha(p) for p in sources},
              frontendSha256={p.relative_to(ROOT).as_posix():sha(p) for p in frontend},
              sdkSha256={p.relative_to(ROOT).as_posix():sha(p) for p in sdk_files},
              jarSha256=jar_hash,uiSha256=ui,records=records,currentNative=dict(sha256=sha(DEST/'M10.5.8-current-native-results.json'),checks=native['checks']),
              historicalNativeCI=dict(path='docs/testing/M10.5.4-native-ci-results.json',sha256=sha(DEST/'M10.5.4-native-ci-results.json'),unchangedSources=native_paths),
              logs={p.relative_to(ROOT).as_posix():sha(p) for p in [log,client]},collectorSha256=sha(Path(__file__)),
              developmentAttempts={p.relative_to(ROOT).as_posix():sha(p) for p in sorted((ROOT/'.dsh-tmp').glob('m1058*.log')) if p.is_file() and p.name != 'm1058-stage-review.log'},
              paidRequests=0,pushed=False,referenceProjectModified=False)
(DEST/'M10.5.8-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(totals=totals,sourceFiles=len(sources),records=records),ensure_ascii=False))
