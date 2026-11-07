"""Build the fixed pty4j Cygwin launcher and exercise its actual three-pipe protocol."""
import hashlib,importlib.util,json,os,platform,shutil,subprocess,urllib.request
from pathlib import Path
from evidence_io import atomic_write_json,stream_sha256
from evidence_run import run
ROOT=Path(__file__).resolve().parents[1]
REVISION='b7554d649f6374183040e6554942b6d5b028b5e5'
RESOURCE='resources/com/pty4j/native/win/x86-64/cyglaunch.exe'
SETUP_URL='https://cygwin.com/setup/setup-2.941.x86_64.exe'
SETUP_SHA512='9bbbd4de497842446af58af875590ace5d8fa3c557cd7e27328569dfa68e58213da1cc1a6fb7102d4f0f01be38e0b92c6fa74f61b6eb7351a0481ca4bd9e8781'
EXEC_PTY_SHA256='93cd28367fdf2c39495f2ed112a076edf45eacc28489e4a0c6bc1833511e4443'

def verify_setup(raw):
    if hashlib.sha512(raw).hexdigest()!=SETUP_SHA512:raise ValueError('Pinned official Cygwin setup SHA512 required')

def configure_command(cmake,source,build,gcc,cxx,make):
    return [cmake,'-S',source,'-B',build,'-G','Unix Makefiles','-DCMAKE_POLICY_VERSION_MINIMUM=3.5',
            '-DCMAKE_C_COMPILER='+gcc,'-DCMAKE_CXX_COMPILER='+cxx,'-DCMAKE_MAKE_PROGRAM='+make]

def guard_linux_syscall_header(raw):
    if hashlib.sha256(raw).hexdigest()!=EXEC_PTY_SHA256 or raw.count(b'#include <sys/syscall.h>')!=1:
        raise ValueError('Exact fixed exec_pty source required for Linux header guard')
    return raw.replace(b'#include <sys/syscall.h>',b'#if defined(__linux__)\n#include <sys/syscall.h>\n#endif')

def require_x64_pe(raw):
    spec=importlib.util.spec_from_file_location('cyg_pe',ROOT/'docs/testing/tools/build-native-from-source.py')
    pe=importlib.util.module_from_spec(spec);spec.loader.exec_module(pe)
    return pe.validate_windows_pe(raw,'amd64')

