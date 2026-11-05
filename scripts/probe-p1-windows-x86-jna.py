"""Check the fresh x86 source output using an actual 32-bit owned C process."""
import json
import os
from pathlib import Path
import subprocess
from evidence_io import atomic_write_json,stream_sha256
from evidence_run import run

ROOT=Path(__file__).resolve().parents[1]


def main():
    if os.name!='nt' or os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Windows CI required')
    base=ROOT/'.build-cache/native-source-build';report=json.loads((base/'report.json').read_bytes())
    if report['status']!='SOURCE_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE' or report['windowsJnaTarget']!='x86':raise ValueError('Successful explicit x86 source candidate required')
    library=base/'jna/build/native-win32-x86/jnidispatch.dll'
    item=next(x for x in report['outputs']['jna'] if x['path']==library.relative_to(ROOT).as_posix())
    if library.is_symlink() or stream_sha256(library)!=item['sha256']:raise ValueError('Exact compiled DLL required')
    out=ROOT/'.build-cache/p1-win32-probe';out.mkdir(exist_ok=False);java=Path(os.environ['JAVA_HOME']);gcc='C:/msys64/mingw32/bin/gcc.exe'
    receipt=dict(status='INCOMPLETE',sourceRun=report['runId'],sourceCommit=report['commit'],sourceReportSha256=stream_sha256(base/'report.json'),dllSha256=item['sha256'],paidRequests=0,productAccepted=False)
    def command(argv,name):
        run(argv,name=name,log=out/(name+'.log'),receipt=out/(name+'-command.json'),run_id='p1-win32-'+os.environ['GITHUB_RUN_ID'],timeout=60)
    try:
        exe=out/'probe.exe'
        command([gcc,'-std=c11','-Wall','-Wextra','-Werror','-municode','-I'+str(java/'include'),'-I'+str(java/'include/win32'),'-I'+str(base/'jna/build/headers'),str(ROOT/'scripts/probes/p1-win-jna.c'),'-o',str(exe)],'compile')
        command([str(exe),str(library),'present'],'load')
        command([str(exe),str(out/'absent.dll'),'missing'],'missing')
        if stream_sha256(library)!=item['sha256']:raise ValueError('DLL changed during probe')
        receipt.update(status='X86_WINDOWS_JNI_PRIMITIVE_COMPONENT_PASS_NOT_PRODUCT_ACCEPTANCE',probeSha256=stream_sha256(exe),probeSourceSha256=stream_sha256(ROOT/'scripts/probes/p1-win-jna.c'),limits=['C-level JNI primitives only; no complete JVM or Java application validation','No uniform candidate replacement yet'])
    finally:atomic_write_json(out/'report.json',receipt)
    print(receipt['status'])


if __name__=='__main__':main()
