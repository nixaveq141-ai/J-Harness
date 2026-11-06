"""Stage the exact independently audited Windows ARM64 JNA and helper components."""
import importlib.util
import json
from pathlib import Path
from evidence_io import stream_sha256
ROOT=Path(__file__).resolve().parents[1]
SPECS={
 'jna':dict(run='42159389661',commit='3e71dddc18b47e25a65a57f218a8f8eed6b91f03',resource='com/sun/jna/win32-aarch64/jnidispatch.dll',revision='c366bc4fd0c50a30ac8d2475603ec564f6611462',file='jnidispatch.dll',commands={'make','jdk','clone','checkout','ant','build','pe-audit','probe-build','candidate-probe','missing-probe','source-diff'}),
 'helper':dict(run='42158936851',commit='b2a18620cc43d0255c5107d9ef0ba0e1ee16477c',resource='resources/com/pty4j/native/win/aarch64/win-helper.dll',revision='b7554d649f6374183040e6554942b6d5b028b5e5',file='win-helper.dll',commands={'cmake','ninja','clone','checkout','configure','build','pe-audit','probe-build','candidate-probe','missing-probe'})}
def receipts():return {key:'docs/testing/gap-p1-2026-11-04/windows-arm64-'+key+'-component-acceptance.json' for key in SPECS}
def load_components(directory,acceptances=None):
 spec=importlib.util.spec_from_file_location('arm_pe',ROOT/'scripts/build-p1-windows-arm64-jna.py');arm=importlib.util.module_from_spec(spec);spec.loader.exec_module(arm)
 accepted=acceptances if acceptances is not None else {key:json.loads((ROOT/path).read_bytes()) for key,path in receipts().items()}
 if set(accepted)!=set(SPECS):raise ValueError('Exact two ARM64 component scopes required')
 result={}
 for key,s in SPECS.items():
  a=accepted[key];base=directory/('p1-windows-arm64-'+key+'-source')
  if (a['status']!='ARM64_'+key.upper()+'_SOURCE_COMPONENT_INDEPENDENTLY_VERIFIED_NOT_PRODUCT_ACCEPTANCE' or a['runId']!=s['run'] or a['commit']!=s['commit'] or a['resource']!=s['resource'] or a['sourceRevision']!=s['revision'] or a['paidRequests']!=0 or a['productAccepted'] is not False):raise ValueError('Exact ARM64 acceptance identity required')
  def verified(name,digest):
   p=base/name
   if p.is_symlink() or not p.is_file() or stream_sha256(p)!=digest:raise ValueError('ARM64 accepted bytes changed: '+name)
   return p
  report=json.loads(verified('report.json',a['reportSha256']).read_bytes())
  status='ARM64_WINDOWS_JNI_PRIMITIVE_COMPONENT_PASS_NOT_PRODUCT_ACCEPTANCE' if key=='jna' else 'ARM64_WINDOWS_HELPER_COMPONENT_PASS_NOT_PRODUCT_ACCEPTANCE'
  if report['status']!=status or report['runId']!=s['run'] or report['commit']!=s['commit'] or report['sourceRevision']!=s['revision'] or report['resource']!=s['resource'] or report['productAccepted'] is not False or report['paidRequests']!=0:raise ValueError('Mixed ARM64 component identity')
  output=verified(s['file'],a['outputSha256']);verified('original.dll',a['originalSha256']);probe=verified('probe.exe',a['probeSha256'])
  if report['outputSha256']!=a['outputSha256'] or report['probeSourceSha256']!=a['probeSourceSha256']:raise ValueError('ARM64 probe/output identity drift')
  if arm.require_arm64_pe(output.read_bytes())!=report['pe'] or arm.require_arm64_pe(probe.read_bytes())!=report['probePe']:raise ValueError('ARM64 PE identity changed')
  if key=='jna':verified('libffi-release-3.4.4.tar.gz',a['libffiReleaseSha256'])
  rows=report['commands'];names=[r['name'] for r in rows]
  if len(names)!=len(set(names)) or set(names)!=s['commands'] or set(a['commandReceiptSha256'])!=s['commands']:raise ValueError('Exact ARM64 command set required')
  source=None
  for item in rows:
   name=item['name']
   if item['sha256']!=a['commandReceiptSha256'][name]:raise ValueError('ARM64 command receipt identity drift')
   c=json.loads(verified(name+'-command.json',item['sha256']).read_bytes());verified(name+'.log',c['logSha256'])
   if c['status']!='PASS' or c['exitCode']!=0 or c['runId']!='p1-winarm-'+key+'-'+s['run'] or c['name']!=name or c['sourceSha256']!=c['sourceAfterSha256']:raise ValueError('Failed or mixed ARM64 command')
   if source is None:source=c['sourceSha256']
   elif source!=c['sourceSha256']:raise ValueError('Mixed ARM64 source fingerprints')
  result[s['resource']]=dict(path=output,sha256=a['outputSha256'],origin=dict(sourceBuildRun=s['run'],sourceBuildCommit=s['commit'],sourceRevision=s['revision'],componentReportSha256=a['reportSha256'],acceptanceReceipt=receipts()[key],scope='Actual native ARM64 owned C component probe; complete Java product on ARM64 not certified'))
 return result
