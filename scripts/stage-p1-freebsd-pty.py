"""Stage only the two independently reviewed FreeBSD PTY source outputs."""
import json
from pathlib import Path
from evidence_io import stream_sha256
ROOT=Path(__file__).resolve().parents[1]
RUN='42003119496'
COMMIT='40610c294c54dd2a53526a0da8c623cdd7c0d562'
RECEIPTS={target:'docs/testing/gap-p1-2026-11-04/freebsd-'+bit+'-dual-components-acceptance.json' for target,bit in [('x86-64','x64'),('x86','x86')]}

def load_components(directory):
    result={}
    for target,receipt in RECEIPTS.items():
        accepted=json.loads((ROOT/receipt).read_bytes());base=directory/('p1-freebsd-'+target+'-pty-source')
        resource='resources/com/pty4j/native/freebsd/'+target+'/libpty.so'
        if (accepted['runId']!=RUN or accepted['commit']!=COMMIT or accepted['resource']!=resource
                or accepted['productAccepted'] is not False or accepted['paidRequests']!=0):raise ValueError('Exact FreeBSD component scope required')
        def verified(name,digest):
            path=base/name
            if path.is_symlink() or not path.is_file() or stream_sha256(path)!=digest:raise ValueError('FreeBSD component bytes changed: '+name)
            return path
        report=json.loads(verified('report.json',accepted['reportSha256']).read_bytes())
        if (report['status']!='FREEBSD_SOURCE_PTY_COMPONENT_PASS_NOT_PRODUCT_ACCEPTANCE' or report['runId']!=RUN
                or report['commit']!=COMMIT or report['target']!=target or report['resource']!=resource):raise ValueError('Mixed FreeBSD source identity')
        library=verified('libpty.so',accepted['outputSha256']);verified('original.so',accepted['originalSha256']);verified('pty-probe',accepted['probeSha256'])
        for name,digest in accepted['commandReceiptSha256'].items():
            c=json.loads(verified(name+'-command.json',digest).read_bytes());verified(name+'.log',c['logSha256'])
            if (c['status']!='PASS' or c['exitCode']!=0 or c['runId']!='p1-freebsd-pty-'+RUN
                    or c['sourceSha256']!=c['sourceAfterSha256']):raise ValueError('Failed or changed FreeBSD command')
        result[resource]=dict(path=library,sha256=accepted['outputSha256'],origin=dict(sourceBuildRun=RUN,sourceBuildCommit=COMMIT,sourceRevision=accepted['sourceRevision'],componentProbeRun=RUN,componentReportSha256=accepted['reportSha256'],acceptanceReceipt=receipt,scope='FreeBSD VM native PTY and child exchange; complete FreeBSD Java application not certified'))
    return result
