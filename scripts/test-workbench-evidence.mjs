import test from 'node:test'
import assert from 'node:assert/strict'
import {mkdtemp,mkdir,writeFile,rm,symlink,readFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {execFileSync,spawnSync} from 'node:child_process'
import {contracts,sha,strictJson,validateRecord,validateFrozen,boundedRead,inputSnapshot,atomicJson} from './workbench-evidence.mjs'
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
  const r={...good('development'),schemaVersion:1,fixture:'development',browserEngine:'chromium',runId:'new-run',command:{exitCode:0,signal:null,timedOut:false},jarSha256:sha('jar'),fixtureSourceSha256:sha('fixture source'),logSha256:sha('PASS fixture'),inputSha256:await inputSnapshot(root),screenshots:[{path:'.build-cache/M10.5.9/browser/chromium/development-a.png',sha256:sha('png')}]}
  await validateFrozen(root,r,'development','chromium',{runId:'new-run'})
  for(const [patch,error]of [[{runId:'old-run'},/Wrong run/],[{command:{exitCode:1,signal:null,timedOut:false}},/Unsuccessful/],[{inputSha256:{}},/Input snapshot/],[{jarSha256:'c'.repeat(64)},/JAR drift/],[{sourceSha256:{}},/Empty source/]])await assert.rejects(validateFrozen(root,{...r,...patch},'development','chromium',{runId:'new-run'}),error)
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
test('runner and finalizer replace stale PASS before rejecting invalid arguments',async()=>{
 const root=await mkdtemp(join(tmpdir(),'jh-stale-'))
 try{
  await mkdir(join(root,'scripts'));await mkdir(join(root,'.build-cache/M10.5.9/browser'),{recursive:true})
  for(const name of ['run-workbench-matrix.mjs','finalize-workbench-matrix.mjs','workbench-evidence.mjs','workbench-case-contract.json'])await writeFile(join(root,'scripts',name),await readFile(new URL(name,import.meta.url)))
  for(const name of ['run-workbench-matrix.mjs','finalize-workbench-matrix.mjs']){
   const path=join(root,'.build-cache/M10.5.9/browser/matrix.json');await atomicJson(path,{status:'PASS'})
   const child=spawnSync(process.execPath,[join(root,'scripts',name),'invalid'],{env:{...process.env,JHARNESS_MATRIX_ENGINES:'invalid'},encoding:'utf8',timeout:10000})
   assert.equal(child.status,1);assert.match(child.stderr,/Invalid engine matrix/);assert.equal(JSON.parse(await readFile(path)).status,'FAILED')
  }
 }finally{await rm(root,{recursive:true,force:true})}
})
