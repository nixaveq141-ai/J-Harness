"""Build pinned ARM64 WinPTY DLL/agent with the reviewed owned-directory patch."""
import importlib.util,json,os,platform,subprocess
from pathlib import Path
from evidence_io import atomic_write_json,stream_sha256
from evidence_run import run
ROOT=Path(__file__).resolve().parents[1]
REVISION='7e59fe2d09adf0fa2aa606492e7ca98efbc5184e'
PTY_REVISION='b7554d649f6374183040e6554942b6d5b028b5e5'
def module(name,path):
 s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
arm=module('arm',ROOT/'scripts/build-p1-windows-arm64-jna.py')
def native_config(revision):
 if revision!=REVISION:raise ValueError('Exact source revision required')
 return ('UNIX_CXX=/usr/bin/g++\nUNIX_LDFLAGS_STATIC=\nMINGW_CXX=/clangarm64/bin/clang++\nCOMMIT_HASH='+revision+'\nCOMMIT_HASH_DEP=config.mk\n').encode()
def main():
 if os.name!='nt' or platform.machine().lower() not in {'arm64','aarch64'} or os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Native Windows ARM64 CI required')
 out=ROOT/'.build-cache/p1-windows-arm64-winpty';out.mkdir(parents=True,exist_ok=False);rid='p1-winarm-winpty-'+os.environ['GITHUB_RUN_ID']
 report=dict(status='INCOMPLETE',runId=os.environ['GITHUB_RUN_ID'],commit=os.environ['GITHUB_SHA'],sourceRevision=REVISION,paidRequests=0,productAccepted=False,commands=[])
 def command(argv,name,cwd=ROOT,timeout=600):
  receipt=out/(name+'-command.json')
  try:run(argv,name=name,log=out/(name+'.log'),receipt=receipt,run_id=rid,cwd=cwd,timeout=timeout)
  finally:
   if receipt.is_file():report['commands'].append(dict(name=name,sha256=stream_sha256(receipt)))
 def checkout(repository,revision,name):
  source=out/name;command(['git','-c','http.https://github.com/.extraheader=','clone','--no-checkout','--filter=blob:none','https://github.com/'+repository+'.git',str(source)],name+'-clone');command(['git','-c','core.autocrlf=false','-c','core.eol=lf','checkout','--detach',revision],name+'-checkout',source)
  if subprocess.check_output(['git','rev-parse','HEAD'],cwd=source).decode().strip()!=revision:raise ValueError('Wrong source revision')
  names=subprocess.check_output(['git','ls-files','-z'],cwd=source).decode().split('\0');return source,{n:stream_sha256(source/n) for n in names if n and (source/n).is_file()}
 try:
  msys=Path(os.environ['JHARNESS_MSYS_ROOT']);clang=msys/'clangarm64/bin/clang++.exe';make=msys/'usr/bin/make.exe'
  os.environ['PATH']=str(msys/'clangarm64/bin')+';'+str(msys/'usr/bin')+';'+os.environ['PATH']
  command([str(msys/'usr/bin/pacman.exe'),'-Q'],'packages');command([str(clang),'--version'],'compiler');command([str(clang),'-dumpmachine'],'target')
  machine=(out/'target.log').read_text().strip()
  if not machine.startswith('aarch64-') or 'windows' not in machine:raise ValueError('ARM64 Windows compiler target required')
  report['targetTriple']=machine;report['hostImage']={k:os.environ.get(k) for k in ['ImageOS','ImageVersion','RUNNER_ARCH']}
  source,before=checkout('rprichard/winpty',REVISION,'winpty');report['sourceTrackedFiles']=before
  original,reference=checkout('JetBrains/pty4j',PTY_REVISION,'pty4j');report['referenceSource']=dict(revision=PTY_REVISION,trackedFiles=reference)
  inventory=json.loads((ROOT/'docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())['binaries'];report['originalSha256']={}
  for name in ['winpty.dll','winpty-agent.exe']:
   resource='resources/com/pty4j/native/win/aarch64/'+name;expected=next(i['sha256'] for i in inventory if i['resource']==resource);p=original/'os/win/aarch64'/name
   if stream_sha256(p)!=expected:raise ValueError('Exact original inventory required')
   arm.require_arm64_pe(p.read_bytes());(out/('original-'+name)).write_bytes(p.read_bytes());report['originalSha256'][resource]=expected
  extension=module('directory_patch',ROOT/'docs/testing/tools/patch-winpty-directory.py');report['directoryExtension']=extension.apply(source)
  config=source/'config.mk';config.write_bytes(native_config(REVISION));report['nativeOnlyConfigSha256']=stream_sha256(config)
  command([str(make),'-j2','build/winpty.dll','build/winpty-agent.exe'],'build',source)
  report['outputs']={};report['pe']={}
  for name in ['winpty.dll','winpty-agent.exe']:
   p=source/'build'/name
   if p.is_symlink():raise ValueError('Regular fresh build outputs required')
   report['pe'][name]=arm.require_arm64_pe(p.read_bytes());(out/name).write_bytes(p.read_bytes());report['outputs'][name]=stream_sha256(p)
  probe=out/'probe.exe';command([str(clang),'-std=c++11','-Wall','-Wextra','-Werror','-municode','-static','-I'+str(source/'src/include'),str(ROOT/'scripts/probes/p1-winpty-arm64.cpp'),'-o',str(probe)],'probe-build')
  report['probePe']=arm.require_arm64_pe(probe.read_bytes());command([str(probe),str(out/'winpty.dll'),'present'],'candidate-probe',timeout=90);command([str(probe),str(out/'absent.dll'),'missing'],'missing-probe',timeout=90)
  after={n:stream_sha256(source/n) for n in before if (source/n).is_file()};changed={n for n in before if before[n]!=after.get(n)}
  if changed!=set(extension.EXPECTED):raise ValueError('Unexpected WinPTY tracked source mutations')
  if reference!={n:stream_sha256(original/n) for n in reference}:raise ValueError('Original reference source changed')
  command(['git','diff','--binary'],'source-diff',source)
  report.update(status='ARM64_WINPTY_AGENT_SOURCE_COMPONENT_PASS_NOT_PRODUCT_ACCEPTANCE',sourceTrackedFilesAfter=after,sourceChangedPaths=sorted(changed),probeSha256=stream_sha256(probe),probeSourceSha256=stream_sha256(ROOT/'scripts/probes/p1-winpty-arm64.cpp'),limits=['Native C++ DLL and owned agent lifecycle, not full Java application acceptance','Upstream source with existing bounded owned-directory patch; no new source port certified until runtime passes'])
 except Exception as error:report['failureType']=type(error).__name__;raise
 finally:atomic_write_json(out/'report.json',report)
 print(report['status'])
if __name__=='__main__':main()
