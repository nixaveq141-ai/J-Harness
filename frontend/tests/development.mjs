import { browserType, engine } from './runtime.mjs'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { createHash, randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'
const root=resolve(import.meta.dirname,'../..'),out=join(root,'.build-cache'),workspace=join(out,'M10.5.9-development-'+Date.now())
const jar=join(root,'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'),exe=n=>join(process.env.JAVA_HOME,'bin',n+(process.platform==='win32'?'.exe':'')),token='development_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ',secret='synthetic-development-'+randomUUID()
const digest=b=>createHash('sha256').update(b).digest('hex'),checks=[],screenshots=[],wire=[],errors=[]
const original='public class App { static int value() { return 1; } }\n',broken=original.replace('return 1;','return broken;')
const test='public class Verify { public static void main(String[] args) { if (App.value()!=2) throw new AssertionError("expected two"); System.out.println("TEST_PASS"); } }\n'
const edit=(oldText,newText,version)=>({path:'App.java',oldText,newText,expectedVersion:version})
const command=argv=>({argv,mode:'ARGV',timeoutMs:30000})
const steps=[['list_directory',{path:'.'}],['read_file',{path:'App.java'}],['edit_file',edit('return 1;','return broken;',digest(original))],
  ['execute_command',command(process.platform==='win32'?['cmd.exe','/c','echo forbidden implicit shell']:['sh','-c','echo forbidden implicit shell'])],
  ['execute_command',command([exe('javac'),'App.java','Verify.java'])],['edit_file',edit('return broken;','return 1;',digest(broken))],
  ['execute_command',command([exe('javac'),'App.java','Verify.java'])],['execute_command',command([exe('java'),'-cp','.','Verify'])],
  ['edit_file',edit('return 1;','return 2;',digest(original))],['execute_command',command([exe('javac'),'App.java','Verify.java'])],['execute_command',command([exe('java'),'-cp','.','Verify'])]]
let host,base,output='',browser,context,page,result,step=0
const wait=ms=>new Promise(r=>setTimeout(r,ms))
async function until(fn,ms=45000){const end=Date.now()+ms;while(Date.now()<end){if(await fn())return;await wait(80)}throw Error('Development fixture timeout')}
function check(name,value=true){assert.ok(value,name);checks.push({name,passed:true});console.log('PASS',name)}
const provider=createServer(async(req,res)=>{let raw='';for await(const c of req)raw+=c;wire.push({authorized:req.headers.authorization==='Bearer '+secret,redacted:!raw.includes(secret),observedShellError:raw.includes('SHELL_REQUIRED'),observedBuildError:raw.includes('NONZERO_EXIT'),observedTestError:raw.includes('expected two')});const next=steps[step++],calls=next?[{id:'development-'+step,type:'function',function:{name:next[0],arguments:JSON.stringify(next[1])}}]:[];res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({choices:[{index:0,finish_reason:next?'tool_calls':'stop',message:{role:'assistant',content:next?null:'Verified build and TEST_PASS after separate approved fixes.',...(next?{tool_calls:calls}:{})}}],usage:{prompt_tokens:100,completion_tokens:20,total_tokens:120}}))})
async function api(path,data){const r=await context.request.fetch(base+path,{method:data===undefined?'GET':'POST',headers:{Authorization:'Bearer '+token,...(data===undefined?{}:{'Content-Type':'application/json'})},...(data===undefined?{}:{data})});assert.ok(r.ok(),String(r.status())+' '+await r.text());return r.json()}
async function events(id){let rows=[],after='';do{const p=await api('/v1/tasks/'+id+'/events?limit=100'+(after?'&after='+after:''));rows.push(...p.events);after=p.more?p.next:''}while(after);return rows}
async function shot(name){const path=join(out,'M10.5.9-development-'+name+'.png');await page.screenshot({path,fullPage:true});screenshots.push({path:path.slice(root.length+1).replaceAll('\\','/'),sha256:digest(await readFile(path))})}
await mkdir(workspace,{recursive:true});await writeFile(join(workspace,'App.java'),original);await writeFile(join(workspace,'Verify.java'),test);await new Promise(r=>provider.listen(0,'127.0.0.1',r));const config=join(workspace,'config.json');await writeFile(config,JSON.stringify({baseUrl:'http://127.0.0.1:'+provider.address().port,model:'development-fixture',maxSteps:16,durationSeconds:180,policy:'default',ecosystem:{web:{port:0,approvalSeconds:60}}}))
try{
  host=spawn(exe('java'),['-Dfile.encoding=UTF-8','-jar',jar,'--workspace',workspace,'--config',config,'--require-read','App.java','--expect-file','App.java','--verify-argv',JSON.stringify([exe('java'),'-cp','.','Verify']),'serve'],{cwd:root,env:{...process.env,JHARNESS_WEB_TOKEN:token,JHARNESS_API_KEY:secret},stdio:'pipe',windowsHide:true});host.stdout.on('data',d=>{output+=d;base=output.match(/WEB (http:\/\/127\.0\.0\.1:\d+)/)?.[1]});host.stderr.on('data',d=>output+=d);await until(()=>base||host.exitCode!==null);assert.ok(base,output)
  browser=await browserType.launch(process.env.JHARNESS_BROWSER_CHANNEL?{channel:process.env.JHARNESS_BROWSER_CHANNEL}:{});context=await browser.newContext({viewport:{width:1440,height:1000}});page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(base)
  await page.getByLabel('任务目标',{exact:true}).fill('Read this project, fix App, build and test it. Every write and command requires approval.');await page.getByRole('button',{name:'创建任务',exact:true}).click()
  await until(async()=>(await api('/v1/tasks')).tasks.length===1);const id=(await api('/v1/tasks')).tasks[0].taskId
  let approvals=0
  await until(async()=>{
    const pending=await api('/v1/tasks/'+id+'/approvals')
    if(pending.approvals.length){await page.getByRole('button',{name:'批准本次操作',exact:true}).waitFor();if(approvals===0)check('project read reaches first write approval without editing',await readFile(join(workspace,'App.java'),'utf8')===original);await page.getByRole('button',{name:'批准本次操作',exact:true}).click();approvals++}
    return(await api('/v1/tasks/'+id)).phase==='FINISHED'
  },180000)
  const task=await api('/v1/tasks/'+id),facts=await events(id),done=facts.filter(e=>e.type==='tool.completed'),by=n=>done.find(e=>e.callId==='development-'+n)
  check('actual list and read tools precede edits',by(1).payload.status==='SUCCESS'&&by(2).payload.status==='SUCCESS')
  check('every edit and command receives separate explicit approval',approvals===9)
  check('implicit shell mode fails before command starts',JSON.stringify(by(4).payload).includes('SHELL_REQUIRED')&&!JSON.stringify(by(4).payload).includes('forbidden implicit shell\n'))
  check('actual javac failure is recorded',by(5).payload.status==='FAILED'&&JSON.stringify(by(5).payload).includes('NONZERO_EXIT'))
  check('first fix really compiles but test really fails',by(7).payload.status==='SUCCESS'&&by(8).payload.status==='FAILED'&&JSON.stringify(by(8).payload).includes('expected two'))
  check('second version-checked fix builds and passes actual test',by(10).payload.status==='SUCCESS'&&by(11).payload.status==='SUCCESS'&&JSON.stringify(by(11).payload).includes('TEST_PASS'))
  check('compiled class and final source agree with recorded success',(await readFile(join(workspace,'App.class'))).length>0&&(await readFile(join(workspace,'App.java'),'utf8')).includes('return 2;')&&task.status==='COMPLETED')
  check('model sees shell build and assertion errors before fixing',wire.some(w=>w.observedShellError)&&wire.some(w=>w.observedBuildError)&&wire.some(w=>w.observedTestError))
  const report=await api('/v1/tasks/'+id+'/report');check('fact report is available without submitting another task',JSON.stringify(report).includes(id)&&wire.length===12)
  await page.locator('#main-content').getByText('已完成',{exact:true}).waitFor();await shot('desktop');await page.setViewportSize({width:390,height:844});await shot('mobile');check('development conversation has no mobile horizontal overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  const before=wire.length;await page.setViewportSize({width:1440,height:1000});await page.reload();await page.getByText('本机服务已连接',{exact:true}).waitFor();check('refresh reads existing build results without replay',wire.length===before)
  check('all model traffic is authorized redacted loopback',wire.every(w=>w.authorized&&w.redacted));check('no browser runtime exception',errors.length===0)
  result={status:'PASS',capturedAt:new Date().toISOString(),browserEngine:engine,browserVersion:browser.version(),checks,screenshots,jarSha256:digest(await readFile(jar)),factsSha256:digest(await readFile(join(workspace,'.jharness/sessions',id+'.jsonl'))),modelRequests:wire.length,paidRequests:0}
}catch(e){if(page)await page.screenshot({path:join(out,'M10.5.9-development-failure.png'),fullPage:true});await writeFile(join(out,'M10.5.9-development-failure.txt'),(String(e.stack)+'\n'+output).replaceAll(secret,'[REDACTED]').replaceAll(token,'[REDACTED]'));throw e}
finally{if(host?.exitCode===null){host.stdin.end(':quit\n');await until(()=>host.exitCode!==null).catch(()=>host.kill())}await browser?.close();provider.closeAllConnections();await new Promise(r=>provider.close(r))}
await writeFile(join(out,'M10.5.9-development-results.json'),JSON.stringify(result,null,2))
