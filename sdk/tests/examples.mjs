import { spawn } from 'node:child_process'
import { mkdir, writeFile, readFile, readdir } from 'node:fs/promises'
import { resolve, join } from 'node:path'
import { createHash } from 'node:crypto'
import assert from 'node:assert/strict'
const root=resolve(import.meta.dirname,'../..'),cache=join(root,'.build-cache'),jar=join(root,'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'),python=process.env.JHARNESS_PYTHON
assert.ok(python)
const provider=spawn(python,['sdk/examples/loopback_provider.py'],{cwd:root,windowsHide:true,stdio:'pipe'})
let ready='',error='';provider.stdout.on('data',d=>ready+=d);provider.stderr.on('data',d=>error+=d)
const checks=[]
try {
  const end=Date.now()+10000
  while(!ready.includes('Offline SDK provider')&&provider.exitCode===null&&Date.now()<end)await new Promise(r=>setTimeout(r,50))
  assert.ok(ready.includes('Offline SDK provider'),error)
  for(const language of ['typescript','python']) {
    const workspace=join(cache,'M10.5.8-example-'+language+'-'+Date.now());await mkdir(workspace,{recursive:true});await writeFile(join(workspace,'README.md'),'README: offline SDK example fixture. 中文\n')
    const child=spawn(language==='typescript'?process.execPath:python,language==='typescript'?['--experimental-strip-types','sdk/examples/run.ts']:['sdk/examples/run.py'],{cwd:root,windowsHide:true,env:{...process.env,...(language==='python'?{PYTHONIOENCODING:'cp1252'}:{}),JHARNESS_SDK_JAR:jar,JHARNESS_SDK_WORKSPACE:workspace,JHARNESS_API_KEY:'synthetic-demo-key'},stdio:'pipe'})
    let output='',stderr='';child.stdout.on('data',d=>output+=d);child.stderr.on('data',d=>stderr+=d)
    const timer=setTimeout(()=>child.kill(),60000),code=await new Promise(r=>child.on('exit',r));clearTimeout(timer)
    assert.equal(code,0,stderr);assert.ok(output.includes('FINISHED')&&output.includes('README')&&output.includes('sanitized-session-content')&&!output.includes('synthetic-demo-key'),output)
    if(language==='python')assert.ok(output.includes('\\u4e2d\\u6587'),'Legacy console output preserves Unicode as JSON escapes')
    const logs=(await readdir(join(workspace,'.jharness/sessions'))).filter(p=>/^web-[a-f0-9]{32}-[a-f0-9]{64}\.jsonl$/.test(p))
    assert.equal(logs.length,1)
    const facts=await readFile(join(workspace,'.jharness/sessions',logs[0]))
    const events=facts.toString('utf8').trim().split('\n').map(line=>JSON.parse(line))
    assert.ok(events.some(e=>e.type==='tool.completed'&&e.payload.tool==='read_file'&&e.payload.status==='SUCCESS'&&e.payload.value.path==='README.md'))
    checks.push({name:language+' documented example with actual owned Java and successful read_file fact',passed:true,factsSha256:createHash('sha256').update(facts).digest('hex')})
  }
  await writeFile(join(cache,'M10.5.8-examples-results.json'),JSON.stringify({status:'PASS',capturedAt:new Date().toISOString(),jarSha256:createHash('sha256').update(await readFile(jar)).digest('hex'),checks,paidRequests:0},null,2));console.log('PASS examples',checks.length)
} finally {provider.kill();await new Promise(r=>provider.exitCode!==null||provider.signalCode!==null?r():provider.once('exit',r))}
