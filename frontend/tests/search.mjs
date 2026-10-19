import { browserType as chromium, stoppedHostDiagnostic } from './runtime.mjs'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { createHash, randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'
const stoppedOrigins=new Set(),transportDiagnostics=[]
const root=resolve(import.meta.dirname,'../..'),out=join(root,'.build-cache'),workspace=join(out,'M10.5.8-search-'+Date.now()),other=workspace+'-other'
const jar=join(root,'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'),java=join(process.env.JAVA_HOME,process.platform==='win32'?'bin/java.exe':'bin/java'),token='search_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ',secret='synthetic-search-'+randomUUID()
const checks=[],screenshots=[],errors=[],wire=[]
let host,base,output='',browser,context,page,result
const wait=ms=>new Promise(r=>setTimeout(r,ms))
async function until(fn,ms=25000){const end=Date.now()+ms;while(Date.now()<end){if(await fn())return;await wait(50)}throw Error('Search fixture timeout')}
function check(name,value=true){assert.ok(value,name);checks.push({name,passed:true});console.log('PASS',name)}
const model=createServer(async(req,res)=>{let raw='';for await(const c of req)raw+=c;wire.push({authorized:req.headers.authorization==='Bearer '+secret,redacted:!raw.includes(secret)});res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({choices:[{index:0,finish_reason:'stop',message:{role:'assistant',content:'Recorded completion.'}}],usage:{prompt_tokens:10,completion_tokens:3,total_tokens:13}}))})
await mkdir(workspace,{recursive:true});await mkdir(other,{recursive:true});await new Promise(r=>model.listen(0,'127.0.0.1',r))
const config=join(workspace,'config.json');await writeFile(config,JSON.stringify({baseUrl:`http://127.0.0.1:${model.address().port}`,model:'search-fixture',maxSteps:2,durationSeconds:20,ecosystem:{web:{port:0}}}))
async function start(){output='';base=undefined;host=spawn(java,['-Dfile.encoding=UTF-8','-jar',jar,'--workspace',workspace,'--config',config,'serve'],{cwd:root,env:{...process.env,JHARNESS_WEB_TOKEN:token,JHARNESS_API_KEY:secret},windowsHide:true,stdio:'pipe'});const receive=d=>{output+=d;base=output.match(/WEB (http:\/\/127\.0\.0\.1:\d+)/)?.[1]};host.stdout.on('data',receive);host.stderr.on('data',receive);await until(()=>base||host.exitCode!==null);assert.ok(base,output)}
async function stop(){if(host?.exitCode===null){stoppedOrigins.add(base);host.stdin.end(':quit\n');await until(()=>host.exitCode!==null);assert.equal(host.exitCode,0)}}
async function api(path,data,auth=true,headers={}){const r=await context.request.fetch(base+path,{method:data===undefined?'GET':'POST',headers:{...(auth?{Authorization:'Bearer '+token}:{}),...(data===undefined?{}:{'Content-Type':'application/json'}),...headers},...(data===undefined?{}:{data})});return{status:r.status(),body:await r.json()}}
const query=(patch={})=>({text:'alpha beta',mode:'tokens',profile:'',view:'active',sessionId:'',cursor:'',limit:20,rebuild:false,...patch})
async function create(profile,goal,id){const r=await api('/v1/tasks',{profile,goal,requestId:id});assert.equal(r.status,202);const task=r.body;await until(async()=>(await api('/v1/tasks/'+task.taskId)).body.phase==='FINISHED');return(await api('/v1/tasks/'+task.taskId)).body}
async function archive(task){const r=await api('/v1/tasks/'+task.taskId+'/metadata',{revision:task.revision,name:task.name||'Archived source',pinned:false,archived:true});assert.equal(r.status,200);return r.body}
async function shot(name){const path=join(out,'M10.5.8-search-'+name+'.png');await page.screenshot({path,fullPage:true});screenshots.push({path:path.replace(root+'\\',''),sha256:createHash('sha256').update(await readFile(path)).digest('hex')})}
try{
  await start();browser=await chromium.launch(process.env.JHARNESS_BROWSER_CHANNEL?{channel:process.env.JHARNESS_BROWSER_CHANNEL}:{})
  context=await browser.newContext({viewport:{width:1440,height:960}});page=await context.newPage();page.on('pageerror',e=>{if(stoppedHostDiagnostic(e.message,stoppedOrigins))transportDiagnostics.push(e.message);else errors.push(e.message)})
  check('content search requires authentication',(await api('/v1/search',query(),false)).status===401)
  check('content search rejects cross-origin request',(await api('/v1/search',query(),true,{Origin:'https://evil.invalid'})).status===403)
  const registry=(await api('/v1/workspaces',{path:other,name:'Secondary search workspace'})).body
  const second=registry.workspaces.find(w=>w.id!=='default').id,primary=[]
  for(let i=0;i<35;i++)primary.push(await create('default',i===34?'orchid alpha beta alpha beta boost 中文检索':`orchid alpha beta primary-${i}${i===1?' '+secret:''}`,'primary-'+i))
  const secondary=[];for(let i=0;i<3;i++)secondary.push(await create(second,i===2?'orchid beta alpha reverse':'orchid alpha beta secondary-'+i,'secondary-'+i))
  await create('default','orchid alphabet betatron','substring-only')
  await archive(primary[0]);await archive(secondary[0])
  check('search spans more than hot-set capacity',(await api('/v1/tasks?limit=32&view=all')).body.historyCount===39)
  const first=(await api('/v1/search',query())).body
  check('tokens require all whole words across workspaces',first.total===36&&first.matches.every(h=>h.snippet.includes('alpha')))
  check('relevance ranks repeated matches before recency',first.matches[0].snippet.includes('boost')&&first.matches[0].score===4)
  const next=(await api('/v1/search',query({cursor:first.next}))).body
  check('pagination keeps stable snapshot without overlap',next.snapshotId===first.snapshotId&&next.total===36&&!next.matches.some(h=>first.matches.some(a=>a.eventId===h.eventId))&&next.matches.length===16)
  check('phrase mode preserves word order',(await api('/v1/search',query({mode:'phrase'}))).body.total===35)
  check('Chinese token search finds actual message',(await api('/v1/search',query({text:'中文'}))).body.total===1)
  check('archived scope is explicit',(await api('/v1/search',query({view:'archived'}))).body.total===2&&(await api('/v1/search',query({view:'all'}))).body.total===38)
  check('workspace scope cannot read another registered session',(await api('/v1/search',query({profile:'default',sessionId:secondary[1].taskId}))).body.error==='SEARCH_SCOPE_DENIED')
  check('single-session scope resolves exact source',(await api('/v1/search',query({sessionId:primary[2].taskId}))).body.total===1)
  check('changed query cannot reuse cursor',(await api('/v1/search',query({text:'orchid',cursor:first.next}))).status===409)
  await archive(primary[3]);check('archival invalidates prior visibility cursor',(await api('/v1/search',query({cursor:first.next}))).body.error==='SEARCH_CURSOR_EXPIRED')
  check('secrets cannot be rediscovered through derived search',(await api('/v1/search',query({text:secret,mode:'phrase',view:'all'}))).body.total===0&&!JSON.stringify(first).includes(secret))
  const source=first.matches[0]
  const fact=(await api(`/v1/tasks/${source.sessionId}/content/${source.eventId}?offset=0`)).body
  check('result event id resolves actual sanitized fact',fact.text.includes('boost')&&fact.eventId===source.eventId)
  const cache=join(workspace,'.jharness/content-search',primary[34].taskId+'.content.json')
  const before=await readFile(join(workspace,'.jharness/sessions',primary[34].taskId+'.jsonl'))
  await writeFile(cache,'broken derived index')
  check('corrupt index rebuilds without changing facts',(await api('/v1/search',query({sessionId:primary[34].taskId}))).body.rebuiltSessions===1&&(await readFile(join(workspace,'.jharness/sessions',primary[34].taskId+'.jsonl'))).equals(before))
  await page.goto(base);await page.getByRole('button',{name:'内容检索',exact:true}).click()
  await page.getByLabel('内容关键词',{exact:true}).fill('alpha beta');await page.getByRole('button',{name:'检索内容',exact:true}).click()
  await page.getByText('找到 35 条正文匹配，已显示 20 条。',{exact:true}).waitFor()
  check('browser uses content results with source identity',await page.locator('.content-search-results>li').count()===20&&await page.locator('.content-search-results>li').first().innerText().then(t=>t.includes('boost')&&t.includes('user.message')))
  await page.getByRole('button',{name:'加载更多正文匹配',exact:true}).click();await page.getByText('找到 35 条正文匹配，已显示 35 条。',{exact:true}).waitFor();check('browser loads stable next page',await page.locator('.content-search-results>li').count()===35)
  await page.getByRole('button',{name:'读取来源事件',exact:true}).first().click();await page.locator('.content-search-results>li').first().getByText('boost',{exact:false}).last().waitFor();check('browser reads exact event content',await page.locator('.content-search-results>li .content-reader').count()===1)
  await shot('desktop')
  await page.getByRole('button',{name:'打开来源会话',exact:true}).first().click();await page.getByRole('region',{name:'继续会话'}).waitFor();check('opening source does not submit a task',wire.length===39)
  await page.getByRole('button',{name:'内容检索',exact:true}).click();await page.getByLabel('仅当前会话').check();await page.getByLabel('内容关键词',{exact:true}).fill('中文');await page.getByRole('button',{name:'检索内容',exact:true}).click();await page.getByText('找到 1 条正文匹配，已显示 1 条。',{exact:true}).waitFor();check('browser supports content search within selected session')
  await page.getByText('索引维护',{exact:true}).click();await page.getByRole('button',{name:'重建范围索引并查询',exact:true}).click();await page.getByText('找到 1 条正文匹配，已显示 1 条。',{exact:true}).waitFor();check('browser exposes explicit derived-index rebuild')
  await page.getByLabel('内容关键词',{exact:true}).fill('not-found');await page.getByRole('button',{name:'检索内容',exact:true}).click();await page.getByText('找到 0 条正文匹配，已显示 0 条。请调整关键词或范围。',{exact:true}).waitFor();check('empty state is explicit')
  await page.getByLabel('内容关键词',{exact:true}).fill('<script>window.__search_xss=1</script>');await page.getByRole('button',{name:'检索内容',exact:true}).click();await until(async()=>!await page.getByText('正在检索…',{exact:false}).isVisible());check('query and snippet never execute markup',await page.evaluate(()=>window.__search_xss===undefined))
  await page.setViewportSize({width:390,height:844});await shot('mobile');check('search controls fit mobile viewport',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  const count=wire.length;await stop();await start();await page.goto(base)
  check('restart rebuilds or reuses indices without model replay',(await api('/v1/search',query({view:'all'}))).body.total===38&&wire.length===count)
  check('all real requests use redacted authorized loopback context',wire.every(w=>w.authorized&&w.redacted))
  check('no unexpected browser runtime exceptions',errors.length===0)
  result={status:'PASS',capturedAt:new Date().toISOString(),checks,screenshots,transportDiagnostics,browserEngine:process.env.JHARNESS_BROWSER_ENGINE??'chromium',browserVersion:browser.version(),browserChannel:process.env.JHARNESS_BROWSER_CHANNEL??process.env.JHARNESS_BROWSER_ENGINE??'chromium',jarSha256:createHash('sha256').update(await readFile(jar)).digest('hex'),paidRequests:0,modelRequests:wire.length}
}catch(e){if(page){await page.screenshot({path:join(out,'M10.5.8-search-failure.png'),fullPage:true});await writeFile(join(out,'M10.5.8-search-failure.txt'),(String(e.stack)+'\n'+output+'\n'+await page.locator('body').innerText()).replaceAll(secret,'[REDACTED]').replaceAll(token,'[REDACTED]'))}throw e}
finally{await stop().catch(()=>host?.kill());await browser?.close();model.closeAllConnections();await new Promise(r=>model.close(r))}
await writeFile(join(out,'M10.5.8-search-results.json'),JSON.stringify(result,null,2))
