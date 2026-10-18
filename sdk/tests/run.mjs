import { createServer } from 'node:http'
import { spawn, execFileSync } from 'node:child_process'
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { createHash, randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'

const root=resolve(import.meta.dirname,'../..'),cache=join(root,'.build-cache'),base=join(cache,'M10.5.8-sdk-'+Date.now())
const secret='synthetic-sdk-'+randomUUID(),wire=[],results=[]
const server=createServer(async(req,res)=>{
  let raw='';for await(const chunk of req)raw+=chunk
  const body=JSON.parse(raw),goal=body.messages.findLast(m=>m.role==='user')?.content??''
  wire.push({goal,authorized:req.headers.authorization==='Bearer '+secret,bodyContainsSecret:raw.includes(secret)})
  if(goal==='SDK_CANCEL'||goal==='SDK_DISCONNECT')return // Cancellation/EOF must stop this owned request.
  if(goal==='SDK_UNKNOWN'){req.socket.destroy();return}
  const tools=body.messages.some(m=>m.role==='tool'),calls=goal==='SDK_FLOW'&&!tools?[
    ['sdk-ask','ask_user',{question:'Choose a format',mode:'single',allowText:true,timeoutSeconds:60,options:[{id:'a',label:'Text',description:'Recommended',recommended:true},{id:'b',label:'JSON',description:'Alternative',recommended:false}]}],
    ['sdk-write','write_file',{path:'approved.txt',content:'approved only after question and tool decision'}],
    ['sdk-read','read_file',{path:'large.txt'}],
  ].map(([id,name,args])=>({id,type:'function',function:{name,arguments:JSON.stringify(args)}})):[]
  res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({choices:[{index:0,finish_reason:calls.length?'tool_calls':'stop',message:{role:'assistant',content:calls.length?null:'SDK fixture finished.',...(calls.length?{tool_calls:calls}:{})}}],usage:{prompt_tokens:30,completion_tokens:10,total_tokens:40}}))
})
await new Promise(r=>server.listen(0,'127.0.0.1',r));await mkdir(base,{recursive:true})
const jar=join(root,'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar')
const peerJar=join(base,'transport-peer.jar')
execFileSync(join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'jar.exe':'jar'),['--create','--file',peerJar,'--main-class','io.jharness.testkit.SdkTransportPeer','-C',join(root,'harness-testkit/target/test-classes'),'io/jharness/testkit/SdkTransportPeer.class'])
try{
  for(const language of ['typescript','python']){
    const workspace=join(base,language)
    for(const scenario of ['flow','cancel','unknown','disconnect']){
      const path=join(workspace,scenario);await mkdir(path,{recursive:true})
      await writeFile(join(path,'large.txt'),('Artifact SDK_FLOW '+secret+'\n').repeat(1000))
      await writeFile(join(path,'config.json'),JSON.stringify({baseUrl:`http://127.0.0.1:${server.address().port}`,model:'sdk-fixture',maxSteps:4,durationSeconds:60,requestTimeoutSeconds:30,ecosystem:{web:{approvalSeconds:60}}}))
    }
    const env={...process.env,JHARNESS_API_KEY:secret,JHARNESS_SDK_JAR:jar,JHARNESS_SDK_WORKSPACE:workspace,JHARNESS_SDK_PEER_JAR:peerJar}
    const program=language==='typescript'?process.execPath:process.env.JHARNESS_PYTHON
    assert.ok(program,'Set JHARNESS_PYTHON to an available Python 3.10+ interpreter')
    const args=language==='typescript'?['--experimental-strip-types','sdk/tests/client.ts']:['sdk/tests/client.py']
    const child=spawn(program,args,{cwd:root,env,windowsHide:true,stdio:'pipe'})
    let output='',error='';child.stdout.on('data',d=>{output+=d});child.stderr.on('data',d=>{if(error.length<65536)error+=d})
    const timeout=setTimeout(()=>child.kill(),120000)
    const code=await new Promise(r=>child.on('exit',r));clearTimeout(timeout)
    if(code!==0)await writeFile(join(cache,`M10.5.8-sdk-${language}-failure.log`),(output+'\n'+error).replaceAll(secret,'[REDACTED]'))
    assert.equal(code,0,`${language} worker failed; see local failure log`)
    const report=JSON.parse(output.trim().split(/\r?\n/).at(-1));results.push(report);console.log('PASS',language,report.checks.length)
  }
  assert.ok(wire.every(w=>w.authorized&&!w.bodyContainsSecret),'Only authorized loopback and redacted model context')
  await writeFile(join(cache,'M10.5.8-sdk-results.json'),JSON.stringify({status:'PASS',capturedAt:new Date().toISOString(),paidRequests:0,jarSha256:createHash('sha256').update(await readFile(jar)).digest('hex'),results,requests:wire.length,allAuthorized:true,modelContextRedacted:true},null,2))
}finally{server.closeAllConnections();await new Promise(r=>server.close(r))}
