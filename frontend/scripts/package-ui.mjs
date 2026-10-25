import { readFile, writeFile, readdir, rename, realpath } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { safeFile, tree, inputs, hash } from './ui-contract.mjs'
import { createHash } from 'node:crypto'

const root = new URL('../', import.meta.url)
const rootPath=await realpath(fileURLToPath(root))
const provenance = JSON.parse(await readFile(new URL('upstream.json', root), 'utf8'))
if(!Array.isArray(provenance.files) || !provenance.files.length)throw Error('Empty upstream provenance')
const expected=[...await tree(rootPath,'src/upstream'),'LICENSE-DeepSeek-Harness.txt'].sort()
const actual=provenance.files.map(file=>file.destination).sort()
if(new Set(actual).size!==actual.length || JSON.stringify(expected)!==JSON.stringify(actual))throw Error('Incomplete upstream provenance')
for (const file of provenance.files) {
  if(!/^[0-9a-f]{64}$/.test(file.sha256))throw Error('Invalid upstream SHA256')
  await safeFile(rootPath,file.destination)
  const hash = createHash('sha256').update(await readFile(new URL(file.destination, root))).digest('hex')
  if (hash !== file.sha256) throw new Error(`Upstream copy changed without provenance update: ${file.destination}`)
}
let licenses = 'J-Harness Web UI: Apache-2.0 for original code.\n\n'
licenses += await readFile(new URL('../LICENSE', root), 'utf8')
licenses += '\n\nDeepSeek Harness components/styles: MIT\n'
licenses += await readFile(new URL('LICENSE-DeepSeek-Harness.txt', root), 'utf8')
// Include actual distributed runtime inputs, not the whole upstream dependency inventory.
const lock=JSON.parse(await readFile(new URL('package-lock.json',root),'utf8'))
const runtime=Object.entries(lock.packages).filter(([path,item])=>path&&!item.dev)
if(!runtime.length)throw Error('Missing production license closure')
for (const [path,item] of runtime) {
  const name=path.split('node_modules/').at(-1)
  if(item.link || !item.integrity)throw Error('Unsupported runtime license source')
  const dir = new URL(path+'/', root)
  const entries = await readdir(dir)
  const license = entries.find(s => /^license(?:\.txt|\.md)?$/i.test(s))
  if (!license) throw new Error(`Missing license for ${name}`)
  const manifest = JSON.parse(await readFile(new URL('package.json', dir), 'utf8'))
  licenses += `\n\n${name}@${manifest.version}\n${await readFile(new URL(license, dir), 'utf8')}`
}
const outputFiles=await tree(rootPath,'dist')
if(!outputFiles.includes('dist/index.html') || outputFiles.some(p=> !/^(dist\/(index\.html|ui-license\.txt|ui-files\.txt|ui-build\.json)|dist\/assets\/[A-Za-z0-9_.-]+\.(js|css))$/.test(p)))throw Error('Unexpected or missing UI output')
const files=['index.html','ui-license.txt',...outputFiles.filter(p=>p.startsWith('dist/assets/')).map(p=>p.slice(5))].sort()
const manifest=files.join('\n')+'\n'
if(Buffer.byteLength(manifest)>16384)throw Error('UI file manifest limit')
const fileHashes={}
for(const name of files)fileHashes[name]=hash(name==='ui-license.txt'?licenses:await readFile(new URL('dist/'+name,root)))
fileHashes['ui-files.txt']=hash(manifest)
const build=JSON.stringify({schemaVersion:1,sourceSha256:await inputs(rootPath),files:fileHashes},null,2)+'\n'
// Validate everything before publication; the build record is published last.
for(const [name,text] of [['ui-license.txt',licenses],['ui-files.txt',manifest],['ui-build.json',build]]){
 await writeFile(new URL('dist/.'+name+'.tmp',root),text)
 await rename(new URL('dist/.'+name+'.tmp',root),new URL('dist/'+name,root))
}
