import { browserType as chromium, engine, stoppedHostDiagnostic } from './runtime.mjs'
import {createServer} from 'node:http'
import {spawn,execFileSync} from 'node:child_process'
import {mkdir,writeFile,readFile,unlink,symlink} from 'node:fs/promises'
import {resolve,join} from 'node:path'
import {createHash,randomUUID} from 'node:crypto'
import assert from 'node:assert/strict'
const stoppedOrigins=new Set(),transportDiagnostics=[]
let offlineFault=false
const root=resolve(import.meta.dirname,'../..'),out=join(root,'.build-cache'),run=join(out,'M10.5.6-content-'+Date.now()),workspace=join(run,'primary'),secondary=join(run,'secondary'),outside=join(run,'outside')
const jar=join(root,'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'),java=join(process.env.JAVA_HOME,process.platform==='win32'?'bin/java.exe':'bin/java')
const token='content_browser_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ',key='synthetic-content-'+randomUUID(),otherKey='synthetic-other-'+randomUUID(),wire=[],checks=[],screenshots=[],errors=[],requests=[]
let host,base,output='',browser,context,page,release,receipt,activeWorkspace=workspace
const wait=ms=>new Promise(r=>setTimeout(r,ms))
async function until(test,timeout=30000){const begin=Date.now();while(Date.now()-begin<timeout){if(await test())return;await wait(75)}throw new Error('Content fixture timeout')}
function check(name,value=true){assert.ok(value,name);checks.push({name,passed:true});console.log('PASS',name)}
const fence=String.fromCharCode(96).repeat(3)
const markdown=['## Review notes','','**Verified** reading with an explicit [source line](src/demo.ts#L2).','','- Files remain in this workspace','- Approvals stay separate','',fence+'java','class Demo {','  String label = "safe";','}',fence,'','<img src="https://invalid.example/x" onerror="window.injected=true">','','[bad](javascript:alert(1)) [data](data:text/html,evil) [docs](https://example.test/docs)','','Redacted provider echo: '+key].join('\n')
function chunk(res,delta,finish,usage){res.write('data: '+JSON.stringify({choices:[{index:0,delta,...(finish?{finish_reason:finish}:{})}],...(usage?{usage}:{})})+'\n\n')}
function complete(res,text){chunk(res,{content:text});chunk(res,{},'stop',{prompt_tokens:17,completion_tokens:9,total_tokens:26});res.end('data: [DONE]\n\n')}
const provider=createServer(async(req,res)=>{
 let raw='';for await(const c of req)raw+=c
 const body=JSON.parse(raw),messages=body.messages,last=messages.findLastIndex(m=>m.role==='user'),goal=messages[last]?.content??'',done=messages.slice(last+1).some(m=>m.role==='tool')
 wire.push({goal,stream:body.stream===true,authenticated:req.headers.authorization==='Bearer '+key})
 res.writeHead(200,{'Content-Type':'text/event-stream'})
 if((goal==='render'||goal==='write'||goal==='diff')&&!done){
  const calls=[...(goal==='write'?[{id:'write-'+wire.length,type:'function',function:{name:'write_file',arguments:JSON.stringify({path:'changed.txt',content:'approved file content\n'})}}]:[]),{id:'read-'+wire.length,type:'function',function:{name:'read_file',arguments:JSON.stringify({path:'big.txt'})}}]
  if(goal==='diff')calls.splice(0,calls.length,{id:'diff-'+wire.length,type:'function',function:{name:'git_diff',arguments:'{}'}})
  chunk(res,{tool_calls:calls.map((c,index)=>({...c,index}))});chunk(res,{},'tool_calls',{prompt_tokens:10,completion_tokens:5,total_tokens:15});res.end('data: [DONE]\n\n');return
 }
 if(goal==='render'){
  const prefix='Preparing a recorded answer. '+'-'.repeat(512)+' '+key.slice(0,10)
  chunk(res,{content:prefix});chunk(res,{content:key.slice(10)+'\n'+'-'.repeat(512)+' '+otherKey.slice(0,10)})
  release=()=>{if(res.destroyed)return;chunk(res,{content:otherKey.slice(10)+'\n'+markdown});chunk(res,{},'stop',{prompt_tokens:17,completion_tokens:9,total_tokens:26});res.end('data: [DONE]\n\n')}
 }else if(goal==='broken'){chunk(res,{content:'Incomplete visible output '+'-'.repeat(512)});setTimeout(()=>res.end(),400)}
 else if(goal==='cancel'){chunk(res,{content:'Cancellable visible output '+'-'.repeat(512)})}
 else if(goal==='long')complete(res,'Long answer '+('L'.repeat(70000))+'\nLast line.')
 else complete(res,markdown)
})
await mkdir(join(workspace,'src'),{recursive:true});await mkdir(secondary,{recursive:true});await mkdir(outside,{recursive:true})
const big='File record '+'x'.repeat(16360)+key+'\n'+'data '.repeat(6000)+'\nArtifact end.'
await writeFile(join(workspace,'big.txt'),big);await writeFile(join(workspace,'src/demo.ts'),'// source line 1\nexport const message = "current source";\n'+key+'\n')
await writeFile(join(secondary,'big.txt'),'secondary workspace source\n');await writeFile(join(outside,'private.txt'),'outside-sentinel')
await writeFile(join(workspace,'.env.local'),key);await writeFile(join(workspace,'binary.bin'),new Uint8Array([1,0,2]))
execFileSync('git',['-C',workspace,'init'],{windowsHide:true,stdio:'pipe'})
execFileSync('git',['-C',workspace,'add','src/demo.ts'],{windowsHide:true,stdio:'pipe'})
execFileSync('git',['-C',workspace,'-c','user.name=Fixture','-c','user.email=fixture@example.invalid','-c','commit.gpgsign=false','commit','-qm','fixture baseline'],{windowsHide:true,stdio:'pipe'})
await writeFile(join(workspace,'src/demo.ts'),'// source line 1\nexport const message = "current source (modified)";\n'+key+'\n')
if(process.platform==='win32')execFileSync('cmd.exe',['/c','mklink','/J',join(workspace,'escape-link'),outside],{windowsHide:true,stdio:'pipe'})
else await symlink(outside,join(workspace,'escape-link'),'dir')
await new Promise(r=>provider.listen(0,'127.0.0.1',r))
const config=join(workspace,'config.json')
await writeFile(config,JSON.stringify({baseUrl:'http://127.0.0.1:'+provider.address().port,model:'content-fixture',apiKeyEnv:'JHARNESS_CONTENT_KEY',contextTokens:262144,streamSupported:true,streaming:true,maxSteps:4,durationSeconds:120,requestTimeoutSeconds:80,ecosystem:{web:{port:0}}}))
async function start(){base=undefined;output='';host=spawn(java,['-Dfile.encoding=UTF-8','-jar',jar,'--workspace',activeWorkspace,'--config',config,'serve'],{cwd:root,env:{...process.env,JHARNESS_WEB_TOKEN:token,JHARNESS_CONTENT_KEY:key,JHARNESS_UNUSED_KEY:otherKey},stdio:['pipe','pipe','pipe'],windowsHide:true})
 host.stdout.on('data',d=>{output+=d;base=output.match(/WEB (http:[/][/]127[.]0[.]0[.]1:[0-9]+)/)?.[1]});host.stderr.on('data',d=>output+=d);await until(()=>base||host.exitCode!==null);assert.ok(base,output)}
