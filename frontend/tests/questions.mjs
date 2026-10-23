import { browserType as chromium, stoppedHostDiagnostic } from './runtime.mjs'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { createHash, randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'
const root=resolve(import.meta.dirname,'../..'),out=join(root,'.build-cache'),workspace=join(out,'M10.5.5-questions-'+Date.now())
const jar=join(root,'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'),java=join(process.env.JAVA_HOME,process.platform==='win32'?'bin/java.exe':'bin/java'),token='questions_fixture_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ',secret='synthetic-question-'+randomUUID()
const checks=[],wire=[],shots=[],errors=[],posts=[]
let host,base,output='',browser,page,context,result,releaseQuestion
const wait=ms=>new Promise(r=>setTimeout(r,ms))
async function until(fn,timeout=30000){const begin=Date.now();while(Date.now()-begin<timeout){if(await fn())return;await wait(60)}throw Error('Question fixture timeout')}
function check(name,value=true){assert.ok(value,name);checks.push({name,passed:true});console.log('PASS',name)}
const question=(mode,seconds)=>({question:mode==='single'?'Choose the delivery format':mode==='multiple'?'Choose verification targets':'Describe additional constraints',mode,allowText:true,timeoutSeconds:seconds,options:mode==='text'?[]:[{id:'a',label:'First choice',description:'Recommended, not submitted',recommended:true},{id:'b',label:'Second choice',description:'Alternative delivery',recommended:false}]})
const provider=createServer(async(req,res)=>{
 let raw='';for await(const c of req)raw+=c
 const body=JSON.parse(raw),goal=body.messages.findLast(m=>m.role==='user')?.content??'',count=body.messages.filter(m=>m.role==='tool'&&m.tool_call_id.startsWith('ask-')).length,n=wire.length
 wire.push({body,goal,authorized:req.headers.authorization==='Bearer '+secret})
 const call=(id,name,args)=>({id,type:'function',function:{name,arguments:JSON.stringify(args)}})
 let calls=[]
 const stages=goal==='flow'?3:1
 if(count<stages){
  const mode=goal==='private'||goal==='lost'?'text':goal==='flow'?['single','multiple','text'][count]:'single'
  calls.push(call('ask-'+n,'ask_user',question(mode,goal==='timeout'?1:120)))
  if(count===0&&goal!=='cancel'&&goal!=='restart'){
   if(goal==='flow')calls.push(call('write-'+n,'write_file',{path:'must-not-write.txt',content:'requires separate approval'}))
   calls.push(call('read-'+n,'read_file',{path:'note.txt'}))
  }
 }
 const send=()=>{res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({choices:[{index:0,finish_reason:calls.length?'tool_calls':'stop',message:calls.length?{role:'assistant',content:null,tool_calls:calls}:{role:'assistant',content:'Fixture finished with recorded answers.'}}],usage:{prompt_tokens:30,completion_tokens:10,total_tokens:40}}))};if(goal==='draft-before-question'&&count===0)releaseQuestion=send;else send()
})
await mkdir(workspace,{recursive:true});await writeFile(join(workspace,'note.txt'),'question evidence\n');await new Promise(r=>provider.listen(0,'127.0.0.1',r))
const config=join(workspace,'config.json')
await writeFile(config,JSON.stringify({baseUrl:'http://127.0.0.1:'+provider.address().port,model:'questions-fixture',apiKeyEnv:'JHARNESS_QUESTION_KEY',maxSteps:6,durationSeconds:240,requestTimeoutSeconds:30,ecosystem:{web:{port:0}}}))
async function start(){base=undefined;output='';host=spawn(java,['-Dfile.encoding=UTF-8','-jar',jar,'--workspace',workspace,'--config',config,'--require-read','note.txt','serve'],{cwd:root,env:{...process.env,JHARNESS_WEB_TOKEN:token,JHARNESS_QUESTION_KEY:secret},stdio:['pipe','pipe','pipe'],windowsHide:true});host.stdout.on('data',d=>{output+=d;base=output.match(/WEB (http:[/][/]127[.]0[.]0[.]1:[0-9]+)/)?.[1]});host.stderr.on('data',d=>output+=d);await until(()=>base||host.exitCode!==null);assert.ok(base,output)}
const stoppedOrigins=new Set(),navigationOrigins=new Set(),transportDiagnostics=[]
async function reloadPage(){navigationOrigins.add(base);try{await page.reload();await page.locator('.connection-state').filter({hasText:'本机服务已连接'}).waitFor({state:'attached'})}finally{navigationOrigins.delete(base)}}
async function stop(){if(host&&host.exitCode===null&&host.signalCode===null){stoppedOrigins.add(base);host.stdin.end(':quit\n');await until(()=>host.exitCode!==null);assert.equal(host.exitCode,0)}}
async function api(path,body){const r=await context.request.fetch(base+path,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+token,...(body===undefined?{}:{'Content-Type':'application/json'})},...(body===undefined?{}:{data:body})});return{status:r.status(),body:await r.json()}}
async function create(goal){await page.locator('.connection-state').filter({hasText:'本机服务已连接'}).waitFor({state:'attached'});const r=await api('/v1/tasks',{profile:'default',goal,requestId:randomUUID()});assert.equal(r.status,202,JSON.stringify(r.body));const id=r.body.taskId;await page.evaluate(id=>sessionStorage.setItem('jh-selected',id),id);await reloadPage();await page.locator('.connection-state').filter({hasText:'本机服务已连接'}).waitFor({state:'attached'});return id}
async function pending(id,number=1){let rows;await until(async()=>{rows=(await api('/v1/tasks/'+id+'/questions')).body.questions;return rows?.length>=number&&rows[number-1].state==='PENDING'});return rows[number-1]}
async function settled(id){await until(async()=>(await api('/v1/tasks/'+id)).body.phase==='FINISHED')}
const card=()=>page.getByLabel('待回答问题',{exact:true})
const submit=()=>page.getByRole('button',{name:'提交回答',exact:true})
async function shot(name){const file=join(out,'M10.5.5-'+name+'.png');await page.screenshot({path:file,fullPage:true});shots.push({path:file.slice(root.length+1).replaceAll('\\','/'),sha256:createHash('sha256').update(await readFile(file)).digest('hex')})}
function body(q,id,selected,text='',skip=false){return {questionId:q.questionId,sessionId:q.sessionId,turnId:q.turnId,callId:q.callId,requestId:id,selected,text,skip}}
async function answer(q,data){return api('/v1/tasks/'+q.sessionId+'/questions/'+q.questionId+'/answer',data)}
try{
 await start();browser=await chromium.launch(process.env.JHARNESS_BROWSER_CHANNEL?{channel:process.env.JHARNESS_BROWSER_CHANNEL}:{});context=await browser.newContext({viewport:{width:1440,height:1000}});page=await context.newPage();page.on('pageerror',e=>{if(stoppedHostDiagnostic(e.message,new Set([...stoppedOrigins,...navigationOrigins])))transportDiagnostics.push(e.message);else errors.push(String(e))});page.on('request',r=>{if(r.method()==='POST'&&r.url().endsWith('/answer'))posts.push(r.postDataJSON())})
 await page.goto(base)
 const id=await create('flow');const q1=await pending(id);await card().waitFor();await card().getByRole('radio',{name:/First choice/}).waitFor()
 check('real SDK ask_user exposes preselected recommendation without submitting',await card().getByRole('radio',{name:/First choice/}).isChecked()&&wire.filter(w=>w.goal==='flow').length===1&&(await api('/v1/tasks/'+id+'/questions')).body.questions[0].state==='PENDING')
 const wrong={...body(q1,'wrong',['a']),sessionId:'different-session'};check('cross-session association is rejected',(await answer(q1,wrong)).status===409)
 check('invalid choice rejected without settling',(await answer(q1,body(q1,'invalid',['missing']))).status===400)
 let releaseDraft, draftCalls=0
 await page.route('**/questions/*/draft',async route=>{draftCalls++;if(draftCalls===1){releaseDraft=()=>route.continue();return}await route.continue()})
 await card().getByRole('radio',{name:/Second choice/}).check();await card().getByLabel('问题文字回答',{exact:true}).fill('Older draft')
 await until(()=>!!releaseDraft);await card().getByLabel('问题文字回答',{exact:true}).fill('Keep separate approval');await wait(600)
 check('draft writes serialize while prior save is delayed',draftCalls===1);await releaseDraft()

 await page.getByText('草稿已保存到当前宿主内存，刷新可恢复',{exact:true}).waitFor();await page.unroute('**/questions/*/draft');await reloadPage();await card().waitFor()
 check('refresh restores scoped choice and text draft',await card().getByRole('radio',{name:/Second choice/}).isChecked()&&await card().getByLabel('问题文字回答',{exact:true}).inputValue()==='Keep separate approval')
 await submit().evaluate(e=>{e.click();e.click()});await page.getByRole('button',{name:'批准本次操作',exact:true}).waitFor()
 check('answer does not approve queued write tool',!(await readFile(join(workspace,'note.txt'),'utf8')).includes('requires separate approval')&&(await api('/v1/tasks/'+id+'/approvals')).body.approvals.length===1)
 await page.getByRole('button',{name:'拒绝',exact:true}).click()
 const q2=await pending(id,2);await card().getByRole('checkbox',{name:/First choice/}).waitFor();await card().getByRole('checkbox',{name:/Second choice/}).check();await submit().click()
 const q3=await pending(id,3);await card().getByLabel('问题文字回答',{exact:true}).fill('Only documented facts');await submit().click();await settled(id)
 const rows=(await api('/v1/tasks/'+id+'/questions')).body.questions
 const older=(await api('/v1/tasks/'+id+'/questions?offset=1')).body
 check('question history uses bounded explicit pages',older.questions.length===2&&older.total===3&&older.questions.every(q=>q.questionId!==q3.questionId))
 check('invalid question page offset is rejected',(await api('/v1/tasks/'+id+'/questions?offset=65')).status===400)
 check('single multi and free text answers reach next model requests',rows.every(q=>q.state==='ANSWERED')&&wire.filter(w=>w.goal==='flow').at(-1).body.messages.some(m=>m.role==='tool'&&m.content.includes('Only documented facts'))&&rows[1].selected.length===2)
 check('double click submits one durable answer',posts.filter(p=>p.questionId===q1.questionId).length===1)
 const receipt=posts.find(p=>p.questionId===q1.questionId);check('same request and answer is idempotent after SDK closes',(await answer(q1,receipt)).status===200)
 check('different answer after settlement conflicts',(await answer(q1,{...receipt,text:'different'})).status===409)
 const skipId=await create('skip');await pending(skipId);await card().waitFor();await page.getByRole('button',{name:'跳过问题',exact:true}).click();await settled(skipId)
 check('skip keeps recommendation unsubmitted',(await api('/v1/tasks/'+skipId+'/questions')).body.questions[0].state==='SKIPPED')
 const timeoutId=await create('timeout');await settled(timeoutId);const timed=(await api('/v1/tasks/'+timeoutId+'/questions')).body.questions[0]
 check('timeout records no selected answer and rejects late response',timed.state==='TIMED_OUT'&&timed.selected===undefined&&(await answer(timed,body(timed,'late',['a']))).status===409)
 const privateId=await create('private');await pending(privateId);await card().getByLabel('问题文字回答',{exact:true}).fill(secret)
 await page.getByText('草稿已保存到当前宿主内存，刷新可恢复',{exact:true}).waitFor();await reloadPage();await card().waitFor()
 check('draft sanitization and refresh do not store key in browser',!(await page.evaluate(()=>JSON.stringify({localStorage,sessionStorage}))).includes(secret)&&await card().getByLabel('问题文字回答',{exact:true}).inputValue()==='[REDACTED]')
 await submit().click();await settled(privateId)
 check('next request contains only redacted answer',wire.filter(w=>w.goal==='private').every(w=>!JSON.stringify(w.body).includes(secret)))
 const lostId=await create('lost');const lost=await pending(lostId);await card().getByLabel('问题文字回答',{exact:true}).fill('One accepted answer')
 await page.route('**/questions/*/answer',async route=>{await route.fetch();await route.abort('failed')});const before=posts.length;await submit().click();await settled(lostId);await page.unroute('**/questions/*/answer');await reloadPage()
 check('lost response reconciles with GET without repost',posts.length===before+1&&(await api('/v1/tasks/'+lostId+'/questions')).body.questions[0].state==='ANSWERED')
 const cancelId=await create('cancel');await pending(cancelId);await card().waitFor();await page.getByRole('button',{name:'取消任务',exact:true}).click();await settled(cancelId)
 check('cancel resolves wait and does not call model again',(await api('/v1/tasks/'+cancelId+'/questions')).body.questions[0].state==='CANCELLED'&&wire.filter(w=>w.goal==='cancel').length===1)
 const screenId=await create('cancel');await pending(screenId);await card().waitFor();await shot('questions-desktop');await page.setViewportSize({width:390,height:844});await card().scrollIntoViewIfNeeded();await shot('questions-mobile')
 check('question controls fit mobile viewport',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
 await page.getByRole('button',{name:'切换深色主题',exact:true}).click();await shot('questions-dark');check('radio choices are keyboard focusable',await card().getByRole('radio').first().evaluate(e=>!e.disabled))
 await page.getByRole('button',{name:'跳过问题',exact:true}).scrollIntoViewIfNeeded();await page.getByRole('button',{name:'跳过问题',exact:true}).click();await settled(screenId);check('mobile skip action is reachable and executable',(await api('/v1/tasks/'+screenId+'/questions')).body.questions[0].state==='SKIPPED');await page.setViewportSize({width:1440,height:1000})
 const draftId=await create('draft-before-question');await until(()=>!!releaseQuestion);await page.getByLabel('后续消息',{exact:true}).fill('Preserve this unsent continuation');releaseQuestion();await pending(draftId);await card().waitFor();await submit().click();await settled(draftId);await page.getByLabel('后续消息',{exact:true}).waitFor();check('pending question preserves already edited continuation draft',await page.getByLabel('后续消息',{exact:true}).inputValue()==='Preserve this unsent continuation');await page.getByRole('button',{name:'丢弃草稿',exact:true}).click()
 // A crash must bypass the POSIX SIGTERM shutdown hook, which settles cancellation.
 const interruptedId=await create('restart');await pending(interruptedId);const requestCount=wire.length;stoppedOrigins.add(base);host.kill('SIGKILL');await until(()=>host.exitCode!==null||host.signalCode!==null);await start()
 const interrupted=(await api('/v1/tasks/'+interruptedId+'/questions')).body.questions[0]
 check('host restart marks unfinished question interrupted without replay',interrupted.state==='INTERRUPTED'&&wire.length===requestCount)
 check('interrupted question rejects late answers',(await answer(interrupted,body(interrupted,'interrupted',['a']))).status===409)
 check('all provider traffic is authenticated loopback',wire.every(w=>w.authorized))
 if(errors.length)console.log('BROWSER_DIAGNOSTIC',JSON.stringify(errors).replaceAll(secret,'[REDACTED]').replaceAll(token,'[REDACTED]'))
 check('no browser runtime exceptions',errors.length===0)
 result={status:'PASS',capturedAt:new Date().toISOString(),checks,screenshots:shots,transportDiagnostics,paidRequests:0,modelRequests:wire.length,jarSha256:createHash('sha256').update(await readFile(jar)).digest('hex'),browser:browser.version()}
}finally{if(browser)await browser.close();await stop();await new Promise(r=>provider.close(r))}
await writeFile(join(out,'M10.5.5-questions-results.json'),JSON.stringify({...result,hostStopped:host.exitCode===0},null,2))
