"""Record actual 32-bit WinPTY DLL/agent lifecycle and exact missing-file refusal."""
import json
import os
from pathlib import Path
from evidence_io import atomic_write_json,stream_sha256
from evidence_run import run

ROOT=Path(__file__).resolve().parents[1]


def main():
    if os.name!='nt' or os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Windows CI required')
    base=ROOT/'.build-cache/windows-pty-source';source=base/'report.json';report=json.loads(source.read_bytes())
    if report['targetArchitecture']!='x86' or report['status']!='SOURCE_WINDOWS_PTY_CANDIDATES_BUILT_NOT_PRODUCT_ACCEPTANCE':raise ValueError('Exact x86 source build required')
    out=ROOT/'.build-cache/p1-win32-pty-probe';out.mkdir(exist_ok=False)
    receipt=dict(status='INCOMPLETE',runId=report['runId'],commit=report['commit'],sourceReportSha256=stream_sha256(source),paidRequests=0,productAccepted=False)
    def command(argv,name):run(argv,name=name,log=out/(name+'.log'),receipt=out/(name+'-command.json'),run_id='p1-win32-pty-'+os.environ['GITHUB_RUN_ID'],timeout=90)
    try:
        before={r['path']:stream_sha256(ROOT/r['path']) for r in report['outputs']}
        if before!={r['path']:r['sha256'] for r in report['outputs']}:raise ValueError('Compiled DLL/agent changed')
        exe=out/'probe.exe';library=base/'winpty/build/winpty.dll'
        command(['C:/msys64/mingw32/bin/g++.exe','-std=c++11','-Wall','-Wextra','-Werror','-municode','-static-libgcc','-static-libstdc++','-I'+str(base/'winpty/src/include'),str(ROOT/'scripts/probes/p1-winpty-x86.cpp'),'-o',str(exe)],'compile')
        command([str(exe),str(library),'present'],'load')
        command([str(exe),str(out/'absent.dll'),'missing'],'missing')
        if before!={n:stream_sha256(ROOT/n) for n in before}:raise ValueError('Component changed during probe')
        receipt.update(status='WINDOWS_X86_WINPTY_AGENT_COMPONENT_PASS_NOT_PRODUCT_ACCEPTANCE',probeSha256=stream_sha256(exe),probeSourceSha256=stream_sha256(ROOT/'scripts/probes/p1-winpty-x86.cpp'),outputs=before,limits=['Actual C++ x86 DLL and owned agent lifecycle, not complete Java application certification'])
    finally:atomic_write_json(out/'report.json',receipt)


if __name__=='__main__':main()
