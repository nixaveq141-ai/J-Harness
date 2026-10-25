"""Validate one current platform; a local pass never grants the cross-platform M11 gate."""
import datetime
import hashlib
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import zipfile
from evidence_io import bounded_path, read_json_snapshot, atomic_write_json, stream_sha256
from evidence_run import inputs, valid_run_id, validate as validate_command
from evidence_checks import suite_snapshot, sdk_checks, native_checks, names, CONTRACT, firefox_unavailable, firefox_executable, validate_java_suites

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.build-cache/M10.5.9'
EXPECTED={'browser':28,'diagnosis':13,'history':19,'turns':15,'models':22,'questions':26,'content':45,'extensions':35,'search':28,'settings':26,'development':14}
def sha(p):return stream_sha256(p)
SNAPSHOTS={}
def load(p):
    snapshot=read_json_snapshot(ROOT,p.relative_to(ROOT).as_posix())
    SNAPSHOTS[p.relative_to(ROOT).as_posix()]=snapshot
    return snapshot.data
def require(value,message):
    if not value:raise ValueError(message)

def collect(log):
    SNAPSHOTS.clear()
    run_id=os.environ.get('JHARNESS_EVIDENCE_RUN_ID')
    # Revoke an old PASS before any validation can fail.
    atomic_write_json(OUT/'runner.json',dict(milestone='M10.5.9',status='INCOMPLETE',m11Eligible=False,runId=run_id))
    require(valid_run_id(run_id),'Missing or invalid explicit evidence run identity')
    source=inputs(ROOT)
    receipt_hashes={}
    command_specs={
        'verify':(log,ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'),
        'client':(OUT/'client.log',None),
        'sdk-typecheck':(OUT/'sdk-typecheck.log',None),
        'native':(OUT/'native.log',ROOT/'.build-cache/M10.5.4-native-credentials.json'),
        'sdk':(OUT/'sdk.log',ROOT/'.build-cache/M10.5.8-sdk-results.json'),
        'examples':(OUT/'examples.log',ROOT/'.build-cache/M10.5.8-examples-results.json'),
        'browser':(OUT/'browser.log',OUT/'browser/matrix.json'),
        'smoke':(OUT/'smoke.log',ROOT/'target/candidate-smoke.json')}
    if sys.platform=='win32':
        command_specs['launcher']=(OUT/'launcher.log',ROOT/'.build-cache/M10-launcher-results.json')
        command_specs['firefox-probe']=(OUT/'firefox-probe.log',OUT/'availability.json')
    expected_commands={
        'verify':(['mvnw.cmd'] if sys.platform=='win32' else ['sh','./mvnw'])+['-B','-Prelease-audit','clean','verify','org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom'],
        'client':['npm','test'],
        'sdk-typecheck':['node','node_modules/typescript/bin/tsc','--ignoreConfig','--noEmit','--strict','--target','es2022','--module','nodenext','--moduleResolution','nodenext','--allowImportingTsExtensions','--types','node','--typeRoots','node_modules/@types','../sdk/typescript/src/index.ts','../sdk/examples/run.ts'],
        'native':[sys.executable,'scripts/test-native-credentials.py'],
        'sdk':['node','sdk/tests/run.mjs'],'examples':['node','sdk/tests/examples.mjs'],
        'browser':['node','scripts/run-workbench-matrix.mjs'],
        'smoke':['python','scripts/smoke-candidate.py'],
        'launcher':[sys.executable,'scripts/test-open-workbench.py'],
        'firefox-probe':[sys.executable,'scripts/probe-firefox.py']}
    for name,(command_log,output) in command_specs.items():
        receipt_path=OUT/'commands'/str(name+'.json');receipt=load(receipt_path)
        output_hashes={output.relative_to(ROOT).as_posix():sha(output)} if output else None
        validate_command(receipt,name=name,log=command_log,run_id=run_id,source=source,outputs=output_hashes,command=expected_commands[name],cwd='frontend' if name in {'client','sdk-typecheck'} else '.')
        receipt_hashes[receipt_path.relative_to(ROOT).as_posix()]=SNAPSHOTS[receipt_path.relative_to(ROOT).as_posix()].sha256
    suites=[]
    for p in sorted(ROOT.glob('harness-*/target/surefire-reports/TEST-*.xml')):
        suites.append(suite_snapshot(p,p.relative_to(ROOT).parts[0]))
    totals={k:sum(s[k] for s in suites) for k in ['tests','failures','errors','skipped']}
    java_contract=load(ROOT/'scripts/contracts/m1059-java-cases.json')
    validate_java_suites(suites,java_contract)
    client=(OUT/'client.log').read_text(encoding='utf-8',errors='replace')
    require(all(re.search(p,client) for p in [r'pass\s+42\b',r'fail\s+0\b',r'skipped\s+0\b']),'Frontend client failure')
    java=[ROOT/'pom.xml']+sorted(ROOT.glob('harness-*/pom.xml'))+sorted(ROOT.glob('harness-*/src/**/*.java'))
    require(all(p.stat().st_mtime<=log.stat().st_mtime for p in java),'Newer Java source')
    jar=ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar';jar_sha=sha(jar)
    matrix=load(OUT/'browser/matrix.json')
    require(matrix.get('runId')==run_id,'Browser matrix from another run')
    subprocess.run(['node','scripts/workbench-evidence.mjs','.build-cache/M10.5.9/browser/matrix.json'],cwd=ROOT,check=True,timeout=120)
    ci=os.environ.get('GITHUB_ACTIONS')=='true'
    engines=set(matrix['engines'])
    require(matrix['status']=='PASS' and (engines=={'chromium','firefox','webkit'} if ci else {'chromium','webkit'}<=engines<={'chromium','firefox','webkit'}) and set(matrix['fixtures'])==set(EXPECTED),'Incomplete browser matrix')
    availability=None
    if 'firefox' not in engines:
        availability=load(OUT/'availability.json')
        require(sys.platform=='win32','Firefox partial exception is Windows-only')
        firefox_unavailable(availability,run_id=run_id,executable=firefox_executable(ROOT),script_sha256=sha(ROOT/'scripts/probe-firefox.py'))
    runtime=load(OUT/'browser-runtime.json')
    require(runtime['scriptSha256']==sha(ROOT/'scripts/prepare-test-browsers.py'),'Browser preparation source drift')
    if runtime['status']=='PRIVATE_ASSEMBLY_MANIFEST':
        private=Path(runtime['privateRoot']).resolve(strict=True)
        require(private.is_relative_to((ROOT/'.build-cache').resolve(strict=True)),'Private browser root outside cache')
        require(runtime['originalFilesUnchanged'] is True and bool(runtime['originalFilesSha256']) and all(sha(bounded_path(private,p))==h for p,h in runtime['originalFilesSha256'].items()),'Browser binary changed')
        require(sha(private/'mozglue.manifest')==runtime['privateManifestSha256'],'Private manifest changed')
    seen=set();records={}
    contract=load(ROOT/'scripts/workbench-case-contract.json')
    expected_cases=contract.get('fixtures',contract)
    for entry in matrix['records']:
        key=(entry['engine'],entry['fixture']);require(key not in seen,'Duplicate browser evidence');seen.add(key)
        path=bounded_path(ROOT,entry['path']);r=load(path);require(SNAPSHOTS[entry['path']].sha256==entry['sha256'],'Browser record drift')
        require(r['jarSha256']==jar_sha and len(r['checks'])==EXPECTED[key[1]],'Wrong JAR or missing checks')
        require(all(isinstance(c,dict) and c.get('passed') is True for c in r['checks']),'Failed browser check')
        names([c.get('name') for c in r['checks']],expected_cases[key[1]],'Wrong browser case identities')
        require(r['browserEngine']==key[0] and r.get('browserVersion',r.get('browser')),'Unknown browser version')
        require(r['fixtureSourceSha256']==sha(ROOT/'frontend/tests'/str(key[1]+'.mjs')),'Fixture source drift')
        require(r.get('paidRequests',r.get('runtime',{}).get('paidRequests'))==0,'Unknown or paid requests')
        require(r['logSha256']==sha(path.with_suffix('.log')),'Browser log drift')
        require(bool(r.get('inputSha256')) and bool(r.get('screenshots')),'Missing browser source or screenshots')
        for shot in r['screenshots']:require(sha(bounded_path(ROOT,shot['path']))==shot['sha256'],'Screenshot drift')
        for name,digest in r.get('sourceSha256',{}).items():require(sha(bounded_path(ROOT,name))==digest,'Browser source drift')
        records[entry['path']]=entry['sha256']
    require(len(seen)==len(EXPECTED)*len(engines),'Missing browser runs')
    sdk=load(ROOT/'.build-cache/M10.5.8-sdk-results.json');examples=load(ROOT/'.build-cache/M10.5.8-examples-results.json')
    for r in [sdk,examples]:require(r['status']=='PASS' and r['jarSha256']==jar_sha and type(r['paidRequests']) is int and r['paidRequests']==0,'SDK/example failure')
    sdk_checks(sdk,examples)
    native=load(ROOT/'.build-cache/M10.5.4-native-credentials.json')
    native_checks(native);require(native['jarSha256']==jar_sha,'Wrong native JAR')
    require(native['sourceSha256']=={p.relative_to(ROOT).as_posix():sha(p) for p in java},'Native source drift')
    if sys.platform=='win32':
        launcher=load(ROOT/'.build-cache/M10-launcher-results.json');require(launcher['status']=='PASS' and launcher['jarSha256']==jar_sha and len(launcher['checks'])==8,'Launcher failure')
        names(launcher['checks'],CONTRACT['launcher'],'Missing launcher cases')
    audit=load(ROOT/'target/frontend-audit.json');require(type(audit['metadata']['vulnerabilities']['total']) is int and audit['metadata']['vulnerabilities']['total']==0,'Frontend vulnerabilities')
    osv=load(ROOT/'target/osv-results.json');require(osv['status']=='PASS' and osv['bomSha256']==sha(ROOT/'target/bom.json') and bool(osv.get('packages')) and all(p.get('vulnerabilities')==[] for p in osv['packages']),'Maven scan incomplete or findings')
    sdk_bom=load(ROOT/'target/sdk-source-inventory.json');require(type(sdk_bom['externalRuntimeDependencyCount']) is int and sdk_bom['externalRuntimeDependencyCount']==0 and sdk_bom['bomSha256']==sha(ROOT/'target/sdk-bom.json'),'SDK dependency or BOM change')
    secrets=load(ROOT/'target/source-secrets.json')
    require(secrets['status']=='PASS' and secrets.get('findings')==[] and bool(secrets.get('sourceSha256')) and type(secrets.get('sourceFiles')) is int and secrets['sourceFiles']==len(secrets['sourceSha256']),'Source secret-pattern findings or empty scope')
    for name,h in secrets['sourceSha256'].items():require(sha(bounded_path(ROOT,name))==h,'Secret scan source drift')
    require(bool(sdk_bom['sourceSha256']),'Empty SDK inventory')
    for name,digest in sdk_bom['sourceSha256'].items():require(sha(bounded_path(ROOT,name))==digest,'SDK inventory drift')
    smoke=load(ROOT/'target/candidate-smoke.json');require(smoke['status']=='PASS' and smoke.get('paidRequests')==0 and set(smoke.get('checks',{}))==set(CONTRACT['smoke']) and all(v is True for v in smoke['checks'].values()) and smoke.get('zipSha256')==sha(ROOT/'target/jharness-0.1.0-SNAPSHOT-candidate.zip'),'Candidate smoke failed or wrong ZIP')
    release=load(ROOT/'target/release-evidence.json')
    require(release.get('status')=='PASS' and release.get('milestone')=='M10.5.9' and release.get('runId')==run_id and release.get('jarSha256')==jar_sha and release.get('sourceSha256')==source and release.get('totals')==totals and sorted(release.get('suites',[]),key=lambda s:(s['module'],s['name']))==suites and release.get('logSha256')==sha(log),'Release evidence mismatch')
    with zipfile.ZipFile(jar) as archive:
        files=archive.read('ui/ui-files.txt').decode().splitlines();require(bool(files) and len(files)==len(set(files)),'Empty or duplicate UI files')
        for p in files:bounded_path(ROOT/'frontend/dist',p)
        ui={p:hashlib.sha256(archive.read('ui/'+p)).hexdigest() for p in files}
        require({n for n in archive.namelist() if n.startswith('ui/') and not n.endswith('/')}=={'ui/'+p for p in files}|{'ui/ui-files.txt','ui/ui-build.json'},'Stale UI resource')
        require(all(sha(ROOT/'frontend/dist'/p)==h for p,h in ui.items()),'UI/JAR mismatch')
        require('Copyright (c) 2026 DeepSeek' in archive.read('ui/ui-license.txt').decode(),'Missing UI attribution')
    import importlib.util
    spec=importlib.util.spec_from_file_location('ui_bundle',ROOT/'scripts/ui-bundle.py');ui_module=importlib.util.module_from_spec(spec);spec.loader.exec_module(ui_module)
    ui=ui_module.verify(ROOT,jar)
    for item in load(ROOT/'frontend/upstream.json')['files']:
        require(sha(bounded_path(ROOT/'frontend',item['destination']))==item['sha256'],'Copied upstream source changed')
    with zipfile.ZipFile(ROOT/'target/jharness-0.1.0-SNAPSHOT-candidate.zip') as archive:
        for name,h in sdk_bom['sourceSha256'].items():require(hashlib.sha256(archive.read('jharness/'+name)).hexdigest()==h,'SDK candidate mismatch')
    report=dict(milestone='M10.5.9',status='RUNNER_PASS' if ci else 'LOCAL_PASS_CI_PENDING' if len(engines)==3 else 'LOCAL_PARTIAL_CI_PENDING',m11Eligible=False,
                runId=run_id,commandReceipts=receipt_hashes,
                capturedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),platform=platform.platform(),architecture=platform.machine(),runnerOs=os.environ.get('RUNNER_OS','local'),
                toolVersions=dict(python=sys.version,node=subprocess.check_output(['node','--version']).decode().strip(),playwright=load(ROOT/'frontend/node_modules/playwright/package.json')['version']),
                commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip(),workingTreeDirty=bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT)),ciRunId=os.environ.get('GITHUB_RUN_ID'),
                java=totals,suites=suites,frontendClientChecks=42,browserChecks=sum(EXPECTED.values())*len(engines),browserMatrix=matrix,browserAvailability=availability,browserRuntime=runtime,sdkChecks=66,exampleChecks=2,nativeChecks=len(native['checks']),
                jarSha256=jar_sha,uiSha256=ui,sourceSha256=source,
                records=records,supplyChain={n:sha(ROOT/'target'/n) for n in ['bom.json','frontend-bom.json','frontend-audit.json','sdk-bom.json','sdk-source-inventory.json','osv-results.json','source-secrets.json','release-evidence.json','candidate-smoke.json','jharness-0.1.0-SNAPSHOT-candidate.zip']},
                verificationLogSha256=sha(log),paidRequests=0,remoteExecution=ci)
    require(inputs(ROOT)==source,'Build inputs changed during evidence collection')
    report['archiveInputs']={name:s.sha256 for name,s in SNAPSHOTS.items()}
    require(all(sha(ROOT/name)==s.sha256 for name,s in SNAPSHOTS.items()),'Evidence changed during collection')
    OUT.mkdir(parents=True,exist_ok=True)
    for name,source in [('sdk.json',ROOT/'.build-cache/M10.5.8-sdk-results.json'),('examples.json',ROOT/'.build-cache/M10.5.8-examples-results.json'),('native.json',ROOT/'.build-cache/M10.5.4-native-credentials.json')]:
        (OUT/name).write_bytes(SNAPSHOTS[source.relative_to(ROOT).as_posix()].raw)
    atomic_write_json(OUT/'runner.json',report)
    print(report['status'],totals,'browserChecks=',report['browserChecks'])

if __name__=='__main__':collect(ROOT/sys.argv[1])
