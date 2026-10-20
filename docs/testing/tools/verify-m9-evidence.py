"""Offline audit of downloaded Actions evidence. Run from any directory with the run ID.

Metadata: docs/testing/M9-ci-{run,jobs,artifacts}.json.
Archives: .build-cache/M9-hosted-final/{windows,linux,macos}.zip.
Optional logs: .build-cache/M9-job-<job_id>.log (additionally verified when present).
The verifier rejects source changes, missing/failed jobs, artifact/report hashes,
wrong candidate commits, incomplete tests/scans and missing legal resources.
No network requests or product-source/Git-index changes; verified evidence JSON is written."""

# Historical acceptance is immutable; retained code is reference only.
if __name__ == '__main__':
    import sys
    sys.stderr.write('HISTORICAL_EVIDENCE_FROZEN: this collector cannot regenerate historical acceptance. Use scripts/collect-m1059-evidence.py with fresh evidence and scripts/archive-m1059-local.py for a new archive.\n')
    raise SystemExit(2)
import datetime
import hashlib
import io
import json
import re
from pathlib import Path
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET

if not __debug__:
    raise SystemExit('Evidence assertions must be enabled; do not use Python -O')

ROOT = Path(__file__).resolve().parents[3]
RUN_ID = int(sys.argv[1])
CACHE = ROOT / '.build-cache/M9-hosted-final'
OUT = ROOT / 'docs/testing'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def data(path):
    return json.loads(path.read_bytes())

run = data(OUT / 'M9-ci-run.json')
assert run['id'] == RUN_ID
assert re.fullmatch('[0-9a-f]{40}', run['head_sha'])
assert run['html_url'] == 'https://github.com/nixaveq141-ai/J-Harness/actions/runs/' + str(RUN_ID)
assert run['status'] == 'completed' and run['conclusion'] == 'success'
jobs = data(OUT / 'M9-ci-jobs.json')['jobs']
artifacts = data(OUT / 'M9-ci-artifacts.json')['artifacts']
assert len(jobs) == 3 and all(j['conclusion'] == 'success' and j['run_id'] == RUN_ID for j in jobs)
source_diff = subprocess.check_output(['git', 'diff', run['head_sha'], '--', '*.java', '*pom.xml'], cwd=ROOT)
assert not source_diff, 'Current Java/POM sources differ from tested commit'
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
modules = [e.text for e in ET.parse(ROOT / 'pom.xml').findall('m:modules/m:module', ns)]
expected_paths = {ROOT / 'pom.xml'}
for module in modules:
    expected_paths.add(ROOT / module / 'pom.xml')
    expected_paths.update((ROOT / module / 'src').rglob('*.java'))
expected_sources = {p.relative_to(ROOT).as_posix() for p in expected_paths}

receipt = {'milestone': 'M9', 'status': 'SUCCESS',
           'verifiedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
           'run': {k: run[k] for k in ['id', 'head_sha', 'html_url', 'status', 'conclusion', 'created_at', 'updated_at']},
           'platforms': [], 'allSourceAndTestNamesMatch': True, 'paidModelRequests': 0}
