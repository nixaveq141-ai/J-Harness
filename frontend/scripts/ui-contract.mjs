import { readFile, readdir, lstat, realpath } from 'node:fs/promises'
import { resolve, relative } from 'node:path'
import { createHash } from 'node:crypto'

export const hash = bytes => createHash('sha256').update(bytes).digest('hex')
export async function safeFile(root, name) {
  if (typeof name !== 'string' || !name || name.includes('\\') || name.split('/').some(p => !p || p === '.' || p === '..' || p.includes(':'))) throw Error('Invalid UI input path')
  const path = resolve(root, name)
  if (!path.startsWith(root + '/'.replace('/', process.platform === 'win32' ? '\\' : '/'))) throw Error('UI path outside root')
  if (!(await lstat(path)).isFile() || await realpath(path) !== path) throw Error('Linked or nonregular UI input')
  return path
}
export async function tree(root, name) {
  const directory = resolve(root, name)
  if(name==='public'){try{await lstat(directory)}catch(error){if(error.code==='ENOENT')return [];throw error}}
  if ((await lstat(directory)).isSymbolicLink() || await realpath(directory) !== directory) throw Error('Linked UI directory')
  const files = []
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const child = name + '/' + entry.name
    if (entry.isDirectory()) files.push(...await tree(root, child))
    else { await safeFile(root, child); files.push(child) }
  }
  return files.sort()
}
export async function inputs(root) {
  const files = [...await tree(root, 'src'), ...await tree(root, 'public'), ...await tree(root, 'scripts'),
    'package.json', 'package-lock.json', 'tsconfig.json', 'vite.config.ts', 'index.html', 'upstream.json', 'LICENSE-DeepSeek-Harness.txt']
  const values = {}
  for (const name of files.sort()) values['frontend/' + name] = hash(await readFile(await safeFile(root, name)))
  values.LICENSE = hash(await readFile(resolve(root, '../LICENSE')))
  return values
}
