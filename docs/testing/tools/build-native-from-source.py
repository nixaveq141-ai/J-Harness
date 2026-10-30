"""Build pinned JNA and POSIX PTY candidates; never silently replace product natives."""
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import time
import urllib.request

ROOT=Path(__file__).resolve().parents[3]
REVISIONS={'jna':('java-native-access/jna','c366bc4fd0c50a30ac8d2475603ec564f6611462'),
           'pty4j':('JetBrains/pty4j','b7554d649f6374183040e6554942b6d5b028b5e5')}
FFI_URL='https://github.com/libffi/libffi/releases/download/v3.4.4/libffi-3.4.4.tar.gz'
FFI_SHA='d66c56ad259a82cf2a9dfc408b32bf5da52371500b84745f7fb8b645712df676'
FFI_BYTES=1362394


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare_libffi(jna,out,fetch=None):
    if fetch is None:
        with urllib.request.urlopen(FFI_URL,timeout=45) as response:raw=response.read(16*1024*1024+1)
    else:raw=fetch(FFI_URL)
    if len(raw)!=FFI_BYTES or hashlib.sha256(raw).hexdigest()!=FFI_SHA:raise ValueError('Pinned libffi release mismatch')
    source=jna/'native/libffi';members={};inputs={};generated={}
    helpers={'configure','aclocal.m4','compile','config.guess','config.sub','depcomp','install-sh','ltmain.sh','missing'}
    with tarfile.open(fileobj=io.BytesIO(raw)) as archive:
        for member in archive.getmembers():
            if not member.isfile():continue
            parts=Path(member.name).parts
            if not parts or parts[0]!='libffi-3.4.4' or '..' in parts or '\\' in member.name:raise ValueError('Release path rejected')
            relative=Path(*parts[1:])
            if member.size>4*1024*1024:raise ValueError('Release member bound')
            members[relative.as_posix()]=(archive.extractfile(member).read(),member.mode)
        for name,(data,_) in members.items():
            if name=='configure.ac' or name.endswith('Makefile.am'):
                existing=(source/name).read_bytes()
                if existing.replace(b'\r\n',b'\n')!=data.replace(b'\r\n',b'\n'):raise ValueError('Autotools source differs from pinned release')
                inputs[name]=dict(sourceSha256=hashlib.sha256(existing).hexdigest(),releaseSha256=hashlib.sha256(data).hexdigest())
        if len(inputs)!=6:raise ValueError('Expected six exact autotools source inputs')
        for name,(data,mode) in members.items():
            target=source/name
            if (name.endswith('.in') or Path(name).name in helpers) and not target.exists():
                if not target.resolve().is_relative_to(source.resolve()):raise ValueError('Generated input path bound')
                target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
                if os.name=='posix':target.chmod(0o755 if mode&0o111 else 0o644)
                generated[name]=hashlib.sha256(data).hexdigest()
    # Generated dependency files must share a stable timestamp newer than checkout.
    # Otherwise make attempts to regenerate them with unavailable host autotools.
    stamp=time.time_ns()
    for name in generated:os.utime(source/name,ns=(stamp,stamp))
    (out/'libffi-release-3.4.4.tar.gz').write_bytes(raw)
    return dict(url=FFI_URL,sha256=FFI_SHA,matchedAutotoolsInputs=inputs,generatedInputs=generated,
                policy='Only missing generated files; never overwrite vendored source')


