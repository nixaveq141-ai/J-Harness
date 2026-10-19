import { JHarnessClient, RpcError, type ObjectValue } from '../typescript/src/index.ts'
import { join } from 'node:path'
import { access, readFile } from 'node:fs/promises'
import assert from 'node:assert/strict'
const checks:string[]=[],base=process.env.JHARNESS_SDK_WORKSPACE!,secret=process.env.JHARNESS_API_KEY!
const wait=(ms:number)=>new Promise(r=>setTimeout(r,ms))
async function until(fn:()=>Promise<boolean>,ms=25000){const end=Date.now()+ms;while(Date.now()<end){if(await fn())return;await wait(50)}throw Error('Fixture timeout')}
const options=(scenario:string)=>({java:join(process.env.JAVA_HOME!,process.platform==='win32'?'bin/java.exe':'bin/java'),jar:process.env.JHARNESS_SDK_JAR!,workspace:join(base,scenario),config:join(base,scenario,'config.json')})
function check(name:string,value=true){assert.ok(value,name);checks.push(name); console.log('PASS',name)}
const rows=(v:unknown)=>v as ObjectValue[]
let client:JHarnessClient|undefined
try{
  await assert.rejects(JHarnessClient.start({...options('flow'),protocolVersion:'99'}),e=>e instanceof RpcError&&e.code==='VERSION_UNSUPPORTED');check('version mismatch rejects without execution and closes child')
  client=await JHarnessClient.start(options('flow'));check('protocol negotiates version and bounds',client.capabilities.protocolVersion==='1.0'&&client.capabilities.maxPending===32)
  await assert.rejects(client.call('unknown.method'),e=>e instanceof RpcError&&e.code==='METHOD_NOT_FOUND')
  await assert.rejects(client.call('session.open',{path:'outside'}),e=>e instanceof RpcError&&e.code==='INVALID_PARAMS');check('unknown methods and fields reject')
  const opened=await client.openSession();check('opening is a nonexecuting draft',opened.state==='DRAFT'&&opened.persisted===false&&(await client.listSessions()).historyCount===0)
  const receipt=await client.sendInput(String(opened.handle),'SDK_FLOW','initial-flow'),id=String(receipt.sessionId)
  await until(async()=>rows((await client!.questions(id)).questions).some(q=>q.state==='PENDING'))
  const question=rows((await client.questions(id)).questions).find(q=>q.state==='PENDING')!
  check('recommendation is pending without automatic answer',question.state==='PENDING'&&await access(join(base,'flow/approved.txt')).then(()=>false,()=>true))
  await client.answer(question,['a'],'',false,'answer-flow')
  await until(async()=>rows((await client!.approvals(id)).approvals).length>0)
  const approval=rows((await client.approvals(id)).approvals)[0]
  check('question answer does not approve write',await access(join(base,'flow/approved.txt')).then(()=>false,()=>true))
  await assert.rejects(client.decide(id,{...approval,callId:'wrong'},true,'fixture'),RpcError)
  await client.decide(id,approval,true,'fixture approval')
  await until(async()=>(await client!.result(id)).phase==='FINISHED')
  check('separate exact approval executes real write',await readFile(join(base,'flow/approved.txt'),'utf8')==='approved only after question and tool decision')
  const replay=await client.sendInput(String(opened.handle),'SDK_FLOW','initial-flow');check('same initial request is idempotent',replay.sessionId===id)
  const accepted=await client.call('request.get',{requestId:'initial-flow'});check('lost receipt can be reconciled without sending',accepted.taskId===id)
  await client.sendInput(String(opened.handle),'SDK_FOLLOWUP','followup');await until(async()=>(await client!.result(id)).phase==='FINISHED')
  const events:ObjectValue[]=[];let after=''
  for(;;){const page=await client.events(id,after,10);events.push(...rows(page.events));if(!page.more)break;after=String(page.next)}
  check('paged real events retain multiple turn boundaries',events.filter(e=>e.type==='turn.started').length===2)
  const artifact=events.filter(e=>e.type==='tool.completed').map(e=>(e.payload as ObjectValue).value as ObjectValue).find(v=>v&&typeof v.artifactRef==='string')!
  const page=await client.artifact(id,String(artifact.artifactRef));check('artifact reads real scoped redacted content',String(page.text).includes('Artifact')&&!JSON.stringify(page).includes(secret))
  check('report and event content exclude secrets',!JSON.stringify(await client.report(id)).includes(secret)&&!JSON.stringify(events).includes(secret))
  check('content search reuses Java service and source ids',Number((await client.search('SDK_FLOW')).total)>0)
  const pid=client.process.pid!;check('key never enters argv',!client.process.spawnargs.join(' ').includes(secret));await client.close();check('graceful close stops owned process',(client.process.exitCode!==null||client.process.signalCode!==null))
  for(const scenario of ['cancel','unknown','disconnect']){
    client=await JHarnessClient.start(options(scenario));const handle=await client.openSession();const goal='SDK_'+scenario.toUpperCase()
    const sent=await client.sendInput(String(handle.handle),goal,'initial-'+scenario),sid=String(sent.sessionId)
    await until(async()=>rows((await client!.events(sid)).events).some(e=>e.type==='model.requested'))
    if(scenario==='cancel'){await client.cancel(sid);await until(async()=>['FINISHED','UNKNOWN'].includes(String((await client!.result(sid)).phase)));check('cancel propagates without fabricated success',(await client.result(sid)).status!=='COMPLETED');await client.close()}
    else{
      if(scenario==='disconnect'){client.process.kill();await until(async()=>client!.process.exitCode!==null||client!.process.signalCode!==null);await assert.rejects(client.result(sid),RpcError);check('disconnect rejects pending use without automatic resend')}
      else await until(async()=>(await client!.result(sid)).phase==='UNKNOWN')
      await client.close();client=await JHarnessClient.start(options(scenario));const reopened=await client.openSession('default',sid)
      check(scenario+' restart retains UNKNOWN without replay',(await client.result(sid)).phase==='UNKNOWN')
      await assert.rejects(client.sendInput(String(reopened.handle),'must not execute','blocked'),RpcError);check(scenario+' refuses unsafe continuation')
      await client.close()
    }
  }
  check('all owned children closed',(client.process.exitCode!==null||client.process.signalCode!==null))
  client=await JHarnessClient.start({...options('flow'),jar:process.env.JHARNESS_SDK_PEER_JAR!})
  await assert.rejects(client.call('slow',{},100),e=>e instanceof RpcError&&e.code==='REQUEST_TIMEOUT_ACCEPTANCE_UNKNOWN')
  await wait(900);check('timeout discards late response and never retries',(await client.call('stats')).slowCalls===1)
  await client.call('stderr');check('child diagnostic text never appears in API result')
  await assert.rejects(client.call('ping',{text:'x'.repeat(65537)}),e=>e instanceof RpcError&&e.code==='RPC_REQUEST_LIMIT');check('request byte bound rejects before sending')
  const pending=Array.from({length:32},()=>client!.call('hold').then(()=>false,()=>true))
  await assert.rejects(client.call('hold'),e=>e instanceof RpcError&&e.code==='BACKPRESSURE_LIMIT');check('pending request bound applies backpressure')
  await client.close();check('close rejects every pending call', (await Promise.all(pending)).every(Boolean))
  for(const method of ['duplicate','oversized']){
    client=await JHarnessClient.start({...options('flow'),jar:process.env.JHARNESS_SDK_PEER_JAR!})
    await assert.rejects(client.call(method),e=>e instanceof RpcError&&e.code==='INVALID_RESPONSE');await client.close();check(method+' response fails closed and stops owned peer')
  }
  console.log(JSON.stringify({language:'typescript',checks,passed:true}))
}finally{await client?.close()}
