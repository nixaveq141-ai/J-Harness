import { browserType as chromium, stoppedHostDiagnostic } from './runtime.mjs'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { mkdir, writeFile, readFile, readdir } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { createHash, randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'

const stoppedOrigins=new Set(),transportDiagnostics=[]
const root = resolve(import.meta.dirname, '../..'), out = join(root,'.build-cache'), workspace = join(out,`M10.5.4-models-${Date.now()}`)
const jar = join(root,'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'), java = join(process.env.JAVA_HOME,process.platform==='win32'?'bin/java.exe':'bin/java')
const token = 'models_browser_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ', key = `synthetic-model-${randomUUID()}`, checks = [], wire = [], screenshots = [], errors = []
let host, base, output = '', browser, context, page, hold, result
const wait = ms => new Promise(r => setTimeout(r,ms))
async function until(test, timeout=30000) { const begin=Date.now(); while(Date.now()-begin<timeout) { if(await test()) return; await wait(70) } throw new Error('Model fixture timeout') }
function check(name,value=true) { assert.ok(value,name); checks.push({name,passed:true}); console.log('PASS',name) }
const frame = value => `event: ${value.type}
data: ${JSON.stringify(value)}

`
function sse(protocol, response) {
  if(protocol==='responses') {
    let sequence=0,text=frame({type:'response.created',sequence_number:sequence++,response:{id:response.id}})
    response.output.forEach((item,output_index)=>{
      text+=frame({type:'response.output_item.added',sequence_number:sequence++,output_index,item})
      text+=frame({type:'response.output_item.done',sequence_number:sequence++,output_index,item})
    })
    return text+frame({type:'response.completed',sequence_number:sequence++,response})
  }
  if(protocol==='anthropic') {
    let text=frame({type:'message_start',message:{...response,content:[],stop_reason:null,usage:{input_tokens:10,output_tokens:0}}})
    response.content.forEach((block,index)=>{
      text+=frame({type:'content_block_start',index,content_block:block.type==='tool_use'?{...block,input:{}}:{type:'text',text:''}})
      text+=frame({type:'content_block_delta',index,delta:block.type==='tool_use'?{type:'input_json_delta',partial_json:JSON.stringify(block.input)}:{type:'text_delta',text:block.text}})
      text+=frame({type:'content_block_stop',index})
    })
    return text+frame({type:'message_delta',delta:{stop_reason:response.stop_reason},usage:{output_tokens:4}})+frame({type:'message_stop'})
  }
  const message=response.choices[0].message, finish=response.choices[0].finish_reason
  const delta=message.tool_calls?{tool_calls:message.tool_calls.map((c,index)=>({...c,index}))}:message
  return `data: ${JSON.stringify({choices:[{index:0,delta}]})}

data: ${JSON.stringify({choices:[{index:0,delta:{},finish_reason:finish}],usage:response.usage})}

data: [DONE]

`
}
const provider=createServer(async(req,res)=>{
  let raw=''; for await(const c of req) raw+=c
  const body=JSON.parse(raw), protocol=req.url.endsWith('/responses')?'responses':req.url.endsWith('/messages')?'anthropic':'chat-completions', n=wire.length
  const input=body.input??body.messages, lastUser=input.findLastIndex(m=>m.role==='user'&&(typeof m.content==='string'||m.content?.some?.(b=>b.type==='text')))
  const m=input[lastUser], goal=typeof m?.content==='string'?m.content:m?.content?.find(b=>b.type==='text')?.text??''
  const done=protocol==='responses'?input.slice(lastUser+1).some(m=>m.type==='function_call_output'):protocol==='anthropic'?input.at(-1)?.content?.some(b=>b.type==='tool_result'):input.slice(lastUser+1).some(m=>m.role==='tool')
  wire.push({body,protocol,path:req.url,authenticated:(protocol==='anthropic'?req.headers['x-api-key']:req.headers.authorization) === (protocol==='anthropic'?key:`Bearer ${key}`),goal})
  const send=()=>{
    if(res.destroyed) return
    const tool=!done&&(body.tools?.length??0)>0, id=`call-${n}`, usage={input_tokens:10,output_tokens:4}
    let response
    if(protocol==='responses') response={id:`r${n}`,model:body.model,status:'completed',output:tool?[{type:'reasoning',id:`reason${n}`,encrypted_content:`opaque-${n}`,summary:[]},{type:'function_call',id:`fc${n}`,call_id:id,name:'read_file',arguments:'{"path":"note.txt"}'}]:[{type:'message',id:`m${n}`,role:'assistant',content:[{type:'output_text',text:'Verified fixture read.'}]}],usage}
    else if(protocol==='anthropic') response={id:`a${n}`,type:'message',role:'assistant',model:body.model,stop_reason:tool?'tool_use':'end_turn',content:tool?[{type:'tool_use',id,name:'read_file',input:{path:'note.txt'}}]:[{type:'text',text:'Verified fixture read.'}],usage}
    else response={choices:[{index:0,finish_reason:tool?'tool_calls':'stop',message:tool?{role:'assistant',content:null,tool_calls:[{id,type:'function',function:{name:'read_file',arguments:'{"path":"note.txt"}'}}]}:{role:'assistant',content:'Verified fixture read.'}}],usage:{prompt_tokens:10,completion_tokens:4,total_tokens:14}}
    res.writeHead(200,{'Content-Type':body.stream?'text/event-stream':'application/json'}); res.end(body.stream?sse(protocol,response):JSON.stringify(response))
  }
  if(goal==='slow-first'&&!done) hold=send; else send()
})
await mkdir(workspace,{recursive:true}); await writeFile(join(workspace,'note.txt'),'fixture evidence\n')
await new Promise(r=>provider.listen(0,'127.0.0.1',r))
const config=join(workspace,'config.json'), endpoint=`http://127.0.0.1:${provider.address().port}/v1`
await writeFile(config,JSON.stringify({baseUrl:endpoint,model:'model-a',apiKeyEnv:'JHARNESS_MODELS_KEY',maxSteps:3,durationSeconds:90,requestTimeoutSeconds:80,ecosystem:{web:{port:0}}}))
async function start() { base=undefined;output='';host=spawn(java,['-Dfile.encoding=UTF-8','-jar',jar,'--workspace',workspace,'--config',config,'--require-read','note.txt','serve'],{cwd:root,env:{...process.env,JHARNESS_WEB_TOKEN:token,JHARNESS_MODELS_KEY:key},stdio:['pipe','pipe','pipe'],windowsHide:true});host.stdout.on('data',d=>{output+=d;base=output.match(/WEB (http:[/][/]127[.]0[.]0[.]1:[0-9]+)/)?.[1]});host.stderr.on('data',d=>output+=d);await until(()=>base||host.exitCode!==null);assert.ok(base,output) }
async function stop() { if(host&&host.exitCode===null) { stoppedOrigins.add(base);host.stdin.end(':quit\n'); await until(()=>host.exitCode!==null);assert.equal(host.exitCode,0) } }
async function api(path,body) { const r=await context.request.fetch(base+path,{method:body===undefined?'GET':'POST',headers:{Authorization:`Bearer ${token}`,...(body===undefined?{}:{'Content-Type':'application/json'})},...(body===undefined?{}:{data:body})});return {status:r.status(),body:await r.json()} }
async function save(change) { const current=(await api('/v1/settings')).body;const r=await api('/v1/settings',{revision:current.revision,models:current.models,selected:current.selected,extensions:current.extensions,policy:current.policy,apiKeys:{},...change});assert.equal(r.status,200,JSON.stringify(r.body));return r.body }
async function settled(id,model) { await until(async()=>{const t=(await api(`/v1/tasks/${id}`)).body;return t.phase==='FINISHED'&&t.modelName===model});const t=(await api(`/v1/tasks/${id}`)).body;assert.equal(t.status,'COMPLETED',JSON.stringify(t)) }
async function events(id) { let after='',rows=[];do{const p=(await api(`/v1/tasks/${id}/events?limit=100${after?'&after='+after:''}`)).body;rows.push(...p.events);after=p.more?p.next:''}while(after);return rows }
async function shot(name) { const file=join(out,`M10.5.4-${name}.png`);await page.screenshot({path:file,fullPage:true});screenshots.push({path:file.slice(root.length+1).replaceAll('\\','/'),sha256:createHash('sha256').update(await readFile(file)).digest('hex')}) }
try {
  await start();browser=await chromium.launch(process.env.JHARNESS_BROWSER_CHANNEL?{channel:process.env.JHARNESS_BROWSER_CHANNEL}:{});context=await browser.newContext({viewport:{width:1440,height:1000}});page=await context.newPage();page.on('pageerror',e=>{if(stoppedHostDiagnostic(e.message,stoppedOrigins))transportDiagnostics.push(e.message);else errors.push(e.message)})
  // Four rounds retain schemas/history; R1 counts escaped prepared bytes and framing before send.
  // Reserve enough for that complete input while keeping the real guard and finite fee cap enabled.
  const initial=(await api('/v1/settings')).body, a={...initial.models[0],id:'a',label:'Chat connection',model:'model-a',protocol:'chat-completions',contextTokens:65536,maxOutputTokens:256,requestTimeoutSeconds:80,toolsSupported:true,streamSupported:true,streaming:false,pricing:{maxCost:'1',currency:'USD',inputPerMillion:'1',outputPerMillion:'2',inputTokenCeiling:20000}}
  const b={...a,id:'b',label:'Responses connection',model:'model-b',protocol:'responses',streaming:true,disableThinking:false,reasoningEffort:'low'}, c={...a,id:'c',label:'Anthropic connection',model:'model-c',protocol:'anthropic',disableThinking:true}
  await save({models:[a,b,c],selected:'a'});await page.goto(base);await page.locator('.connection-state').filter({hasText:'本机服务已连接'}).waitFor()
  await page.getByLabel('模型',{exact:true}).selectOption('a');check('input exposes three trusted model connections',await page.getByLabel('模型',{exact:true}).locator('option').count()===3)
  const count=wire.length;const local=await api('/v1/models/check',{modelId:'b'});check('configuration check performs no provider request',local.body.networkChecked===false&&local.body.requestSent===false&&wire.length===count)
  await page.getByLabel('任务目标',{exact:true}).fill('slow-first');await page.getByRole('button',{name:'创建任务',exact:true}).click();await until(()=>!!hold)
  const list=(await api('/v1/tasks?view=all&limit=32')).body;const id=list.tasks[0].taskId
  await page.getByLabel('下一条消息模型',{exact:true}).selectOption('b');await page.getByLabel('后续消息',{exact:true}).fill('second-responses');await page.getByRole('button',{name:'发送后续消息',exact:true}).click();await until(async()=>((await api(`/v1/tasks/${id}/turns`)).body.submissions.length===1))
  check('next model selection leaves active request unchanged',wire.length===1&&wire[0].body.model==='model-a')
  const busy=(await api('/v1/settings')).body;check('settings mutation blocked during active round',(await api('/v1/settings',{revision:busy.revision,models:busy.models,selected:'c',extensions:busy.extensions,policy:busy.policy,apiKeys:{}})).status===409)
  hold();await settled(id,'model-b');check('queued Responses round uses captured route and SSE',wire.filter(w=>w.goal==='second-responses').every(w=>w.body.model==='model-b'&&w.protocol==='responses'&&w.body.stream===true))
  const bRequests=wire.filter(w=>w.goal==='second-responses');check('Responses replays encrypted state with linked tool output',bRequests.at(-1).body.input.some(i=>i.type==='reasoning'&&i.encrypted_content)&&bRequests.at(-1).body.input.some(i=>i.type==='function_call_output'))
  check('model selection does not mutate saved default',(await api('/v1/settings')).body.selected==='a')
  const history=await events(id);check('prepared envelopes persist protocol model and cost bound',history.filter(e=>e.type==='request.context').some(e=>e.payload.parameters?.protocol==='responses')&&history.some(e=>e.type==='budget.reserved'))
  await save({selected:'c'});await page.getByLabel('下一条消息模型',{exact:true}).selectOption('c');await page.getByLabel('后续消息',{exact:true}).fill('third-anthropic');await page.getByRole('button',{name:'发送后续消息',exact:true}).click();await settled(id,'model-c')
  check('Anthropic round maps tools system and linked result',wire.filter(w=>w.goal==='third-anthropic').some(w=>w.body.messages.some(m=>m.content.some(x=>x.type==='tool_result')))&&wire.filter(w=>w.protocol==='anthropic').every(w=>!w.body.input&&!w.body.stream_options))
  await page.getByText('当前轮模型：model-c · anthropic',{exact:true}).waitFor();check('current round and next message model have separate labels')
  const settings=(await api('/v1/settings')).body;const changed=settings.models.map(m=>m.id==='c'?{...m,streaming:true}:m);await save({models:changed});await page.getByLabel('后续消息',{exact:true}).fill('fourth-anthropic-sse');await page.getByRole('button',{name:'发送后续消息',exact:true}).click();await until(()=>wire.some(w=>w.goal==='fourth-anthropic-sse'));await settled(id,'model-c')
  check('Anthropic SSE round executes only validated tool arguments',wire.filter(w=>w.goal==='fourth-anthropic-sse').length===2)
  check('all protocol calls authenticate only through headers',wire.every(w=>w.authenticated&&!JSON.stringify(w.body).includes(key)))
  await page.locator('#main-content').getByText('已完成',{exact:true}).waitFor();await page.getByText('消息队列与轮次（本页 3 条）',{exact:true}).waitFor();await shot('models-desktop');await page.setViewportSize({width:390,height:844});await shot('models-mobile');check('model controls fit mobile viewport',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  await page.getByRole('button',{name:'工作台设置',exact:true}).click();await page.getByRole('button',{name:/Responses connection/}).click();await page.getByLabel('API 协议',{exact:true}).selectOption('anthropic')
  const pricing=page.locator('details').filter({has:page.locator('summary').filter({hasText:'环境变量与费用限制'})});await pricing.locator('summary').click();check('protocol switch retains fee cap and clears inherited prices',await page.getByLabel('启用费用上限',{exact:true}).isChecked()&&await page.getByLabel('每百万输入 token 价格',{exact:true}).inputValue()==='')
  await page.getByRole('button',{name:'保存设置',exact:true}).click();await page.getByRole('alert').waitFor();check('missing verified prices rejects save',(await api('/v1/settings')).body.models.find(m=>m.id==='b').protocol==='responses')
  await page.getByRole('button',{name:'放弃修改并重新读取',exact:true}).click();await page.getByRole('button',{name:/Responses connection/}).click();const before=wire.length;let releaseCheck;await page.route('**/v1/models/check', async route=>{const response=await route.fetch();releaseCheck=()=>route.fulfill({response})});await page.getByRole('button',{name:'检查已保存配置',exact:true}).click();await until(()=>!!releaseCheck);check('pending check locks connection navigation',await page.getByRole('button',{name:/Chat connection/}).isDisabled());await releaseCheck();await page.getByText('Responses connection：本地配置有效、密钥可读取。未连接服务，未发送模型请求。',{exact:true}).waitFor();check('settings check is explicitly local and free of model calls',wire.length===before)
  await page.getByLabel('API 协议',{exact:true}).scrollIntoViewIfNeeded();await shot('model-settings-mobile');check('no unexpected browser runtime errors',errors.length===0)
  await stop();await start();const restored=(await api(`/v1/tasks/${id}`)).body;check('restart retains last executing model metadata',restored.modelName==='model-c'&&restored.modelProtocol==='anthropic')
  const catalogDir=join(workspace,'.jharness/web');let catalogText='';for(const f of await readdir(catalogDir)) if(f.endsWith('.jsonl'))catalogText+=await readFile(join(catalogDir,f),'utf8');check('catalog upgraded to version four',catalogText.includes('"version":4'))
  const taskDirs=(await readdir(catalogDir)).filter(n=>n.startsWith('tasks-'));let facts='';for(const dir of taskDirs)for(const file of await readdir(join(catalogDir,dir)))if(file.endsWith('.jsonl'))facts+=await readFile(join(catalogDir,dir,file),'utf8')
  check('accepted queued inputs retain immutable nonsecret snapshot',facts.includes('"settingsRevision"')&&facts.includes('"maxOutputTokens":256')&&facts.includes('"modelRoute":"b"')&&!facts.includes(key))
  check('saved settings and session events contain no API key',!(await readFile(join(workspace,'.jharness/settings/workbench.json'),'utf8')).includes(key)&&!JSON.stringify(history).includes(key))
  result={status:'PASS',date:new Date().toISOString(),checks,screenshots,transportDiagnostics,browser:browser.version(),paidRequests:0,modelRequests:wire.length,jarSha256:createHash('sha256').update(await readFile(jar)).digest('hex')}
} finally { if(browser)await browser.close();await stop();await new Promise(r=>provider.close(r)) }
await writeFile(join(out,'M10.5.4-models-results.json'),JSON.stringify({...result,hostStopped:host.exitCode===0},null,2))
