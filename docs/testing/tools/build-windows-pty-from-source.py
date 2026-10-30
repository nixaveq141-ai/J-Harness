"""Build reviewed Windows PTY alternatives in isolated CI; no product substitution."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import time

ROOT=Path(__file__).resolve().parents[3]
PINS={
    'winpty':('rprichard/winpty','7e59fe2d09adf0fa2aa606492e7ca98efbc5184e'),
    'pty4j':('JetBrains/pty4j','b7554d649f6374183040e6554942b6d5b028b5e5'),
    'console':('microsoft/terminal','7ff11ee107c984e236960ec948b450ccbc10187b'),
    'vcpkg':('microsoft/vcpkg','fe1cde61e971d53c9687cf9a46308f8f55da19fa'),
}
CONSOLE_VERSION='1.24.12741.0'


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


def prefetch_port_trees(tool,manifest,out,run,manifest_root=None):
    """Materialize exact overridden historical port trees before MSBuild runs.

    A filtered Git checkout has current port blobs but may lack overridden old
    ones. Archive prefetch triggers that fetch with the recorded build environment;
    subsequent vcpkg read-tree can consume the same content-addressed objects.
    """
    overlays=manifest.get('vcpkg-configuration',{}).get('overlay-ports',[])
    if overlays and manifest_root is None:raise ValueError('Pinned manifest root required for overlay resolution')
    overlay_roots=[]
    for entry in overlays:
        path=(manifest_root/entry).resolve()
        if not path.is_relative_to(manifest_root.resolve()) or not path.is_dir():
            raise ValueError('Bounded pinned overlay directory required')
        overlay_roots.append(path)
    found=[]
    for item in manifest['overrides']:
        name=item['name'];requested=item['version'];port=item.get('port-version',0)
        if not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*',name):raise ValueError('Safe pinned port name required')
        candidates=[path/name/'vcpkg.json' for path in overlay_roots if (path/name/'vcpkg.json').is_file()]
        if candidates:
            if len(candidates)!=1:raise ValueError('Ambiguous pinned overlay rejected')
            path=candidates[0];data=json.loads(path.read_bytes())
            if (data.get('name')!=name or data.get('port-version',0)!=port or
                    not any(data.get(key)==requested for key in ['version','version-semver','version-string','version-date'])):
                raise ValueError('Pinned overlay version drift rejected')
            found.append(dict(name=name,version=requested,portVersion=port,sourceKind='PINNED_CONSOLE_OVERLAY',
                              manifestPath=path.relative_to(manifest_root).as_posix(),manifestSha256=sha(path),
                              filesSha256={p.relative_to(manifest_root).as_posix():sha(p)
                                           for p in path.parent.rglob('*') if p.is_file() and not p.is_symlink()}))
            continue
        versions=tool/'versions'/(name[0]+'-')/(name+'.json')
        rows=json.loads(versions.read_bytes())['versions']
        matches=[v for v in rows if v.get('port-version',0)==port and
                 any(v.get(key)==requested for key in ['version','version-semver','version-string','version-date'])]
        if len(matches)!=1 or not re.fullmatch(r'[a-f0-9]{40}',matches[0]['git-tree']):
            raise ValueError('Unique pinned port version tree required')
        tree=matches[0]['git-tree'];archive=out/('port-tree-'+name+'.tar')
        if archive.exists():raise ValueError('Fresh port tree archive required')
        run(['git','-c','core.autocrlf=false','archive','--format=tar','--output='+str(archive),tree],tool)
        if not archive.is_file() or archive.stat().st_size>32*1024*1024:raise ValueError('Bounded materialized port tree required')
        found.append(dict(name=name,version=requested,portVersion=port,sourceKind='PINNED_REGISTRY_TREE',gitTree=tree,
                          versionManifestSha256=sha(versions),archiveSha256=sha(archive),archiveBytes=archive.stat().st_size))
    return found


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
            spec=importlib.util.spec_from_file_location('directory_patch',Path(__file__).with_name('patch-winpty-directory.py'))
            extension=importlib.util.module_from_spec(spec);spec.loader.exec_module(extension)
            report['sources']['winpty']['directoryExtension']=extension.apply(target)
            probe=out/'directory-probe'
            run(['cmake','-S',str(ROOT/'docs/testing/native/directory-probe'),'-B',str(probe),
                 '-G','Visual Studio 17 2022','-A','x64'],ROOT)
            run(['cmake','--build',str(probe),'--config','Release'],ROOT)
            first=out/'directory-first';second=out/'directory-second';first.mkdir();second.mkdir()
            run([str(probe/'Release/owned-directory-probe.exe'),str(first),str(second)],ROOT,30)
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
            spec=importlib.util.spec_from_file_location('console_security',Path(__file__).with_name('console-security-baseline.py'))
            security=importlib.util.module_from_spec(spec);spec.loader.exec_module(security)
            report['enumeratedVendorSecurityGate']=security.validate(PINS['console'][1],CONSOLE_VERSION)
            manifest=json.loads((target/'vcpkg.json').read_bytes())
            if manifest.get('builtin-baseline')!=PINS['vcpkg'][1]:raise ValueError('Pinned vcpkg baseline drift rejected')
            vcpkg,tool_source=checkout('vcpkg',out,run,env);report['sources']['vcpkg']=tool_source
            report['sources']['vcpkg']['preparedPortSources']=prefetch_port_trees(vcpkg,manifest,out,run,target)
            env.update(VCPKG_ROOT=str(vcpkg),VCPKG_DISABLE_METRICS='1',VCPKG_BINARY_SOURCES='clear',
                       VCPKG_DOWNLOADS=str(out/'vcpkg-downloads'))
            run(['cmd.exe','/d','/c','bootstrap-vcpkg.bat','-disableMetrics'],vcpkg)
            report['sources']['vcpkg']['bootstrapExecutableSha256']=sha(vcpkg/'vcpkg.exe')
            run([str(vcpkg/'vcpkg.exe'),'version'],vcpkg,30)
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
            installed=target/'obj/x64/vcpkg'
            report['sources']['console']['vcpkgInstalledFiles']={p.relative_to(installed).as_posix():sha(p)
                for p in installed.rglob('*') if p.is_file() and not p.is_symlink()}
            report['sources']['console']['vcpkgDownloadedInputs']={p.relative_to(out/'vcpkg-downloads').as_posix():sha(p)
                for p in (out/'vcpkg-downloads').rglob('*') if p.is_file() and not p.is_symlink()}
            report['sources']['console']['vcpkgBinaryCachePolicy']='clear; source builds, no external binary cache'
            found={name:[p for p in target.rglob(name) if p.is_file() and 'Release' in p.parts] for name in ['OpenConsole.exe','conpty.dll']}
            if any(len(values)!=1 for values in found.values()):raise ValueError('Ambiguous or missing compiled console outputs')
            report['outputs']+=outputs([v[0] for v in found.values()],target)
        report['status']='SOURCE_WINDOWS_PTY_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE'
    finally:(out/'report.json').write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print(report['status'])


if __name__=='__main__':main()
