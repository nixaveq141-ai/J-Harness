"""Load audited source-built candidates with missing-library negative controls."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess

ROOT=Path(__file__).resolve().parents[3]
SOURCE_RUNS={'40995618881':'a2dc791bae10addc3b53b3d951b94db897ccc265'}


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def validate(directory,run):
    if run not in SOURCE_RUNS:raise ValueError('Unreviewed source run')
    reports=list(directory.rglob('report.json'))
    if len(reports)!=1 or reports[0].stat().st_size>4*1024*1024:raise ValueError('Source report bound')
    report=json.loads(reports[0].read_bytes());base=reports[0].parent
    if (report.get('status')!='SOURCE_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE'
            or report.get('commit')!=SOURCE_RUNS[run] or report.get('runId')!=run
            or report['sources']['jna']['revision']!='c366bc4fd0c50a30ac8d2475603ec564f6611462'
            or report['sources']['pty4j']['revision']!='b7554d649f6374183040e6554942b6d5b028b5e5'
            or report['sources']['jna']['libffiBuildInputs']['sha256']!='d66c56ad259a82cf2a9dfc408b32bf5da52371500b84745f7fb8b645712df676'):
        raise ValueError('Source identity rejected')
    if not any(v.get('argv')==['ant','-Dbuild-native=true','native'] for v in report['commands']):
        raise ValueError('Native compilation command required')
    for command in report['commands']:
        log=base/command['log']
        if not log.resolve().is_relative_to(base.resolve()) or command['exitCode']!=0 or sha(log)!=command['logSha256']:
            raise ValueError('Source build command evidence rejected')
    def output(item):
        parts=Path(item['path']).parts
        if parts[:2]!=('.build-cache','native-source-build'):raise ValueError('Unexpected candidate prefix')
        path=base.joinpath(*parts[2:])
        if (not path.resolve().is_relative_to(base.resolve()) or path.is_symlink()
                or not path.is_file() or path.stat().st_size>16*1024*1024 or sha(path)!=item['sha256']):
            raise ValueError('Candidate path/hash rejected')
        return path
    jna=[output(v) for v in report['outputs']['jna']]
    if not jna:raise ValueError('No JNI candidates')
    pty=output(report['outputs']['pty']) if 'pty' in report['outputs'] else None
    return report,jna[0],pty


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source-run',required=True);parser.add_argument('--input',type=Path,required=True);args=parser.parse_args()
    source,jna,pty=validate(args.input,args.source_run)
    arch=lambda value: {'amd64':'x86_64','aarch64':'arm64'}.get(value.lower(),value.lower())
    if arch(source['architecture'])!=arch(platform.machine()):raise ValueError('Candidate architecture mismatch')
    out=ROOT/'.build-cache/native-source-load';out.mkdir(parents=True)
    jar=ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar';probe=ROOT/'docs/testing/tools/NativeCandidateProbe.java'
    if not jar.is_file():raise ValueError('Product JAR required')
    env={k:os.environ[k] for k in ['SystemRoot','WINDIR','TEMP','TMP','JAVA_HOME'] if k in os.environ}
    java=Path(env['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java')
    env['PATH']=str(java.parent)+os.pathsep+(str(Path(env['SystemRoot'])/'System32') if os.name=='nt' else '/usr/bin:/bin')
    empty=out/'empty';empty.mkdir()
    report=dict(status='INCOMPLETE',commit=os.environ.get('GITHUB_SHA'),runId=os.environ.get('GITHUB_RUN_ID'),
        sourceRun=args.source_run,sourceCommit=SOURCE_RUNS[args.source_run],os=platform.platform(),
        jarSha256=sha(jar),probeSha256=sha(probe),jniSha256=sha(jna),ptySha256=sha(pty) if pty else None,
        sourceReportSha256=sha(next(args.input.rglob('report.json'))),probes=[],paidRequests=0,productReplacement=False)
    def run(label,jni_folder,pty_folder=None,success=True):
        command=[str(java),'-Djna.nounpack=true','-Djna.nosys=true','-Djna.boot.library.path='+str(jni_folder)]
        if pty_folder is not None:command+=['-Dpty4j.preferred.native.folder='+str(pty_folder)]
        command+=['-cp',str(jar),str(probe)]+(['pty'] if pty_folder is not None else [])
        result=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=30)
        raw=result.stdout+result.stderr
        if len(raw)>65536:raise ValueError('Probe output bound')
        log=out/(label+'.log');log.write_bytes(raw)
        report['probes'].append(dict(label=label,exitCode=result.returncode,logSha256=sha(log)))
        if success:
            if result.returncode or b'SOURCE_JNI_PASS' not in raw or pty_folder is not None and b'SOURCE_PTY_PASS' not in raw:raise ValueError('Native source behavior rejected')
        elif result.returncode==0 or b'UnsatisfiedLinkError' not in raw:
            raise ValueError('Missing candidate must fail native loading, no fallback')
    try:
        run('jni-positive',jna.parent)
        run('jni-missing-rejected',empty,success=False)
        if pty:
            # pty4j resolves platform subdirectories relative to its preferred root.
            root=pty.parent.parent if platform.system()=='Darwin' else pty.parent.parent.parent
            run('pty-positive',jna.parent,root)
            run('pty-missing-rejected',jna.parent,empty,success=False)
        if sha(jar)!=report['jarSha256'] or sha(jna)!=report['jniSha256'] or pty and sha(pty)!=report['ptySha256']:
            raise ValueError('Probe input drift')
        report['status']='SOURCE_CANDIDATE_LOAD_AND_NO_FALLBACK_PASS'
    finally:(out/'report.json').write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print(report['status'])


if __name__=='__main__':main()
