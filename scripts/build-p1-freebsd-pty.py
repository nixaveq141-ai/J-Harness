"""Build and exercise fixed FreeBSD x64 PTY source inside an isolated CI VM."""
import argparse
import json
import os
from pathlib import Path
import platform
import subprocess
from evidence_io import atomic_write_json,stream_sha256
from evidence_run import run

ROOT=Path(__file__).resolve().parents[1]
REVISION='b7554d649f6374183040e6554942b6d5b028b5e5'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--target',choices=['x86-64','x86'],default='x86-64');args=parser.parse_args()
    if platform.system()!='FreeBSD' or platform.machine()!='amd64' or os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Isolated FreeBSD amd64 CI required')
    out=ROOT/'.build-cache/p1-freebsd-pty';out.mkdir(parents=True,exist_ok=False)
    report=dict(status='INCOMPLETE',runId=os.environ['GITHUB_RUN_ID'],commit=os.environ['GITHUB_SHA'],sourceRevision=REVISION,target=args.target,paidRequests=0,productAccepted=False,commands=[])
    def command(argv,name,cwd=ROOT,timeout=300):
        log=out/(name+'.log');receipt=out/(name+'-command.json')
        run(argv,name=name,log=log,receipt=receipt,run_id='p1-freebsd-pty-'+report['runId'],cwd=cwd,timeout=timeout)
        report['commands'].append(dict(name=name,sha256=stream_sha256(receipt)))
    try:
        command(['uname','-a'],'system');command(['pkg','info'],'packages');command(['cc','--version'],'compiler')
        source=out/'pty4j'
        command(['git','-c','http.https://github.com/.extraheader=','clone','--no-checkout','https://github.com/JetBrains/pty4j.git',str(source)],'clone')
        command(['git','-c','core.autocrlf=false','-c','core.eol=lf','checkout','--detach',REVISION],'checkout',source)
        names=subprocess.check_output(['git','ls-files','-z'],cwd=source).split(b'\0')
        before={n.decode():stream_sha256(source/n.decode()) for n in names if n and (source/n.decode()).is_file()};report['sourceTrackedFiles']=before
        relative='os/freebsd/'+args.target+'/libpty.so';library=source/relative
        item=next(x for x in json.loads((ROOT/'docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())['binaries'] if x['resource']=='resources/com/pty4j/native/freebsd/'+args.target+'/libpty.so')
        if stream_sha256(library)!=item['sha256']:raise ValueError('Original FreeBSD resource differs from frozen inventory')
        original=out/'original.so';original.write_bytes(library.read_bytes());library.unlink()
        compiler=['cc']+(['-m32'] if args.target=='x86' else [])
        command(['gmake','-C','native','-B','linux_x86_64','LINUX=freebsd','ARCH_FLAG_X86_64=','ARCH_X86_64='+args.target,'CC='+' '.join(compiler)],'build',source)
        raw=library.read_bytes()
        width,machine=(1,3) if args.target=='x86' else (2,62)
        if raw[:6]!=b'\x7fELF'+bytes([width,1]) or int.from_bytes(raw[18:20],'little')!=machine:raise ValueError('Requested FreeBSD ELF class/machine required')
        output=out/'libpty.so';output.write_bytes(raw)
        command(['readelf','-h','-d','-Ws',str(output)],'elf-audit')
        probe=out/'pty-probe'
        command([*compiler,'-std=c11','-Wall','-Wextra','-Werror','-O2',str(ROOT/'scripts/probes/p1-pty.c'),'-o',str(probe)],'probe-build')
        command([str(probe),str(output),'present','native'],'candidate-probe',timeout=30)
        command([str(probe),str(out/'absent.so'),'missing'],'missing-probe',timeout=30)
        after={n:stream_sha256(source/n) for n in before}
        if {n for n in before if before[n]!=after[n]}!={relative}:raise ValueError('Unexpected source mutation')
        report.update(status='FREEBSD_SOURCE_PTY_COMPONENT_PASS_NOT_PRODUCT_ACCEPTANCE',resource=item['resource'],originalSha256=item['sha256'],outputSha256=stream_sha256(output),probeSha256=stream_sha256(probe),probeSourceSha256=stream_sha256(ROOT/'scripts/probes/p1-pty.c'),sourceTrackedFilesAfter=after,limits=['FreeBSD VM native component probe only; no complete Java application or OS/CPE certification'])
    finally:atomic_write_json(out/'report.json',report)


if __name__=='__main__':main()
