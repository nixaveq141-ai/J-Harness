"""Build fixed OpenBSD JNA components in a native CI VM."""
import argparse
import faulthandler
import hashlib
import importlib.util
import json
import os
import platform
from pathlib import Path
import struct
import shutil
import tempfile
import subprocess
import zipfile
from evidence_io import atomic_write_json, stream_sha256
from evidence_run import run

ROOT=Path(__file__).resolve().parents[1]
REVISION='c366bc4fd0c50a30ac8d2475603ec564f6611462'
TARGETS={'x86-64':(2,1,62,'amd64'),'x86':(1,1,3,'i386')}


def require_elf(raw,target):
 width,endian,machine,_=TARGETS[target]
 if len(raw)<64 or raw[:6]!=b'\x7fELF'+bytes([width,endian]):raise ValueError('ELF class/endian mismatch')
 order='<' if endian==1 else '>'
 kind,actual=struct.unpack_from(order+'HH',raw,16)
 flags=struct.unpack_from(order+'I',raw,36 if width==1 else 48)[0]
 if (kind,actual)!=(3,machine):raise ValueError('ELF machine mismatch')
 return dict(elfClass=width,endianness=endian,machine=actual,flags=flags)


def compiled_library(source,target):
 library=source/'build'/('native-openbsd-'+target)/'libjnidispatch.so'
 if library.is_symlink() or not library.is_file():raise ValueError('Fresh native build output required')
 expected=stream_sha256(library)
 copies=[p for p in (source/'build').rglob('libjnidispatch.so') if p.is_file()]
 if any(p.is_symlink() or stream_sha256(p)!=expected for p in copies):raise ValueError('Ant native/class copies differ')
 return library