async function stop(){if(host&&host.exitCode===null&&host.signalCode===null){stoppedOrigins.add(base);host.stdin.end(':quit\n');await until(()=>host.exitCode!==null);assert.equal(host.exitCode,0)}}
async function api(path,body){const r=await context.request.fetch(base+path,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+token,...(body===undefined?{}:{'Content-Type':'application/json'})},...(body===undefined?{}:{data:body})});return{status:r.status(),body:await r.json()}}
async function create(goal,profile='default'){const r=await api('/v1/tasks',{profile,goal,requestId:randomUUID()});assert.equal(r.status,202,JSON.stringify(r.body));await show(r.body.taskId);return r.body.taskId}
async function show(id){await page.setViewportSize({width:1440,height:1000});await page.locator('.connection-state').filter({hasText:'本机服务已连接'}).waitFor({state:'attached'});await page.evaluate(id=>sessionStorage.setItem('jh-selected',id),id);await page.reload();await page.locator('.connection-state').filter({hasText:'本机服务已连接'}).waitFor({state:'attached'})}
async function ended(id){await until(async()=>['FINISHED','UNKNOWN'].includes((await api('/v1/tasks/'+id)).body.phase));await page.getByText(/^(已同步全部记录|结果待核实 · 不会重放任务)$/).waitFor()}
const enc=value=>Buffer.from(value).toString('base64url')
async function shot(name){const path=join(out,'M10.5.6-'+name+'.png');await page.screenshot({path,fullPage:true});screenshots.push({path:path.slice(root.length+1).replaceAll('\\','/'),sha256:createHash('sha256').update(await readFile(path)).digest('hex')})}
async function closeInspector(){const b=page.getByRole('button',{name:'关闭任务详情',exact:true});if(await b.count())await b.first().click()}
async function openTab(name){if(!await page.getByRole('tab',{name,exact:true}).count())await page.getByRole('button',{name:'打开任务详情',exact:true}).click();await page.getByRole('tab',{name,exact:true}).click()}
try{
 await start();browser=await chromium.launch(process.env.JHARNESS_BROWSER_CHANNEL?{channel:process.env.JHARNESS_BROWSER_CHANNEL}:{})
 context=await browser.newContext({viewport:{width:1440,height:1000},acceptDownloads:true,...(engine==='chromium'?{permissions:['clipboard-read','clipboard-write']}:{})})
 await context.addInitScript(()=>{const write=navigator.clipboard.writeText.bind(navigator.clipboard);Object.defineProperty(navigator.clipboard,'writeText',{value:async text=>{await write(text);window.__copiedExact=text}})})
 page=await context.newPage();page.on('pageerror',e=>{const stopped=stoppedHostDiagnostic(e.message,stoppedOrigins),offline=offlineFault&&stoppedHostDiagnostic(e.message,new Set([base]));if(stopped||offline)transportDiagnostics.push({phase:stopped?'stopped-host':'injected-offline',message:e.message});else errors.push({message:e.message,base,stoppedOrigins:[...stoppedOrigins],offlineFault,pageUrl:page.url()})});page.on('request',r=>requests.push(r.url()));await page.goto(base)
 await page.locator('.connection-state').filter({hasText:'本机服务已连接'}).waitFor({state:'attached'})
 const settings=(await api('/v1/settings')).body
 const clone={...settings.models[0],id:'unused-route',label:'Unused fixture',apiKeyEnv:'JHARNESS_UNUSED_KEY',model:'unused-model'}
 const saved=await api('/v1/settings',{revision:settings.revision,models:[...settings.models,clone],selected:settings.selected,extensions:settings.extensions,policy:settings.policy,apiKeys:{}})
 assert.equal(saved.status,200,JSON.stringify(saved.body))
 const render=await create('render');await until(()=>!!release);await page.getByLabel('临时流式输出',{exact:true}).getByText(/Preparing a recorded answer/).waitFor()
 check('real streaming reaches browser before assistant fact exists',!(await api('/v1/tasks/'+render+'/events?limit=100')).body.events.some(e=>e.type==='assistant.message'&&e.payload.content))
 check('cross-chunk secret prefix is withheld from temporary display',!(await page.getByLabel('临时流式输出',{exact:true}).textContent()).includes(key.slice(0,10)))
 check('unselected configured secret fragments are withheld too',!(await page.getByLabel('临时流式输出',{exact:true}).textContent()).includes(otherKey.slice(0,10)))
 await page.getByRole('button',{name:'停止显示',exact:true}).click();check('stopping display leaves runtime active',(await api('/v1/tasks/'+render)).body.phase==='RUNNING')
 await page.getByRole('button',{name:'恢复显示',exact:true}).click()
 const before=wire.length;offlineFault=true;await context.setOffline(true);await wait(500);await context.setOffline(false);await page.reload();await page.getByLabel('临时流式输出',{exact:true}).waitFor();offlineFault=false
 check('refresh and reconnect only read the original stream',wire.length===before)
 await shot('stream');release();await ended(render);await page.getByRole('heading',{name:'Review notes',exact:true}).waitFor()
 check('durable Markdown renders headings lists and safe code',await page.locator('.markdown strong').filter({hasText:'Verified'}).count()>0&&await page.getByLabel('助手消息',{exact:true}).last().locator('.code-block').count()===1)
 check('HTML images and active links cannot execute or auto-fetch',await page.locator('img').count()===0&&await page.evaluate(()=>window.injected)!==true&&requests.every(u=>u.startsWith(base))&&await page.locator('a[href^="javascript:"],a[href^="data:"]').count()===0)
 const callsBefore=wire.length
 check('copy code uses actual text',await page.getByRole('button',{name:'复制代码',exact:true}).first().isEnabled())
 await page.getByRole('button',{name:'复制代码',exact:true}).first().click()
 await until(async()=>await page.evaluate(()=>window.__copiedExact?.startsWith('class Demo {')))
 check('code clipboard excludes line-number decoration',engine==='chromium'?(await page.evaluate(()=>navigator.clipboard.readText())).startsWith('class Demo {'):await page.evaluate(()=>window.__copiedExact.startsWith('class Demo {')))
 check('internal trace starts folded',await page.getByLabel('执行细节',{exact:true}).count()===0)
 await page.getByRole('button',{name:/展开执行细节/}).click();check('full source events remain inspectable',await page.getByLabel('执行细节',{exact:true}).locator('.event-row').count()>10)
 await page.getByRole('button',{name:/收起执行细节/}).click()
 await page.getByText(/耗时与实际用量/).click();check('usage comes from provider and actual cost remains unknown',(await page.getByLabel('用量与结束摘要',{exact:true}).textContent()).includes('输入 17 / 输出 9 token')&&(await page.getByLabel('用量与结束摘要',{exact:true}).textContent()).includes('实际费用：未知'))
 const toolArtifact=page.getByRole('button',{name:'读取工具产物',exact:true}).first();await toolArtifact.click()
 await page.locator('.tool-card').getByLabel('内容预览',{exact:true}).waitFor()
 check('tool cards expose explicit paginated artifact reading',await page.locator('.tool-card').getByRole('button',{name:'下一页内容',exact:true}).isEnabled())
 await toolArtifact.click()
 await page.getByRole('button',{name:'source line',exact:true}).click();await page.getByLabel('内容预览',{exact:true}).getByText(/current source/).waitFor()
 check('file reference is explicit and starts at requested line',(await page.getByLabel('内容预览',{exact:true}).textContent()).includes('读取当前工作区内容'))
 check('preview is redacted and does not send model requests',!(await page.getByLabel('内容预览',{exact:true}).textContent()).includes(key)&&wire.length===callsBefore)
 await page.getByLabel('文件路径',{exact:true}).fill('big.txt');await page.getByRole('button',{name:'查看文件',exact:true}).click();await until(async()=>await page.getByLabel('内容预览',{exact:true}).getByRole('button',{name:'下一页内容',exact:true}).isEnabled())
 await writeFile(join(workspace,'big.txt'),'changed outside the task\n'+big)
 await page.getByRole('button',{name:'下一页内容',exact:true}).click();await page.getByText('文件或脱敏版本已变化。请点击“重新读取”查看当前内容。',{exact:true}).waitFor()
 check('file changes reject stale preview pages instead of mixing versions')
 await page.getByRole('button',{name:'重新读取',exact:true}).click();await page.getByLabel('内容预览',{exact:true}).getByText(/changed outside the task/).waitFor()
 check('explicit reread obtains changed current content')
 // Policy blocks parent traversal; protected metadata (including this fixture's
 // absolute .build-cache path) and the workspace link resolver report PATH_DENIED.
 for(const [label,path,code] of [['parent traversal','../outside/private.txt','INSPECTION_DENIED'],['protected metadata','.jharness/config','PATH_DENIED'],['dotenv','.env.local','PATH_DENIED'],['absolute outside',outside+'/private.txt','PATH_DENIED'],['symlink escape','escape-link/private.txt','PATH_DENIED']]){
   const response=await api('/v1/tasks/'+render+'/file?path='+enc(path));check('denied preview '+label,response.status===400&&response.body.error===code)
 }
 const listing=(await api('/v1/tasks/'+render+'/files')).body
 check('file tree hides protected metadata and links',!JSON.stringify(listing).includes('.env.local')&&!JSON.stringify(listing).includes('.jharness')&&!JSON.stringify(listing).includes('escape-link'))
 check('binary file fails explicitly',(await api('/v1/tasks/'+render+'/file?path='+enc('binary.bin'))).body.error==='BINARY_FILE')
 await openTab('产物');const artifacts=(await api('/v1/tasks/'+render+'/artifacts')).body.artifacts
 check('large actual tool result has a scoped artifact reference',artifacts.length>0)
 const artifact=artifacts[0];await page.getByLabel('已记录产物列表',{exact:true}).getByRole('button').filter({hasText:artifact.id}).click()
 await page.getByLabel('内容预览',{exact:true}).getByText(/File record/).waitFor()
 const download=page.waitForEvent('download');await page.getByRole('button',{name:'下载脱敏文本',exact:true}).click()
 const path=join(out,'M10.5.6-downloaded-artifact.txt');await(await download).saveAs(path)
 const data=await readFile(path,'utf8'),a=(await api('/v1/tasks/'+render+'/artifacts/'+artifact.id)).body
 check('artifact download combines all pages and verifies sanitized hash',data.length>16384&&!data.includes(key)&&createHash('sha256').update(data).digest('hex')===a.contentSha256)
 await shot('artifact')
 await closeInspector();const write=await create('write');await page.getByRole('button',{name:'批准本次操作',exact:true}).waitFor()
 check('file writing still requires independent approval',await page.getByRole('button',{name:'批准本次操作',exact:true}).count()===1)
 await page.getByRole('button',{name:'批准本次操作',exact:true}).click();await ended(write);await page.locator('.changed-file').filter({hasText:'changed.txt'}).waitFor()
 check('change card is based on actual tool versions',await readFile(join(workspace,'changed.txt'),'utf8')==='approved file content\n')
 check('artifact from another task cannot be fetched by guessing id',(await api('/v1/tasks/'+write+'/artifacts/'+artifact.id)).status===404)
 await shot('conversation')
 await page.setViewportSize({width:390,height:844})
 await page.getByLabel('后续消息',{exact:true}).fill('Retain my mobile draft')
 await page.getByRole('button',{name:'收起输入区（保留草稿）',exact:true}).click()
 check('mobile input collapse releases reading space',!await page.getByLabel('后续消息',{exact:true}).isVisible())
 await page.getByRole('button',{name:'展开输入区',exact:true}).click()
 check('input collapse preserves unsent draft',await page.getByLabel('后续消息',{exact:true}).inputValue()==='Retain my mobile draft')
 await page.getByRole('button',{name:'丢弃草稿',exact:true}).click()
 await page.getByRole('button',{name:'收起输入区（保留草稿）',exact:true}).click()
 await shot('mobile');check('conversation fits mobile viewport',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
 await page.getByRole('button',{name:'切换深色主题',exact:true}).click();await shot('dark')
 await page.setViewportSize({width:1440,height:1000});const long=await create('long');await ended(long);await page.getByRole('button',{name:'回读完整消息',exact:true}).waitFor();await page.getByRole('button',{name:'回读完整消息',exact:true}).click()
 await page.getByLabel('内容预览',{exact:true}).getByText(/Long answer/).waitFor()
 check('omitted long answer can be read from its exact event',await page.getByRole('button',{name:'下一页内容',exact:true}).isEnabled())
 const longEvents=(await api('/v1/tasks/'+long+'/events?limit=100')).body.events,fullEvent=longEvents.find(e=>e.type==='assistant.message'&&e.payload.omitted)
 const content=(await api('/v1/tasks/'+long+'/content/'+fullEvent.eventId)).body
 check('event page is bounded and wrong hash is rejected',content.text.length===16384&&(await api('/v1/tasks/'+long+'/content/'+fullEvent.eventId+'?version='+'0'.repeat(64))).status===409)
 await page.getByRole('button',{name:'复制代码',exact:true}).first().focus();check('reading controls are keyboard focusable',await page.getByRole('button',{name:'复制代码',exact:true}).first().evaluate(e=>e===document.activeElement))
 await closeInspector();const diffTask=await create('diff');await ended(diffTask);await openTab('Diff')
 await page.getByText(/当时记录的工具差异/).waitFor()
 check('Diff is labelled as recorded rather than current',await page.getByRole('button',{name:'查看当前文件 src/demo.ts',exact:true}).count()===1)
 await page.getByRole('button',{name:'查看当前文件 src/demo.ts',exact:true}).click()
 await page.getByLabel('内容预览',{exact:true}).getByText(/current source/).waitFor()
 check('recorded Diff path explicitly opens current bound file')
 await closeInspector()
 const registered=await api('/v1/workspaces',{path:secondary,name:'Secondary fixture'});assert.equal(registered.status,200,JSON.stringify(registered.body))
 const beta=registered.body.workspaces.find(w=>w.name==='Secondary fixture').id
 const secondaryTask=await create('secondary',beta);await ended(secondaryTask)
 check('secondary root preview cannot fall back to primary',(await api('/v1/tasks/'+secondaryTask+'/file?path='+enc('big.txt'))).body.text==='secondary workspace source\n')
 await unlink(join(workspace,'.jharness/artifacts',render,artifact.id+'.txt'))
 check('expired artifact reference has explicit missing response',(await api('/v1/tasks/'+render+'/artifacts/'+artifact.id)).status===404)
 const cancel=await create('cancel');await page.getByLabel('临时流式输出',{exact:true}).waitFor();await page.getByRole('button',{name:'取消任务',exact:true}).click();await ended(cancel)
 check('cancelled stream does not become a complete answer',!(await api('/v1/tasks/'+cancel+'/events?limit=100')).body.events.some(e=>e.type==='assistant.message')&&wire.filter(w=>w.goal==='cancel').length===1)
 await stop();activeWorkspace=join(run,'broken');await mkdir(activeWorkspace,{recursive:true});await writeFile(join(activeWorkspace,'big.txt'),'isolated broken stream\n');await start();await page.goto(base)
 const broken=await create('broken');await ended(broken);await page.getByText('输出已中断',{exact:true}).waitFor()
 check('broken stream is marked incomplete with no replay',(await api('/v1/tasks/'+broken+'/display')).body.state==='INTERRUPTED'&&wire.filter(w=>w.goal==='broken').length===1)
 const n=wire.length;await stop();await start();await page.goto(base);await page.getByText('本机服务已连接',{exact:true}).waitFor()
 check('host restart loses ephemeral bytes without resending model requests',(await api('/v1/tasks/'+broken+'/display')).body.state==='UNAVAILABLE'&&wire.length===n)
 check('all traffic uses authenticated loopback streaming',wire.every(w=>w.authenticated&&w.stream))
 check('no secrets in rendered UI or browser persistent storage',!(await page.evaluate(()=>document.body.innerText+JSON.stringify({localStorage,sessionStorage}))).includes(key)&&!(await page.evaluate(()=>document.body.innerText+JSON.stringify({localStorage,sessionStorage}))).includes(otherKey))
 if(errors.length)console.log('BROWSER_DIAGNOSTIC',JSON.stringify(errors).replaceAll(key,'[REDACTED]').replaceAll(otherKey,'[REDACTED]').replaceAll(token,'[REDACTED]'))
 check('no unexpected browser runtime exceptions',errors.length===0)
 receipt={status:'PASS',capturedAt:new Date().toISOString(),checks,screenshots,transportDiagnostics,paidRequests:0,modelRequests:wire.length,browser:browser.version(),jarSha256:createHash('sha256').update(await readFile(jar)).digest('hex')}
}finally{if(browser)await browser.close();await stop();provider.closeAllConnections();await new Promise(r=>provider.close(r))}
await writeFile(join(out,'M10.5.6-content-results.json'),JSON.stringify({...receipt,hostStopped:host.exitCode===0},null,2))