def main():
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Use dedicated source-build CI')
    out=ROOT/'.build-cache/native-source-build';out.mkdir(parents=True)
    env={k:os.environ[k] for k in ['PATH','JAVA_HOME','SystemRoot','WINDIR','TEMP','TMP','HOME','SDKROOT'] if k in os.environ}
    env.update(GIT_TERMINAL_PROMPT='0',GCM_INTERACTIVE='Never')
    report=dict(status='INCOMPLETE',commit=os.environ.get('GITHUB_SHA'),runId=os.environ.get('GITHUB_RUN_ID'),
                os=platform.platform(),architecture=platform.machine(),commands=[],sources={},outputs={},
                productReplacement=False,windowsPty='NOT_SOURCE_BUILT')
    def run(command,cwd,seconds=900):
        index=len(report['commands'])+1;log=out/(str(index)+'.log');begin=time.monotonic()
        record=dict(argv=command,cwd=Path(cwd).relative_to(ROOT).as_posix(),exitCode=None,log=log.name)
        executed=['cmd.exe','/d','/c','call',*command] if os.name=='nt' and command[0]=='ant' else command
        record['executedArgv']=executed
        report['commands'].append(record)
        try:
            with log.open('xb') as stream:
                result=subprocess.run(executed,cwd=cwd,env=env,stdin=subprocess.DEVNULL,stdout=stream,stderr=subprocess.STDOUT,timeout=seconds)
            record['exitCode']=result.returncode
        except Exception as error:
            record['failureType']=type(error).__name__
            raise
        finally:
            record.update(elapsedSeconds=round(time.monotonic()-begin,3),logSha256=digest(log))
        if result.returncode:raise ValueError('Source build command failed; see fixed command log')
        return log
    try:
        run(['java','-version'],ROOT,30);run(['ant','-version'],ROOT,30)
        for name,(repo,revision) in REVISIONS.items():
            target=out/name
            run(['git','-c','http.https://github.com/.extraheader=','clone','--no-checkout','--filter=blob:none',
                 'https://github.com/'+repo+'.git',str(target)],ROOT)
            run(['git','checkout','--detach',revision],target)
            actual=subprocess.run(['git','rev-parse','HEAD'],cwd=target,env=env,capture_output=True,check=True).stdout.decode().strip()
            if actual!=revision:raise ValueError('Pinned revision mismatch')
            paths=subprocess.run(['git','ls-files','-z'],cwd=target,env=env,capture_output=True,check=True).stdout.split(b'\0')
            files={p.decode():digest(target/p.decode()) for p in paths if p and (target/p.decode()).is_file()}
            report['sources'][name]=dict(repository=repo,revision=revision,trackedFiles=files)
        jna=out/'jna'
        report['sources']['jna']['libffiBuildInputs']=prepare_libffi(jna,out)
        makefile=jna/'native/Makefile';before=makefile.read_bytes()
        old=b'FFI_CONFIG=--enable-static --disable-shared --with-pic=yes'
        if before.count(old)!=2:raise ValueError('Pinned libffi build option anchors changed')
        after=before.replace(old,old+b' --disable-docs');makefile.write_bytes(after)
        report['sources']['jna']['manualGenerationOnlyPatch']=dict(beforeSha256=hashlib.sha256(before).hexdigest(),afterSha256=hashlib.sha256(after).hexdigest(),option='--disable-docs')
        run(['gcc','--version'],ROOT,30)
        run(['ant','-Dbuild-native=true','native'],jna)
        candidates=[p for p in (jna/'build').rglob('*') if p.is_file() and p.name in ['jnidispatch.dll','libjnidispatch.so','libjnidispatch.jnilib','libjnidispatch.dylib']]
        if not candidates:raise ValueError('No compiled JNA candidate')
        report['outputs']['jna']=[dict(path=p.relative_to(ROOT).as_posix(),sha256=digest(p)) for p in candidates]
        if os.name=='posix':
            pty=out/'pty4j';mac=platform.system()=='Darwin'
            output=pty/('os/darwin/libpty.dylib' if mac else 'os/linux/x86-64/libpty.so')
            if not output.resolve().is_relative_to(pty.resolve()):raise ValueError('Output path bound')
            previous=digest(output) if output.exists() else None
            output.unlink(missing_ok=True)
            command=['make','-C','native','-B','x86_64','CC=clang'] if mac else ['make','-C','native','-B','linux_x86_64','ARCH_FLAG_X86_64=']
            run(command,pty)
            if not output.is_file():raise ValueError('Compiled PTY candidate missing')
            report['outputs']['pty']=dict(path=output.relative_to(ROOT).as_posix(),sha256=digest(output),removedPrebuiltSha256=previous)
        for name in REVISIONS:
            target=out/name
            patch=subprocess.run(['git','diff','--binary'],cwd=target,env=env,capture_output=True,check=True).stdout
            (out/(name+'-build-source-diff.patch')).write_bytes(patch)
            report['sources'][name]['buildSourceDiffSha256']=hashlib.sha256(patch).hexdigest()
        report['status']='SOURCE_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE'
    finally:(out/'report.json').write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print(report['status'])


if __name__=='__main__':main()
