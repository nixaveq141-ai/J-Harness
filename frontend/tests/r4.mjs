// Deterministic browser fault windows complement the real Java workbench fixtures.
import { createServer } from 'vite'
import { browserType, engine } from './runtime.mjs'
import assert from 'node:assert/strict'
import { writeFile, mkdir, readFile, readdir } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { resolve } from 'node:path'
const root=resolve(import.meta.dirname,'..'),checks=[],errors=[]
const output=resolve(root,'../.build-cache/R4/browser-'+engine+'.json')
await mkdir(resolve(root,'../.build-cache/R4'),{recursive:true})
await writeFile(output,JSON.stringify({status:'INCOMPLETE',engine,checks,errors},null,2)+'\n')
const hash=data=>createHash('sha256').update(data).digest('hex')
async function sourceSnapshot(){
 const names=(await readdir(resolve(root,'src'),{recursive:true,withFileTypes:true})).filter(e=>e.isFile()).map(e=>resolve(e.parentPath,e.name))
 names.push(resolve(root,'tests/r4.mjs'),resolve(root,'tests/r4-fixture.tsx'),resolve(root,'tests/runtime.mjs'))
 return Object.fromEntries(await Promise.all(names.sort().map(async name=>[name.slice(root.length+1).replaceAll('\\','/'),hash(await readFile(name))])))
}
const sources=await sourceSnapshot()
const server=await createServer({root,server:{host:'127.0.0.1',port:0},plugins:[{name:'r4-fixture',configureServer(s){s.middlewares.use('/__r4',async(_q,r)=>{r.setHeader('Content-Type','text/html');r.end(await s.transformIndexHtml('/__r4','<html><body><div id="root"></div><script type="module" src="/tests/r4-fixture.tsx"></script></body></html>'))})}}]})
let browser
try{
 await server.listen();browser=await browserType.launch(process.env.JHARNESS_BROWSER_CHANNEL?{channel:process.env.JHARNESS_BROWSER_CHANNEL}:{})
 const page=await browser.newPage({viewport:{width:390,height:844}});page.on('pageerror',e=>{errors.push(e.message);console.error(e.message)});await page.goto('http://127.0.0.1:'+server.httpServer.address().port+'/__r4');await page.waitForFunction(()=>window.r4)
 const check=(name)=>{checks.push({name,passed:true});console.log('PASS '+name)}
 await page.evaluate(()=>{
  window.model={id:'a',label:'A',model:'fixture',baseUrl:'http://127.0.0.1',apiKeyEnv:'FIXTURE',keyConfigured:true,pricing:{},disableThinking:false}
  window.data={selected:'a',models:[window.model,{...window.model,id:'b',label:'B'}],extensions:{},corePlugins:[],credentialStore:'fixture',credentialCleanupPending:false,policy:'default',revision:'r1'}
  window.props={api:{models:()=>new Promise(r=>window.releaseModel=r)},value:'',onChange:v=>window.selection=v,disabled:false}
  window.r4.mount('ModelPicker',window.props)
 });
 await page.getByRole('combobox').waitFor()
 await page.evaluate(()=>{window.props={...window.props,value:'b'};window.r4.mount('ModelPicker',window.props)})
 await page.waitForTimeout(50);await page.evaluate(()=>window.releaseModel({models:window.data.models,selected:'a'}));await page.waitForFunction(()=>document.querySelector('select').value==='b');assert.equal(await page.evaluate(()=>window.selection),undefined);check('late model catalog preserves the latest explicit choice')
 await page.evaluate(()=>window.r4.mount('ModelPicker',{...window.props,value:'',onChange:v=>window.selection=v}));await page.waitForFunction(()=>window.selection==='a');check('reset model selection restores the saved default')
 await page.evaluate(()=>{window.selection='untouched';window.r4.mount('ModelPicker',{...window.props,api:{models:()=>Promise.reject(Error())}})})
 await page.getByText('无法读取连接，请重连或检查设置').waitFor({state:'attached'});assert.equal(await page.getByRole('option').count(),1);check('replaced API failure clears the prior model catalog')
 await page.evaluate(()=>{window.saves=[];window.keyValue='synthetic-r4-private';window.settingsApi={settings:async()=>window.data,saveSettings:async(data,keys)=>{window.saves.push({...keys});if(window.saves.length===1)throw Error();return data}};window.r4.mount('Settings',{api:window.settingsApi,onBack:()=>{},onGuardChange:()=>{}})})
 await page.getByLabel('API Key',{exact:true}).fill('synthetic-r4-private');await page.getByRole('button',{name:'保存设置',exact:true}).click();await page.getByRole('alert').waitFor();assert.equal(await page.getByLabel('API Key',{exact:true}).inputValue(),'synthetic-r4-private');await page.getByRole('button',{name:'保存设置',exact:true}).click();await page.getByText('设置已保存，将用于后续新消息。').waitFor();assert.deepEqual(await page.evaluate(()=>window.saves),[{a:'synthetic-r4-private'},{a:'synthetic-r4-private'}]);assert.equal(await page.getByLabel('API Key',{exact:true}).inputValue(),'');check('failed settings save retains memory-only key and retry sends the same key')
 assert.equal(await page.evaluate(()=>JSON.stringify({...localStorage,...sessionStorage}).includes('synthetic-r4-private')),false);check('key draft never enters browser storage')
 await page.getByRole('button',{name:'B fixture'}).click();await page.getByLabel('连接名称',{exact:true}).fill('B edited');await page.getByRole('button',{name:'保存设置',exact:true}).click();await page.getByText('设置已保存，将用于后续新消息。').waitFor();assert.equal(await page.getByLabel('连接名称',{exact:true}).inputValue(),'B edited');check('saving a nondefault connection preserves the edited connection')
 await page.evaluate(()=>{window.r4.mount('Settings',{api:{settings:()=>new Promise(r=>window.releaseOld=r)},onBack:()=>{},onGuardChange:()=>{}})})
 await page.getByText('正在处理设置…').waitFor();await page.evaluate(()=>window.r4.mount('Settings',{api:{settings:async()=>({...window.data,models:[{...window.model,label:'New context'}]})},onBack:()=>{},onGuardChange:()=>{}}));await page.getByLabel('连接名称',{exact:true}).waitFor();await page.evaluate(()=>window.releaseOld({...window.data,models:[{...window.model,label:'STALE'}]}));assert.equal(await page.getByLabel('连接名称',{exact:true}).inputValue(),'New context');check('late settings response cannot overwrite a replacement API context')
 await page.evaluate(()=>{window.r4.mount('WorkspacePicker',{api:{workspaces:async()=>({workspaces:[{id:'old',name:'old',path:'/old'}],canRegister:true}),registerWorkspace:async()=>({workspaces:[{id:'new',name:'new',path:'/new'},{id:'old',name:'old',path:'/old'}],canRegister:true})},value:'',disabled:false,onProfiles:()=>{},onSelect:id=>window.registered=id})})
 await page.getByText('注册工作区',{exact:true}).click();await page.getByLabel('工作区名称',{exact:true}).fill('new');await page.getByLabel('工作区绝对目录',{exact:true}).fill('/new');await page.getByRole('button',{name:'注册并选择'}).click();await page.waitForFunction(()=>window.registered==='new');check('workspace registration identifies the added workspace independent of response order')
 await page.evaluate(()=>window.r4.mount('Markdown',{text:'7. Seven\n8. Eight\n- Bullet\n- Second\n9. Nine'}));await page.locator('ol').first().waitFor();assert.deepEqual(await page.locator('ol').evaluateAll(rows=>rows.map(r=>r.start)),[7,9]);assert.equal(await page.locator('ul li').count(),2);check('Markdown preserves ordered-list start and mixed list boundaries')
 await page.evaluate(()=>window.r4.mount('SegmentedTabs',{items:[{value:'a',label:'很长的中文标签用来验证窄屏文字换行',id:'one',panelId:'p1'},{value:'b',label:'第二项',id:'two',panelId:'p2'}],value:'missing',label:'tabs',onChange:v=>window.tabValue=v}));await page.getByRole('tab',{name:'第二项'}).waitFor();assert.equal(await page.getByRole('tab').first().getAttribute('tabindex'),'0');await page.locator('body').click({position:{x:1,y:1}});await page.keyboard.press('Tab');assert.equal(await page.getByRole('tab').first().evaluate(n=>n===document.activeElement),true);await page.keyboard.press('ArrowRight');assert.equal(await page.getByRole('tab').last().evaluate(n=>n===document.activeElement),true);check('invalid tab value retains a keyboard entry and arrow focus reaches the next direct tab')
 await page.emulateMedia({forcedColors:'active',reducedMotion:'reduce'});assert.notEqual(await page.getByRole('tab').first().evaluate(n=>getComputedStyle(n).borderStyle),'none');check('forced-colors retains a real selected boundary')
 await page.evaluate(()=>window.r4.mount('Button',{type:undefined,children:'native'}));await page.getByRole('button',{name:'native'}).waitFor();assert.equal(await page.getByRole('button',{name:'native'}).getAttribute('type'),'button');check('undefined button type preserves the non-submit default')
 await page.evaluate(()=>{
  window.sessionProps={api:{},task:{taskId:'session',name:'Original',phase:'FINISHED',status:'COMPLETED',error:'',cancelRequested:false,recovered:false,pinned:false,archived:false},lastEvent:'',onUpdated:()=>{},onFork:async()=>{},onGuard:()=>{}}
  window.r4.mount('SessionActions',window.sessionProps)
 });await page.getByText('会话管理',{exact:true}).click();await page.getByLabel('会话名称',{exact:true}).waitFor()
 await page.evaluate(()=>{window.sessionProps={...window.sessionProps,task:{...window.sessionProps.task,name:'External'}};window.r4.mount('SessionActions',window.sessionProps)});await page.waitForFunction(()=>document.querySelector('input').value==='External');check('clean session name follows a same-session external metadata update')
 await page.getByLabel('会话名称',{exact:true}).fill('Local draft');await page.evaluate(()=>window.r4.mount('SessionActions',{...window.sessionProps,task:{...window.sessionProps.task,name:'Remote again'}}));assert.equal(await page.getByLabel('会话名称',{exact:true}).inputValue(),'Local draft');assert.equal(await page.getByRole('button',{name:'重读会话',exact:true}).isDisabled(),true);check('external metadata does not erase a local name draft and reload stays guarded')
 await page.evaluate(()=>{
  window.searchProps={api:{searchContent:()=>new Promise(r=>window.releaseSearch=r)},profile:'',view:'all',currentSession:'task_1',workspaces:{},onBack:()=>{},onOpen:async()=>{}}
  window.r4.mount('ContentSearch',window.searchProps)
 });
 await page.getByLabel('内容关键词',{exact:true}).fill('needle');await page.getByRole('button',{name:'检索内容',exact:true}).click();await page.getByText('正在检索…').waitFor()
 await page.evaluate(()=>window.r4.mount('ContentSearch',{...window.searchProps,api:{searchContent:async()=>({matches:[],total:0,more:false,next:'',snapshotId:'new',reusedEvents:0,rebuiltSessions:0})}}));await page.getByRole('button',{name:'检索内容',exact:true}).click();await page.getByText(/找到 0 条正文匹配/).waitFor();await page.evaluate(()=>window.releaseSearch({matches:[],total:99,more:false,next:'',snapshotId:'old'}));assert.equal(await page.getByText(/找到 0 条正文匹配/).count(),1);check('replacing a search API releases the lock and rejects the previous late response')
 await page.getByLabel('仅当前会话',{exact:true}).check();await page.evaluate(()=>window.r4.mount('ContentSearch',{...window.searchProps,currentSession:'',api:{searchContent:async()=>({matches:[],total:0,more:false,next:'',snapshotId:'new',reusedEvents:0,rebuiltSessions:0})}}));await page.waitForFunction(()=>!document.querySelector('input[type=checkbox]').checked);check('missing current session cannot retain a checked single-session scope')
 await page.evaluate(()=>{
  window.q={questionId:'q1',sessionId:'task_1',turnId:'turn',callId:'call',question:'Answer now',mode:'text',options:[],allowText:true,state:'PENDING',expiresAt:'2026-10-24T00:00:00Z',draftRevision:0}
  window.answers=[];window.savedDrafts=[];window.answerAttempts=0
  window.r4.mount('Questions',{api:{questionPage:async()=>({questions:[window.q],total:1,hasWaiting:true}),questionDraft:async(q,selected,text,revision)=>{window.savedDrafts.push(text);return {revision:revision+1}},answerQuestion:async(q,id,selected,text)=>{window.answers.push({id,text});if(++window.answerAttempts===1)throw Error('lost response');return {...q,state:'SUBMITTED'}},questionStatus:async()=>window.q},id:'task_1',onGuard:()=>{},onWaiting:()=>{}})
 });
 await page.getByLabel('问题文字回答',{exact:true}).fill('immediate snapshot');await page.getByRole('button',{name:'提交回答',exact:true}).click();await page.getByText('回答未确认，请核对状态；不会自动重发。').waitFor();assert.equal(await page.evaluate(()=>window.savedDrafts.at(-1)),'immediate snapshot');await page.getByRole('button',{name:'清除本页标记',exact:true}).click();await page.getByRole('button',{name:'提交回答',exact:true}).click();await page.getByText('已接收，正在核对持久记录').waitFor();const answers=await page.evaluate(()=>window.answers);assert.equal(answers.length,2);assert.equal(answers[0].id,answers[1].id);check('immediate answer flushes its draft and an explicit retry keeps the same admission identity')
 assert.deepEqual(errors,[])
 assert.deepEqual(await sourceSnapshot(),sources)
 const screenshot=resolve(root,'../.build-cache/R4/browser-'+engine+'.png');await page.screenshot({path:screenshot,fullPage:true})
 await writeFile(output,JSON.stringify({status:'PASS',engine,checks,errors,sourceSha256:sources,screenshot:{path:screenshot,sha256:hash(await readFile(screenshot))}},null,2)+'\n')
}catch(error){await writeFile(output,JSON.stringify({status:'FAILED',engine,checks,errors,error:String(error)},null,2)+'\n');throw error}
finally{await browser?.close();await server.close()}
