"""Load only the exact independently reviewed Cygwin dual-mode source component."""
import importlib.util,json
from pathlib import Path
from evidence_io import stream_sha256
ROOT=Path(__file__).resolve().parents[1]
RECEIPT='docs/testing/gap-p1-2026-11-04/cyglaunch-component-acceptance.json'
RUN='42375096503'
COMMIT='d10e7bc7b5f9c917b84760ba6a12e04339b2dd89'
RESOURCE='resources/com/pty4j/native/win/x86-64/cyglaunch.exe'
COMMANDS={'install','packages','compiler','target','clone','checkout','configure','build','p1-cyglaunch-child-build','p1-cyglaunch-build','candidate-probe','candidate-no-log-probe','missing-probe','source-diff'}

def load_components(directory,acceptance=None):
    a=acceptance if acceptance is not None else json.loads((ROOT/RECEIPT).read_bytes())
    if (a['status']!='CYGWIN_X64_SOURCE_AND_TWO_MODE_PTY_COMPONENT_INDEPENDENTLY_VERIFIED_NOT_PRODUCT_ACCEPTANCE' or a['runId']!=RUN or a['commit']!=COMMIT
            or a['resource']!=RESOURCE or a['sourceRevision']!='b7554d649f6374183040e6554942b6d5b028b5e5' or a['productAccepted'] is not False or a['paidRequests']!=0):raise ValueError('Exact Cygwin component acceptance required')
    base=directory/'p1-cygwin-x64-launcher-source'
    def verified(name,digest):
        path=base/name
        if path.is_symlink() or not path.is_file() or stream_sha256(path)!=digest:raise ValueError('Cygwin accepted bytes changed: '+name)
        return path
    r=json.loads(verified('report.json',a['sourceReportSha256']).read_bytes())
    if r['status']!='CYGWIN_X64_SOURCE_PTY_COMPONENT_PASS_NOT_PRODUCT_ACCEPTANCE' or r['runId']!=RUN or r['commit']!=COMMIT or r['resource']!=RESOURCE or r['sourceRevision']!=a['sourceRevision']:raise ValueError('Wrong Cygwin source identity')
    path=verified('cyglaunch.exe',a['outputSha256']);verified('original.exe',a['originalSha256']);verified('probe.exe',a['probeSha256']);verified('owned-child.exe',a['childSha256'])
    if r['outputSha256']!=a['outputSha256'] or r['portabilityPatch']!=a['portabilityPatch'] or r['probeSources']!=a['probeSources']:raise ValueError('Cygwin source/probe identity drift')
    spec=importlib.util.spec_from_file_location('cyg_builder',ROOT/'scripts/build-p1-cyglaunch.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    if m.require_x64_pe(path.read_bytes())!=a['pe']:raise ValueError('Cygwin PE identity drift')
    seen=set();source=None
    for row in r['commands']:
        name=row['name']
        if name in seen or name not in COMMANDS or row['sha256']!=a['commandReceiptSha256'][name]:raise ValueError('Exact Cygwin command set required')
        seen.add(name);c=json.loads(verified(name+'-command.json',row['sha256']).read_bytes());verified(name+'.log',c['logSha256'])
        if c['name']!=name or c['status']!='PASS' or c['exitCode']!=0 or c['runId']!='p1-cyglaunch-'+RUN or c['sourceSha256']!=c['sourceAfterSha256']:raise ValueError('Failed Cygwin component command')
        if source is None:source=c['sourceSha256']
        elif source!=c['sourceSha256']:raise ValueError('Mixed Cygwin component sources')
    if seen!=COMMANDS:raise ValueError('Incomplete Cygwin command set')
    return {RESOURCE:dict(path=path,sha256=a['outputSha256'],origin=dict(sourceBuildRun=RUN,sourceBuildCommit=COMMIT,sourceRevision=a['sourceRevision'],componentProbeRun=RUN,componentReportSha256=a['sourceReportSha256'],acceptanceReceipt=RECEIPT,portabilityPatch=a['portabilityPatch'],scope='Cygwin native PTY three pipes/cwd/input/exit in logged and default no-log modes; full Java product qualification pending'))}
