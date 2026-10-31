// Current-run evidence contract. Historical JSON files are intentionally not migrated.
import {readFile,writeFile,rename,lstat,realpath,mkdir,rm,open} from 'node:fs/promises'
import {constants} from 'node:fs'
import {resolve,relative,dirname,isAbsolute,join} from 'node:path'
import {createHash,randomUUID} from 'node:crypto'
import {execFileSync,spawn} from 'node:child_process'
import {fileURLToPath} from 'node:url'
export const sha=b=>createHash('sha256').update(b).digest('hex')
export const failureDiagnostics={
 questions:['M10.5.5-questions-failure.json'],
 development:['M10.5.9-development-diagnostic-failure.txt'],
 models:['M10.5.4-models-failure.txt'],
}
export async function freezeFailureDiagnostics(root,fixture,engine){
 requireThat(Object.hasOwn(fixtures,fixture)&&['chromium','firefox','webkit'].includes(engine),'Invalid failure diagnostic scope')
 const result=[]
 for(const name of failureDiagnostics[fixture]??[]){
  let bytes
  try{bytes=await boundedRead(root,'.build-cache/'+name,4*1024*1024)}catch(error){if(error.code==='ENOENT')continue;throw error}
  const path=`.build-cache/M10.5.9/browser/${engine}/${fixture}-${name}`
  await mkdir(dirname(join(root,path)),{recursive:true});await writeFile(join(root,path),bytes)
  result.push({path,sha256:sha(bytes)})
 }
 return result
}
export function strictJson(bytes){
  const text=bytes.toString('utf8');let cursor=0
  const space=()=>{while(/\s/.test(text[cursor]??'')&&cursor<text.length)cursor++}
  function string(){const start=cursor++;while(cursor<text.length){const char=text[cursor++];if(char==='\\')cursor++;else if(char==='"')return JSON.parse(text.slice(start,cursor))}throw Error('Invalid JSON string')}
  function value(depth=0){
    requireThat(depth<=128,'JSON nesting limit exceeded');space();const char=text[cursor]
    if(char==='{'){
      cursor++;space();const keys=new Set();if(text[cursor]==='}'){cursor++;return}
      while(true){space();requireThat(text[cursor]==='"','Invalid JSON object');const key=string();requireThat(!keys.has(key),'Duplicate JSON key');keys.add(key);space();requireThat(text[cursor++]===':','Invalid JSON colon');value(depth+1);space();const end=text[cursor++];if(end==='}')return;requireThat(end===',','Invalid JSON object separator')}
    }
    if(char==='['){cursor++;space();if(text[cursor]===']'){cursor++;return}while(true){value(depth+1);space();const end=text[cursor++];if(end===']')return;requireThat(end===',','Invalid JSON array separator')}}
    if(char==='"'){string();return}
    const token=/^(?:true|false|null|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?)/.exec(text.slice(cursor));requireThat(token,'Invalid JSON value');cursor+=token[0].length
    if(/^-?\d/.test(token[0]))requireThat(Number.isFinite(Number(token[0])),'Nonfinite JSON number')
  }
  value();space();requireThat(cursor===text.length,'Trailing JSON input');return JSON.parse(text)
}
export const fixtures={browser:'M10-browser-results.json',diagnosis:'M10.5.1-diagnosis-results.json',history:'M10.5.2-history-results.json',turns:'M10.5.3-turns-results.json',models:'M10.5.4-models-results.json',questions:'M10.5.5-questions-results.json',content:'M10.5.6-content-results.json',extensions:'M10.5.7-extensions-results.json',search:'M10.5.8-search-results.json',settings:'M10-settings-browser-results.json',development:'M10.5.9-development-results.json'}
export function validateContract(contract){
  requireThat(contract?.schemaVersion===1&&contract.fixtures&&typeof contract.fixtures==='object'&&!Array.isArray(contract.fixtures),'Invalid case contract schema')
  requireThat(JSON.stringify(Object.keys(contract.fixtures).sort())===JSON.stringify(Object.keys(fixtures).sort()),'Wrong contract fixture set')
  for(const names of Object.values(contract.fixtures))requireThat(Array.isArray(names)&&names.length>0&&names.every(n=>typeof n==='string'&&n.trim().length>0)&&new Set(names).size===names.length,'Empty or duplicate contract case IDs')
  return contract.fixtures
}
export const contracts=validateContract(strictJson(await readFile(new URL('./workbench-case-contract.json',import.meta.url))))
export function requireThat(value,message){if(!value)throw Error(message)}
// Each fixture owns an isolated POSIX process group. Windows uses taskkill's tree
// traversal while the fixture parent is still owned; unsuccessful cleanup is recorded
// as a failure, never a claim that an unobservable descendant has been reaped.
export async function runFixtureCommand(command,{cwd,env=process.env,timeoutMs=300000,maxOutputBytes=8*1024*1024,cleanupMs=5000}={}){
  requireThat(Array.isArray(command)&&command.length>0&&timeoutMs>0&&maxOutputBytes>0&&cleanupMs>0,'Invalid fixture execution limits')
  const child=spawn(command[0],command.slice(1),{cwd,env,windowsHide:true,detached:process.platform!=='win32',stdio:['ignore','pipe','pipe']})
  const chunks=[];let size=0,timedOut=false,overflow=false,spawnFailure,cleanupPromise,cleanup={attempted:false},closed=false,exitCode=null,signal=null
  const complete=new Promise(accept=>{child.once('error',error=>{spawnFailure=error.message});child.once('close',(code,receivedSignal)=>{closed=true;exitCode=code;signal=receivedSignal;accept()})})
  async function cleanTree(){
    cleanup={attempted:true,method:process.platform==='win32'?'taskkill-tree':'isolated-process-group',completed:false}
    try{
      if(process.platform==='win32'){
        requireThat(child.pid,'Fixture never started')
        const killer=spawn('taskkill.exe',['/PID',String(child.pid),'/T','/F'],{windowsHide:true,stdio:'ignore'})
        const code=await new Promise((accept,reject)=>{let timer=setTimeout(()=>{killer.kill();reject(Error('Tree cleanup command timed out'))},cleanupMs);killer.once('error',error=>{clearTimeout(timer);reject(error)});killer.once('close',code=>{clearTimeout(timer);accept(code)})})
        cleanup.exitCode=code;requireThat(code===0,'Tree cleanup command failed')
      }else{try{process.kill(-child.pid,'SIGKILL')}catch(error){if(error.code!=='ESRCH')throw error}}
      cleanup.completed=true
    }catch(error){cleanup.failure=error.message;if(!closed)child.kill('SIGKILL')}
  }
  const terminate=()=>{if(!cleanupPromise)cleanupPromise=cleanTree()}
  const append=bytes=>{const keep=Math.min(bytes.length,maxOutputBytes-size);if(keep>0){chunks.push(bytes.subarray(0,keep));size+=keep}if(keep<bytes.length){overflow=true;terminate()}}
  child.stdout.on('data',append);child.stderr.on('data',append)
  const timer=setTimeout(()=>{timedOut=true;terminate()},timeoutMs)
  let finalTimer
  try{
    await Promise.race([complete,new Promise((_,reject)=>{finalTimer=setTimeout(()=>{terminate();reject(Error('Fixture process did not close after tree cleanup'))},timeoutMs+cleanupMs*2+1000)})])
    if(cleanupPromise)await cleanupPromise
  }finally{clearTimeout(timer);clearTimeout(finalTimer);if(!closed){terminate();await cleanupPromise}}
  return{output:Buffer.concat(chunks,size),command:{exitCode,signal,timedOut,overflow,cleanup,...(spawnFailure?{spawnFailure}:{})}}
}
async function readConsistent(root,name,max,{allowEmpty=false,hashOnly=false,onOpened}={}){
  requireThat(Number.isSafeInteger(max)&&max>=0,'Invalid file size limit')
  requireThat(typeof name==='string'&&name.length>0&&!name.includes('\\')&&!isAbsolute(name)&&!name.split('/').some(p=>!p||p==='.'||p==='..')&&!/[:\x00]/.test(name),'Unsafe evidence path')
  const base=await realpath(root),path=resolve(base,name)
  requireThat(!relative(base,path).startsWith('..'),'Evidence path escapes root')
  let current=base
  for(const part of name.split('/')){current=join(current,part);requireThat(!(await lstat(current)).isSymbolicLink(),'Evidence symlink rejected')}
  // Trusted, exclusively owned local evidence directories are required. The descriptor,
  // identity and final path checks detect ordinary concurrent changes; O_NOFOLLOW only
  // protects the final component on POSIX, not hostile intermediate-directory swaps.
  const before=await lstat(path,{bigint:true})
  requireThat(before.isFile()&&(allowEmpty||before.size>0n)&&before.size<=BigInt(max),'Evidence file empty, oversized or not regular')
  const handle=await open(path,constants.O_RDONLY|(process.platform==='win32'?0:constants.O_NOFOLLOW))
  const same=(a,b)=>a.dev===b.dev&&a.ino===b.ino&&a.size===b.size&&a.mtimeNs===b.mtimeNs&&a.ctimeNs===b.ctimeNs
  try{
    const opened=await handle.stat({bigint:true});requireThat(same(before,opened),'File changed before opening')
    if(onOpened)await onOpened()
    const chunks=[],hash=createHash('sha256');let total=0
    while(true){const buffer=Buffer.alloc(Math.min(64*1024,max-total+1)),{bytesRead}=await handle.read(buffer,0,buffer.length,null);if(!bytesRead)break;total+=bytesRead;requireThat(total<=max,'Evidence file oversized during read');const bytes=buffer.subarray(0,bytesRead);if(hashOnly)hash.update(bytes);else chunks.push(bytes)}
    requireThat(allowEmpty||total>0,'Evidence file empty')
    requireThat(same(opened,await handle.stat({bigint:true}))&&same(opened,await lstat(path,{bigint:true}))&&await realpath(path)===path,'File changed during read')
    return hashOnly?{sha256:hash.digest('hex'),size:total}:Buffer.concat(chunks,total)
  }finally{await handle.close()}
}
export async function boundedRead(root,name,max=16*1024*1024,options={}){return readConsistent(root,name,max,options)}
export async function sourceDigest(root,name,max=64*1024*1024){return readConsistent(root,name,max,{allowEmpty:true,hashOnly:true})}
export async function atomicJson(path,value){
  await mkdir(dirname(path),{recursive:true});const temporary=path+'.'+randomUUID()+'.tmp'
  try{await writeFile(temporary,JSON.stringify(value,null,2)+'\n',{flag:'wx'});await rename(temporary,path)}finally{await rm(temporary,{force:true})}
}
export async function inputSnapshot(root,{fileLimit=64*1024*1024,totalLimit=512*1024*1024}={}){
  requireThat(Number.isSafeInteger(fileLimit)&&fileLimit>=0&&Number.isSafeInteger(totalLimit)&&totalLimit>=0,'Invalid source snapshot limits')
  const names=execFileSync('git',['ls-files','--cached','--others','--exclude-standard','-z'],{cwd:root,maxBuffer:16*1024*1024}).toString().split('\0').filter(n=>n&&(/^(frontend\/(src|tests|scripts|public)\/|frontend\/[^/]+$|harness-[^/]+\/src\/|sdk\/|scripts\/|third-party\/|\.mvn\/|\.github\/workflows\/)/.test(n)||/(^|\/)(pom\.xml|mvnw(\.cmd)?)$/.test(n))).filter(n=>!n.includes('/__pycache__/'))
  requireThat(names.length>0,'Empty input snapshot');const result={};let total=0
  for(const name of [...new Set(names)].sort()){const item=await sourceDigest(root,name,fileLimit);total+=item.size;requireThat(total<=totalLimit,'Source snapshot total size limit exceeded');result[name]=item.sha256}return result
}
export function validateRecord(record,fixture,engine){
  requireThat(Object.hasOwn(fixtures,fixture),'Unknown fixture')
  requireThat(record&&typeof record==='object'&&!Array.isArray(record),'Invalid fixture record')
  requireThat(record.status==='PASS'||(fixture==='settings'&&record.status===undefined&&record.fixtureCredentialsCleaned===true),'Fixture did not pass')
  if(fixture==='settings')requireThat(record.fixtureCredentialsCleaned===true,'Settings credentials not cleaned')
  requireThat(record.browserEngine===undefined||record.browserEngine===engine,'Wrong browser engine')
  requireThat((record.paidRequests??record.runtime?.paidRequests)===0,'Unknown or nonzero paid requests')
  requireThat(/^[0-9a-f]{64}$/.test(record.jarSha256),'Invalid JAR digest')
  requireThat(Array.isArray(record.checks)&&record.checks.length===contracts[fixture].length,'Wrong case count')
  const names=record.checks.map(c=>{requireThat(c&&typeof c==='object'&&typeof c.name==='string'&&c.passed===true,'Invalid case verdict');return c.name})
  requireThat(new Set(names).size===names.length,'Duplicate case ID')
  requireThat(JSON.stringify([...names].sort())===JSON.stringify([...contracts[fixture]].sort()),'Wrong case IDs')
  requireThat(Array.isArray(record.screenshots)&&record.screenshots.length>0,'Missing screenshots')
  const basenames=record.screenshots.map(s=>{requireThat(s&&typeof s.path==='string'&&/^[0-9a-f]{64}$/.test(s.sha256),'Invalid screenshot');return s.path.replaceAll('\\','/').split('/').at(-1)})
  requireThat(new Set(basenames).size===basenames.length,'Duplicate screenshot basename')
}
export async function validateFrozen(root,record,fixture,engine,{runId}={}){
  validateRecord(record,fixture,engine)
  requireThat(record.schemaVersion===1,'Unsupported frozen record schema')
  requireThat(record.fixture===fixture&&record.browserEngine===engine,'Frozen fixture identity mismatch')
  requireThat(typeof record.runId==='string'&&record.runId.length>0&&(!runId||record.runId===runId),'Wrong run identity')
  requireThat(record.command?.exitCode===0&&record.command?.signal===null&&record.command?.timedOut===false&&record.command?.overflow===false,'Unsuccessful fixture command')
  requireThat(record.jarSha256===sha(await boundedRead(root,'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar',256*1024*1024)),'JAR drift')
  requireThat(record.fixtureSourceSha256===sha(await boundedRead(root,'frontend/tests/'+fixture+'.mjs')),'Fixture source drift')
  requireThat(record.logSha256===sha(await boundedRead(root,`.build-cache/M10.5.9/browser/${engine}/${fixture}.log`)),'Log drift')
  const snapshot=await inputSnapshot(root)
  requireThat(JSON.stringify(record.inputSha256)===JSON.stringify(snapshot),'Input snapshot drift')
  if(record.sourceSha256!==undefined){requireThat(record.sourceSha256&&Object.keys(record.sourceSha256).length>0,'Empty source manifest');for(const [name,digest]of Object.entries(record.sourceSha256))requireThat(digest===(await sourceDigest(root,name)).sha256,'Source drift')}
  for(const shot of record.screenshots){requireThat(shot.path.startsWith(`.build-cache/M10.5.9/browser/${engine}/${fixture}-`),'Screenshot outside frozen fixture');requireThat(sha(await boundedRead(root,shot.path,32*1024*1024))===shot.sha256,'Screenshot drift')}
}
export async function validateMatrix(root,name='.build-cache/M10.5.9/browser/matrix.json'){
  const matrix=strictJson(await boundedRead(root,name,8*1024*1024))
  requireThat(matrix.schemaVersion===1&&matrix.status==='PASS'&&matrix.paidRequests===0,'Matrix did not pass current schema')
  requireThat(Array.isArray(matrix.engines)&&matrix.engines.length&&new Set(matrix.engines).size===matrix.engines.length&&matrix.engines.every(e=>['chromium','firefox','webkit'].includes(e)),'Invalid engine matrix')
  requireThat(Array.isArray(matrix.fixtures)&&JSON.stringify([...matrix.fixtures].sort())===JSON.stringify(Object.keys(fixtures).sort()),'Incomplete fixture matrix')
  requireThat(typeof matrix.runId==='string'&&matrix.runId.length>0,'Missing matrix run identity')
  requireThat(Array.isArray(matrix.records)&&matrix.records.length===matrix.engines.length*matrix.fixtures.length,'Incomplete matrix records')
  const seen=new Set()
  for(const entry of matrix.records){
    requireThat(matrix.engines.includes(entry.engine)&&matrix.fixtures.includes(entry.fixture),'Foreign matrix entry')
    const key=entry.engine+'/'+entry.fixture;requireThat(!seen.has(key),'Duplicate matrix entry');seen.add(key)
    requireThat(entry.path===`.build-cache/M10.5.9/browser/${entry.engine}/${entry.fixture}.json`,'Wrong matrix entry path')
    const bytes=await boundedRead(root,entry.path,8*1024*1024);requireThat(sha(bytes)===entry.sha256,'Matrix record digest mismatch')
    const record=strictJson(bytes);requireThat(entry.checks===record.checks?.length,'Matrix case count mismatch')
    await validateFrozen(root,record,entry.fixture,entry.engine,{runId:matrix.runId})
  }
  return matrix
}
const entryPath=process.argv[1]?await realpath(resolve(process.argv[1])).catch(()=>null):null
// Canonicalize both sides through the same filesystem API: Windows module URLs
// can retain drive/directory casing or short aliases that realpath normalizes.
const modulePath=await realpath(fileURLToPath(import.meta.url))
if(entryPath&&modulePath===entryPath){
  requireThat(process.argv.length===3,'Usage: node scripts/workbench-evidence.mjs <repository-relative matrix.json>')
  const matrix=await validateMatrix(resolve(import.meta.dirname,'..'),process.argv[2]);console.log(JSON.stringify({status:'PASS',runId:matrix.runId,records:matrix.records.length}))
}
