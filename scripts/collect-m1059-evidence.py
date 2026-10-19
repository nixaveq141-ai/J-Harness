"""Validate one current platform; a local pass never grants the cross-platform M11 gate."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.build-cache/M10.5.9'
EXPECTED={'browser':28,'diagnosis':13,'history':19,'turns':15,'models':22,'questions':26,'content':45,'extensions':35,'search':28,'settings':26,'development':14}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def load(p):return json.loads(p.read_bytes())
def require(value,message):
    if not value:raise ValueError(message)

def collect(log):
    require('BUILD SUCCESS' in log.read_text(encoding='utf-8',errors='replace'),'No successful full verification')
    suites=[]
    for p in sorted(ROOT.glob('harness-*/target/surefire-reports/TEST-*.xml')):
        xml=ET.parse(p).getroot();require(p.stat().st_mtime<=log.stat().st_mtime,'Newer report')
        suites.append(dict(name=xml.get('name'),cases=sorted(e.get('name') for e in xml.findall('testcase')),sha256=sha(p),**{k:int(xml.get(k,'0')) for k in ['tests','failures','errors','skipped']}))
    totals={k:sum(s[k] for s in suites) for k in ['tests','failures','errors','skipped']}
    require(totals==dict(tests=410,failures=0,errors=0,skipped=0),'Incomplete Java verification')
    client=(OUT/'client.log').read_text(encoding='utf-8',errors='replace')
    require(all(re.search(p,client) for p in [r'pass\s+33\b',r'fail\s+0\b',r'skipped\s+0\b']),'Frontend client failure')
    require(not (OUT/'sdk-typecheck.log').read_text(encoding='utf-8').strip(),'SDK TypeScript failure')
    java=[ROOT/'pom.xml']+sorted(ROOT.glob('harness-*/pom.xml'))+sorted(ROOT.glob('harness-*/src/**/*.java'))
    require(all(p.stat().st_mtime<=log.stat().st_mtime for p in java),'Newer Java source')
    jar=ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar';jar_sha=sha(jar)
    matrix=load(OUT/'browser/matrix.json')
    ci=os.environ.get('GITHUB_ACTIONS')=='true'
    engines=set(matrix['engines'])
    require(matrix['status']=='PASS' and (engines=={'chromium','firefox','webkit'} if ci else {'chromium','webkit'}<=engines<={'chromium','firefox','webkit'}) and set(matrix['fixtures'])==set(EXPECTED),'Incomplete browser matrix')
    availability=None
    if 'firefox' not in engines:
        availability=load(OUT/'availability.json')
        require(availability['firefox']['status']=='UNAVAILABLE' and availability['firefox']['winerror']==14001,'Missing explicit Firefox availability evidence')
    runtime=load(OUT/'browser-runtime.json')
    require(runtime['scriptSha256']==sha(ROOT/'scripts/prepare-test-browsers.py'),'Browser preparation source drift')
    if runtime['status']=='PRIVATE_ASSEMBLY_MANIFEST':
        private=Path(runtime['privateRoot'])
        require(runtime['originalFilesUnchanged'] and all(sha(private/p)==h for p,h in runtime['originalFilesSha256'].items()),'Browser binary changed')
        require(sha(private/'mozglue.manifest')==runtime['privateManifestSha256'],'Private manifest changed')
    seen=set();records={}
    for entry in matrix['records']:
        key=(entry['engine'],entry['fixture']);require(key not in seen,'Duplicate browser evidence');seen.add(key)
        path=ROOT/entry['path'];require(sha(path)==entry['sha256'],'Browser record drift');r=load(path)
        require(r['jarSha256']==jar_sha and len(r['checks'])==EXPECTED[key[1]],'Wrong JAR or missing checks')
        require(all(isinstance(c,str) or c.get('passed') is True for c in r['checks']),'Failed browser check')
        require(r['browserEngine']==key[0] and r.get('browserVersion',r.get('browser')),'Unknown browser version')
        require(r['fixtureSourceSha256']==sha(ROOT/'frontend/tests'/str(key[1]+'.mjs')),'Fixture source drift')
        require(r.get('paidRequests',r.get('runtime',{}).get('paidRequests'))==0,'Unknown or paid requests')
        require(r['logSha256']==sha(path.with_suffix('.log')),'Browser log drift')
        for shot in r.get('screenshots',[]):require(sha(ROOT/shot['path'])==shot['sha256'],'Screenshot drift')
        for name,digest in r.get('sourceSha256',{}).items():require(sha(ROOT/name)==digest,'Browser source drift')
        records[entry['path']]=entry['sha256']
    require(len(seen)==len(EXPECTED)*len(engines),'Missing browser runs')
    sdk=load(ROOT/'.build-cache/M10.5.8-sdk-results.json');examples=load(ROOT/'.build-cache/M10.5.8-examples-results.json')
    for r in [sdk,examples]:require(r['status']=='PASS' and r['jarSha256']==jar_sha and r['paidRequests']==0,'SDK/example failure')
    require(len(sdk['results'])==2 and all(r['passed'] and len(r['checks'])==29 for r in sdk['results']),'Missing SDK checks')
    require(sdk['allAuthorized'] and sdk['modelContextRedacted'] and len(examples['checks'])==2 and all(c['passed'] and c.get('factsSha256') for c in examples['checks']),'Missing SDK facts')
    native=load(ROOT/'.build-cache/M10.5.4-native-credentials.json')
    require(native['expectationMet'] and native['passed'] and native['jarSha256']==jar_sha and all(native['checks'].values()),'Native failure')
    require(native['sourceSha256']=={p.relative_to(ROOT).as_posix():sha(p) for p in java},'Native source drift')
    if sys.platform=='win32':
        launcher=load(ROOT/'.build-cache/M10-launcher-results.json');require(launcher['status']=='PASS' and launcher['jarSha256']==jar_sha and len(launcher['checks'])==8,'Launcher failure')
    audit=load(ROOT/'target/frontend-audit.json');require(audit['metadata']['vulnerabilities']['total']==0,'Frontend vulnerabilities')
    osv=load(ROOT/'target/osv-results.json');require(osv['status']=='PASS' and osv['bomSha256']==sha(ROOT/'target/bom.json'),'Maven scan incomplete or findings')
    sdk_bom=load(ROOT/'target/sdk-source-inventory.json');require(sdk_bom['externalRuntimeDependencyCount']==0 and sdk_bom['bomSha256']==sha(ROOT/'target/sdk-bom.json'),'SDK dependency or BOM change')
    require(load(ROOT/'target/source-secrets.json')['status']=='PASS','Source secret-pattern findings')
    for name,digest in sdk_bom['sourceSha256'].items():require(sha(ROOT/name)==digest,'SDK inventory drift')
    smoke=load(ROOT/'target/candidate-smoke.json');require(smoke['status']=='PASS','Candidate smoke failed')
    with zipfile.ZipFile(jar) as archive:
        files=archive.read('ui/ui-files.txt').decode().splitlines();ui={p:hashlib.sha256(archive.read('ui/'+p)).hexdigest() for p in files}
        require({n for n in archive.namelist() if n.startswith('ui/') and not n.endswith('/')}=={'ui/'+p for p in files}|{'ui/ui-files.txt'},'Stale UI resource')
        require(all(sha(ROOT/'frontend/dist'/p)==h for p,h in ui.items()),'UI/JAR mismatch')
        require('Copyright (c) 2026 DeepSeek' in archive.read('ui/ui-license.txt').decode(),'Missing UI attribution')
    for item in load(ROOT/'frontend/upstream.json')['files']:
        require(sha(ROOT/'frontend'/item['destination'])==item['sha256'],'Copied upstream source changed')
    with zipfile.ZipFile(ROOT/'target/jharness-0.1.0-SNAPSHOT-candidate.zip') as archive:
        for name,h in sdk_bom['sourceSha256'].items():require(hashlib.sha256(archive.read('jharness/'+name)).hexdigest()==h,'SDK candidate mismatch')
    inputs=set(java)
    for folder in ['frontend/src','frontend/tests','frontend/scripts','sdk','scripts','.github/workflows','third-party']:
        inputs.update(p for p in (ROOT/folder).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
    inputs.update(ROOT/p for p in ['frontend/package.json','frontend/package-lock.json','frontend/tsconfig.json','frontend/vite.config.ts','LICENSE','NOTICE','docs/examples/m1059-distribution.md'])
    report=dict(milestone='M10.5.9',status='RUNNER_PASS' if ci else 'LOCAL_PASS_CI_PENDING' if len(engines)==3 else 'LOCAL_PARTIAL_CI_PENDING',m11Eligible=False,
                capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),platform=platform.platform(),architecture=platform.machine(),runnerOs=os.environ.get('RUNNER_OS','local'),
                toolVersions=dict(python=sys.version,node=subprocess.check_output(['node','--version']).decode().strip(),playwright=load(ROOT/'frontend/node_modules/playwright/package.json')['version']),
                commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip(),workingTreeDirty=bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT)),ciRunId=os.environ.get('GITHUB_RUN_ID'),
                java=totals,suites=suites,frontendClientChecks=33,browserChecks=sum(EXPECTED.values())*len(engines),browserMatrix=matrix,browserAvailability=availability,browserRuntime=runtime,sdkChecks=58,exampleChecks=2,nativeChecks=len(native['checks']),
                jarSha256=jar_sha,uiSha256=ui,sourceSha256={p.relative_to(ROOT).as_posix():sha(p) for p in sorted(inputs)},
                records=records,supplyChain={n:sha(ROOT/'target'/n) for n in ['bom.json','frontend-bom.json','frontend-audit.json','sdk-bom.json','sdk-source-inventory.json','osv-results.json','source-secrets.json','release-evidence.json','candidate-smoke.json','jharness-0.1.0-SNAPSHOT-candidate.zip']},
                verificationLogSha256=sha(log),paidRequests=0,remoteExecution=ci)
    OUT.mkdir(parents=True,exist_ok=True);(OUT/'runner.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for name,source in [('sdk.json',ROOT/'.build-cache/M10.5.8-sdk-results.json'),('examples.json',ROOT/'.build-cache/M10.5.8-examples-results.json'),('native.json',ROOT/'.build-cache/M10.5.4-native-credentials.json')]:
        (OUT/name).write_bytes(source.read_bytes())
    print(report['status'],totals,'browserChecks=',report['browserChecks'])

if __name__=='__main__':collect(ROOT/sys.argv[1])
