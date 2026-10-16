import { readFile, writeFile, readdir } from 'node:fs/promises'
import { createHash } from 'node:crypto'

const root = new URL('../', import.meta.url)
const provenance = JSON.parse(await readFile(new URL('upstream.json', root), 'utf8'))
for (const file of provenance.files) {
  const hash = createHash('sha256').update(await readFile(new URL(file.destination, root))).digest('hex')
  if (hash !== file.sha256) throw new Error(`Upstream copy changed without provenance update: ${file.destination}`)
}
let licenses = 'J-Harness Web UI: Apache-2.0 for original code.\n\n'
licenses += await readFile(new URL('../LICENSE', root), 'utf8')
licenses += '\n\nDeepSeek Harness components/styles: MIT\n'
licenses += await readFile(new URL('LICENSE-DeepSeek-Harness.txt', root), 'utf8')
// Include actual distributed runtime inputs, not the whole upstream dependency inventory.
for (const name of ['react', 'react-dom', 'scheduler', 'clsx']) {
  const dir = new URL(`node_modules/${name}/`, root)
  const entries = await readdir(dir)
  const license = entries.find(s => /^license(?:\.txt|\.md)?$/i.test(s))
  if (!license) throw new Error(`Missing license for ${name}`)
  const manifest = JSON.parse(await readFile(new URL('package.json', dir), 'utf8'))
  licenses += `\n\n${name}@${manifest.version}\n${await readFile(new URL(license, dir), 'utf8')}`
}
await writeFile(new URL('dist/ui-license.txt', root), licenses)
const files = ['index.html', 'ui-license.txt', ...(await readdir(new URL('dist/assets/', root))).map(n => `assets/${n}`)]
if (files.some(p => !/^(index\.html|ui-license\.txt|assets\/[A-Za-z0-9_-]+\.(js|css))$/.test(p))) throw new Error('Unexpected UI build output')
await writeFile(new URL('dist/ui-files.txt', root), files.sort().join('\n') + '\n')
