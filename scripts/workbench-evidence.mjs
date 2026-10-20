// Current-run evidence contract. Historical JSON files are intentionally not migrated.
import {readFile,writeFile,rename,lstat,realpath,mkdir,rm} from 'node:fs/promises'
import {resolve,relative,dirname,isAbsolute,join} from 'node:path'
import {createHash,randomUUID} from 'node:crypto'
import {execFileSync} from 'node:child_process'
import {pathToFileURL} from 'node:url'
export const sha=b=>createHash('sha256').update(b).digest('hex')
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
export const contracts=JSON.parse(await readFile(new URL('./workbench-case-contract.json',import.meta.url),'utf8')).fixtures
export function requireThat(value,message){if(!value)throw Error(message)}
export async function boundedRead(root,name,max=16*1024*1024){
  requireThat(typeof name==='string'&&name.length>0&&!name.includes('\\')&&!isAbsolute(name)&&!name.split('/').some(p=>!p||p==='.'||p==='..')&&!/[:\x00]/.test(name),'Unsafe evidence path')
  const base=await realpath(root),path=resolve(base,name)
  requireThat(!relative(base,path).startsWith('..'),'Evidence path escapes root')
  let current=base
  for(const part of name.split('/')){current=join(current,part);requireThat(!(await lstat(current)).isSymbolicLink(),'Evidence symlink rejected')}
  const stat=await lstat(path);requireThat(stat.isFile()&&stat.size>0&&stat.size<=max,'Evidence file empty, oversized or not regular')
  const bytes=await readFile(path);requireThat(bytes.length>0&&bytes.length<=max,'Evidence file empty or oversized');return bytes
}
export async function atomicJson(path,value){
  await mkdir(dirname(path),{recursive:true});const temporary=path+'.'+randomUUID()+'.tmp'
  try{await writeFile(temporary,JSON.stringify(value,null,2)+'\n',{flag:'wx'});await rename(temporary,path)}finally{await rm(temporary,{force:true})}
}
export async function inputSnapshot(root){
  const names=execFileSync('git',['ls-files','--cached','--others','--exclude-standard','-z'],{cwd:root,maxBuffer:16*1024*1024}).toString().split('\0').filter(n=>n&&(/^(frontend\/(src|tests|scripts|public)\/|frontend\/[^/]+$|harness-[^/]+\/src\/|sdk\/|scripts\/|third-party\/|\.mvn\/|\.github\/workflows\/)/.test(n)||/(^|\/)(pom\.xml|mvnw(\.cmd)?)$/.test(n))).filter(n=>!n.includes('/__pycache__/'))
  requireThat(names.length>0,'Empty input snapshot');const result={};for(const name of [...new Set(names)].sort())result[name]=sha(await boundedRead(root,name));return result
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
  requireThat(record.command?.exitCode===0&&record.command?.signal===null&&record.command?.timedOut===false,'Unsuccessful fixture command')
  requireThat(record.jarSha256===sha(await boundedRead(root,'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar',256*1024*1024)),'JAR drift')
  requireThat(record.fixtureSourceSha256===sha(await boundedRead(root,'frontend/tests/'+fixture+'.mjs')),'Fixture source drift')
  requireThat(record.logSha256===sha(await boundedRead(root,`.build-cache/M10.5.9/browser/${engine}/${fixture}.log`)),'Log drift')
  const snapshot=await inputSnapshot(root)
  requireThat(JSON.stringify(record.inputSha256)===JSON.stringify(snapshot),'Input snapshot drift')
  if(record.sourceSha256!==undefined){requireThat(record.sourceSha256&&Object.keys(record.sourceSha256).length>0,'Empty source manifest');for(const [name,digest]of Object.entries(record.sourceSha256))requireThat(digest===sha(await boundedRead(root,name)),'Source drift')}
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
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  requireThat(process.argv.length===3,'Usage: node scripts/workbench-evidence.mjs <repository-relative matrix.json>')
  const matrix=await validateMatrix(resolve(import.meta.dirname,'..'),process.argv[2]);console.log(JSON.stringify({status:'PASS',runId:matrix.runId,records:matrix.records.length}))
}
