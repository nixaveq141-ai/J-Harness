"""Qualify source-native Windows Java tests and candidate installation separately.

Uses accepted native bytes, explicit JVM paths and actual native-library load
logs. Does not turn a component scan into full OS/CPE or release approval.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import zipfile

from evidence_run import run
from evidence_checks import suite_details, validate_java_suites

ROOT=Path(__file__).resolve().parents[1]


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def validate_loads(raw_logs, jni_path, expected_sha):
    """Reject any successfully loaded alternate dispatch library, not just errors."""
    if sha(jni_path.read_bytes())!=expected_sha:
        raise ValueError('Source JNI bytes changed')
    hits=[]
    for name,raw in raw_logs.items():
        for line in raw.decode('utf-8',errors='strict').splitlines():
            match=re.search(r'Loaded library (.+?)\s*,?\s*handle\s+',line)
            if not match:continue
            path=match.group(1).rstrip(',')
            if 'jnidispatch' not in path.lower():continue
            if Path(path).resolve()!=jni_path.resolve():
                raise ValueError('Alternate JNI load rejected: '+name)
            hits.append(name)
    if not hits:raise ValueError('Actual source JNI library load required')
    return sorted(set(hits))


def main():
    if os.name!='nt' or os.environ.get('GITHUB_ACTIONS')!='true':
        raise ValueError('Dedicated Windows qualification CI required')
    out=ROOT/'.build-cache/source-qualification';out.mkdir()
    run_id=os.environ['JHARNESS_EVIDENCE_RUN_ID']
    report=dict(status='INCOMPLETE',runId=run_id,commit=os.environ['GITHUB_SHA'],
                releaseApproved=False,paidRequests=0)

    def command(name,argv,env=None,outputs=()):
        log=out/(name+'.log');receipt=out/(name+'-command.json')
        run(argv,name=name,log=log,receipt=receipt,run_id=run_id,cwd=ROOT,root=ROOT,
            env=env,timeout=2400,outputs=outputs)
        report.setdefault('commands',{})[name]=dict(logSha256=sha(log.read_bytes()),
                                                    receiptSha256=sha(receipt.read_bytes()))

    try:
        python=sys.executable
        command('stage-unit',[python,'scripts/stage-source-native-runtime.py','--output',
                              '.build-cache/source-qualification/unit-stage'])
        stage=json.loads((out/'unit-stage/stage.json').read_bytes())
        native=out/'native';jni=native/'jni';pty=native/'pty/win/x86-64'
        jni.mkdir(parents=True);pty.mkdir(parents=True)
        for resource,item in stage['nativeReplacements'].items():
            raw=(out/'unit-stage/native-inputs'/Path(resource).name).read_bytes()
            if sha(raw)!=item['sha256']:raise ValueError('Accepted payload drift')
            (jni/Path(resource).name if resource.startswith('com/sun/jna/') else pty/Path(resource).name).write_bytes(raw)
        env=dict(os.environ)
        if any(env.get(k) for k in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')):
            raise ValueError('Unreviewed inherited JVM options')
        # Pass options directly to Surefire forks. JAVA_TOOL_OPTIONS writes an
        # unsolicited stderr banner into exact-output subprocess protocols.
        # Do not weaken those assertions or alter unrelated child environments.
        options=['-Djna.nounpack=true','-Djna.nosys=true',
                 '-Djna.boot.library.path='+str(jni),
                 '-Dpty4j.preferred.native.folder='+str(native/'pty'),
                 '-Xlog:library=info:file=native-load-%p.log']
        argline=' '.join('"'+v+'"' for v in options)
        command('source-verify',['mvnw.cmd','-B','-Prelease-audit','-DargLine='+argline,'clean','verify',
                'org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom'],env)
        suites=[];properties={}
        for module in sorted(ROOT.glob('harness-*')):
            for path in sorted((module/'target/surefire-reports').glob('TEST-*.xml')):
                row,xml=suite_details(path,module.name);suites.append(row)
                props={p.get('name'):p.get('value') for p in xml.findall('properties/property')}
                for key,value in [('jna.nounpack','true'),('jna.nosys','true'),
                                  ('jna.boot.library.path',str(jni)),
                                  ('pty4j.preferred.native.folder',str(native/'pty'))]:
                    if props.get(key)!=value:raise ValueError('Source JVM properties missing: '+path.name)
                properties[path.relative_to(ROOT).as_posix()]=sha(path.read_bytes())
        report['javaTests']=validate_java_suites(suites,json.loads((ROOT/'scripts/contracts/m1059-java-cases.json').read_bytes()))
        report['javaSuites']=suites
        report['sourceJvmReportSha256']=properties
        load_paths=list(ROOT.glob('native-load-*.log'))
        for module in ROOT.glob('harness-*'):
            load_paths.extend(module.glob('native-load-*.log'))
        raw_logs={p.relative_to(ROOT).as_posix():p.read_bytes() for p in load_paths}
        report['jniLoadLogs']=validate_loads(raw_logs,jni/'jnidispatch.dll',
            stage['nativeReplacements']['com/sun/jna/win32-x86-64/jnidispatch.dll']['sha256'])
        report['nativeLoadLogSha256']={n:sha(v) for n,v in raw_logs.items()}
        for resource,item in stage['nativeReplacements'].items():
            path=jni/Path(resource).name if resource.startswith('com/sun/jna/') else pty/Path(resource).name
            if sha(path.read_bytes())!=item['sha256']:raise ValueError('Native payload changed during tests')
        # Verify rebuilds the shaded JAR. Reassemble against that fresh current
        # baseline, rather than claiming the pre-verify JAR was tested unchanged.
        command('stage-candidate',[python,'scripts/stage-source-native-runtime.py','--output',
                                  '.build-cache/source-qualification/candidate-stage'])
        final=json.loads((out/'candidate-stage/stage.json').read_bytes())
        report['candidateStage']=final
        for name,argv in [
            ('sdk-inventory',[python,'scripts/sdk-inventory.py']),
            ('frontend-bom',[python,'scripts/frontend-sbom.py','target/frontend-bom.json']),
            ('frontend-audit',['npm.cmd','--prefix','frontend','audit','--json']),
            ('legal',[python,'scripts/prepare-legal.py']),
            ('legal-drift',['git','diff','--exit-code','--','third-party']),
            ('osv',[python,'scripts/scan-osv.py','target/bom.json','target/osv-results.json']),
            ('candidate',[python,'scripts/build-candidate.py']),
            ('install-smoke',[python,'scripts/smoke-candidate.py'])]:
            command(name,argv)
        runtime=ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
        if sha(runtime.read_bytes())!=final['runtimeJarSha256']:raise ValueError('Candidate JAR drift')
        with zipfile.ZipFile(ROOT/'target/jharness-0.1.0-SNAPSHOT-candidate.zip') as archive:
            if sha(archive.read('jharness/lib/jharness.jar'))!=final['runtimeJarSha256']:
                raise ValueError('Installed candidate does not contain source runtime')
        smoke=json.loads((ROOT/'target/candidate-smoke.json').read_bytes())
        if smoke['status']!='PASS' or smoke['paidRequests']!=0:raise ValueError('Install smoke incomplete')
        report.update(status='WINDOWS_SOURCE_JAVA_VERIFY_AND_CANDIDATE_INSTALL_PASS_NOT_RELEASE',
            smoke=smoke,candidateZipSha256=sha((ROOT/'target/jharness-0.1.0-SNAPSHOT-candidate.zip').read_bytes()),
            limits=['No complete OS/CPE clearance','No Linux/macOS source-runtime full qualification',
                    'No historical macOS causal root proof','No release approval or publication'])
    finally:
        (out/'result.json').write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print(report['status'])


if __name__=='__main__':main()