to_write = {}
all_sources, all_tests = [], []
for label, key in [('windows-latest', 'windows'), ('ubuntu-latest', 'linux'), ('macos-latest', 'macos')]:
    job = next(j for j in jobs if j['name'] == f'verify ({label})')
    assert all(next(s for s in job['steps'] if s['name'] == step)['conclusion'] == 'success'
               for step in ['Validate release gates and install candidate', 'Upload test evidence'])
    artifact = next(a for a in artifacts if a['name'] == 'test-results-' + label)
    assert not artifact['expired']
    raw_zip = (CACHE / (key + '.zip')).read_bytes()
    assert sha(raw_zip) == artifact['digest'].removeprefix('sha256:')
    assert len(raw_zip) == artifact['size_in_bytes']
    with zipfile.ZipFile(io.BytesIO(raw_zip)) as z:
        raw = {name: z.read('target/' + name) for name in ['release-evidence.json', 'candidate-smoke.json', 'osv-results.json', 'bom.json']}
        evidence, smoke, security, bom = (json.loads(raw[name]) for name in raw)
        assert evidence['commit'] == run['head_sha'] and evidence['ciRunUrl'] == run['html_url']
        assert evidence['buildResult'] == 'SUCCESS' and not evidence['workingTreeDirty']
        assert evidence['totals']['tests'] >= 296 and all(evidence['totals'][k] == 0 for k in ['failures','errors','skipped'])
        assert evidence['logSha256'] == sha(z.read('verify.log'))
        for suite in evidence['suites']:
            path = suite['module'] + '/target/surefire-reports/TEST-' + suite['name'] + '.xml'
            assert suite['sha256'] == sha(z.read(path)), path
        sources = evidence['sourceSha256']
        assert set(sources) == expected_sources, 'Incomplete source fingerprint set'
        for name, digest in sources.items():
            path = (ROOT / name).resolve()
            assert path.is_relative_to(ROOT)
            assert sha(path.read_bytes()) == digest, name
        names = sorted(s['module'] + ':' + s['name'] + '#' + case for s in evidence['suites'] for case in s['cases'])
        assert len(names) == len(set(names)) == evidence['totals']['tests']
        all_sources.append(sources)
        all_tests.append(names)
        assert smoke['status'] == 'PASS' and smoke['modelRequests'] == 7 and smoke['paidRequests'] == 0
        assert all(smoke['checks'].values()) and len(smoke['checks']) == 5
        external = {c['group'] + ':' + c['name']: c['version'] for c in bom['components'] if c['group'] != 'io.jharness'}
        assert len(external) == 18
        assert security['status'] == 'PASS' and security['activeFindings'] == 0 and security['packageCount'] == 18
        assert security['bomSha256'] == sha(raw['bom.json'])
        assert {p['package']['name']: p['version'] for p in security['packages']} == external
        assert not any(p['vulnerabilities'] for p in security['packages'])
        cli_component = next(c for c in bom['components'] if c['group'] == 'io.jharness' and c['name'] == 'harness-cli')
        assert next(h['content'] for h in cli_component['hashes'] if h['alg'] == 'SHA-256') == evidence['jarSha256']
        candidate = z.read('target/jharness-0.1.0-SNAPSHOT-candidate.zip')
        assert sha(candidate) == smoke['zipSha256'] == z.read('target/candidate-sha256.txt').decode().split()[0]
        with zipfile.ZipFile(io.BytesIO(candidate)) as bundle:
            manifest_bytes = bundle.read('jharness/manifest.json')
            manifest = json.loads(manifest_bytes)
            assert manifest['gitCommit'] == run['head_sha'] and not manifest['workingTreeDirty']
            assert {p: manifest['sourceSha256'][p] for p in sources} == sources
            assert set(bundle.namelist()) == {'jharness/' + p for p in manifest['files']} | {'jharness/manifest.json'}
            for name, digest in manifest['files'].items():
                assert sha(bundle.read('jharness/' + name)) == digest, name
            assert bundle.read('jharness/bom.json') == raw['bom.json']
            assert bundle.read('jharness/osv-results.json') == raw['osv-results.json']
            jar = bundle.read('jharness/lib/jharness.jar')
            assert sha(jar) == evidence['jarSha256']
            with zipfile.ZipFile(io.BytesIO(jar)) as executable:
                for name in manifest['files']:
                    if name.startswith('third-party/'):
                        assert executable.read('META-INF/jharness/' + name) == bundle.read('jharness/' + name)
        raw['manifest.json'] = manifest_bytes
        for name, content in raw.items():
            suffix = {'release-evidence.json': 'results.json', 'candidate-smoke.json': 'smoke.json',
                      'osv-results.json': 'security.json', 'bom.json': 'sbom.json', 'manifest.json': 'manifest.json'}[name]
            to_write[f'M9-hosted-{key}-{suffix}'] = content
        receipt['platforms'].append({'runnerLabel': label, 'jobId': job['id'], 'jobUrl': job['html_url'],
              'platform': evidence['platform'], 'architecture': evidence['architecture'],
              'javaVersions': evidence['javaVersions'], 'totals': evidence['totals'],
              'artifactId': artifact['id'], 'artifactUrl': run['html_url'] + '/artifacts/' + str(artifact['id']),
              'artifactZipSha256': sha(raw_zip), 'jarSha256': sha(jar), 'candidateZipSha256': sha(candidate),
              'bomSha256': sha(raw['bom.json']), 'testNamesSha256': sha(json.dumps(names, ensure_ascii=True).encode()),
              'sourceFileCount': len(sources), 'checks': {'artifactDigest': True, 'rawReportsAndLog': True,
              'allCases': True, 'sourcesMatchCurrent': True, 'securityAndBom': True,
              'installedCandidateAndManifest': True, 'legalResourcesInJar': True}})
        log_path = ROOT / f'.build-cache/M9-job-{job["id"]}.log'
        if log_path.exists():
            job_log = log_path.read_bytes()
            job_text = job_log.decode('utf-8', errors='replace')
            assert 'Ran 5 tests' in job_text and 'paidRequests' in job_text
            entry = receipt['platforms'][-1]
            entry.update(jobLogSha256=sha(job_log), offlineGateTests=5)
            lines = job_text.splitlines()
            for index, line in enumerate(lines):
                if 'Operating System' in line:
                    entry['operatingSystemDetails'] = [v.split('Z ', 1)[-1] for v in lines[index + 1:index + 4]]
                    break
assert all_sources[0] == all_sources[1] == all_sources[2]
assert all_tests[0] == all_tests[1] == all_tests[2]
receipt['sourceFileCount'] = len(all_sources[0])
receipt['evidenceSha256'] = {name: sha(content) for name, content in sorted(to_write.items())}
receipt['scope'] = 'Hosted Windows/Linux/macOS on exact recorded OS/architecture; Windows10 and independent WSL remain untested; no formal Release publication'
for name, content in to_write.items():
    (OUT / name).write_bytes(content)
(OUT / 'M9-ci-results.json').write_bytes((json.dumps(receipt, indent=2) + '\n').encode())
print(json.dumps({'status': receipt['status'], 'run': RUN_ID, 'source': run['head_sha'], 'sourceFileCount': receipt['sourceFileCount'],
                  'platforms': [{k: p[k] for k in ['runnerLabel', 'platform', 'architecture', 'totals', 'jarSha256', 'candidateZipSha256']} for p in receipt['platforms']]}, indent=2))
