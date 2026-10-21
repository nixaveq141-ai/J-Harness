import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import { spawnSync } from 'node:child_process'
import { resolve } from 'node:path'
import { createHash } from 'node:crypto'
import { validateProgress, stateDescription, implementationArtifacts } from './ocr-progress.mjs'

const root = resolve(import.meta.dirname, '..')
const valid = () => ({ updatedAt: '2026-10-21', status: 'IMPLEMENTATION_IN_PROGRESS', tasks: {
  'R0-01': { status: 'IN_PROGRESS', description: 'Concrete pending work' },
} })
const known = ['R0-01', 'R0-02']

test('progress schema accepts an explicit current state and normalizes multiline descriptions', () => {
  const progress = valid()
  progress.tasks['R0-01'].description = 'pending\r\n### heading\n追溯：not a new paragraph'
  validateProgress(progress, known)
  assert.equal(stateDescription(progress.tasks['R0-01']), 'pending ### heading 追溯：not a new paragraph')
  assert.equal(stateDescription(undefined), '未实施')
})

for (const [name, mutate, reason] of [
  ['missing tasks', p => { delete p.tasks }, /tasks must be a nonempty object/],
  ['array tasks', p => { p.tasks = [] }, /tasks must be a nonempty object/],
  ['unknown task', p => { p.tasks['R0-typo'] = p.tasks['R0-01'] }, /Unknown implementation task/],
  ['missing status', p => { delete p.tasks['R0-01'].status }, /Invalid implementation task status/],
  ['unknown status', p => { p.tasks['R0-01'].status = 'PASSED_BY_TYPO' }, /Invalid implementation task status/],
  ['empty description', p => { p.tasks['R0-01'].description = '\n ' }, /Invalid implementation description/],
  ['oversized description', p => { p.tasks['R0-01'].description = 'x'.repeat(2001) }, /Invalid implementation description/],
]) test(`progress rejects ${name} with a specific diagnostic`, () => {
  const progress = valid(); mutate(progress)
  assert.throws(() => validateProgress(progress, known), reason)
})

test('implementation artifact hashes are separate from the static review inputs', () => {
  const hash = data => createHash('sha256').update(data).digest('hex')
  const options = { progressPath: 'progress.json', progressBytes: 'current', assignmentPath: 'plan.json', assignmentBytes: 'frozen', markdownPath: 'plan.md', markdown: 'generated', hash }
  const before = implementationArtifacts(options)
  const after = implementationArtifacts({ ...options, progressBytes: 'changed' })
  assert.equal(before.inputs['plan.json'], after.inputs['plan.json'])
  assert.notEqual(before.inputs['progress.json'], after.inputs['progress.json'])
  assert.deepEqual(Object.keys(before.outputs), ['plan.md'])
})

function checkReadOnlyMutation(kind) {
  const code = `
    import fs from 'node:fs';
    import {pathToFileURL} from 'node:url';
    import {join} from 'node:path';
    const root=process.argv[1], kind=process.argv[2], read=fs.readFileSync;
    fs.writeFileSync=()=>{throw Error('Read-only verifier attempted a write')};
    fs.readFileSync=function(name,...args){
      const bytes=read.call(this,name,...args), path=String(name);
      if(kind==='progress'&&path===join(root,'docs/development/OCR-remediation-progress.json')){
        const value=JSON.parse(bytes);value.tasks['R0-01'].description+=' changed after render';
        return Buffer.from(JSON.stringify(value));
      }
      if(kind==='markdown'&&path===join(root,'docs/requirements/OCR-remediation-plan-2026-10-20.md'))return bytes+' stale';
      if(kind==='manifest'&&path===join(root,'docs/development/OCR-implementation-artifacts.json')){
        const value=JSON.parse(bytes);value.inputs[Object.keys(value.inputs)[0]]='0'.repeat(64);
        return JSON.stringify(value);
      }
      return bytes;
    };
    await import(pathToFileURL(join(root,'scripts/verify-ocr-review.mjs')).href);
  `
  return spawnSync(process.execPath, ['--input-type=module', '-e', code, root, kind], { encoding: 'utf8', timeout: 20000 })
}

for (const [kind, reason] of [['progress', /implementation plan is stale/], ['markdown', /implementation plan is stale/], ['manifest', /implementation artifact manifest is stale/]]) {
  test(`read-only verifier rejects changed ${kind} without rewriting historical data`, () => {
    const files = ['docs/development/ocr-verification-2026-10-20/review.json', 'docs/development/ocr-verification-2026-10-20/plan.json', 'docs/testing/OCR-verification-2026-10-20-results.json']
    const before = files.map(name => fs.readFileSync(resolve(root, name)))
    const result = checkReadOnlyMutation(kind)
    assert.equal(result.status, 1, result.stderr)
    assert.match(result.stderr, reason)
    files.forEach((name, index) => assert.deepEqual(fs.readFileSync(resolve(root, name)), before[index]))
  })
}
