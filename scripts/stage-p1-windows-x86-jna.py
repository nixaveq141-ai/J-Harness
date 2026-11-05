"""Load only the independently reviewed Windows x86 JNA source component."""
import json
from pathlib import Path
from evidence_io import stream_sha256

ROOT=Path(__file__).resolve().parents[1]
RECEIPT='docs/testing/gap-p1-2026-11-04/windows-x86-components-acceptance.json'
RUN='41992679907'
COMMIT='e6d3091a56e2551fc864afaefc8b74a3e7e3cf2a'
RESOURCE='com/sun/jna/win32-x86/jnidispatch.dll'


def load_components(directory,acceptance=None):
    accepted=acceptance if acceptance is not None else json.loads((ROOT/RECEIPT).read_bytes())
    if (accepted['status']!='WINDOWS_X86_JNA_SOURCE_AND_C_PRIMITIVE_COMPONENT_INDEPENDENTLY_VERIFIED_NOT_PRODUCT_ACCEPTANCE'
            or accepted['runId']!=RUN or accepted['commit']!=COMMIT
            or accepted['resource']!=RESOURCE or accepted['paidRequests']!=0
            or accepted['productAccepted'] is not False):
        raise ValueError('Exact accepted Windows x86 component required')
    def verified(name,digest):
        path=directory/name
        if path.is_symlink() or not path.is_file() or stream_sha256(path)!=digest:
            raise ValueError('Accepted Windows x86 bytes changed: '+name)
        return path
    report=json.loads(verified('native-source-build/report.json',accepted['sourceReportSha256']).read_bytes())
    probe=json.loads(verified('p1-win32-probe/report.json',accepted['probeReportSha256']).read_bytes())
    if (report['commit']!=COMMIT or report['runId']!=RUN or report['windowsJnaTarget']!='x86'
            or probe['sourceCommit']!=COMMIT or probe['sourceRun']!=RUN
            or probe['dllSha256']!=accepted['dllSha256']):
        raise ValueError('Mixed Windows x86 component identity')
    library=verified('native-source-build/jna/build/native-win32-x86/jnidispatch.dll',accepted['dllSha256'])
    verified('native-source-build/jna/build/classes/com/sun/jna/win32-x86/jnidispatch.dll',accepted['dllSha256'])
    verified('native-source-build/libffi-release-3.4.4.tar.gz',accepted['libffiReleaseSha256'])
    verified('p1-win32-probe/probe.exe',accepted['probeSha256'])
    for command in report['commands']:
        verified('native-source-build/'+command['log'],command['logSha256'])
        if command['exitCode']!=0:raise ValueError('Failed source build command')
    for name,digest in accepted['probeCommandReceiptSha256'].items():
        command=json.loads(verified('p1-win32-probe/'+name+'-command.json',digest).read_bytes())
        verified('p1-win32-probe/'+name+'.log',command['logSha256'])
        if (command['status']!='PASS' or command['exitCode']!=0
                or command['sourceSha256']!=command['sourceAfterSha256']):
            raise ValueError('Failed or changed component probe')
    return {RESOURCE:dict(path=library,sha256=accepted['dllSha256'],origin=dict(
        sourceBuildRun=RUN,sourceBuildCommit=COMMIT,
        sourceRevision=report['sources']['jna']['revision'],
        componentProbeRun=RUN,componentReportSha256=accepted['sourceReportSha256'],
        libffiReleaseSha256=accepted['libffiReleaseSha256'],acceptanceReceipt=RECEIPT,
        scope='Actual 32-bit C JNI primitives; complete x86 JVM/Java not certified'))}
