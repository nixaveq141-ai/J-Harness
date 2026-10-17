import { test } from 'node:test'
import assert from 'node:assert/strict'
import { Api, ApiError, mergeEvents, task, type SessionEvent } from '../src/api.ts'

const token = 'test_token_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ'
test('browser connection obtains an in-memory session without user input', async () => {
  const calls: { path: string; init: RequestInit }[] = []
  const api = await Api.connect((async (path, init) => {
    calls.push({ path: String(path), init: init! })
    return json(String(path) === '/v1/browser-session' ? { token } : { profiles: ['default'], protocolVersion: '1.9' })
  }) as typeof fetch)
  assert.deepEqual((await api.info()).profiles, ['default'])
  assert.equal(calls[0]!.path, '/v1/browser-session')
  assert.equal(calls[0]!.init.method, 'POST')
  assert.equal(calls[0]!.init.body, '{}')
  assert.equal(new Headers(calls[1]!.init.headers).get('Authorization'), `Bearer ${token}`)
  assert.ok(calls.every(c => !c.path.includes(token)))
})
const event: SessionEvent = { eventId: 'event_1', type: 'assistant.message', timestamp: '2026-10-16T00:00:00Z', callId: null, payload: { content: '<script>window.injected=true</script>' } }
const row = { taskId: 'task_1', profile: 'default', phase: 'RUNNING', status: 'RUNNING',
  error: '', cancelRequested: false, recovered: false, protocolVersion: '1.9' }
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'content-type': 'application/json' } })

test('mutations are never automatically retried and token stays in headers', async () => {
  const calls: { url: string; init: RequestInit }[] = []
  const api = new Api(token, (async (url, init) => { calls.push({ url: String(url), init: init! }); throw new TypeError('network ' + token) }) as typeof fetch)
  await assert.rejects(api.submit('default', 'goal', 'fixed_id'), (e: unknown) => e instanceof ApiError && e.code === 'CONNECTION_LOST' && !e.message.includes(token))
  assert.equal(calls.length, 1)
  assert.equal(calls[0].init.method, 'POST')
  assert.equal(calls[0].url, '/v1/tasks')
  assert.equal(new Headers(calls[0].init.headers).get('Authorization'), `Bearer ${token}`)
  assert.ok(!String(calls[0].init.body).includes(token))
  assert.equal(calls[0].init.redirect, 'error')
  assert.equal(calls[0].init.credentials, 'omit')
})
test('runtime shape and path validation reject invalid wire data', async () => {
  assert.throws(() => task({ ...row, cancelRequested: 'false' }), ApiError)
  assert.throws(() => task({ ...row, taskId: '../etc' }), ApiError)
  const api = new Api(token, (async () => json({ tasks: [null] })) as typeof fetch)
  await assert.rejects(api.tasks(), ApiError)
  await assert.rejects(api.detail('../secrets'), ApiError)
})
test('SSE retains only complete matching-id frames and de-duplicates reconnects', async () => {
  const encoded = new TextEncoder().encode(`id: event_1\nevent: session\ndata: ${JSON.stringify(event)}\n\nid: unfinished\ndata: {`)
  const api = new Api(token, (async (_url, init) => {
    assert.equal(init?.method, 'GET')
    return new Response(new ReadableStream({ start(c) { c.enqueue(encoded.slice(0, 32)); c.enqueue(encoded.slice(32)); c.close() } }), { headers: { 'content-type': 'text/event-stream' } })
  }) as typeof fetch)
  const received: SessionEvent[] = []
  await api.stream('task_1', '', e => received.push(e), new AbortController().signal)
  assert.deepEqual(received, [event])
  assert.deepEqual(mergeEvents(received, [event, { ...event, eventId: 'event_2' }]).map(e => e.eventId), ['event_1', 'event_2'])
  assert.equal(mergeEvents([], Array.from({ length: 4100 }, (_, i) => ({ ...event, eventId: `bounded_${i}` }))).length, 4000)
  assert.equal(received[0].payload.content, event.payload.content)
})
test('SSE mismatched id is rejected instead of advancing the cursor', async () => {
  const api = new Api(token, (async () => new Response(`id: other\ndata: ${JSON.stringify(event)}\n\n`, { headers: { 'content-type': 'text/event-stream' } })) as typeof fetch)
  await assert.rejects(api.stream('task_1', '', () => assert.fail(), new AbortController().signal), ApiError)
})
test('approval association is checked before any request', async () => {
  let called = false
  const api = new Api(token, (async () => { called = true; return json({}) }) as typeof fetch)
  await assert.rejects(api.decide('task_1', { approvalId: 'approval_1', callId: 'call_1', sessionId: 'other_task', tool: 'write_file', arguments: {}, sideEffectLevel: 'WORKSPACE_WRITE' }, true, 'yes'), e => e instanceof ApiError && e.code === 'STALE_APPROVAL')
  assert.equal(called, false)
})
test('expired cursor is explicit and unknown backend error messages are not surfaced', async () => {
  const api = new Api(token, (async () => json({ error: 'UNKNOWN_CURSOR' }, 400)) as typeof fetch)
  await assert.rejects(api.events('task_1', 'invalid'), e => e instanceof ApiError && e.code === 'UNKNOWN_CURSOR')
  const hostile = new Api(token, (async () => json({ error: token, detail: token }, 500)) as typeof fetch)
  await assert.rejects(hostile.info(), e => e instanceof ApiError && e.message === 'HTTP_500')
})
test('oversized responses fail closed', async () => {
  const api = new Api(token, (async () => new Response('x'.repeat(1048577))) as typeof fetch)
  await assert.rejects(api.info(), e => e instanceof ApiError && e.code === 'RESPONSE_LIMIT')
})
test('submission reconciliation uses only GET', async () => {
  const methods: string[] = []
  const api = new Api(token, (async (url, init) => { assert.equal(url, '/v1/submissions/fixed_id'); methods.push(init!.method!); return json(row) }) as typeof fetch)
  assert.equal((await api.submission('fixed_id')).taskId, 'task_1')
  assert.deepEqual(methods, ['GET'])
})
