"""Independently audit pinned P1 unified32 original CI artifacts. Run from repository root.

Arguments: original ZIP, expected SHA256, artifact ID, platform label.
The supplied expected SHA must come from the authenticated Actions artifact metadata.
"""
from pathlib import Path,PureWindowsPath
import hashlib,zipfile,io,json,sys,subprocess,xml.etree.ElementTree as ET,re
sys.path.insert(0,'scripts');from evidence_checks import validate_java_suites
sha=lambda raw:hashlib.sha256(raw).hexdigest();p=Path(sys.argv[1]);assert sha(p.read_bytes())==sys.argv[2];prefix='.build-cache/source-qualification/';commit='b86aaa1f4a5dc7159a6757cf66c3db6cee83ef4b'
with zipfile.ZipFile(p) as z:
 result=json.loads(z.read(prefix+'result.json'));assert result['status'] in ['LINUX_SOURCE_JAVA_VERIFY_AND_CANDIDATE_INSTALL_PASS_NOT_RELEASE','DARWIN_SOURCE_JAVA_VERIFY_AND_CANDIDATE_INSTALL_PASS_NOT_RELEASE','WINDOWS_SOURCE_JAVA_VERIFY_AND_CANDIDATE_INSTALL_PASS_NOT_RELEASE']; mac=result['status'].startswith('DARWIN'); windows=result['status'].startswith('WINDOWS'); label=sys.argv[4]; host='D:/a/J-Harness/J-Harness/' if windows else '/Users/runner/work/J-Harness/J-Harness/' if mac else '/home/runner/work/J-Harness/J-Harness/';assert result['commit']==commit
 labels={'linux':'ubuntu-24.04','linux-arm64':'ubuntu-24.04-arm','macos':'macos-latest','macos-intel':'macos-15-intel','windows':'windows-2022'}
 assert label in labels and result['runId']=='unified-source-42150336971-1-'+labels[label]+'-'+commit
 assert result['releaseApproved'] is False and result['paidRequests']==0
 assert (windows,mac)==(label=='windows',label in {'macos','macos-intel'})
 assert len(z.namelist())==len(set(z.namelist()))
 commands={};source=None
 for n in z.namelist():
  if not n.startswith(prefix) or not n.endswith('-command.json') or '/unit-stage/' in n or '/candidate-stage/' in n:continue
  r=json.loads(z.read(n));assert r['status']=='PASS' and r['exitCode']==0 and r['runId']==result['runId'];assert r['sourceSha256']==r['sourceAfterSha256'];assert sha(z.read(prefix+Path(n).name.replace('-command.json','.log')))==r['logSha256'];commands[Path(n).name]=sha(z.read(n))
  if source is None:source=r['sourceSha256']
  else:assert source==r['sourceSha256']
 assert len(commands)==14,len(commands)
 query=''.join(commit+':'+n+'\n' for n in source).encode();raw=subprocess.check_output(['git','cat-file','--batch'],input=query);at=0
 for n,expected in source.items():
  end=raw.index(b'\n',at);header=raw[at:end].split();assert header[1]==b'blob';size=int(header[2]);blob=raw[end+1:end+1+size];at=end+size+2
  if sha(blob)!=expected:
   assert n in ['mvnw.cmd','jharness.cmd'];assert sha(blob.replace(b'\n',b'\r\n'))==expected
 suites=[];xmlhash={}
 for n in z.namelist():
  if '/surefire-reports/TEST-' not in n or not n.endswith('.xml'):continue
  raw=z.read(n);x=ET.fromstring(raw);xmlhash[n]=sha(raw);suites.append(dict(module=n.split('/')[0],name=x.get('name'),cases=sorted(v.get('name') for v in x.findall('testcase')),tests=int(x.get('tests')),failures=int(x.get('failures')),errors=int(x.get('errors')),skipped=int(x.get('skipped'))))
  props={v.get('name'):v.get('value') for v in x.findall('properties/property')};base=host+prefix+'native/'
  for k,v in [('jna.nounpack','true'),('jna.nosys','true'),('jna.boot.library.path',base+'jni'),('pty4j.preferred.native.folder',base+'pty')]:assert props[k]==v
 assert validate_java_suites(suites,json.loads(Path('scripts/contracts/m1059-java-cases.json').read_bytes()))==735
 for n,h in result['nativeLoadLogSha256'].items():assert sha(z.read(n))==h
 hits=[]
 for n in result['nativeLoadLogSha256']:
  for line in z.read(n).decode().splitlines():
   m=re.search(r'Loaded library (.+?)\s*,?\s*handle\s+',line)
   if m and 'jnidispatch' in m[1].lower():assert m[1].rstrip(',').replace(chr(92),'/').lower()==(base+'jni/'+('jnidispatch.dll' if windows else ('libjnidispatch.jnilib' if mac else 'libjnidispatch.so'))).lower();hits.append(n)
 assert hits
 final=result['candidateStage']
 assert sha(z.read(prefix+'candidate-stage/baseline-runtime.jar'))==final['baselineJarSha256']
 assert sha(z.read(prefix+'candidate-stage/canonical-baseline-runtime.jar'))==final['canonicalBaselineJarSha256']
 with zipfile.ZipFile(io.BytesIO(z.read(prefix+'candidate-stage/baseline-runtime.jar'))) as original,zipfile.ZipFile(io.BytesIO(z.read(prefix+'candidate-stage/canonical-baseline-runtime.jar'))) as canonical:
  assert set(original.namelist())==set(canonical.namelist());changed={}
  for n in original.namelist():
   before=original.read(n);after=canonical.read(n)
   if before!=after:
    assert n.startswith('META-INF/maven/io.jharness/') and n.endswith('/pom.properties') and before.replace(b'\r\n',b'\n')==after
    changed[n]=dict(beforeSha256=sha(before),afterSha256=sha(after))
  assert changed==final['internalMavenNewlineChanges']
 jar=z.read('harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar');assert sha(jar)==final['runtimeJarSha256']
 with zipfile.ZipFile(io.BytesIO(jar)) as j,zipfile.ZipFile(io.BytesIO(z.read(prefix+'candidate-stage/canonical-baseline-runtime.jar'))) as baseline:
  replacements=final['allNativeReplacements'];assert len(replacements)==32
  assert set(j.namelist())==set(baseline.namelist())|{'META-INF/jharness/source-native-preview.json'}
  for n in baseline.namelist():
   if n in replacements:assert sha(j.read(n))==replacements[n]['sha256']
   else:assert j.read(n)==baseline.read(n)
 reviewed=json.loads(subprocess.check_output(['git','show',commit+':docs/testing/native-security/reviewed-native-source-map.json']))['resources'];assert replacements==reviewed
 with zipfile.ZipFile(io.BytesIO(jar)) as runtime:
  inventory=json.loads(Path('docs/testing/M10-native-inventory-50-2026-11-01.json').read_bytes())['binaries']
  for item in inventory:
   n=item['resource'];assert sha(runtime.read(n))==(reviewed[n]['sha256'] if n in reviewed else item['sha256'])
 with zipfile.ZipFile(io.BytesIO(jar)) as canonical:
  assert canonical.namelist()==sorted(canonical.namelist())
  assert all(v.compress_type==zipfile.ZIP_STORED and v.date_time==(2026,10,16,0,0,0) and v.create_system==3 for v in canonical.infolist())
  assert sha(canonical.read('META-INF/jharness/third-party/upstream/cli11-LICENSE'))=='3ee4a90b05dd076b70a36a0330be8bdfecbe75bbd0fc3343a4f289c86e2063aa'
 coverage=json.loads(z.read(prefix+'native-coverage.json'));assert coverage==result['nativeCoverage'] and coverage['jarSha256']==sha(jar) and coverage['inventoryCount']==50 and coverage['reviewedSourceCount']==32 and coverage['retainedOriginalCount']==18 and coverage['fullOsCpeClearance'] is False
 abi=None
 if not windows:
  abi=json.loads(z.read('.build-cache/directory-abi/result.json'))
  offsets=(4,16,20,0x100000,0x100) if mac else (16,24,28,0x4000,0x8000) if label=='linux-arm64' else (24,28,32,0x10000,0x20000)
  assert tuple(abi[k] for k in ['modeOffset','uidOffset','gidOffset','directoryFlag','noFollowFlag'])==offsets
  assert max(offsets[:3])+4<=abi['statBytes']<=512
 candidate=z.read('target/jharness-0.1.0-SNAPSHOT-candidate.zip');assert sha(candidate)==result['candidateZipSha256']
 with zipfile.ZipFile(io.BytesIO(candidate)) as c:
  assert c.read('jharness/lib/jharness.jar')==jar;manifest=json.loads(c.read('jharness/manifest.json'));assert manifest['gitCommit']==commit
  for n,h in manifest['files'].items():assert sha(c.read('jharness/'+n))==h
 smoke=json.loads(z.read('target/candidate-smoke.json'));assert smoke==result['smoke'] and smoke['status']=='PASS' and smoke['paidRequests']==0
 run=result['runId']
 def merge(rows):
  for n,h in rows.items():
   if n in source:assert source[n]==h,n
   else:assert sha(subprocess.check_output(['git','show',commit+':'+n]))==h,n
 matrix=json.loads(z.read('.build-cache/M10.5.9/browser/matrix.json'));assert matrix['status']=='PASS' and matrix['runId']==run and len(matrix['records'])==33 and not matrix['failures'];checks=0
 for item in matrix['records']:
  raw=z.read(item['path']);assert sha(raw)==item['sha256'];r=json.loads(raw);assert r['status']=='PASS' and r['jarSha256']==sha(jar) and r['runId']==run and r['command']['exitCode']==0 and all(v['passed'] is True for v in r['checks']);checks+=len(r['checks']);merge(r['inputSha256']);assert sha(z.read('.build-cache/M10.5.9/browser/'+item['engine']+'/'+item['fixture']+'.log'))==r['logSha256']
  for s in r['screenshots']:assert sha(z.read(s['path']))==s['sha256']
 assert checks==813
 r4={}
 for engine in ['chromium','firefox','webkit']:
  n='.build-cache/R4/'+run+'/browser-'+engine+'.json';r=json.loads(z.read(n));assert r['status']=='PASS' and r['runId']==run and len(r['checks'])==51 and all(v['passed'] is True for v in r['checks']);assert sha(z.read(r['screenshot']['path']))==r['screenshot']['sha256'];merge({'frontend/'+n:s for n,s in r['sourceSha256'].items()});r4[engine]=dict(checks=51,reportSha256=sha(z.read(n)))
 native=json.loads(z.read('.build-cache/M10.5.4-native-credentials.json'));assert native['status']=='PASS' and native['jarSha256']==sha(jar) and all(v is True for v in native['checks'].values())
 sdk=json.loads(z.read('.build-cache/M10.5.8-sdk-results.json'));assert sdk['status']=='PASS' and sdk['jarSha256']==sha(jar) and sdk['paidRequests']==0 and all(v['passed'] is True for v in sdk['results']);sdkchecks=sum(len(v['checks']) for v in sdk['results']);assert sdkchecks==66
 report=dict(status=label.upper()+'_UNIFIED_SOURCE_FULL_QUALIFICATION_ACCEPTED_NOT_RELEASE',runId='42150336971',commit=commit,artifact=dict(id=int(sys.argv[3]),bytes=p.stat().st_size,sha256=sha(p.read_bytes()),path=p.as_posix()),sourceGitInputsVerified=len(source),javaTests=735,javaSuites=len(suites),failures=0,errors=0,skipped=0,xmlSha256=xmlhash,commandReceiptSha256=commands,actualSourceJniLoadLogs=sorted(set(hits)),runtimeJarSha256=sha(jar),candidateZipSha256=sha(candidate),candidateManifestFilesVerified=len(manifest['files']),smoke=smoke,limits=result['limits'],nativeDirectoryAbi=abi,nativeInventoryCount=50,reviewedSourceNativeCount=32,retainedOriginalNativeCount=18,browser=dict(fixtures=33,checks=813),r4=r4,nativeChecks=len(native['checks']),sdkChecks=sdkchecks,paidRequests=0)
 Path('docs/testing/M10-unified32-final-'+label+'-2026-11-06.json').write_bytes((json.dumps(report,indent=2)+'\n').encode());print('PASS',len(source),'Git inputs; 735 tests;',len(commands),'commands;',len(manifest['files']),'candidate files;',len(set(hits)),'source JNI logs')