def main():
    if os.name!='nt' or platform.machine().lower() not in {'amd64','x86_64'} or os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Dedicated x64 Windows CI required')
    out=ROOT/'.build-cache/p1-cyglaunch';out.mkdir(parents=True,exist_ok=False);rid='p1-cyglaunch-'+os.environ['GITHUB_RUN_ID']
    report=dict(status='INCOMPLETE',runId=os.environ['GITHUB_RUN_ID'],commit=os.environ['GITHUB_SHA'],sourceRevision=REVISION,resource=RESOURCE,commands=[],productAccepted=False,paidRequests=0)
    def command(argv,name,cwd=ROOT,timeout=600):
        receipt=out/(name+'-command.json')
        try:run(argv,name=name,log=out/(name+'.log'),receipt=receipt,run_id=rid,cwd=cwd,timeout=timeout)
        finally:
            if receipt.is_file():report['commands'].append(dict(name=name,sha256=stream_sha256(receipt)))
    try:
        setup=out/'setup-2.941.x86_64.exe'
        with urllib.request.urlopen(SETUP_URL,timeout=60) as response:raw=response.read(8*1024*1024)
        verify_setup(raw);setup.write_bytes(raw);report['installer']=dict(url=SETUP_URL,sha512=SETUP_SHA512,checksumSource='https://cygwin.com/setup/sha512.sum')
        cygroot=out/'cygwin';cache=out/'package-cache'
        command([str(setup),'--quiet-mode','--no-admin','--no-shortcuts','--no-version-check','--only-site','--root',str(cygroot),'--local-package-dir',str(cache),'--site','https://mirrors.kernel.org/sourceware/cygwin/','--packages','gcc-core,gcc-g++,cmake,make'],'install',timeout=1200)
        bin=cygroot/'bin';gcc=bin/'gcc.exe';cmake=bin/'cmake.exe';os.environ['PATH']=str(bin)+';'+os.environ['PATH']
        command([str(bin/'cygcheck.exe'),'-c'],'packages');command([str(gcc),'--version'],'compiler');command([str(gcc),'-dumpmachine'],'target')
        if (out/'target.log').read_text().strip()!='x86_64-pc-cygwin':raise ValueError('Actual Cygwin x64 target required')
        report['toolchain']={p.name:stream_sha256(p) for p in [gcc,bin/'g++.exe',cmake,bin/'make.exe',bin/'cygwin1.dll']}
        report['installedDbSha256']=stream_sha256(cygroot/'etc/setup/installed.db')
        report['packageInputs']={p.relative_to(cache).as_posix():stream_sha256(p) for p in cache.rglob('*') if p.is_file() and not p.is_symlink()}
        source=out/'pty4j';command(['git','-c','http.https://github.com/.extraheader=','clone','--no-checkout','--filter=blob:none','https://github.com/JetBrains/pty4j.git',str(source)],'clone');command(['git','-c','core.autocrlf=false','-c','core.eol=lf','checkout','--detach',REVISION],'checkout',source)
        if subprocess.check_output(['git','rev-parse','HEAD'],cwd=source).decode().strip()!=REVISION:raise ValueError('Fixed source revision required')
        names=subprocess.check_output(['git','ls-files','-z'],cwd=source).decode().split('\0');before={n:stream_sha256(source/n) for n in names if n and (source/n).is_file()};report['sourceTrackedFiles']=before
        original=source/'os/win/x86-64/cyglaunch.exe';inventory=json.loads((ROOT/'docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())['binaries'];expected=next(i['sha256'] for i in inventory if i['resource']==RESOURCE)
        if stream_sha256(original)!=expected:raise ValueError('Original inventory byte identity required')
        shutil.copyfile(original,out/'original.exe');report['originalSha256']=expected
        exec_pty=source/'native/exec_pty.c';exec_pty.write_bytes(guard_linux_syscall_header(exec_pty.read_bytes()))
        report['portabilityPatch']=dict(path='native/exec_pty.c',beforeSha256=EXEC_PTY_SHA256,afterSha256=stream_sha256(exec_pty),reason='sys/syscall.h is Linux-only here; its close_range use is already guarded by __linux__ in fixed upstream source')
        def posix(p):return subprocess.check_output([str(bin/'cygpath.exe'),'-u',str(p)]).decode().strip()
        build=out/'native-build';command(configure_command(str(cmake),posix(source/'native/cyglaunch'),posix(build),posix(gcc),posix(bin/'g++.exe'),posix(bin/'make.exe')),'configure')
        command([str(cmake),'--build',posix(build),'--parallel','2'],'build')
        output=out/'cyglaunch.exe';shutil.copyfile(build/'cyglaunch.exe',output)
        report['pe']=require_x64_pe(output.read_bytes());report['outputSha256']=stream_sha256(output)
        child=out/'owned-child.exe';probe=out/'probe.exe'
        for stem,target in [('p1-cyglaunch-child',child),('p1-cyglaunch',probe)]:command([str(gcc),'-std=c11','-Wall','-Wextra','-Werror','-O2',posix(ROOT/('scripts/probes/'+stem+'.c')),'-o',posix(target)],stem+'-build')
        report['childSha256']=stream_sha256(child);report['probeSha256']=stream_sha256(probe);report['probePe']=require_x64_pe(probe.read_bytes())
        report['probeSources']={n:stream_sha256(ROOT/n) for n in ['scripts/probes/p1-cyglaunch.c','scripts/probes/p1-cyglaunch-child.c']}
        work=out/'owned-work';work.mkdir();(work/'jh-owned-marker').write_bytes(b'owned-directory\n')
        command([str(probe),str(output),str(child),'present'],'candidate-probe',work,90)
        command([str(probe),str(output),str(child),'present-no-log'],'candidate-no-log-probe',work,90)
        command([str(probe),str(out/'absent.exe'),str(child),'missing'],'missing-probe',work,45)
        after={n:stream_sha256(source/n) for n in before};report['sourceTrackedFilesAfter']=after
        if {n for n in before if before[n]!=after[n]}!={'native/exec_pty.c'} or after['native/exec_pty.c']!=report['portabilityPatch']['afterSha256']:raise ValueError('Tracked upstream source drift')
        command(['git','diff','--binary'],'source-diff',source)
        report.update(status='CYGWIN_X64_SOURCE_PTY_COMPONENT_PASS_NOT_PRODUCT_ACCEPTANCE',hostImage={k:os.environ.get(k) for k in ['ImageOS','ImageVersion','RUNNER_ARCH']},limits=['External signed Cygwin runtime required; no arbitrary existing Cygwin version compatibility claim','Actual native three-pipe PTY component; complete target Java and product qualification pending'])
    finally:atomic_write_json(out/'report.json',report)

if __name__=='__main__':main()
