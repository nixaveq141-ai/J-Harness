"""Verify current Console source outputs, assemble and exercise product terminal."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sys

ROOT=Path(__file__).resolve().parents[3]


def module(name,file):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file))
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def outputs(report,folder,names):
    found={}
    for item in report['outputs']:
        path=folder/item['path']
        if (Path(item['path']).is_absolute() or not path.resolve().is_relative_to(folder.resolve())
                or path.is_symlink() or not path.is_file() or path.stat().st_size!=item['bytes']
                or path.stat().st_size>32*1024*1024 or sha(path)!=item['sha256'] or path.name in found):
            raise ValueError('Console assembly output identity/path rejected')
        found[path.name]=path
    if set(found)!=set(names):raise ValueError('Exact native output set required')
    return found


def validate_console(source,commit,run):
    builder=module('console_builder','build-windows-pty-from-source.py')
    gate=module('console_gate','console-security-baseline.py')
    report=json.loads((source/'report.json').read_bytes())
    if (report.get('status')!='SOURCE_WINDOWS_PTY_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE'
            or report.get('component')!='console' or report.get('commit')!=commit or report.get('runId')!=run
            or report['sources']['console']['revision']!=builder.PINS['console'][1]
            or report['sources']['vcpkg']['revision']!=builder.PINS['vcpkg'][1]
            or report['enumeratedVendorSecurityGate']!=gate.validate(builder.PINS['console'][1],builder.CONSOLE_VERSION)
            or report['sources']['console']['vcpkgBinaryCachePolicy']!='clear; source builds, no external binary cache'):
        raise ValueError('Current reviewed Console source/security identity rejected')
    for command in report['commands']:
        log=source/command['log']
        if not log.resolve().is_relative_to(source.resolve()) or command['exitCode']!=0 or sha(log)!=command['logSha256']:
            raise ValueError('Console source command evidence rejected')
    projects={arg for command in report['commands'] for arg in command['argv'] if arg.endswith('.vcxproj')}
    if projects!={'src/host/exe/Host.EXE.vcxproj','src/winconpty/dll/winconptydll.vcxproj'}:
        raise ValueError('Both source Console projects must compile')
    native=outputs(report,ROOT,{'conpty.dll','OpenConsole.exe'})
    if any(not path.resolve().is_relative_to(source.resolve()) for path in native.values()):
        raise ValueError('Current source output boundary rejected')
    return report,native


def main():
    if os.name!='nt' or os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Use dedicated Windows source CI')
    source_report,native=validate_console(ROOT/'.build-cache/windows-pty-source',os.environ['GITHUB_SHA'],os.environ['GITHUB_RUN_ID'])
    approved=json.loads((ROOT/'docs/testing/M10-winpty-enhanced-acceptance-2026-10-30.json').read_bytes())
    downloaded=ROOT/'.build-cache/reviewed-winpty'
    prior=json.loads((downloaded/'.build-cache/windows-pty-source/report.json').read_bytes())
    if prior['commit']!=approved['commit'] or prior['runId']!=approved['runId']:
        raise ValueError('Reviewed WinPTY artifact identity rejected')
    winpty=outputs(prior,downloaded,{'winpty.dll','winpty-agent.exe','win-helper.dll'})
    if {n:sha(p) for n,p in winpty.items()}!=approved['load']['nativeSha256']:
        raise ValueError('Previously accepted WinPTY bytes required')
    native.update(winpty)
    jni_loader=module('console_jni_loader','load-native-source-candidates.py')
    _,jni,_,_=jni_loader.validate(ROOT/'.build-cache/verified-source-jni','40998915552')
    builder=module('console_env','build-windows-pty-from-source.py')
    assembler=module('console_assembler','assemble-native-preview.py')
    env=builder.build_environment();env['JAVA_HOME']=os.environ['JAVA_HOME']
    java=Path(env['JAVA_HOME'])/'bin/java.exe';javac=java.with_name('javac.exe')
    out=ROOT/'.build-cache/windows-console-load';out.mkdir(parents=True)
    folder=out/'native/pty/win/x86-64';folder.mkdir(parents=True)
    for name,path in native.items():shutil.copyfile(path,folder/name)
    jnifolder=out/'native/jni';jnifolder.mkdir();shutil.copyfile(jni,jnifolder/jni.name)
    inventory={v['resource']:v['sha256'] for v in json.loads((ROOT/'docs/testing/M10-native-inventory-48-2026-10-30.json').read_bytes())['binaries']}
    replacements={'resources/com/pty4j/native/win/x86-64/'+n:dict(path=folder/n,sha256=sha(p),origin=dict(runId=source_report['runId'] if n in {'conpty.dll','OpenConsole.exe'} else approved['runId'])) for n,p in native.items()}
    replacements['com/sun/jna/win32-x86-64/jnidispatch.dll']=dict(path=jnifolder/jni.name,sha256=sha(jni),origin=dict(runId='40998915552'))
    base=ROOT/'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar';jar=out/'source-native-preview.jar'
    report=dict(status='INCOMPLETE',runId=os.environ['GITHUB_RUN_ID'],commit=os.environ['GITHUB_SHA'],
                assembly=assembler.assemble(base,jar,replacements,sha(base),inventory,'Windows x64 current Console source component/product-terminal preview'),
                assemblerSha256=sha(ROOT/'docs/testing/tools/assemble-native-preview.py'),probes=[],paidRequests=0,fullProductAcceptance=False)
    sys.path.insert(0,str(ROOT/'scripts'));import evidence_run
    def run(label,argv,marker=None,expected_success=True):
        log=out/(label+'.log');receipt=out/(label+'-command.json')
        try:
            evidence_run.run(argv,name=label,log=log,receipt=receipt,run_id='console-source-'+os.environ['GITHUB_RUN_ID'],cwd=ROOT,root=ROOT,env=env,timeout=60,max_log_bytes=131072)
        except ValueError:
            if expected_success:raise
        result=json.loads(receipt.read_bytes());raw=log.read_bytes()
        report['probes'].append(dict(label=label,exitCode=result['exitCode'],logSha256=sha(log),receiptSha256=sha(receipt)))
        if (result.get('timedOut') or result.get('logBudgetExceeded') or
                (expected_success and result['status']!='PASS') or (not expected_success and result['exitCode']==0)
                or (marker is not None and marker not in raw)):
            raise ValueError('Source Console probe behavior rejected: '+label)
    classes=out/'classes';classes.mkdir()
    probes=[ROOT/'docs/testing/tools'/name for name in ['WinPtySourceProbe.java','NativeProductTerminalProbe.java']]
    try:
        report['probeSha256']={p.name:sha(p) for p in probes}
        run('compile',[str(javac),'--release','21','-proc:none','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),*[str(p) for p in probes]])
        report['classSha256']={p.name:sha(p) for p in classes.glob('*.class')}
        vm=[str(java),'-Djna.nounpack=true','-Djna.nosys=true','-Djna.boot.library.path='+str(jnifolder)]
        cp=['-cp',str(jar)+os.pathsep+str(classes)]
        for label,backend,success in [('source-positive',out/'native/pty',True),('missing-bundle-rejected',out/'empty',False)]:
            backend.mkdir(exist_ok=True);work=out/(label+'-work');work.mkdir()
            marker=b'SOURCE_CONPTY_CWD_PROCESS_LIST_RESIZE_AND_EXIT_PASS' if success else b'EXPECTED_SOURCE_BUNDLED_CONPTY'
            run(label,vm+['-Dpty4j.preferred.native.folder='+str(backend)]+cp+['WinPtySourceProbe',str(work),'conpty'],marker,success)
        work=out/'product-work';work.mkdir()
        run('product-terminal',vm+['-Dpty4j.preferred.native.folder='+str(out/'native/pty')]+cp+['NativeProductTerminalProbe',str(work)],b'SOURCE_NATIVE_PRODUCT_TERMINAL_INPUT_CWD_RESIZE_EXIT_STOP_TIMEOUT_PASS')
        if sha(jar)!=report['assembly']['previewJarSha256']:raise ValueError('Assembled runtime drift rejected')
        report['status']='SOURCE_CONSOLE_ABI_PRODUCT_TERMINAL_AND_MISSING_BUNDLE_REJECTION_PASS'
    finally:(out/'report.json').write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print(report['status'])


if __name__=='__main__':main()
