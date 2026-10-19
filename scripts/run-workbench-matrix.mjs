// Execute one engine at a time and freeze its evidence before another engine can overwrite it.
import {spawn} from 'node:child_process'
import {readFile,writeFile,mkdir,copyFile} from 'node:fs/promises'
import {resolve,join} from 'node:path'
import {createHash} from 'node:crypto'
const root=resolve(import.meta.dirname,'..'),cache=join(root,'.build-cache'),out=join(cache,'M10.5.9/browser')
const fixtures={browser:'M10-browser-results.json',diagnosis:'M10.5.1-diagnosis-results.json',history:'M10.5.2-history-results.json',turns:'M10.5.3-turns-results.json',models:'M10.5.4-models-results.json',questions:'M10.5.5-questions-results.json',content:'M10.5.6-content-results.json',extensions:'M10.5.7-extensions-results.json',search:'M10.5.8-search-results.json',settings:'M10-settings-browser-results.json',development:'M10.5.9-development-results.json'}
const engines=(process.env.JHARNESS_MATRIX_ENGINES??'chromium,firefox,webkit').split(',')
if(new Set(engines).size!==engines.length||engines.some(e=>!['chromium','firefox','webkit'].includes(e)))throw Error('Invalid engine matrix')
const selected=(process.env.JHARNESS_MATRIX_FIXTURES??Object.keys(fixtures).join(',')).split(',')
if(new Set(selected).size!==selected.length||selected.some(f=>!fixtures[f]))throw Error('Invalid fixture matrix')
const sha=b=>createHash('sha256').update(b).digest('hex'),records=[],failures=[]
for(const engine of engines){
  const directory=join(out,engine);await mkdir(directory,{recursive:true})
  for(const fixture of selected){
    const env={...process.env,JHARNESS_BROWSER_ENGINE:engine}
    if(engine!=='chromium')delete env.JHARNESS_BROWSER_CHANNEL
    const log=join(directory,fixture+'.log'),child=spawn(process.execPath,['frontend/tests/'+fixture+'.mjs'],{cwd:root,env,windowsHide:true,stdio:'pipe'})
    let output='';child.stdout.on('data',d=>output+=d);child.stderr.on('data',d=>output+=d)
    const timer=setTimeout(()=>child.kill(),300000)
    const code=await new Promise((r,j)=>{child.once('error',j);child.once('exit',r)});clearTimeout(timer);await writeFile(log,output)
    if(code!==0){failures.push({engine,fixture,log});if(process.env.JHARNESS_MATRIX_KEEP_GOING!=='1')throw Error(engine+'/'+fixture+' failed; see '+log);console.log('FAILED',engine,fixture);continue}
    const record=JSON.parse(await readFile(join(cache,fixtures[fixture]),'utf8'))
    if(!(record.status==='PASS'||record.fixtureCredentialsCleaned)||!record.checks.length)throw Error('Incomplete fixture '+fixture)
    const paid=record.paidRequests??record.runtime?.paidRequests
    if(paid!==0)throw Error('Nonzero or unknown paid request count')
    for(const shot of record.screenshots??[]){
      const source=resolve(root,shot.path.replaceAll('\\','/'))
      if(!source.startsWith(cache+'\\')&&!source.startsWith(cache+'/'))throw Error('Screenshot outside fixture cache')
      const bytes=await readFile(source);if(sha(bytes)!==shot.sha256)throw Error('Screenshot changed')
      const target=join(directory,fixture+'-'+source.split(/[\\/]/).at(-1));await copyFile(source,target);shot.path=target.slice(root.length+1).replaceAll('\\','/')
    }
    record.browserEngine=engine;record.fixture=fixture;record.fixtureSourceSha256=sha(await readFile(join(root,'frontend/tests/'+fixture+'.mjs')))
    record.logSha256=sha(await readFile(log));const target=join(directory,fixture+'.json');await writeFile(target,JSON.stringify(record,null,2)+'\n')
    records.push({engine,fixture,checks:record.checks.length,path:target.slice(root.length+1).replaceAll('\\','/'),sha256:sha(await readFile(target))})
    console.log('PASS',engine,fixture,record.checks.length)
  }
}
await writeFile(join(out,'matrix.json'),JSON.stringify({status:failures.length?'FAILED':'PASS',capturedAt:new Date().toISOString(),platform:process.platform,engines,fixtures:selected,records,failures,paidRequests:0},null,2)+'\n')
if(failures.length)throw Error('Failed browser runs: '+failures.map(f=>f.engine+'/'+f.fixture).join(', '))