def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('target',choices=TARGETS);a=parser.parse_args()
 if platform.system()!='OpenBSD' or platform.machine()!=TARGETS[a.target][3]:raise ValueError('Native OpenBSD target VM required')
 if os.environ.get('GITHUB_ACTIONS')!='true' or not os.environ.get('GITHUB_RUN_ID','').isdigit():raise ValueError('Dedicated CI required')
 faulthandler.dump_traceback_later(120,repeat=True)
 target=a.target;_,_,_,arch=TARGETS[target]
 compiler=['cc']
 out=ROOT/'.build-cache/p1-openbsd-jna'/target;out.mkdir(parents=True,exist_ok=False)
 rid='p1-jna-'+os.environ['GITHUB_RUN_ID']+'-'+target
 report=dict(status='INCOMPLETE',target=target,runId=os.environ['GITHUB_RUN_ID'],
  commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip(),sourceRevision=REVISION,
  paidRequests=0,productAccepted=False,commands=[],hostImage={k:os.environ.get(k) for k in ['ImageOS','ImageVersion','RUNNER_ARCH']})
 def command(argv,name,cwd=ROOT,timeout=600):
  print('OpenBSD component command:',name,flush=True)
  receipt=out/(name+'-command.json')
  try:run(argv,name=name,log=out/(name+'.log'),receipt=receipt,run_id=rid,cwd=cwd,timeout=timeout)
  finally:
   if receipt.is_file():report['commands'].append(dict(name=name,sha256=stream_sha256(receipt)))
 try:
  command(['pkg_info','-a'],'host-packages');command(['uname','-a'],'system');command(['mount'],'mounts')
  command(['cc','--version'],'compiler')
  command(['java','-version'],'jdk');command(['ant','-version'],'ant')
  source=out/'jna'
  command(['git','-c','http.https://github.com/.extraheader=','clone','--no-checkout','--filter=blob:none','https://github.com/java-native-access/jna.git',str(source)],'clone')
  command(['git','-c','core.autocrlf=false','-c','core.eol=lf','checkout','--detach',REVISION],'checkout',source)
  if subprocess.check_output(['git','rev-parse','HEAD'],cwd=source).decode().strip()!=REVISION:raise ValueError('Wrong source revision')
  names=subprocess.check_output(['git','ls-files','-z'],cwd=source).decode().split('\0')
  before={n:stream_sha256(source/n) for n in names if n and (source/n).is_file()};report['sourceTrackedFiles']=before
  resource='com/sun/jna/openbsd-'+target+'/libjnidispatch.so'
  item=next(r for r in json.loads((ROOT/'docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())['binaries'] if r['resource']==resource)
  native_jar='lib/native/openbsd-'+target+'.jar'
  with zipfile.ZipFile(source/native_jar) as z:original=z.read('libjnidispatch.so')
  if hashlib.sha256(original).hexdigest()!=item['sha256']:raise ValueError('Original source binary not inventory exact')
  (out/'original.so').write_bytes(original);report['originalSha256']=item['sha256'];report['originalElf']=require_elf(original,target)
  helper_path=ROOT/'docs/testing/tools/build-native-from-source.py'
  spec=importlib.util.spec_from_file_location('generated_inputs',helper_path);helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
  report['generatedHelperSha256']=stream_sha256(helper_path)
  report['libffiBuildInputs']=helper.prepare_libffi(source,out)
  makefile=source/'native/Makefile';raw=makefile.read_bytes();anchor=b'FFI_CONFIG=--enable-static --disable-shared --with-pic=yes'
  if raw.count(anchor)!=2:raise ValueError('Pinned Makefile anchor mismatch')
  makefile.write_bytes(raw.replace(anchor,anchor+b' --disable-docs --disable-maintainer-mode --disable-dependency-tracking --disable-multi-os-directory'))
  report['buildConfig']={'arch':arch,'osPrefix':'openbsd-'+target,'compiler':compiler,'libffiStatic':True,'configureMake':'gmake','libffiConfigureOptions':['--disable-docs','--disable-maintainer-mode','--disable-dependency-tracking','--disable-multi-os-directory']}
  options=['-Dmake=gmake','-Dos.prefix=openbsd-'+target,'-DARCH='+arch,'-DCC='+' '.join(compiler),'-Dcflags_extra.native=-I/usr/local/include']
  if target=='x86':options+=['-DEXTRA_MAKE_OPTS=HOST_CONFIG=--host=i386-unknown-openbsd']
  os.environ['MAKE']='gmake'
  command(['ant','-Dbuild-native=true',*options,'native'],'build',source)
  library=compiled_library(source,target);identity=require_elf(library.read_bytes(),target);output=out/'libjnidispatch.so';output.write_bytes(library.read_bytes())
  command(['readelf','-h','-d','-Ws',str(output)],'elf-audit')
  ffi_headers=library.parent/'libffi/include';probe=out/'jni-probe';java=Path(os.environ['JAVA_HOME'])
  promotion=out/'callback-control'
  command([*compiler,'-std=c11','-Wall','-Wextra','-Werror','-I'+str(ffi_headers),
   str(ROOT/'scripts/probes/p1-jna-callback-control.c'),'-o',str(promotion)],'promotion-build')
  command([str(promotion)],'promotion-control',timeout=30)
  command([*compiler,'-std=c11','-Wall','-Wextra','-Werror','-O2','-I'+str(ffi_headers),'-I'+str(source/'build/headers'),
   '-I'+str(java/'include'),'-I'+str(java/'include/openbsd'),str(ROOT/'scripts/probes/p1-jna.c'),'-Wl,-z,wxneeded','-o',str(probe)],'probe-build')
  # The executable's filesystem must already permit wxneeded programs. Do not
  # change mount options or product permissions; copy only our owned probe.
  with tempfile.TemporaryDirectory(prefix='jh-p1-'+os.environ['GITHUB_RUN_ID']+'-',dir='/usr/local') as folder:
   runtime_probe=Path(folder)/'jni-probe';shutil.copyfile(probe,runtime_probe);runtime_probe.chmod(0o700)
   if stream_sha256(runtime_probe)!=stream_sha256(probe):raise ValueError('Runtime probe copy drift')
   report['runtimeProbePath']=str(runtime_probe);report['runtimeProbeSha256']=stream_sha256(runtime_probe)
   command([str(runtime_probe),str(output),'present'],'candidate-probe',timeout=45)
   command([str(runtime_probe),str(out/'absent.so'),'missing'],'missing-probe',timeout=45)
   if stream_sha256(runtime_probe)!=report['runtimeProbeSha256']:raise ValueError('Runtime probe changed')
  # Original libffi exports can differ; old bytes are retained and checked by
  # ELF identity, not silently claimed to pass this new static-libffi probe.
  after={n:stream_sha256(source/n) for n in before if (source/n).is_file()}
  changed={n for n in before if before[n]!=after.get(n)}
  if not changed.issubset({'native/Makefile',native_jar}) or 'native/Makefile' not in changed:raise ValueError('Unexpected source mutation')
  report['sourceTrackedFilesAfter']=after;report['sourceChangedPaths']=sorted(changed)
  command(['git','diff','--binary'],'source-diff',source)
  report.update(status='SOURCE_JNA_ABI_AND_FFI_PROBE_PASS_NOT_PRODUCT_ACCEPTANCE',resource=resource,
   outputSha256=stream_sha256(output),elf=identity,probeSha256=stream_sha256(probe),probeSourceSha256=stream_sha256(ROOT/'scripts/probes/p1-jna.c'),
   probeSupportSha256={n:stream_sha256(ROOT/n) for n in ['scripts/probes/p1-jna-callback.h','scripts/probes/p1-jna-callback-control.c']},
   limits=['C-level JNI primitives and real bundled ffi/closure only; no complete JVM loading/initIDs proof','No product replacement or full OS/CPE clearance','No claim of arbitrary OpenBSD release compatibility'])
 except Exception as error:
  report['failureType']=type(error).__name__;raise
 finally:
  faulthandler.cancel_dump_traceback_later()
  config=out/'jna/build'/('native-openbsd-'+target)/'libffi/config.log'
  if config.is_file():(out/'libffi-config.log').write_bytes(config.read_bytes());report['libffiConfigLogSha256']=stream_sha256(out/'libffi-config.log')
  atomic_write_json(out/'report.json',report)
 print(report['status'])


if __name__=='__main__':main()
