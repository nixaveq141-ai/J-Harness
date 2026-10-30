"""Load audited source-built candidates with missing-library negative controls."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[3]
SOURCE_RUNS={'40995618881':'a2dc791bae10addc3b53b3d951b94db897ccc265',
             '40998915552':'3571d2269895644e9888963fbf54a4cf101c9597'}


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def original_mac_controls(jar,out):
    """Trusted original bytes for causal comparison, never a source candidate fallback."""
    inventory=json.loads((ROOT/'docs/testing/M10-native-inventory-48-2026-10-30.json').read_bytes())
    resources={v['resource']:v for v in inventory['binaries']}
    arch='aarch64' if platform.machine()=='arm64' else 'x86-64'
    names=['com/sun/jna/darwin-'+arch+'/libjnidispatch.jnilib',
           'resources/com/pty4j/native/darwin/libpty.dylib',
           'resources/com/pty4j/native/darwin/pty4j-unix-spawn-helper']
    with zipfile.ZipFile(jar) as archive:
        for name in names:
            info=archive.getinfo(name)
            if info.file_size>16*1024*1024:raise ValueError('Original native control bound')
            raw=archive.read(info)
            if hashlib.sha256(raw).hexdigest()!=resources[name]['sha256']:
                raise ValueError('Original native control inventory mismatch')
            target=out/('jni' if name.startswith('com/sun/jna') else 'pty/darwin')/Path(name).name
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
            if target.name=='pty4j-unix-spawn-helper':target.chmod(0o755)
    return out/'jni',out/'pty'


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
    native_commands=[['ant','-Dbuild-native=true','native'],
        ['ant','-Dbuild-native=true','-Dmake=C:\\msys64\\usr\\bin\\make.exe',
         '-DEXTRA_MAKE_OPTS=OS=win32 MINGW_PREFIX=','-DARCH=amd64','-DUSE_MSVC=false','native']]
    if not any(v.get('argv') in native_commands for v in report['commands']):
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
    helper=output(report['outputs']['ptyHelper']) if 'ptyHelper' in report['outputs'] else None
    if report.get('os','').startswith('macOS'):
        if helper is None or helper.name!='pty4j-unix-spawn-helper' or not any(
                v.get('argv')==['cmake','--build','native/helper-build','--config','Release'] for v in report['commands']):
            raise ValueError('macOS source-built spawn helper required')
    return report,jna[0],pty,helper


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source-run',required=True);parser.add_argument('--input',type=Path,required=True);args=parser.parse_args()
    source,jna,pty,helper=validate(args.input,args.source_run)
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
        sourceReportSha256=sha(next(args.input.rglob('report.json'))),probes=[],paidRequests=0,productReplacement=False,
        ptyHelperSha256=sha(helper) if helper else None)
    classes=out/'classes';classes.mkdir()
    compile_command=[str(java.parent/('javac.exe' if os.name=='nt' else 'javac')),
        '--release','21','-proc:none','-Xlint:all','-Werror','-cp',str(jar),'-d',str(classes),str(probe)]
    result=subprocess.run(compile_command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=30)
    compile_log=out/'probe-compile.log';compile_log.write_bytes(result.stdout+result.stderr)
    report['probeCompilation']=dict(argv=compile_command,exitCode=result.returncode,logSha256=sha(compile_log))
    def run(label,jni_folder,pty_folder=None,success=True,required=True,defer_drain=False):
        command=[str(java),'-Djna.nounpack=true','-Djna.nosys=true','-Djna.boot.library.path='+str(jni_folder)]
        if pty_folder is not None:command+=['-Dpty4j.preferred.native.folder='+str(pty_folder)]
        command+=['-cp',str(jar)+os.pathsep+str(classes),'NativeCandidateProbe']+(['pty'] if pty_folder is not None else [])
        if defer_drain:command+=['defer-drain']
        result=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=30)
        raw=result.stdout+result.stderr
        if len(raw)>65536:raise ValueError('Probe output bound')
        log=out/(label+'.log');log.write_bytes(raw)
        passed=result.returncode==0 and b'SOURCE_JNI_PASS' in raw and (pty_folder is None or b'SOURCE_PTY_PASS' in raw)
        report['probes'].append(dict(label=label,exitCode=result.returncode,logSha256=sha(log),behaviorPassed=passed,required=required))
        if not required:return passed
        if success:
            if not passed:raise ValueError('Native source behavior rejected')
        elif result.returncode==0 or b'UnsatisfiedLinkError' not in raw:
            raise ValueError('Missing candidate must fail native loading, no fallback')
    try:
        if result.returncode:raise ValueError('Trusted probe compilation rejected')
        report['probeClassSha256']=sha(classes/'NativeCandidateProbe.class')
        run('jni-positive',jna.parent)
        run('jni-missing-rejected',empty,success=False)
        if pty:
            if helper:
                # GitHub ZIP artifacts preserve bytes, not executable modes.
                helper.chmod(0o755)
            # pty4j resolves platform subdirectories relative to its preferred root.
            root=pty.parent.parent if platform.system()=='Darwin' else pty.parent.parent.parent
            if platform.system()=='Darwin':
                original_jni,original_pty=original_mac_controls(jar,out/'original-controls')
                run('original-jni-original-pty',original_jni,original_pty,required=False)
                run('source-jni-original-pty',jna.parent,original_pty,required=False)
                run('original-jni-source-pty',original_jni,root,required=False)
                run('original-pty-deferred-drain',original_jni,original_pty,defer_drain=True)
                run('source-pty-deferred-drain',jna.parent,root,defer_drain=True)
            run('pty-positive',jna.parent,root)
            run('pty-missing-rejected',jna.parent,empty,success=False)
        if (sha(jar)!=report['jarSha256'] or sha(jna)!=report['jniSha256']
                or pty and sha(pty)!=report['ptySha256'] or helper and sha(helper)!=report['ptyHelperSha256']):
            raise ValueError('Probe input drift')
        report['status']='SOURCE_CANDIDATE_LOAD_AND_NO_FALLBACK_PASS'
    finally:(out/'report.json').write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print(report['status'])


if __name__=='__main__':main()
