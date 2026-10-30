"""Build reviewed Windows PTY alternatives in isolated CI; no product substitution."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import time

ROOT=Path(__file__).resolve().parents[3]
PINS={
    'winpty':('rprichard/winpty','3e1ab962d5262dd76159870c6dc0724927ca6a9d'),
    'pty4j':('JetBrains/pty4j','b7554d649f6374183040e6554942b6d5b028b5e5'),
    'console':('microsoft/terminal','6ffde34897e99a57a56ec52c12e1bf2730ceed71'),
}


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def build_environment():
    # Visual Studio discovery and MSBuild FileTracker require the shared
    # application-data roots; omitting them breaks an otherwise installed SDK.
    keys=['PATH','SystemRoot','WINDIR','TEMP','TMP','USERPROFILE','ProgramFiles','ProgramFiles(x86)',
          'ProgramData','ALLUSERSPROFILE','ComSpec','SystemDrive','ProgramW6432','PUBLIC',
          'CommonProgramFiles','CommonProgramFiles(x86)','CommonProgramW6432',
          'LOCALAPPDATA','APPDATA','HOMEDRIVE','HOMEPATH',
          'VSINSTALLDIR','VCToolsInstallDir','WindowsSdkDir']
    return {k:os.environ[k] for k in keys if k in os.environ}


def checkout(name,out,run,env):
    repository,revision=PINS[name];target=out/name
    run(['git','-c','http.https://github.com/.extraheader=','clone','--filter=blob:none','--no-checkout',
         'https://github.com/'+repository+'.git',str(target)],ROOT)
    run(['git','-c','core.autocrlf=false','checkout','--detach',revision],target)
    actual=subprocess.run(['git','rev-parse','HEAD'],cwd=target,env=env,capture_output=True,check=True).stdout.decode().strip()
    if actual!=revision:raise ValueError('Pinned source revision rejected')
    names=subprocess.run(['git','ls-files','-z'],cwd=target,env=env,capture_output=True,check=True).stdout.split(b'\0')
    files={n.decode():sha(target/n.decode()) for n in names if n and (target/n.decode()).is_file()}
    return target,dict(repository=repository,revision=revision,trackedFiles=files)


def outputs(paths,source):
    result=[]
    for path in paths:
        if not path.resolve().is_relative_to(source.resolve()) or path.is_symlink() or not path.is_file():
            raise ValueError('Fresh source output required')
        if path.stat().st_size>32*1024*1024 or path.read_bytes()[:2]!=b'MZ':
            raise ValueError('Bounded PE output required')
        result.append(dict(path=path.relative_to(ROOT).as_posix(),bytes=path.stat().st_size,sha256=sha(path)))
    return result


def winpty_eligibility(raw):
    # Required name absence is sufficient to reject a named JNA lookup. Presence
    # alone is never ABI/runtime acceptance (it may be a non-export string).
    required=['winpty_get_console_process_list','winpty_get_current_directory']
    missing=[name for name in required if name.encode()+b'\0' not in raw]
    return dict(status='REJECTED_REQUIRED_NAMES_ABSENT' if missing else 'ABI_RUNTIME_VALIDATION_REQUIRED',
                missingRequiredNames=missing,productReplacementApproved=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--component',choices=['winpty','console'],required=True);args=parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS')!='true' or os.name!='nt':raise ValueError('Use isolated Windows CI')
    out=ROOT/'.build-cache/windows-pty-source';out.mkdir(parents=True)
    env=build_environment()
    env.update(GIT_TERMINAL_PROMPT='0',GCM_INTERACTIVE='Never')
    report=dict(status='INCOMPLETE',runId=os.environ.get('GITHUB_RUN_ID'),commit=os.environ.get('GITHUB_SHA'),
        component=args.component,os=platform.platform(),architecture=platform.machine(),sources={},commands=[],outputs=[],
        productReplacement=False,originalJetBrainsBuildAttested=False,paidRequests=0)
    def run(argv,cwd,timeout=900):
        log=out/(str(len(report['commands'])+1)+'.log')
        record=dict(argv=argv,cwd=Path(cwd).relative_to(ROOT).as_posix(),exitCode=None,log=log.name);report['commands'].append(record)
        started=time.monotonic()
        try:
            with log.open('xb') as stream:
                result=subprocess.run(argv,cwd=cwd,env=env,stdin=subprocess.DEVNULL,stdout=stream,stderr=subprocess.STDOUT,timeout=timeout)
            record['exitCode']=result.returncode
        except Exception as error:
            record['failureType']=type(error).__name__;raise
        finally:record.update(logSha256=sha(log),elapsedSeconds=round(time.monotonic()-started,3))
        if result.returncode:raise ValueError('Source command rejected; original log retained')
    try:
        target,source=checkout(args.component,out,run,env);report['sources'][args.component]=source
        if args.component=='winpty':
            # Build only the native Windows DLL/agent targets, not the MSYS adapter.
            run(['C:/msys64/mingw64/bin/g++.exe','--version'],target,30)
            # Upstream configure insists on an MSYS adapter compiler even when
            # no adapter target is requested. Generate its native-only inputs
            # explicitly; do not edit or substitute any upstream C++ source.
            config=target/'config.mk'
            config.write_bytes(('UNIX_CXX=/usr/bin/g++\nUNIX_LDFLAGS_STATIC=\n'
                'MINGW_CXX=/mingw64/bin/g++\nCOMMIT_HASH='+PINS['winpty'][1]+'\nCOMMIT_HASH_DEP=config.mk\n').encode())
            report['sources']['winpty']['nativeOnlyConfig']=dict(sha256=sha(config),
                reason='Build only upstream build/winpty.dll and build/winpty-agent.exe; no MSYS adapter')
            run(['C:/msys64/usr/bin/make.exe','-j2','build/winpty.dll','build/winpty-agent.exe'],target)
            report['outputs']+=outputs([target/'build/winpty.dll',target/'build/winpty-agent.exe'],target)
            report['winptyEligibility']=winpty_eligibility((target/'build/winpty.dll').read_bytes())
            helper,source=checkout('pty4j',out,run,env);report['sources']['pty4j']=source
            run(['cmake','-S','native/win-helper','-B','native/helper-build','-G','Visual Studio 17 2022',
                 '-A','x64','-DTARGET_X64=ON','-DCMAKE_POLICY_VERSION_MINIMUM=3.5'],helper)
            run(['cmake','--build','native/helper-build','--config','Release'],helper)
            report['outputs']+=outputs([helper/'native/helper-build/Release/win-helper.dll'],helper)
        else:
            run(['nuget.exe','help','restore'],target,30)
            run(['nuget.exe','restore','OpenConsole.sln','-NonInteractive'],target)
            # Native dependencies live in standalone packages.config manifests;
            # restoring only the solution restores managed projects, not these.
            for manifest in ['.nuget/packages.config','build/packages.config','dep/nuget/packages.config','tools/packages.config']:
                run(['nuget.exe','restore',manifest,'-NonInteractive','-PackagesDirectory',str(target/'packages')],target)
            report['sources']['console']['nugetPackages']={p.relative_to(target).as_posix():sha(p)
                for p in (target/'packages').rglob('*.nupkg') if p.is_file()}
            for project in ['src/host/exe/Host.EXE.vcxproj','src/winconpty/dll/winconptydll.vcxproj']:
                run(['MSBuild.exe',project,'/m:2','/p:SolutionDir='+target.as_posix()+'/',
                     '/p:Configuration=Release','/p:Platform=x64','/verbosity:minimal','/nologo'],target,1200)
            found={name:[p for p in target.rglob(name) if p.is_file() and 'Release' in p.parts] for name in ['OpenConsole.exe','conpty.dll']}
            if any(len(values)!=1 for values in found.values()):raise ValueError('Ambiguous or missing compiled console outputs')
            report['outputs']+=outputs([v[0] for v in found.values()],target)
        report['status']='SOURCE_WINDOWS_PTY_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE'
    finally:(out/'report.json').write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print(report['status'])


if __name__=='__main__':main()
