"""Require exact runtime JAR byte equality across every selected target."""
import hashlib
import os
from pathlib import Path
import subprocess
import zipfile
import re
from evidence_checks import suite_details, validate_java_suites, passed_checks, sdk_checks, native_checks, names, CONTRACT
from evidence_io import bounded_path, read_json_snapshot, stream_sha256, atomic_write_json
from evidence_run import inputs, validate
from r4_faults import validate_record as validate_r4, frontend_sources

ROOT=Path(__file__).resolve().parents[1]
COMMANDS={'stage-unit','source-verify','stage-candidate','native-coverage','sdk-inventory',
          'frontend-bom','frontend-audit','legal','legal-drift','osv','candidate','install-smoke','packaged-matrix'}


def require_identical_hashes(results,extended=False):
    expected={'windows-2022','ubuntu-24.04','macos-latest'}
    if extended:expected|={'ubuntu-24.04-arm','macos-15-intel'}
    if set(results)!=expected:
        raise ValueError('All selected target identities required')
    if len({v['runtimeJarSha256'] for v in results.values()})!=1:
        raise ValueError('All selected qualified runtime JARs must be byte-identical')


def verify_command_identity(name,command,windows):
    if not isinstance(command,list) or not command or any(not isinstance(v,str) for v in command):
        raise ValueError('Command argument vector required')
    if name=='source-verify':
        expected=(['mvnw.cmd'] if windows else ['sh','./mvnw'])+[
            '-B','-Prelease-audit','-DargLine=@../.build-cache/source-qualification/test-jvm.args',
            'clean','verify','org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom']
        if command!=expected:raise ValueError('Wrong source verification command')
        return
    direct={'frontend-audit':['npm.cmd' if windows else 'npm','--prefix','frontend','audit','--json'],
            'legal-drift':['git','diff','--exit-code','--','third-party'],
            'sdk':['node','sdk/tests/run.mjs'],'examples':['node','sdk/tests/examples.mjs'],
            'browser':['node','scripts/run-workbench-matrix.mjs']}
    if name.startswith('r4-') and name!='r4-gate':direct[name]=['node','frontend/tests/r4.mjs']
    if name in direct:
        if command!=direct[name]:raise ValueError('Wrong packaged validation command: '+name)
        return
    executable=command[0].replace('\\','/').split('/')[-1].lower()
    if not re.fullmatch(r'python(?:\d+(?:\.\d+)*)?(?:\.exe)?',executable):
        raise ValueError('Python evidence driver required')
    expected={
        'stage-unit':['scripts/stage-unified-native-runtime.py','--output','.build-cache/source-qualification/unit-stage'],
        'stage-candidate':['scripts/stage-unified-native-runtime.py','--output','.build-cache/source-qualification/candidate-stage'],
        'sdk-inventory':['scripts/sdk-inventory.py'],'frontend-bom':['scripts/frontend-sbom.py','target/frontend-bom.json'],
        'legal':['scripts/prepare-legal.py'],'osv':['scripts/scan-osv.py','target/bom.json','target/osv-results.json'],
        'candidate':['scripts/build-candidate.py'],'install-smoke':['scripts/smoke-candidate.py'],
        'packaged-matrix':['scripts/run-m1059-platform.py'],'native':['scripts/test-native-credentials.py'],
        'launcher':['scripts/test-open-workbench.py'],'firefox-probe':['scripts/probe-firefox.py'],
        'r4-gate':['scripts/test-r4-faults.py']}
    if name=='native-coverage':
        if (len(command)!=4 or command[1:3]!=['scripts/audit-native-coverage.py','harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar']
                or not command[3].replace('\\','/').endswith('/.build-cache/source-qualification/native-coverage.json')):
            raise ValueError('Wrong native coverage command')
    elif name not in expected or command[1:]!=expected[name]:raise ValueError('Wrong evidence command: '+name)


def verify_git_inputs(source,commit):
    if set(source)!=set(inputs(ROOT)):
        raise ValueError('Complete current build-input path set required')
    names=sorted(source)
    if any('\n' in n or '\r' in n or '\\' in n or '..' in Path(n).parts or Path(n).is_absolute() for n in names):
        raise ValueError('Invalid source input path')
    raw=subprocess.check_output(['git','cat-file','--batch'],cwd=ROOT,
        input=''.join(commit+':'+n+'\n' for n in names).encode())
    offset=0
    for name in names:
        end=raw.index(b'\n',offset);header=raw[offset:end].split()
        if len(header)!=3 or header[1]!=b'blob':raise ValueError('Missing committed source blob')
        size=int(header[2]);data=raw[end+1:end+1+size];offset=end+size+2
        if hashlib.sha256(data).hexdigest()!=source[name]:
            if (name not in {'mvnw.cmd','jharness.cmd'} or
                    hashlib.sha256(data.replace(b'\r\n',b'\n').replace(b'\n',b'\r\n')).hexdigest()!=source[name]):
                raise ValueError('Wrong committed source bytes: '+name)
    if offset!=len(raw):raise ValueError('Incomplete source batch')


