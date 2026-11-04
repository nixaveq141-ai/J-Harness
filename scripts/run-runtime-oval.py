"""Qualify a separate pinned runtime container; never clear the original host."""
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
from evidence_io import atomic_write_json, stream_sha256
from evidence_run import run

ROOT=Path(__file__).resolve().parents[1]


def verify_image(image,locked):
    expected='eclipse-temurin@'+locked['digest']
    if (image.get('Architecture')!=locked['architecture'] or image.get('Os')!='linux'
            or expected not in image.get('RepoDigests',[])):
        raise ValueError('Exact runtime architecture and repository digest required')


def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Disposable CI host required')
    run_id=os.environ['GITHUB_RUN_ID'];attempt=os.environ.get('GITHUB_RUN_ATTEMPT','1')
    if not run_id.isdecimal() or not attempt.isdecimal():raise ValueError('Numeric run identity required')
    lock_path=ROOT/'docs/testing/ubuntu-runtime-image-lock-2026-11-04.json'
    locked=json.loads(lock_path.read_bytes())['images'][platform.machine()]
    image='eclipse-temurin@'+locked['digest'];name='jharness-runtime-oval-'+run_id+'-'+attempt
    out=ROOT/'.build-cache/runtime-oval';out.mkdir();context=out/'context';context.mkdir()
    report=dict(status='INCOMPLETE',runId=run_id,commit=os.environ['GITHUB_SHA'],image=image,
                imageLockSha256=stream_sha256(lock_path),commands={},fullOsCpeClearance=False,paidRequests=0)
    created=False;seeded=False
    def command(label,argv):
        run(argv,name=label,log=out/(label+'.log'),receipt=out/(label+'-command.json'),
            run_id=run_id,cwd=ROOT,root=ROOT,timeout=1200)
        report['commands'][label]=stream_sha256(out/(label+'-command.json'))
    atomic_write_json(out/'report.json',report)
    try:
        receipt=json.loads((ROOT/'docs/testing/M10-unified14-final-linux-x64-2026-11-01.json').read_bytes())
        candidate=ROOT/'.build-cache/runtime-input/target/jharness-0.1.0-SNAPSHOT-candidate.zip'
        if stream_sha256(candidate)!=receipt['candidateZipSha256']:raise ValueError('Accepted candidate bytes required')
        report['candidateZipSha256']=receipt['candidateZipSha256'];report['runtimeJarSha256']=receipt['runtimeJarSha256']
        report['runtimeCommit']=receipt['commit'];report['candidateSourceRun']=receipt['runId']
        report['scanSourceScope']='EXACT_COPIED_CONTEXT_SUBSET_BOUND_TO_HOST_COMMAND_RECEIPTS'
        (context/'scripts').mkdir();(context/'target').mkdir()
        for file in ['scan-ubuntu-oval.py','evidence_io.py','evidence_run.py','smoke-candidate.py']:
            shutil.copyfile(ROOT/'scripts'/file,context/'scripts'/file)
        shutil.copytree(ROOT/'third-party',context/'third-party')
        for file in ['LICENSE','NOTICE']:shutil.copyfile(ROOT/file,context/file)
        shutil.copyfile(candidate,context/'target/jharness-0.1.0-SNAPSHOT-candidate.zip')
        report['contextFileSha256']={p.relative_to(context).as_posix():stream_sha256(p) for p in context.rglob('*') if p.is_file()}
        command('image-pull',['docker','pull',image])
        command('image-inspect',['docker','image','inspect',image]);inspected=json.loads((out/'image-inspect.log').read_bytes())[0]
        verify_image(inspected,locked);report['imageId']=inspected['Id']
        command('create',['docker','create','--name',name,'--label','jharness.audit='+run_id,'--init',
            '--entrypoint','sleep','-e','GITHUB_ACTIONS=true','-e','GITHUB_SHA='+report['commit'],
            '-e','GITHUB_RUN_ID='+run_id,'-e','ImageOS=pinned-temurin-noble-runtime',
            '-e','ImageVersion='+locked['digest'],'-e','RUNNER_ARCH='+locked['architecture'],image,'infinity']);created=True
        command('start',['docker','start',name])
        command('copy-context',['docker','cp',str(context),name+':/audit']);seeded=True
        command('install-scanner',['docker','exec',name,'bash','-euc',
            'apt-get update; DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends python3 git openscap-scanner'])
        command('before',['docker','exec','-w','/audit',name,'python3','scripts/scan-ubuntu-oval.py'])
        command('update',['docker','exec',name,'env','DEBIAN_FRONTEND=noninteractive','NEEDRESTART_MODE=l',
                          'apt-get','-y','--no-install-recommends','upgrade'])
        command('after',['docker','exec','-w','/audit',name,'python3','scripts/scan-ubuntu-oval.py','--phase','after'])
        command('disconnect',['docker','network','disconnect','bridge',name])
        command('network-inspect',['docker','inspect',name]);state=json.loads((out/'network-inspect.log').read_bytes())[0]
        if state['NetworkSettings']['Networks']:raise ValueError('Smoke must have no external container network')
        command('install-smoke',['docker','exec','-w','/audit',name,'python3','scripts/smoke-candidate.py'])
        command('copy-smoke',['docker','cp',name+':/audit/target/candidate-smoke.json',str(out/'candidate-smoke.json')])
        command('copy-scans',['docker','cp',name+':/audit/.build-cache/ubuntu-oval',str(out/'scans')])
        smoke=json.loads((out/'candidate-smoke.json').read_bytes());after=json.loads((out/'scans/after/report.json').read_bytes())
        if smoke['status']!='PASS' or smoke['paidRequests']!=0 or smoke['zipSha256']!=report['candidateZipSha256']:
            raise ValueError('Actual accepted candidate installation required')
        if after['status']!='NO_USN_FINDINGS_IN_SELECTED_HOST_SCOPE':raise ValueError('Runtime USN findings remain')
        report['status']='SELECTED_RUNTIME_USN_AND_OFFLINE_INSTALL_PASS_NOT_FULL_CPE'
        report['smoke']=smoke;report['assessmentCounts']=after['assessmentCounts']
    except BaseException as error:
        report['failureType']=type(error).__name__
        raise
    finally:
        if created:
            if seeded:
                try:
                    capture=subprocess.run(['docker','cp',name+':/audit/.build-cache/ubuntu-oval',str(out/'partial-scans')],capture_output=True,timeout=60)
                    report['partialCaptureExitCode']=capture.returncode
                except Exception as error:report['partialCaptureFailureType']=type(error).__name__
            try:
                cleanup=subprocess.run(['docker','rm','-f',name],capture_output=True,timeout=30)
                report['cleanupExitCode']=cleanup.returncode
                if cleanup.returncode:report['status']='INCOMPLETE'
            except Exception as error:
                report['cleanupFailureType']=type(error).__name__;report['status']='INCOMPLETE'
        atomic_write_json(out/'report.json',report)
    print(report['status'])


if __name__=='__main__':main()
