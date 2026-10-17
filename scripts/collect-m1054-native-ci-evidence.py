"""Validate downloaded native CI receipts, their source identity and actual cleanup steps."""
import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
cache = root / '.build-cache'
run_id = int(sys.argv[1])


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path):
    return json.loads(path.read_bytes())


run = next(r for r in load(cache / 'M10.5.4-workflow-runs.json')['workflow_runs'] if r['id'] == run_id)
require(run['head_branch'] == 'codex/m1054-native-validation'
        and run['status'] == 'completed' and run['conclusion'] == 'success', 'CI not completed successfully')
commit = run['head_sha']
require(re.fullmatch('[a-f0-9]{40}', commit), 'Invalid CI commit')
require(not subprocess.check_output(['git', 'diff', '--name-only', commit, '--', '*.java', 'pom.xml', '*/pom.xml'], cwd=root),
        'Current Java/POM differs from tested commit')
sources = [root / 'pom.xml'] + sorted(root.glob('harness-*/pom.xml')) + sorted(root.glob('harness-*/src/**/*.java'))
manifest = {p.relative_to(root).as_posix(): sha(p) for p in sources}
require(len(manifest) == 280, 'Incomplete source manifest')
jobs = load(cache / f'M10.5.4-jobs-{run_id}.json')['jobs']
artifacts = load(cache / f'M10.5.4-artifacts-{run_id}.json')['artifacts']
require(len(jobs) == 3 and all(j['status'] == 'completed' and j['conclusion'] == 'success' for j in jobs), 'Incomplete jobs')
platforms = {}
for label in ['windows-2022', 'ubuntu-24.04', 'macos-15']:
    job = next(j for j in jobs if j['name'] == f'native-credentials ({label})')
    steps = {s['name']: s['conclusion'] for s in job['steps']}
    require(steps.get('Upload native evidence') == 'success', 'Artifact upload not successful')
    require(steps.get('Verify Linux native persistence' if label == 'ubuntu-24.04'
                      else 'Verify Windows and macOS native persistence') == 'success', 'Native probe step failed')
    if label == 'macos-15':
        require(steps.get('Prepare isolated macOS test keychain') == 'success'
                and steps.get('Delete isolated macOS test keychain') == 'success', 'macOS keychain cleanup failed')
    artifact = next(a for a in artifacts if a['name'] == f'native-credentials-{label}')
    archive = cache / f'M10.5.4-ci-{run_id}-{label}.zip'
    require(not artifact['expired'] and archive.stat().st_size < 2 * 1024 * 1024, 'Artifact unavailable or too large')
    require(artifact['digest'] == 'sha256:' + sha(archive), 'Artifact digest mismatch')
    cases = {}
    expected = ['credentials', 'missing-service', 'locked-service'] if label == 'ubuntu-24.04' else ['credentials']
    with zipfile.ZipFile(archive) as z:
        require(set(z.namelist()) == {f'M10.5.4-native-{case}.json' for case in expected}, 'Unexpected artifact contents')
        for case in expected:
            name = f'M10.5.4-native-{case}.json'
            require(z.getinfo(name).file_size < 256 * 1024, 'Receipt too large')
            raw = z.read(name)
            data = json.loads(raw)
            require(data['sourceSha256'] == manifest and data['expectationMet'], 'Source or expectation mismatch')
            require(data['probeScriptSha256'] == sha(root / 'scripts/test-native-credentials.py'), 'Probe script drift')
            if case == 'credentials':
                require(data['passed'] and data['exitCode'] == 0 and len(data['checks']) == 7
                        and all(data['checks'].values()), 'Native persistence failed')
            else:
                require(data['passed'] is False and data['expectedUnavailable'] and data['exitCode'] != 0,
                        'Unavailable counted as persistence')
            cases[case] = {k: v for k, v in data.items() if k != 'sourceSha256'}
            cases[case]['receiptSha256'] = hashlib.sha256(raw).hexdigest()
    platforms[label] = dict(jobId=job['id'], jobUrl=job['html_url'], conclusion=job['conclusion'],
                           artifactId=artifact['id'], artifactSha256=sha(archive), cases=cases)

log = cache / 'M10.5.4-ci-final-retry-verify.log'
require('BUILD SUCCESS' in log.read_text(encoding='utf-8'), 'No current local Java verification')
require(all(p.stat().st_mtime <= log.stat().st_mtime for p in sources), 'Local Java/POM changed after verify')
suites = [ET.parse(p).getroot() for p in root.glob('harness-*/target/surefire-reports/TEST-*.xml')]
totals = {k: sum(int(s.get(k, '0')) for s in suites) for k in ['tests', 'failures', 'errors', 'skipped']}
require(totals == dict(tests=338, failures=0, errors=0, skipped=0), 'Incomplete local Java tests')
jar_hash = sha(root / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar')
local_native = load(cache / 'M10.5.4-native-credentials.json')
require(local_native['jarSha256'] == jar_hash and local_native['sourceSha256'] == manifest
        and local_native['passed'] and local_native['expectationMet'], 'Local native probe drift')
with zipfile.ZipFile(root / 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar') as z:
    files = z.read('ui/ui-files.txt').decode().splitlines()
    require({p for p in z.namelist() if p.startswith('ui/') and not p.endswith('/')}
            == {'ui/' + p for p in files} | {'ui/ui-files.txt'}, 'Stale packaged UI')
    ui_manifest = {p: hashlib.sha256(z.read('ui/' + p)).hexdigest() for p in files}
    require(all(sha(root / 'frontend/dist' / p) == digest for p, digest in ui_manifest.items()), 'Local UI/JAR drift')
browser = load(cache / 'M10-settings-browser-results.json')
compat = load(cache / 'M10.5.4-windows-compat.json')
require(browser['jarSha256'] == jar_hash and len(browser['checks']) == 26
        and all(c['passed'] for c in browser['checks']) and browser['fixtureCredentialsCleaned']
        and browser['paidRequests'] == 0, 'Chrome regression incomplete')
require(compat['currentJarSha256'] == jar_hash and compat['passed'] and compat['exitCode'] == 0
        and len(compat['checks']) == 4 and all(compat['checks'].values()), 'Old/new credential compatibility failed')
evidence = dict(scope='M10.5.4 native credentials', status='PASS', milestoneStatus='IN_PROGRESS',
                capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                runId=run_id, runUrl=run['html_url'], sourceCommit=commit,
                sourceSha256=manifest, platforms=platforms, localJava=totals,
                localJarSha256=jar_hash, uiFilesSha256=ui_manifest,
                localNative={k: v for k, v in local_native.items() if k != 'sourceSha256'},
                chromeSettings=browser, windowsCompatibility=compat,
                localVerifyLogSha256=sha(log), collectionToolSha256=sha(Path(__file__)), paidModelRequests=0,
                limits=['CI runs native probes, not the full Java/browser release matrix.',
                        'Each platform JAR hash is recorded separately; source manifests must match.',
                        'Model selection, frozen per-turn configuration, capabilities and multi-protocol work remain pending.'])
(root / 'docs/testing/M10.5.4-native-ci-results.json').write_bytes((json.dumps(evidence, indent=2) + '\n').encode())
print('PASS: three native OS stores, Linux unavailable/locked cases, 280 source hashes; local Java338/Chrome26 and old/new Windows compatibility4. M10.5.4 remains in progress.')