def verify_packaged_matrix(base,run_id,source,digest,windows,commit):
    def load(name):return read_json_snapshot(base,name).data
    contract=read_json_snapshot(ROOT,'scripts/workbench-case-contract.json').data['fixtures']
    matrix=load('.build-cache/M10.5.9/browser/matrix.json')
    engines={'chromium','firefox','webkit'}
    if (matrix['status']!='PASS' or matrix['runId']!=run_id or set(matrix['engines'])!=engines
            or set(matrix['fixtures'])!=set(contract) or matrix['failures'] or matrix['paidRequests']!=0):
        raise ValueError('Complete same-run three-browser matrix required')
    seen=set()
    for entry in matrix['records']:
        key=(entry['engine'],entry['fixture'])
        if key in seen or key[0] not in engines or key[1] not in contract:raise ValueError('Duplicate or foreign browser fixture')
        seen.add(key);snap=read_json_snapshot(base,entry['path']);r=snap.data
        if (snap.sha256!=entry['sha256'] or r['status']!='PASS' or r['runId']!=run_id
                or r['jarSha256']!=digest or r['browserEngine']!=key[0] or r['fixture']!=key[1]
                or (key[1] in {'browser','diagnosis'} and r['runtime']['paidRequests']!=0)
                or type(r['command']['exitCode']) is not int or r['command']['exitCode']!=0
                or r['command']['timedOut'] is not False or r['command']['overflow'] is not False
                or r['command']['signal'] is not None):
            raise ValueError('Browser execution identity or bytes changed')
        passed_checks(r['checks'],contract[key[1]],'Missing browser case identities')
        if stream_sha256(bounded_path(base,entry['path']).with_suffix('.log'))!=r['logSha256']:
            raise ValueError('Browser command log changed')
        if not r.get('inputSha256') or not r.get('screenshots'):raise ValueError('Browser source or screenshots missing')
        for name,h in r['inputSha256'].items():
            if name in source:
                if source[name]!=h:raise ValueError('Browser source drift')
            elif ('\n' in name or '\r' in name or '\\' in name or '..' in Path(name).parts or Path(name).is_absolute()
                  or hashlib.sha256(subprocess.check_output(['git','show',commit+':'+name],cwd=ROOT)).hexdigest()!=h):
                raise ValueError('Browser supplemental source drift')
        for shot in r['screenshots']:
            if stream_sha256(bounded_path(base,shot['path']))!=shot['sha256']:raise ValueError('Browser screenshot drift')
    if len(seen)!=len(contract)*3:raise ValueError('Missing browser runs')
    r4_cases=read_json_snapshot(ROOT,'scripts/contracts/r4-fault-cases.json').data['cases']
    r4_sources={name:source['frontend/'+name] for name in frontend_sources(ROOT)}
    for engine in sorted(engines):
        record=load('.build-cache/R4/'+run_id+'/browser-'+engine+'.json')
        validate_r4(record,r4_cases,engine=engine,run_id=run_id,sources=r4_sources)
        if stream_sha256(bounded_path(base,record['screenshot']['path']))!=record['screenshot']['sha256']:
            raise ValueError('R4 screenshot drift')
    sdk=load('.build-cache/M10.5.8-sdk-results.json');examples=load('.build-cache/M10.5.8-examples-results.json')
    for record in (sdk,examples):
        if record['status']!='PASS' or record['jarSha256']!=digest or record['paidRequests']!=0:
            raise ValueError('SDK/examples execution mismatch')
    sdk_checks(sdk,examples)
    native=load('.build-cache/M10.5.4-native-credentials.json');native_checks(native)
    if native['jarSha256']!=digest:raise ValueError('Native runtime mismatch')
    if windows:
        launcher=load('.build-cache/M10-launcher-results.json')
        if launcher['status']!='PASS' or launcher['jarSha256']!=digest:raise ValueError('Launcher mismatch')
        names(launcher['checks'],CONTRACT['launcher'],'Missing launcher cases')
    for name in ['native','sdk','examples','browser','r4-gate','r4-chromium','r4-firefox','r4-webkit']+(['launcher','firefox-probe'] if windows else []):
        receipt=load('.build-cache/M10.5.9/commands/'+name+'.json')
        verify_command_identity(name,receipt['command'],windows)
        validate(receipt,name=name,log=bounded_path(base,'.build-cache/M10.5.9/'+name+'.log'),run_id=run_id,source=source,cwd='.')


