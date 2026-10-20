import {spawn} from 'node:child_process'
import {rm,mkdir,writeFile} from 'node:fs/promises'
import {resolve,join} from 'node:path'
import {randomUUID} from 'node:crypto'
import {fixtures,sha,strictJson,requireThat,boundedRead,atomicJson,inputSnapshot,validateRecord,validateFrozen} from './workbench-evidence.mjs'
const root=resolve(import.meta.dirname,'..'),cache=join(root,'.build-cache'),out=join(cache,'M10.5.9/browser')
const engines=(process.env.JHARNESS_MATRIX_ENGINES??'chromium,firefox,webkit').split(',')
const selected=(process.env.JHARNESS_MATRIX_FIXTURES??Object.keys(fixtures).join(',')).split(',')
const runId=process.env.JHARNESS_EVIDENCE_RUN_ID??randomUUID(),records=[],failures=[],matrixPath=join(out,'matrix.json')
const matrix=status=>({schemaVersion:1,status,runId,capturedAt:new Date().toISOString(),platform:process.platform,engines,fixtures:selected,records,failures,paidRequests:0})
// Replace the old PASS before any operation that can fail, including argument validation.
await atomicJson(matrixPath,matrix('INCOMPLETE'))
try{
 requireThat(typeof runId==='string'&&/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(runId),'Invalid evidence run identity')
 requireThat(engines.length&&new Set(engines).size===engines.length&&engines.every(e=>['chromium','firefox','webkit'].includes(e)),'Invalid engine matrix')
 requireThat(selected.length&&new Set(selected).size===selected.length&&selected.every(f=>Object.hasOwn(fixtures,f)),'Invalid fixture matrix')
 for(const engine of engines)for(const fixture of selected){
  const directory=join(out,engine);await mkdir(directory,{recursive:true})
  const target=join(directory,fixture+'.json'),log=join(directory,fixture+'.log')
  await atomicJson(target,{status:'INCOMPLETE',runId,engine,fixture})
  await rm(join(cache,fixtures[fixture]),{force:true})
  const before=await inputSnapshot(root),env={...process.env,JHARNESS_BROWSER_ENGINE:engine}
  if(engine!=='chromium')delete env.JHARNESS_BROWSER_CHANNEL
  const child=spawn(process.execPath,['frontend/tests/'+fixture+'.mjs'],{cwd:root,env,windowsHide:true,stdio:['ignore','pipe','pipe']})
  let output=Buffer.alloc(0),timedOut=false,overflow=false
  const append=d=>{if(output.length+d.length>8*1024*1024){overflow=true;child.kill('SIGKILL')}else output=Buffer.concat([output,d])}
  child.stdout.on('data',append);child.stderr.on('data',append)
  let hardTimer,boundTimer
  const timer=setTimeout(()=>{timedOut=true;child.kill();hardTimer=setTimeout(()=>child.kill('SIGKILL'),2000)},300000)
  let command
  try{command=await new Promise((accept,reject)=>{child.once('error',reject);child.once('close',(exitCode,signal)=>accept({exitCode,signal,timedOut}));boundTimer=setTimeout(()=>reject(Error('Fixture process did not close after termination')),307000)})}
  finally{clearTimeout(timer);clearTimeout(hardTimer);clearTimeout(boundTimer);await writeFile(log,output.length?output:Buffer.from('[no command output]\n'))}
  try{
   requireThat(command.exitCode===0&&command.signal===null&&!timedOut&&!overflow,'Fixture command failed, timed out or exceeded output limit')
   const record=strictJson(await boundedRead(cache,fixtures[fixture],8*1024*1024));validateRecord(record,fixture,engine)
   requireThat(JSON.stringify(before)===JSON.stringify(await inputSnapshot(root)),'Inputs changed during fixture')
   for(const shot of record.screenshots){
    const name=shot.path.replaceAll('\\','/');requireThat(name.startsWith('.build-cache/'),'Screenshot outside fixture cache')
    const bytes=await boundedRead(root,name,32*1024*1024);requireThat(sha(bytes)===shot.sha256,'Screenshot changed')
    const frozen=`.build-cache/M10.5.9/browser/${engine}/${fixture}-${name.split('/').at(-1)}`
    await writeFile(join(root,frozen),bytes);shot.path=frozen
   }
   Object.assign(record,{schemaVersion:1,status:'PASS',runId,command,inputSha256:before,browserEngine:engine,fixture,fixtureSourceSha256:sha(await boundedRead(root,'frontend/tests/'+fixture+'.mjs')),logSha256:sha(output.length?output:Buffer.from('[no command output]\n'))})
   await validateFrozen(root,record,fixture,engine,{runId});await atomicJson(target,record)
   const bytes=await boundedRead(root,`.build-cache/M10.5.9/browser/${engine}/${fixture}.json`)
   records.push({engine,fixture,checks:record.checks.length,path:`.build-cache/M10.5.9/browser/${engine}/${fixture}.json`,sha256:sha(bytes)})
   console.log('PASS',engine,fixture,record.checks.length)
  }catch(error){failures.push({engine,fixture,command,error:error.message,log:`.build-cache/M10.5.9/browser/${engine}/${fixture}.log`});await atomicJson(target,{status:'FAILED',runId,engine,fixture,command,error:error.message});if(process.env.JHARNESS_MATRIX_KEEP_GOING!=='1')throw error}
 }
 requireThat(!failures.length,'One or more browser fixtures failed')
 await atomicJson(matrixPath,matrix('PASS'))
}catch(error){failures.push({error:error.message});await atomicJson(matrixPath,matrix('FAILED'));throw error}
