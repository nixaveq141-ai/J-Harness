// Resume evidence only when every retained record still matches current code, JAR, log and screenshots.
import {readFile,writeFile} from 'node:fs/promises'
import {resolve,join} from 'node:path'
import {createHash} from 'node:crypto'
import assert from 'node:assert/strict'
const root=resolve(import.meta.dirname,'..'),out=join(root,'.build-cache/M10.5.9/browser'),engines=process.argv.slice(2)
assert.ok(engines.length&&new Set(engines).size===engines.length&&engines.every(e=>['chromium','firefox','webkit'].includes(e)))
const fixtures=['browser','diagnosis','history','turns','models','questions','content','extensions','search','settings','development']
const sha=b=>createHash('sha256').update(b).digest('hex'),jar=sha(await readFile(join(root,'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'))),records=[]
for(const engine of engines)for(const fixture of fixtures){
  const path=join(out,engine,fixture+'.json'),bytes=await readFile(path),r=JSON.parse(bytes)
  assert.equal(r.jarSha256,jar);assert.equal(r.browserEngine,engine);assert.equal(r.fixture,fixture)
  assert.equal(r.fixtureSourceSha256,sha(await readFile(join(root,'frontend/tests/'+fixture+'.mjs'))))
  assert.equal(r.logSha256,sha(await readFile(join(out,engine,fixture+'.log'))))
  assert.ok(r.checks.length&&r.checks.every(c=>typeof c==='string'||c.passed===true))
  for(const [name,hash] of Object.entries(r.sourceSha256??{}))assert.equal(sha(await readFile(join(root,name))),hash,'Source drift: '+name)
  for(const shot of r.screenshots??[])assert.equal(sha(await readFile(join(root,shot.path))),shot.sha256,'Screenshot drift')
  records.push({engine,fixture,checks:r.checks.length,path:path.slice(root.length+1).replaceAll('\\','/'),sha256:sha(bytes)})
}
await writeFile(join(out,'matrix.json'),JSON.stringify({status:'PASS',capturedAt:new Date().toISOString(),platform:process.platform,engines,fixtures,records,paidRequests:0},null,2)+'\n')
console.log('PASS consistent retained matrix',records.length,'runs',records.reduce((n,r)=>n+r.checks,0),'checks')