def check(directory,commit,run,attempt='1',extended=False):
    results={}
    contract=read_json_snapshot(ROOT,'scripts/contracts/m1059-java-cases.json').data
    labels=['windows-2022','ubuntu-24.04','macos-latest']+(['ubuntu-24.04-arm','macos-15-intel'] if extended else [])
    for label in labels:
        base=directory/label
        r=read_json_snapshot(base,'.build-cache/source-qualification/result.json').data
        expected_platform={'windows-2022':'WINDOWS','ubuntu-24.04':'LINUX','macos-latest':'DARWIN',
                           'ubuntu-24.04-arm':'LINUX','macos-15-intel':'DARWIN'}[label]
        if (r['commit']!=commit or r['runId']!='unified-source-'+run+'-'+attempt+'-'+label+'-'+commit
                or r['status']!=expected_platform+'_SOURCE_JAVA_VERIFY_AND_CANDIDATE_INSTALL_PASS_NOT_RELEASE'
                or r['javaTests']!=735 or len(r['javaSuites'])!=124 or r['paidRequests']!=0):
            raise ValueError('Successful same-commit same-run full qualification required')
        validate_java_suites(r['javaSuites'],contract)
        actual=[]
        for suite in r['javaSuites']:
            path=bounded_path(base,suite['module']+'/target/surefire-reports/TEST-'+suite['name']+'.xml')
            row,_=suite_details(path,suite['module'])
            if row!=suite:raise ValueError('Actual Java XML differs from qualified report')
            actual.append(row)
        validate_java_suites(actual,contract)
        if set(r['commands'])!=COMMANDS:raise ValueError('Every qualification command receipt required')
        source=None
        for name in sorted(COMMANDS):
            snap=read_json_snapshot(base,'.build-cache/source-qualification/'+name+'-command.json')
            if snap.sha256!=r['commands'][name]['receiptSha256']:
                raise ValueError('Qualification command receipt bytes changed')
            verify_command_identity(name,snap.data['command'],label=='windows-2022')
            if source is None:source=snap.data['sourceSha256'];verify_git_inputs(source,commit)
            log=bounded_path(base,'.build-cache/source-qualification/'+name+'.log')
            validate(snap.data,name=name,log=log,run_id=r['runId'],source=source,cwd='.')
        jar=bounded_path(base,'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar')
        digest=stream_sha256(jar,max_bytes=256*1024*1024)
        if digest!=r['candidateStage']['runtimeJarSha256']:raise ValueError('Qualified JAR bytes changed')
        verify_packaged_matrix(base,r['runId'],source,digest,label=='windows-2022',commit)
        smoke=read_json_snapshot(base,'target/candidate-smoke.json').data
        if (smoke!=r['smoke'] or smoke['status']!='PASS' or smoke['paidRequests']!=0
                or set(smoke['checks'])!=set(CONTRACT['smoke']) or not all(v is True for v in smoke['checks'].values())):
            raise ValueError('Exact candidate install checks required')
        candidate=bounded_path(base,'target/jharness-0.1.0-SNAPSHOT-candidate.zip')
        candidate_hash=stream_sha256(candidate,max_bytes=256*1024*1024)
        if candidate_hash!=r['candidateZipSha256'] or candidate_hash!=smoke['zipSha256']:
            raise ValueError('Installed candidate ZIP changed')
        with zipfile.ZipFile(candidate) as archive:
            if len(archive.namelist())!=len(set(archive.namelist())):raise ValueError('Duplicate candidate entries')
            if archive.getinfo('jharness/lib/jharness.jar').file_size!=jar.stat().st_size:
                raise ValueError('Installed JAR size mismatch')
            with archive.open('jharness/lib/jharness.jar') as stream:
                installed=hashlib.file_digest(stream,'sha256').hexdigest()
            if installed!=digest:raise ValueError('Installed JAR differs from qualified runtime')
        results[label]=dict(runtimeJarSha256=digest,candidateZipSha256=r['candidateZipSha256'])
    require_identical_hashes(results,extended)
    return dict(status=('FIVE_TARGET' if extended else 'THREE_PLATFORM')+'_IDENTICAL_RUNTIME_JAR_QUALIFIED_NOT_RELEASE',
                commit=commit,runId=run,platforms=results,releaseApproved=False,
                scope='Byte-identical runtime only; outer candidate ZIPs include platform-specific evidence. Not full native provenance or OS/CPE certification.')


def main():
    output=ROOT/'.build-cache/unified-equality.json'
    atomic_write_json(output,dict(status='INCOMPLETE',releaseApproved=False))
    r=check(ROOT/'.build-cache/unified-check',os.environ['GITHUB_SHA'],os.environ['GITHUB_RUN_ID'],os.environ['GITHUB_RUN_ATTEMPT'],os.environ.get('JHARNESS_EXTENDED_SOURCE')=='1')
    atomic_write_json(output,r);print(r['status'])


if __name__=='__main__':main()
