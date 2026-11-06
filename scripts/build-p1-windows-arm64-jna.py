"""Build pinned JNA with its upstream MSVC ARM64 branch on a real ARM64 runner."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import shutil
import struct
import subprocess
import zipfile
from evidence_io import atomic_write_json,stream_sha256
from evidence_run import run

ROOT=Path(__file__).resolve().parents[1]
REVISION='c366bc4fd0c50a30ac8d2475603ec564f6611462'
RESOURCE='com/sun/jna/win32-aarch64/jnidispatch.dll'

def require_arm64_pe(raw):
 if len(raw)<128 or raw[:2]!=b'MZ':raise ValueError('Complete PE required')
 offset=struct.unpack_from('<I',raw,60)[0]
 if offset<64 or offset+26>len(raw) or raw[offset:offset+4]!=b'PE\0\0':raise ValueError('Bounded PE header required')
 machine=struct.unpack_from('<H',raw,offset+4)[0];magic=struct.unpack_from('<H',raw,offset+24)[0]
 if (machine,magic)!=(0xaa64,0x20b):raise ValueError('Native ARM64 PE32+ required; emulated x64 is not accepted')
 return dict(machine=machine,optionalHeaderMagic=magic)

def main():
 if os.name!='nt' or platform.machine().lower() not in {'arm64','aarch64'}:raise ValueError('Native Windows ARM64 Python required')
 if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Dedicated CI required')
 out=ROOT/'.build-cache/p1-windows-arm64-jna';out.mkdir(parents=True,exist_ok=False)
 rid='p1-winarm-jna-'+os.environ['GITHUB_RUN_ID']
 report=dict(status='INCOMPLETE',runId=os.environ['GITHUB_RUN_ID'],commit=os.environ['GITHUB_SHA'],sourceRevision=REVISION,resource=RESOURCE,paidRequests=0,productAccepted=False,commands=[])
 def command(argv,name,cwd=ROOT,timeout=600):
  receipt=out/(name+'-command.json')
  try:run(argv,name=name,log=out/(name+'.log'),receipt=receipt,run_id=rid,cwd=cwd,timeout=timeout)
  finally:
   if receipt.is_file():report['commands'].append(dict(name=name,sha256=stream_sha256(receipt)))
 try:
  vswhere=Path(os.environ['ProgramFiles(x86)'])/'Microsoft Visual Studio/Installer/vswhere.exe'
  vs=subprocess.check_output([str(vswhere),'-latest','-products','*','-property','installationPath']).decode().strip()
  devcmd=Path(vs)/'Common7/Tools/VsDevCmd.bat'
  if not devcmd.is_file():raise ValueError('Visual Studio developer environment required')
  env=subprocess.check_output(['cmd.exe','/d','/s','/c','"'+str(devcmd)+'" -no_logo -arch=arm64 -host_arch=arm64 >nul && set']).decode(errors='strict')
  for row in env.splitlines():
   if '=' in row and not row.startswith('='):
    key,value=row.split('=',1);os.environ[key]=value
  if os.environ.get('VSCMD_ARG_TGT_ARCH')!='arm64':raise ValueError('MSVC target must be ARM64')
  msys=Path(os.environ['JHARNESS_MSYS_ROOT']);make=msys/'usr/bin/make.exe'
  if not make.is_file():raise ValueError('MSYS make required')
  os.environ['PATH']+=';'+str(msys/'usr/bin')
  report['hostImage']={k:os.environ.get(k) for k in ['ImageOS','ImageVersion','RUNNER_ARCH','VCToolsVersion','WindowsSDKVersion']}
  compiler=shutil.which('cl.exe')
  if compiler is None:raise ValueError('MSVC compiler required')
  os.environ['PATH']=str(Path(compiler).parent)+';'+str(msys/'usr/bin')+';'+os.environ['PATH']
  report['compiler']=dict(path=compiler,sha256=stream_sha256(Path(compiler)))
  command([str(make),'--version'],'make');command(['java','-version'],'jdk')
  source=out/'jna';command(['git','-c','http.https://github.com/.extraheader=','clone','--no-checkout','--filter=blob:none','https://github.com/java-native-access/jna.git',str(source)],'clone')
  command(['git','-c','core.autocrlf=false','-c','core.eol=lf','checkout','--detach',REVISION],'checkout',source)
  if subprocess.check_output(['git','rev-parse','HEAD'],cwd=source).decode().strip()!=REVISION:raise ValueError('Wrong source revision')
  names=subprocess.check_output(['git','ls-files','-z'],cwd=source).decode().split('\0')
  before={n:stream_sha256(source/n) for n in names if n and (source/n).is_file()};report['sourceTrackedFiles']=before
  item=next(x for x in json.loads((ROOT/'docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())['binaries'] if x['resource']==RESOURCE)
  with zipfile.ZipFile(source/'lib/native/win32-aarch64.jar') as z:original=z.read('jnidispatch.dll')
  if hashlib.sha256(original).hexdigest()!=item['sha256']:raise ValueError('Inventory exact original required')
  (out/'original.dll').write_bytes(original);report['originalSha256']=item['sha256'];report['originalPe']=require_arm64_pe(original)
  helper_path=ROOT/'docs/testing/tools/build-native-from-source.py';spec=importlib.util.spec_from_file_location('inputs',helper_path);helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
  report['generatedHelperSha256']=stream_sha256(helper_path);report['libffiBuildInputs']=helper.prepare_libffi(source,out)
  makefile=source/'native/Makefile';raw=makefile.read_bytes();anchor=b'FFI_CONFIG=--enable-static --disable-shared --with-pic=yes'
  if raw.count(anchor)!=2:raise ValueError('Pinned Makefile anchor required')
  makefile.write_bytes(raw.replace(anchor,anchor+b' --disable-docs --disable-maintainer-mode'))
  report['buildConfig']=dict(arch='aarch64',osPrefix='win32-aarch64',useMsvc=True,libffiStatic=True,upstreamAsmFnOff=True)
  command(['cmd.exe','/d','/c','call','ant','-Dbuild-native=true','-Dmake='+str(make),'-DARCH=aarch64','-Dos.prefix=win32-aarch64','-DUSE_MSVC=true','-DEXTRA_MAKE_OPTS=OS=win32','native'],'build',source)
  library=source/'build/native-win32-aarch64/jnidispatch.dll';report['pe']=require_arm64_pe(library.read_bytes())
  expected=stream_sha256(library)
  if library.is_symlink() or any(p.is_symlink() or stream_sha256(p)!=expected for p in (source/'build').rglob('jnidispatch.dll')):raise ValueError('Native/class DLL copies differ')
  output=out/'jnidispatch.dll';output.write_bytes(library.read_bytes())
  command(['dumpbin.exe','/headers','/exports','/dependents',str(output)],'pe-audit')
  java=Path(os.environ['JAVA_HOME']);exe=out/'probe.exe'
  command(['cl.exe','/nologo','/std:c11','/W4','/WX','/I'+str(java/'include'),'/I'+str(java/'include/win32'),'/I'+str(source/'build/headers'),str(ROOT/'scripts/probes/p1-win-arm64-jna.c'),'/Fe:'+str(exe),'/Fo:'+str(out/'probe.obj')],'probe-build')
  report['probePe']=require_arm64_pe(exe.read_bytes())
  command([str(exe),str(output),'present'],'candidate-probe',timeout=45);command([str(exe),str(out/'absent.dll'),'missing'],'missing-probe',timeout=45)
  after={n:stream_sha256(source/n) for n in before if (source/n).is_file()};changed={n for n in before if before[n]!=after.get(n)}
  if not changed.issubset({'native/Makefile','lib/native/win32-aarch64.jar'}) or 'native/Makefile' not in changed:raise ValueError('Unexpected source mutation')
  report['sourceTrackedFilesAfter']=after;report['sourceChangedPaths']=sorted(changed);command(['git','diff','--binary'],'source-diff',source)
  report.update(status='ARM64_WINDOWS_JNI_PRIMITIVE_COMPONENT_PASS_NOT_PRODUCT_ACCEPTANCE',outputSha256=expected,probeSha256=stream_sha256(exe),probeSourceSha256=stream_sha256(ROOT/'scripts/probes/p1-win-arm64-jna.c'),limits=['C JNI primitive validation only; no full Java application acceptance','Upstream Windows ARM64 ASMFN_OFF behavior retained','Not yet assembled into unified candidate'])
 except Exception as error:report['failureType']=type(error).__name__;raise
 finally:atomic_write_json(out/'report.json',report)
 print(report['status'])

if __name__=='__main__':main()
