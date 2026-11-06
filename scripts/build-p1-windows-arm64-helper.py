"""Build fixed pty4j ARM64 helper and probe only this owned process."""
import importlib.util
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
from evidence_io import atomic_write_json,stream_sha256
from evidence_run import run
ROOT=Path(__file__).resolve().parents[1]
REVISION='b7554d649f6374183040e6554942b6d5b028b5e5'
RESOURCE='resources/com/pty4j/native/win/aarch64/win-helper.dll'
spec=importlib.util.spec_from_file_location('arm',ROOT/'scripts/build-p1-windows-arm64-jna.py');arm=importlib.util.module_from_spec(spec);spec.loader.exec_module(arm)
def main():
 if os.name!='nt' or platform.machine().lower() not in {'arm64','aarch64'} or os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Native Windows ARM64 CI required')
 out=ROOT/'.build-cache/p1-windows-arm64-helper';out.mkdir(parents=True,exist_ok=False)
 rid='p1-winarm-helper-'+os.environ['GITHUB_RUN_ID'];report=dict(status='INCOMPLETE',runId=os.environ['GITHUB_RUN_ID'],commit=os.environ['GITHUB_SHA'],sourceRevision=REVISION,resource=RESOURCE,commands=[],paidRequests=0,productAccepted=False)
 def command(argv,name,cwd=ROOT,timeout=600):
  receipt=out/(name+'-command.json')
  try:run(argv,name=name,log=out/(name+'.log'),receipt=receipt,run_id=rid,cwd=cwd,timeout=timeout)
  finally:
   if receipt.is_file():report['commands'].append(dict(name=name,sha256=stream_sha256(receipt)))
 try:
  vswhere=Path(os.environ['ProgramFiles(x86)'])/'Microsoft Visual Studio/Installer/vswhere.exe'
  vs=subprocess.check_output([str(vswhere),'-latest','-products','*','-property','installationPath']).decode().strip();devcmd=Path(vs)/'Common7/Tools/VsDevCmd.bat'
  if not devcmd.is_file():raise ValueError('MSVC environment required')
  os.environ.update(arm.msvc_environment(devcmd,out))
  if os.environ.get('VSCMD_ARG_TGT_ARCH')!='arm64':raise ValueError('ARM64 compiler target required')
  compiler=shutil.which('cl.exe');report['compiler']=dict(path=compiler,sha256=stream_sha256(Path(compiler)))
  report['hostImage']={k:os.environ.get(k) for k in ['ImageOS','ImageVersion','RUNNER_ARCH','VCToolsVersion','WindowsSDKVersion']}
  command(['cmake','--version'],'cmake');command(['ninja','--version'],'ninja')
  source=out/'pty4j';command(['git','-c','http.https://github.com/.extraheader=','clone','--no-checkout','--filter=blob:none','https://github.com/JetBrains/pty4j.git',str(source)],'clone')
  command(['git','-c','core.autocrlf=false','-c','core.eol=lf','checkout','--detach',REVISION],'checkout',source)
  if subprocess.check_output(['git','rev-parse','HEAD'],cwd=source).decode().strip()!=REVISION:raise ValueError('Wrong source revision')
  names=subprocess.check_output(['git','ls-files','-z'],cwd=source).decode().split('\0');before={n:stream_sha256(source/n) for n in names if n and (source/n).is_file()};report['sourceTrackedFiles']=before
  item=next(r for r in json.loads((ROOT/'docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())['binaries'] if r['resource']==RESOURCE)
  original=source/'os/win/aarch64/win-helper.dll'
  if stream_sha256(original)!=item['sha256']:raise ValueError('Exact original inventory required')
  report['originalSha256']=item['sha256'];report['originalPe']=arm.require_arm64_pe(original.read_bytes());(out/'original.dll').write_bytes(original.read_bytes())
  build=source/'native/helper-build'
  command(['cmake','-S',str(source/'native/win-helper'),'-B',str(build),'-G','Ninja','-DCMAKE_BUILD_TYPE=Release','-DCMAKE_POLICY_VERSION_MINIMUM=3.5'],'configure')
  command(['cmake','--build',str(build),'--verbose'],'build')
  library=build/'win-helper.dll';report['pe']=arm.require_arm64_pe(library.read_bytes())
  if library.is_symlink():raise ValueError('Regular source-built helper required')
  output=out/'win-helper.dll';output.write_bytes(library.read_bytes());report['outputSha256']=stream_sha256(output)
  command(['dumpbin.exe','/headers','/exports','/dependents',str(output)],'pe-audit')
  probe=out/'probe.exe';command(['cl.exe','/nologo','/std:c++17','/EHsc','/W4','/WX',str(ROOT/'scripts/probes/p1-win-arm64-helper.cpp'),'/Fe:'+str(probe),'/Fo:'+str(out/'probe.obj')],'probe-build')
  report['probePe']=arm.require_arm64_pe(probe.read_bytes());first=out/'first';second=out/'second';first.mkdir();second.mkdir()
  command([str(probe),str(output),'present',str(first),str(second)],'candidate-probe',timeout=45)
  command([str(probe),str(out/'absent.dll'),'missing',str(first),str(second)],'missing-probe',timeout=45)
  after={n:stream_sha256(source/n) for n in before if (source/n).is_file()}
  if before!=after:raise ValueError('Tracked source changed')
  report.update(status='ARM64_WINDOWS_HELPER_COMPONENT_PASS_NOT_PRODUCT_ACCEPTANCE',sourceTrackedFilesAfter=after,probeSha256=stream_sha256(probe),probeSourceSha256=stream_sha256(ROOT/'scripts/probes/p1-win-arm64-helper.cpp'),limits=['Owned self process directory ABI only; no unrelated process inspection','No full Java application or unified replacement acceptance'])
 except Exception as error:report['failureType']=type(error).__name__;raise
 finally:atomic_write_json(out/'report.json',report)
 print(report['status'])
if __name__=='__main__':main()
