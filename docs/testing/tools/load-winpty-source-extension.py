"""Run actual source WinPTY extension calls, rejecting replay, drift and fallback."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[3]


def module(name,file):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file))
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_source(out,commit,run):
    patch=module('directory_patch','patch-winpty-directory.py')
    report=json.loads((out/'report.json').read_bytes())
    if (report.get('status')!='SOURCE_WINDOWS_PTY_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE'
            or report.get('commit')!=commit or report.get('runId')!=run
            or report['sources']['winpty']['revision']!=patch.REVISION
            or report['winptyEligibility']['status']!='ABI_RUNTIME_VALIDATION_REQUIRED'):
        raise ValueError('Current source identity or ABI names rejected')
    extension=report['sources']['winpty']['directoryExtension']
    if extension['baseRevision']!=patch.REVISION or extension['ownedChildHelperSha256']!=sha(ROOT/'docs/testing/native/winpty-owned-directory.h'):
        raise ValueError('Owned directory helper identity rejected')
    changes={v['path']:v for v in extension['changes']}
    if len(extension['changes'])!=len(patch.EXPECTED) or set(changes)!=set(patch.EXPECTED):raise ValueError('Patch source set rejected')
    for name,expected in patch.EXPECTED.items():
        if changes[name]['beforeSha256']!=expected or sha(out/'winpty'/name)!=changes[name]['afterSha256']:
            raise ValueError('Patched source drift rejected')
    if sha(out/'winpty/src/agent/jh-owned-directory.h')!=extension['ownedChildHelperSha256']:raise ValueError('Agent helper drift rejected')
    for command in report['commands']:
        log=out/command['log']
        if (not log.resolve().is_relative_to(out.resolve()) or command['exitCode']!=0
                or sha(log)!=command['logSha256']):raise ValueError('Source command evidence rejected')
    if not any(c['argv']==['C:/msys64/usr/bin/make.exe','-j2','build/winpty.dll','build/winpty-agent.exe'] for c in report['commands']):
        raise ValueError('Source native compile required')
    found={}
    for item in report['outputs']:
        path=ROOT/item['path']
        if (Path(item['path']).is_absolute() or not path.resolve().is_relative_to(out.resolve())
                or path.is_symlink() or not path.is_file() or path.stat().st_size>32*1024*1024
                or sha(path)!=item['sha256']):raise ValueError('Native candidate path/hash rejected')
        if path.name in found:raise ValueError('Duplicate native candidate')
        found[path.name]=path
    if set(found)!={'winpty.dll','winpty-agent.exe','win-helper.dll'}:raise ValueError('Complete native candidate set required')
    return found


def main():
    if os.environ.get('GITHUB_ACTIONS')!='true' or os.name!='nt':raise ValueError('Use dedicated Windows source CI')
    source=ROOT/'.build-cache/windows-pty-source'
    native=validate_source(source,os.environ['GITHUB_SHA'],os.environ['GITHUB_RUN_ID'])
    jni_loader=module('jni_source_loader','load-native-source-candidates.py')
    _,jni,_,_=jni_loader.validate(ROOT/'.build-cache/verified-source-jni','40998915552')
    builder=module('windows_source_builder','build-windows-pty-from-source.py')
    env=builder.build_environment();env['JAVA_HOME']=os.environ['JAVA_HOME']
    java=Path(env['JAVA_HOME'])/'bin/java.exe';javac=java.with_name('javac.exe')
    jar=ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'
    probe=ROOT/'docs/testing/tools/WinPtySourceProbe.java'
    out=ROOT/'.build-cache/windows-winpty-load';out.mkdir(parents=True)
    classes=out/'classes';classes.mkdir()
    report=dict(status='INCOMPLETE',commit=os.environ['GITHUB_SHA'],runId=os.environ['GITHUB_RUN_ID'],
        jarSha256=sha(jar),probeSha256=sha(probe),jniSha256=sha(jni),
        nativeSha256={name:sha(path) for name,path in native.items()},probes=[],paidRequests=0,productReplacement=False)
    def run(label,argv,timeout=45):
        result=subprocess.run(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=timeout)
        raw=result.stdout+result.stderr
        if len(raw)>131072:raise ValueError('Probe output bound')
        log=out/(label+'.log');log.write_bytes(raw)
        report['probes'].append(dict(label=label,argv=argv,exitCode=result.returncode,logSha256=sha(log)))
        return result.returncode,raw
    try:
        code,_=run('compile',[str(javac),'--release','21','-proc:none','-Xlint:all','-Werror','-encoding','UTF-8',
            '-cp',str(jar),'-d',str(classes),str(probe)])
        if code:raise ValueError('Trusted source probe compilation rejected')
        report['classSha256']=sha(classes/'WinPtySourceProbe.class')
        folder=out/'native/win/x86-64';folder.mkdir(parents=True)
        for name,path in native.items():
            shutil.copyfile(path,folder/name)
            if sha(folder/name)!=report['nativeSha256'][name]:raise ValueError('Staged native byte drift')
        base=[str(java),'-Djna.nounpack=true','-Djna.nosys=true','-Djna.boot.library.path='+str(jni.parent)]
        classpath=['-cp',str(jar)+os.pathsep+str(classes),'WinPtySourceProbe']
        for label,root,success in [('source-positive',out/'native',True),('missing-library-rejected',out/'empty',False)]:
            root.mkdir(exist_ok=True);work=out/(label+'-work');work.mkdir()
            code,raw=run(label,base+['-Dpty4j.preferred.native.folder='+str(root)]+classpath+[str(work)])
            if success:
                if code or b'SOURCE_WINPTY_CWD_PROCESS_LIST_RESIZE_AND_EXIT_PASS' not in raw:raise ValueError('Source WinPTY extension behavior rejected')
            elif code==0 or b'winpty.dll' not in raw or not any(v in raw for v in [b'UnsatisfiedLinkError',b'IOException']):
                raise ValueError('Missing source library did not explicitly fail loading')
        if sha(jar)!=report['jarSha256'] or sha(jni)!=report['jniSha256'] or any(sha(p)!=report['nativeSha256'][n] for n,p in native.items()):
            raise ValueError('Probe source input drift')
        report['status']='SOURCE_WINPTY_CWD_PROCESS_LIST_RESIZE_EXIT_AND_NO_FALLBACK_PASS'
    finally:(out/'report.json').write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print(report['status'])


if __name__=='__main__':main()
