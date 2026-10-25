import test from 'node:test'
import assert from 'node:assert/strict'
import {mkdtemp,mkdir,writeFile,rm,symlink,readFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {pathToFileURL} from 'node:url'
import {execFileSync,spawnSync} from 'node:child_process'
import {contracts,sha,strictJson,validateRecord,validateFrozen,boundedRead,inputSnapshot,atomicJson,runFixtureCommand,sourceDigest} from './workbench-evidence.mjs'
const good=fixture=>({status:'PASS',paidRequests:0,jarSha256:'a'.repeat(64),checks:contracts[fixture].map(name=>({name,passed:true})),screenshots:[{path:'.build-cache/a.png',sha256:'b'.repeat(64)}],...(fixture==='settings'?{fixtureCredentialsCleaned:true}:{})})
test('JSON rejects duplicate and escaped duplicate keys, overflow and malformed trailing input',()=>{
 assert.deepEqual(strictJson(Buffer.from('{"a":[{"b":true}],"c":null}')),{a:[{b:true}],c:null})
 for(const text of ['{"status":"FAILED","status":"PASS"}','{"a":1,"\\u0061":2}','{"n":1e400}','[NaN]','{} trailing'])assert.throws(()=>strictJson(Buffer.from(text)),/JSON/)
})
test('all eleven frozen case contracts are nonempty and unique',()=>{assert.equal(Object.keys(contracts).length,11);for(const [fixture,names]of Object.entries(contracts)){assert.ok(names.length);assert.equal(new Set(names).size,names.length);validateRecord(good(fixture),fixture,'chromium')}})
for(const [title,mutate,error]of [
 ['failed status cannot be rescued by cleanup',r=>{r.status='FAILED';r.fixtureCredentialsCleaned=true},/did not pass/],
 ['string checks',r=>{r.checks[0]=r.checks[0].name},/Invalid case verdict/],
 ['truthy verdict',r=>{r.checks[0].passed='true'},/Invalid case verdict/],
 ['missing case',r=>r.checks.pop(),/Wrong case count/],
 ['duplicate ID',r=>{r.checks[1].name=r.checks[0].name},/Duplicate case ID/],
 ['invented ID',r=>{r.checks[0].name='invented'},/Wrong case IDs/],
 ['empty screenshots',r=>{r.screenshots=[]},/Missing screenshots/],
 ['duplicate screenshot filename',r=>r.screenshots.push({path:'.build-cache/other/a.png',sha256:'b'.repeat(64)}),/Duplicate screenshot basename/],
 ['unknown payment count',r=>{delete r.paidRequests},/paid requests/],
 ['truthy cleanup',r=>{r.fixtureCredentialsCleaned=1},/credentials not cleaned/]
])test(title,()=>{const fixture=title==='truthy cleanup'?'settings':'development',r=good(fixture);mutate(r);assert.throws(()=>validateRecord(r,fixture,'chromium'),error)})
test('legacy settings compatibility is explicit and boolean',()=>{const r=good('settings');delete r.status;validateRecord(r,'settings','webkit');r.status='FAILED';assert.throws(()=>validateRecord(r,'settings','webkit'),/did not pass/)})
test('frozen evidence validates bytes, command exit, source and run identity',async()=>{
 const root=await mkdtemp(join(tmpdir(),'jh-evidence-'))
 try{
  execFileSync('git',['init','--quiet'],{cwd:root})
  const files={'frontend/tests/development.mjs':'fixture source','harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar':'jar','.build-cache/M10.5.9/browser/chromium/development.log':'PASS fixture','.build-cache/M10.5.9/browser/chromium/development-a.png':'png'}
  for(const [name,bytes]of Object.entries(files)){await mkdir(join(root,name,'..'),{recursive:true});await writeFile(join(root,name),bytes)}
  const r={...good('development'),schemaVersion:1,fixture:'development',browserEngine:'chromium',runId:'new-run',command:{exitCode:0,signal:null,timedOut:false,overflow:false},jarSha256:sha('jar'),fixtureSourceSha256:sha('fixture source'),logSha256:sha('PASS fixture'),inputSha256:await inputSnapshot(root),screenshots:[{path:'.build-cache/M10.5.9/browser/chromium/development-a.png',sha256:sha('png')}]}
  await validateFrozen(root,r,'development','chromium',{runId:'new-run'})
  for(const [patch,error]of [[{runId:'old-run'},/Wrong run/],[{command:{exitCode:1,signal:null,timedOut:false,overflow:false}},/Unsuccessful/],[{inputSha256:{}},/Input snapshot/],[{jarSha256:'c'.repeat(64)},/JAR drift/],[{sourceSha256:{}},/Empty source/]])await assert.rejects(validateFrozen(root,{...r,...patch},'development','chromium',{runId:'new-run'}),error)
  await writeFile(join(root,'frontend/tests/development.mjs'),'changed');await assert.rejects(validateFrozen(root,r,'development','chromium'),/Fixture source drift/)
  await atomicJson(join(root,'receipt.json'),{status:'PASS'});await atomicJson(join(root,'receipt.json'),{status:'FAILED'});assert.equal(JSON.parse(await readFile(join(root,'receipt.json'))).status,'FAILED')
 }finally{await rm(root,{recursive:true,force:true})}
})
test('bounded reader rejects traversal, absolute, empty, oversized and linked input',async()=>{
 const root=await mkdtemp(join(tmpdir(),'jh-path-'))
 try{
  await writeFile(join(root,'file'),'abc');await writeFile(join(root,'empty'),'')
  for(const name of ['../file','/file','C:/file','x/../file','x\\file'])await assert.rejects(boundedRead(root,name),/Unsafe evidence path/)
  await assert.rejects(boundedRead(root,'empty'),/empty/);await assert.rejects(boundedRead(root,'file',2),/oversized/)
  // A directory junction works without Windows developer-mode symlink privileges.
  await symlink(root,join(root,'linked'),process.platform==='win32'?'junction':'dir')
  await assert.rejects(boundedRead(root,'linked/file'),/symlink/)
 }finally{await rm(root,{recursive:true,force:true})}
})
test('runner replaces stale PASS before rejecting invalid arguments',async()=>{
 const root=await mkdtemp(join(tmpdir(),'jh-stale-'))
 try{
  await mkdir(join(root,'scripts'));await mkdir(join(root,'.build-cache/M10.5.9/browser'),{recursive:true})
  for(const name of ['run-workbench-matrix.mjs','finalize-workbench-matrix.mjs','workbench-evidence.mjs','workbench-case-contract.json'])await writeFile(join(root,'scripts',name),await readFile(new URL(name,import.meta.url)))
  for(const name of ['run-workbench-matrix.mjs']){
   const path=join(root,'.build-cache/M10.5.9/browser/matrix.json');await atomicJson(path,{status:'PASS'})
   const child=spawnSync(process.execPath,[join(root,'scripts',name),'invalid'],{env:{...process.env,JHARNESS_MATRIX_ENGINES:'invalid'},encoding:'utf8',timeout:10000})
   assert.equal(child.status,1);assert.match(child.stderr,/Invalid engine matrix/);assert.equal(JSON.parse(await readFile(path)).status,'FAILED')
  }
 }finally{await rm(root,{recursive:true,force:true})}
})

// R0 rereview regressions; red/green evidence is recorded separately per finding.
test('R0R-024 empty source is valid independently of nonempty evidence',async()=>{
 const root=await mkdtemp(join(tmpdir(),'jh-empty-'))
 try{execFileSync('git',['init','--quiet'],{cwd:root});await mkdir(join(root,'frontend/public'),{recursive:true});await writeFile(join(root,'frontend/public/.gitkeep'),'');assert.equal((await inputSnapshot(root))['frontend/public/.gitkeep'],sha(''))}finally{await rm(root,{recursive:true,force:true})}
})
test('R0R-025 contract import rejects duplicate keys and empty/foreign case sets',async()=>{
 const root=await mkdtemp(join(tmpdir(),'jh-contract-'))
 try{await writeFile(join(root,'workbench-evidence.mjs'),await readFile(new URL('workbench-evidence.mjs',import.meta.url)));for(const bytes of ['{"schemaVersion":1,"fixtures":{"browser":["x"],"browser":[]}}',JSON.stringify({schemaVersion:1,fixtures:{browser:[]}})]){await writeFile(join(root,'workbench-case-contract.json'),bytes);const child=spawnSync(process.execPath,['--input-type=module','-e','await import('+JSON.stringify(pathToFileURL(join(root,'workbench-evidence.mjs')).href)+')'],{encoding:'utf8'});assert.notEqual(child.status,0,child.stdout)}}finally{await rm(root,{recursive:true,force:true})}
})
test('R0R-035 screenshot producers emit repository-relative paths on POSIX',async()=>{
 const {runInNewContext}=await import('node:vm'),{posix}=await import('node:path')
 for(const name of ['search','settings','extensions']){
  const source=await readFile(new URL('../frontend/tests/'+name+'.mjs',import.meta.url),'utf8'),start=source.indexOf('async function shot('),end=source.indexOf('\n',start),fn=source.slice(start,end),screenshots=[]
  await runInNewContext(fn+';shot("probe")',{root:'/fixture',out:'/fixture/.build-cache',join:posix.join,relative:posix.relative,wait:async()=>{},page:{screenshot:async()=>{}},screenshots,createHash:()=>({update:()=>({digest:()=> 'a'.repeat(64)})}),readFile:async()=>Buffer.from('png')})
  assert.match(screenshots[0].path,/^\.build-cache\//,name)
 }
})
test('R0R-006 path probes require PATH_DENIED rather than an unrelated HTTP 400',async()=>{
 const {runInNewContext}=await import('node:vm'),source=await readFile(new URL('../frontend/tests/content.mjs',import.meta.url),'utf8'),code=source.slice(source.indexOf(' for(const [label,path,code]'),source.indexOf(' const listing='))
 const expected=new Map([['../outside/private.txt','INSPECTION_DENIED'],['.jharness/config','PATH_DENIED'],['.env.local','PATH_DENIED'],['/outside/private.txt','PATH_DENIED'],['escape-link/private.txt','PATH_DENIED']])
 const run=(wrongPath,wrongCode)=>runInNewContext('(async()=>{'+code+'})()',{outside:'/outside',render:'task',enc:x=>x,api:async route=>{const path=route.split('?path=')[1];return{status:400,body:{error:path===wrongPath?wrongCode:expected.get(path)}}},check:(_name,ok)=>assert.ok(ok)})
 await run();for(const [path,error] of expected)await assert.rejects(run(path,error==='PATH_DENIED'?'INSPECTION_DENIED':'PATH_DENIED'),/assertion|false|true/i);await assert.rejects(run('../outside/private.txt','INVALID_ARGUMENT'),/assertion|false|true/i)
})

test('R0R-026 complete matrix and readonly CLI reject identity, subset, duplicate, digest and path mutations',async()=>{
 const {validateMatrix}=await import('./workbench-evidence.mjs'),root=await mkdtemp(join(tmpdir(),'jh-matrix-'))
 try{
  execFileSync('git',['init','--quiet'],{cwd:root});await mkdir(join(root,'scripts'))
  for(const name of ['workbench-evidence.mjs','workbench-case-contract.json','finalize-workbench-matrix.mjs'])await writeFile(join(root,'scripts',name),await readFile(new URL(name,import.meta.url)))
  const put=async(name,bytes)=>{await mkdir(join(root,name,'..'),{recursive:true});await writeFile(join(root,name),bytes)}
  await put('harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar','jar')
  for(const fixture of Object.keys(contracts)){await put('frontend/tests/'+fixture+'.mjs','fixture '+fixture);await put('.build-cache/M10.5.9/browser/chromium/'+fixture+'.log','PASS '+fixture);await put('.build-cache/M10.5.9/browser/chromium/'+fixture+'-a.png','png')}
  const inputs=await inputSnapshot(root),records=[]
  for(const fixture of Object.keys(contracts)){
   const record={...good(fixture),schemaVersion:1,fixture,browserEngine:'chromium',capturedAt:'2020-01-01T00:00:00.000Z',runId:'matrix-run',command:{exitCode:0,signal:null,timedOut:false,overflow:false},jarSha256:sha('jar'),fixtureSourceSha256:sha('fixture '+fixture),logSha256:sha('PASS '+fixture),inputSha256:inputs,screenshots:[{path:'.build-cache/M10.5.9/browser/chromium/'+fixture+'-a.png',sha256:sha('png')}]},path='.build-cache/M10.5.9/browser/chromium/'+fixture+'.json'
   await atomicJson(join(root,path),record);records.push({engine:'chromium',fixture,checks:record.checks.length,path,sha256:sha(await readFile(join(root,path)))})
  }
  const matrix={schemaVersion:1,status:'PASS',runId:'matrix-run',capturedAt:'2020-01-02T00:00:00.000Z',platform:process.platform,paidRequests:0,engines:['chromium'],fixtures:Object.keys(contracts),records},path='.build-cache/M10.5.9/browser/matrix.json'
  await atomicJson(join(root,path),matrix);assert.equal((await validateMatrix(root)).runId,'matrix-run')
  const original=await readFile(join(root,path)),cli=spawnSync(process.execPath,[join(root,'scripts/workbench-evidence.mjs'),path],{encoding:'utf8',timeout:15000});assert.equal(cli.status,0,cli.stderr);assert.equal(JSON.parse(cli.stdout).records,11);assert.deepEqual(await readFile(join(root,path)),original)
  if(process.platform==='win32'){
   const caseRoot=(root[0]===root[0].toUpperCase()?root[0].toLowerCase():root[0].toUpperCase())+root.slice(1)
   const caseCli=spawnSync(process.execPath,[join(caseRoot,'scripts/workbench-evidence.mjs'),path],{encoding:'utf8',timeout:15000})
   assert.equal(caseCli.status,0,caseCli.stderr);assert.equal(JSON.parse(caseCli.stdout).records,11);assert.deepEqual(await readFile(join(root,path)),original)
  }
  const aliasParent=await mkdtemp(join(tmpdir(),'jh-cli-entry-'))
  try{
   const alias=join(aliasParent,'root');await symlink(root,alias,process.platform==='win32'?'junction':'dir')
   const aliasedCli=spawnSync(process.execPath,[join(alias,'scripts/workbench-evidence.mjs'),path],{encoding:'utf8',timeout:15000})
   assert.equal(aliasedCli.status,0,aliasedCli.stderr);assert.equal(JSON.parse(aliasedCli.stdout).records,11);assert.deepEqual(await readFile(join(root,path)),original)
  }finally{await rm(aliasParent,{recursive:true,force:true})}
  for(const [change,error]of [[m=>m.fixtures.pop(),/fixture matrix/],[m=>{m.records[1]=m.records[0]},/Duplicate matrix/],[m=>{m.records[0].sha256='a'.repeat(64)},/digest mismatch/],[m=>{m.records[0].path='../escape'},/entry path/],[m=>{m.runId='another-run'},/run identity/],[m=>m.engines.push('chromium'),/engine matrix/],[m=>m.records.pop(),/Incomplete matrix records/]]){const changed=structuredClone(matrix);change(changed);await atomicJson(join(root,path),changed);await assert.rejects(validateMatrix(root),error);const before=sha(await readFile(join(root,path))),failedCli=spawnSync(process.execPath,[join(root,'scripts/workbench-evidence.mjs'),path],{encoding:'utf8',timeout:15000});assert.equal(failedCli.status,1);assert.match(failedCli.stderr,error);assert.equal(sha(await readFile(join(root,path))),before)}
  const originalRecord=await readFile(join(root,records[0].path)),overflowRecord=JSON.parse(originalRecord);overflowRecord.command.overflow=true;await atomicJson(join(root,records[0].path),overflowRecord);const overflowMatrix=structuredClone(matrix);overflowMatrix.records[0].sha256=sha(await readFile(join(root,records[0].path)));await atomicJson(join(root,path),overflowMatrix);await assert.rejects(validateMatrix(root),/Unsuccessful fixture command/);const overflowCli=spawnSync(process.execPath,[join(root,'scripts/workbench-evidence.mjs'),path],{encoding:'utf8',timeout:15000});assert.equal(overflowCli.status,1);assert.match(overflowCli.stderr,/Unsuccessful fixture command/);await writeFile(join(root,records[0].path),originalRecord)
  await atomicJson(join(root,path),matrix)
  const finalizer=spawnSync(process.execPath,[join(root,'scripts/finalize-workbench-matrix.mjs'),'chromium'],{encoding:'utf8',timeout:15000});assert.equal(finalizer.status,0,finalizer.stderr)
  assert.deepEqual(await readFile(join(root,path)),original,'finalization must preserve the browser command pinned output bytes')
  const final=JSON.parse(finalizer.stdout);assert.equal(final.capturedAt,matrix.capturedAt);assert.ok(Date.parse(final.finalizedAt)>Date.parse(final.capturedAt))
  const failed=spawnSync(process.execPath,[join(root,'scripts/finalize-workbench-matrix.mjs'),'invalid'],{encoding:'utf8',timeout:15000});assert.notEqual(failed.status,0);assert.deepEqual(await readFile(join(root,path)),original)
 }finally{await rm(root,{recursive:true,force:true})}
})

test('R0R-022 timed-out runner terminates its real grandchild before returning failure',{timeout:15000},async()=>{
 const root=await mkdtemp(join(tmpdir(),'jh-tree-old-'));let grandPid
 try{
  execFileSync('git',['init','--quiet'],{cwd:root});await mkdir(join(root,'scripts'));await mkdir(join(root,'frontend/tests'),{recursive:true});await mkdir(join(root,'.build-cache'))
  for(const name of ['run-workbench-matrix.mjs','workbench-evidence.mjs','workbench-case-contract.json']){let bytes=await readFile(new URL(name,import.meta.url),'utf8');bytes=bytes.replaceAll('300000','700').replaceAll('307000','3500');await writeFile(join(root,'scripts',name),bytes)}
  await writeFile(join(root,'frontend/tests/development.mjs'),`import {spawn} from 'node:child_process';import {writeFileSync} from 'node:fs';const c=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{stdio:'ignore'});writeFileSync('.build-cache/grand.pid',String(c.pid));setInterval(()=>{},1000)`)
  const child=spawnSync(process.execPath,[join(root,'scripts/run-workbench-matrix.mjs')],{cwd:root,env:{...process.env,JHARNESS_MATRIX_ENGINES:'chromium',JHARNESS_MATRIX_FIXTURES:'development'},encoding:'utf8',timeout:10000});assert.notEqual(child.status,0)
  grandPid=Number(await readFile(join(root,'.build-cache/grand.pid'),'utf8'));let alive=true;try{process.kill(grandPid,0)}catch(error){if(error.code==='ESRCH')alive=false;else throw error}
  assert.equal(alive,false,'timeout must not leave the fixture grandchild alive')
 }finally{if(grandPid)try{process.kill(grandPid,'SIGKILL')}catch{}await rm(root,{recursive:true,force:true})}
})

test('R0R-022 timeout and overflow use tree cleanup with an actually running grandchild',{timeout:30000},async()=>{
 for(const reason of ['timeout','overflow']){
  const root=await mkdtemp(join(tmpdir(),'jh-tree-'));let pid
  try{
   const path=join(root,'grand.pid'),marker=join(root,'ready'),grand=`require('fs').writeFileSync(${JSON.stringify(marker)},'ready');setInterval(()=>{},1000)`
   const code=`const {spawn}=require('child_process'),fs=require('fs');const c=spawn(process.execPath,['-e',${JSON.stringify(grand)}],{stdio:'ignore'});fs.writeFileSync(${JSON.stringify(path)},String(c.pid));const t=setInterval(()=>{if(fs.existsSync(${JSON.stringify(marker)})){clearInterval(t);${reason==='overflow'?"process.stdout.write('x'.repeat(8192));":''}}},10);setInterval(()=>{},1000)`
   const result=await runFixtureCommand([process.execPath,'-e',code],{cwd:root,timeoutMs:reason==='timeout'?1200:5000,maxOutputBytes:1024,cleanupMs:5000})
   assert.equal(await readFile(marker,'utf8'),'ready');pid=Number(await readFile(path,'utf8'));assert.equal(result.command[reason==='timeout'?'timedOut':'overflow'],true);assert.equal(result.command.cleanup.completed,true,JSON.stringify(result.command));assert.ok(result.output.length<=1024)
   assert.throws(()=>process.kill(pid,0),error=>error.code==='ESRCH','real grandchild remains alive after cleanup')
  }finally{if(pid)try{process.kill(pid,'SIGKILL')}catch{}await rm(root,{recursive:true,force:true})}
 }
})
test('R0R-023 detects deterministic in-place changes after descriptor acquisition',async()=>{
 const root=await mkdtemp(join(tmpdir(),'jh-change-'))
 try{await writeFile(join(root,'file'),'before');await assert.rejects(boundedRead(root,'file',1024,{onOpened:async()=>writeFile(join(root,'file'),'after mutation')}),/File changed during read/)}finally{await rm(root,{recursive:true,force:true})}
})
test('R0R-024 source hashes stream empty and large files with separate per-file and total budgets',async()=>{
 const root=await mkdtemp(join(tmpdir(),'jh-source-'))
 try{execFileSync('git',['init','--quiet'],{cwd:root});await mkdir(join(root,'frontend/public'),{recursive:true});await writeFile(join(root,'frontend/public/large'),Buffer.alloc(2*1024*1024,7));await writeFile(join(root,'frontend/public/empty'),'');const item=await sourceDigest(root,'frontend/public/large',3*1024*1024);assert.equal(item.sha256,sha(Buffer.alloc(2*1024*1024,7)));assert.equal(item.size,2*1024*1024);await assert.rejects(sourceDigest(root,'frontend/public/large',1024),/oversized/);await assert.rejects(inputSnapshot(root,{fileLimit:3*1024*1024,totalLimit:1024}),/total size limit/);await assert.rejects(boundedRead(root,'frontend/public/empty'),/empty/)}finally{await rm(root,{recursive:true,force:true})}
})
