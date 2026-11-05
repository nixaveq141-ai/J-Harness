"""Stage the exact independently reviewed x86 WinPTY DLL and matching agent."""
import json
from pathlib import Path
from evidence_io import stream_sha256

ROOT=Path(__file__).resolve().parents[1]
RECEIPT='docs/testing/gap-p1-2026-11-04/windows-x86-pty-components-acceptance.json'
RUN='41998518872'
COMMIT='7c8c9e78755675cfbcad6bf20d2182f78c13e539'
NAMES={'winpty.dll','winpty-agent.exe'}


def load_components(directory,acceptance=None):
    accepted=acceptance if acceptance is not None else json.loads((ROOT/RECEIPT).read_bytes())
    if (accepted['status']!='WINDOWS_X86_WINPTY_TWO_SOURCE_COMPONENTS_INDEPENDENTLY_VERIFIED_NOT_PRODUCT_ACCEPTANCE'
            or accepted['runId']!=RUN or accepted['commit']!=COMMIT
            or accepted['productAccepted'] is not False or accepted['paidRequests']!=0
            or set(accepted['outputs'])!={'.build-cache/windows-pty-source/winpty/build/'+n for n in NAMES}):
        raise ValueError('Exact paired x86 WinPTY acceptance required')
    def verified(name,digest):
        path=directory/name
        if path.is_symlink() or not path.is_file() or stream_sha256(path)!=digest:
            raise ValueError('Accepted WinPTY component bytes changed: '+name)
        return path
    report=json.loads(verified('windows-pty-source/report.json',accepted['sourceReportSha256']).read_bytes())
    verified('p1-win32-pty-probe/report.json',accepted['probeReportSha256'])
    verified('p1-win32-pty-probe/probe.exe',accepted['probeSha256'])
    if report['commit']!=COMMIT or report['runId']!=RUN or report['targetArchitecture']!='x86':raise ValueError('Mixed source build identity')
    for command in report['commands']:
        verified('windows-pty-source/'+command['log'],command['logSha256'])
        if command['exitCode']!=0:raise ValueError('Failed source build')
    for name,digest in accepted['probeCommandReceiptSha256'].items():
        command=json.loads(verified('p1-win32-pty-probe/'+name+'-command.json',digest).read_bytes())
        verified('p1-win32-pty-probe/'+name+'.log',command['logSha256'])
        if command['status']!='PASS' or command['sourceSha256']!=command['sourceAfterSha256']:raise ValueError('Failed or changed probe')
    result={}
    for name in sorted(NAMES):
        member='windows-pty-source/winpty/build/'+name;digest=accepted['outputs']['.build-cache/'+member]
        result['resources/com/pty4j/native/win/x86/'+name]=dict(path=verified(member,digest),sha256=digest,origin=dict(
            sourceBuildRun=RUN,sourceBuildCommit=COMMIT,sourceRevision=accepted['sourceRevision'],
            componentProbeRun=RUN,componentReportSha256=accepted['sourceReportSha256'],
            acceptanceReceipt=RECEIPT,scope='Actual x86 WinPTY DLL and owned agent lifecycle; complete x86 JVM/Java not certified'))
    return result
