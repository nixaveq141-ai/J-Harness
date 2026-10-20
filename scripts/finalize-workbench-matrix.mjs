// Resume only a single current run whose complete artifacts still match current inputs.
import {resolve,join} from 'node:path'
import {fixtures,sha,strictJson,requireThat,boundedRead,atomicJson,validateFrozen} from './workbench-evidence.mjs'
const root=resolve(import.meta.dirname,'..'),out=join(root,'.build-cache/M10.5.9/browser'),engines=process.argv.slice(2),records=[]
let runId
const result=status=>({schemaVersion:1,status,runId:runId??null,capturedAt:new Date().toISOString(),platform:process.platform,engines,fixtures:Object.keys(fixtures),records,paidRequests:0})
await atomicJson(join(out,'matrix.json'),result('INCOMPLETE'))
try{
 requireThat(engines.length&&new Set(engines).size===engines.length&&engines.every(e=>['chromium','firefox','webkit'].includes(e)),'Invalid engine matrix')
 for(const engine of engines)for(const fixture of Object.keys(fixtures)){
  const path=`.build-cache/M10.5.9/browser/${engine}/${fixture}.json`,bytes=await boundedRead(root,path,8*1024*1024),record=strictJson(bytes)
  if(runId===undefined)runId=record.runId
  await validateFrozen(root,record,fixture,engine,{runId})
  records.push({engine,fixture,checks:record.checks.length,path,sha256:sha(bytes)})
 }
 await atomicJson(join(out,'matrix.json'),result('PASS'))
 console.log('PASS consistent retained matrix',records.length,'runs',records.reduce((n,r)=>n+r.checks,0),'checks')
}catch(error){await atomicJson(join(out,'matrix.json'),{...result('FAILED'),error:error.message});throw error}
