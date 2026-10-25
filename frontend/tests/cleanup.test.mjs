import test from 'node:test'
import assert from 'node:assert/strict'
import { cleanupFixture } from './cleanup.mjs'

test('fixture cleanup preserves primary and attempts every remaining owner', async () => {
  const primary = new Error('browser failure'), secondary = new Error('cleanup failure'), calls = []
  let diagnostics = 0
  await assert.rejects(async () => {
    try { throw primary } finally {
      await cleanupFixture(primary, [() => { calls.push('first'); throw secondary }, () => calls.push('last')], () => { diagnostics++ })
    }
  }, error => error === primary)
  assert.deepEqual(calls, ['first', 'last'])
  assert.deepEqual(primary.suppressed, [secondary])
  assert.equal(diagnostics, 1)
})

test('cleanup failure without a primary prevents a success result', async () => {
  const first = new Error('first'), second = new Error('second')
  await assert.rejects(cleanupFixture(null, [() => { throw first }, () => { throw second }]), error => {
    assert.ok(error instanceof AggregateError)
    assert.deepEqual(error.errors, [first, second])
    return true
  })
})

test('frozen primary and failing diagnostic still preserve the original failure', async () => {
  const primary = Object.freeze(new Error('original'))
  await assert.rejects(async () => {
    try { throw primary } finally {
      await cleanupFixture(primary, [() => { throw new Error('secondary') }], () => { throw new Error('diagnostic') })
    }
  }, error => error === primary)
})

test('successful cleanup does not diagnose or alter the primary', async () => {
  const primary = new Error('original')
  await cleanupFixture(primary, [async () => {}], () => assert.fail('unexpected diagnostic'))
  assert.equal(primary.suppressed, undefined)
})
