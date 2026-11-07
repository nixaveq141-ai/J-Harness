"""Stage the exact independently reviewed ARM64 WinPTY DLL and owned agent."""
import importlib.util
import json
from pathlib import Path
from evidence_io import stream_sha256

ROOT=Path(__file__).resolve().parents[1]
RECEIPT='docs/testing/gap-p1-2026-11-04/windows-arm64-winpty-components-acceptance.json'
RUN='42228145670'
COMMIT='ead88a173d1d787a11f2bdadc6a0f831be164684'
REVISION='7e59fe2d09adf0fa2aa606492e7ca98efbc5184e'
RESOURCES={'resources/com/pty4j/native/win/aarch64/'+n for n in ['winpty.dll','winpty-agent.exe']}
COMMANDS={'packages','compiler','target','winpty-clone','winpty-checkout','pty4j-clone','pty4j-checkout','build','probe-build','candidate-probe','missing-probe','source-diff'}

def load_components(directory,acceptance=None):
    a=acceptance if acceptance is not None else json.loads((ROOT/RECEIPT).read_bytes())
    if (a['status']!='ARM64_WINPTY_TWO_SOURCE_COMPONENTS_INDEPENDENTLY_VERIFIED_NOT_PRODUCT_ACCEPTANCE'
            or a['runId']!=RUN or a['commit']!=COMMIT or a['sourceRevision']!=REVISION
            or set(a['outputs'])!=RESOURCES or a['productAccepted'] is not False or a['paidRequests']!=0):
        raise ValueError('Exact ARM64 WinPTY pair acceptance required')
    base=directory/'p1-windows-arm64-winpty-source'
    def verified(name,digest):
        p=base/name
        if p.is_symlink() or not p.is_file() or stream_sha256(p)!=digest:raise ValueError('ARM64 WinPTY accepted bytes changed: '+name)
        return p
    spec=importlib.util.spec_from_file_location('arm_pe',ROOT/'scripts/build-p1-windows-arm64-jna.py')
    arm=importlib.util.module_from_spec(spec);spec.loader.exec_module(arm)
    r=json.loads(verified('report.json',a['reportSha256']).read_bytes())
    if (r['status']!='ARM64_WINPTY_AGENT_SOURCE_COMPONENT_PASS_NOT_PRODUCT_ACCEPTANCE' or r['runId']!=RUN
            or r['commit']!=COMMIT or r['sourceRevision']!=REVISION or r['paidRequests']!=0 or r['productAccepted'] is not False):
        raise ValueError('Wrong ARM64 WinPTY source report identity')
    probe=verified('probe.exe',a['probeSha256'])
    if arm.require_arm64_pe(probe.read_bytes())!=r['probePe'] or r['probeSourceSha256']!=a['probeSourceSha256']:
        raise ValueError('ARM64 WinPTY probe identity drift')
    seen=set();source=None
    for row in r['commands']:
        name=row['name']
        if name in seen or name not in COMMANDS or row['sha256']!=a['commandReceiptSha256'][name]:raise ValueError('Exact ARM64 WinPTY command set required')
        seen.add(name);c=json.loads(verified(name+'-command.json',row['sha256']).read_bytes())
        if c['status']!='PASS' or c['exitCode']!=0 or c['name']!=name or c['runId']!='p1-winarm-winpty-'+RUN or c['sourceSha256']!=c['sourceAfterSha256']:
            raise ValueError('Failed ARM64 WinPTY command')
        verified(name+'.log',c['logSha256'])
        if source is None:source=c['sourceSha256']
        elif source!=c['sourceSha256']:raise ValueError('Mixed ARM64 WinPTY command sources')
    if seen!=COMMANDS or set(a['commandReceiptSha256'])!=COMMANDS:raise ValueError('Incomplete ARM64 WinPTY commands')
    result={}
    for resource in sorted(RESOURCES):
        name=resource.rsplit('/',1)[-1];item=a['outputs'][resource];p=verified(name,item['sha256'])
        original=verified('original-'+name,item['originalSha256']);arm.require_arm64_pe(original.read_bytes())
        if (r['outputs'][name]!=item['sha256'] or r['originalSha256'][resource]!=item['originalSha256']
                or arm.require_arm64_pe(p.read_bytes())!=item['pe'] or r['pe'][name]!=item['pe']):raise ValueError('ARM64 WinPTY payload identity drift')
        result[resource]=dict(path=p,sha256=item['sha256'],origin=dict(sourceBuildRun=RUN,sourceBuildCommit=COMMIT,sourceRevision=REVISION,
            componentReportSha256=a['reportSha256'],componentProbeRun=RUN,acceptanceReceipt=RECEIPT,
            scope='Native ARM64 C++ exact DLL and owned agent lifecycle; full target Java application not certified'))
    return result
