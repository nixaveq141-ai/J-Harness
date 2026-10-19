import { test } from 'node:test'
import assert from 'node:assert/strict'
import { Api, ApiError, mergeEvents, task, uncertain, type SessionEvent } from '../src/api.ts'

const token = 'test_token_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ'

test('stopped-host event diagnostic is scoped to the deliberately stopped origin', async()=>{
  const {stoppedHostDiagnostic}=await import('./runtime.mjs')
  const origins=new Set(['http://127.0.0.1:9999'])
  assert.equal(stoppedHostDiagnostic('Fetch API cannot load http://127.0.0.1:9999/v1/tasks/task_1/events?limit=100 due to access control checks.',origins),true)
  assert.equal(stoppedHostDiagnostic('/127.0.0.1:9999/v1/tasks/task_1/approvals due to access control checks.',origins),true)
  assert.equal(stoppedHostDiagnostic('Fetch API cannot load http: /127.0.0.1:9999/v1/tasks/task_1/approvals due to access control checks.',origins),true)
})
test('live-host CORS writes and JavaScript errors remain failures',async()=>{
  const {stoppedHostDiagnostic}=await import('./runtime.mjs'),origins=new Set(['http://127.0.0.1:9999'])
  for(const message of ['TypeError: fetch failed','Fetch API cannot load http://127.0.0.1:8888/v1/tasks/task_1/events?limit=100 due to access control checks.','Fetch API cannot load http://127.0.0.1:9999/v1/tasks due to access control checks.','/127.0.0.1:9999/v1/tasks/task_1/approvals/approve_1 due to access control checks.','/127.0.0.1:9999/v1/tasks/task_1/turns due to access control checks.','Fetch API cannot load https://evil.invalid/v1/tasks/task_1/events?limit=100 due to access control checks.'])assert.equal(stoppedHostDiagnostic(message,origins),false)
})
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
  error: '', cancelRequested: false, recovered: false, protocolVersion: '1.9', name: '', pinned: false, archived: false, revision: '', createdAt: '' }
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'content-type': 'application/json' } })

test('diagnosis is read only and rejects incompatible or excessive facts', async () => {
  assert.equal(uncertain({ ...row, phase: 'VERIFIED', status: 'UNKNOWN' }), false)
  const data = { schemaVersion: 1, source: 'session-events', eventCount: 0, task: row, association: {}, configuration: {}, turnIds: [],
    issues: [], issuesLimited: false, sdkFactsSettled: false, pending: { uncertainCalls: [], unsettledCalls: [], pendingRequests: [], pendingTerminals: [], limited: false } }
  let payload: unknown = data
  const calls: string[] = []
  const api = new Api(token, (async (url, init) => { assert.equal(init?.method, 'GET'); calls.push(String(url)); return json(payload) }) as typeof fetch)
  assert.equal((await api.diagnosis('task_1')).sdkFactsSettled, false)
  payload = { ...data, schemaVersion: 2 }; await assert.rejects(api.diagnosis('task_1'), ApiError)
  payload = { ...data, issues: Array.from({ length: 13 }, () => ({})) }; await assert.rejects(api.diagnosis('task_1'), ApiError)
  payload = { ...data, pending: { ...data.pending, pendingRequests: Array(65).fill('step') } }; await assert.rejects(api.diagnosis('task_1'), ApiError)
  assert.ok(calls.every(p => p === '/v1/tasks/task_1/diagnosis'))
})

test('history pagination has bounded rows and explicit workspace filters', async () => {
  let value: unknown = { tasks: [row], next: 'cursor_1', more: true }
  const api = new Api(token, (async (path, init) => { assert.equal(init?.method, 'GET'); assert.ok(String(path).includes('profile=default')); return json(value) }) as typeof fetch)
  const page = await api.taskPage('default', 'archived')
  assert.equal(page.tasks[0].taskId, row.taskId); assert.equal(page.more, true)
  value = { tasks: Array(33).fill(row), next: '', more: false }
  await assert.rejects(api.taskPage('default'), ApiError)
})

test('session metadata uses revision and fork has an independent submission identity', async () => {
  const calls: { path: string; body: Record<string, unknown> }[] = []
  const api = new Api(token, (async (path, init) => { calls.push({ path: String(path), body: JSON.parse(String(init?.body)) }); return json(row) }) as typeof fetch)
  await api.metadata(row, { name: 'renamed', pinned: true, archived: false })
  await api.fork(row, 'event_1', 'fork_request')
  assert.equal(calls[0].body.revision, row.revision); assert.equal(calls[0].body.name, 'renamed')
  assert.deepEqual(calls[1].body, { eventId: 'event_1', requestId: 'fork_request' })
  assert.ok(calls[1].path.endsWith('/fork'))
})

test('export rejects substituted session identity', async () => {
  const api = new Api(token, (async () => json({ sessionId: 'other', content: '{}', sha256: 'a'.repeat(64), artifactsIncluded: false })) as typeof fetch)
  await assert.rejects(api.exportSession(row.taskId), ApiError)
})

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

test('continued submission lost response sends once and reconciliation is GET only', async () => {
  const calls: string[] = []
  const api = new Api(token, (async (url, init) => { calls.push(`${init!.method} ${url}`); throw new Error('offline') }) as typeof fetch)
  await assert.rejects(api.submitTurn('task_1', 'message_1', 'sensitive draft', 'message'), ApiError)
  assert.deepEqual(calls, ['POST /v1/tasks/task_1/turns'])
  await assert.rejects(api.turnStatus('task_1', 'message_1'), ApiError)
  assert.deepEqual(calls, ['POST /v1/tasks/task_1/turns', 'GET /v1/tasks/task_1/turns?request=message_1'])
})
test('turn paging rejects malformed states and oversized queue pages', async () => {
  const invalid = new Api(token, (async () => json({ submissions: Array(33).fill({}), more: false, next: 33, total: 33 })) as typeof fetch)
  await assert.rejects(invalid.turns('task_1'), ApiError)
  const bad = new Api(token, (async () => json({ task: row, submission: { submissionId: 'a'.repeat(64), kind: 'message', state: 'pretend-success', sequence: 2, turnId: '', status: '' } })) as typeof fetch)
  await assert.rejects(bad.turnStatus('task_1', 'message_1'), ApiError)
})
test('queue actions use submission identity and preserve task association', async () => {
  const hash = 'b'.repeat(64)
  const api = new Api(token, (async (url, init) => { assert.equal(url, `/v1/tasks/task_1/turns/${hash}/cancel`); assert.equal(init!.method, 'POST'); return json({ task: row, submission: { submissionId: hash, kind: 'supplement', state: 'CANCELLED', sequence: 2, turnId: '', status: '' } }) }) as typeof fetch)
  assert.equal((await api.turnAction('task_1', hash, 'cancel')).submission.state, 'CANCELLED')
})

test('model choice travels only with its admission and reconciliation remains read only', async () => {
  const bodies: unknown[] = []
  const api = new Api(token, (async (_url, init) => { if (init?.body) bodies.push(JSON.parse(String(init.body))); return json(row) }) as typeof fetch)
  await api.submit('default', 'goal', 'request_a', 'model_b')
  assert.deepEqual(bodies, [{ profile: 'default', goal: 'goal', requestId: 'request_a', modelId: 'model_b' }])
  await api.submission('request_a')
  assert.equal(bodies.length, 1)
})
test('model catalog retains protocol capabilities and rejects unsupported protocol', async () => {
  const model = { id: 'b', label: 'B', baseUrl: 'https://example.com/v1', model: 'model-b', apiKeyEnv: 'TEST_KEY', keyConfigured: true, disableThinking: false, protocol: 'responses', reasoningEffort: 'low', maxOutputTokens: 256, toolsSupported: true, streamSupported: true, streaming: true, pricing: { maxCost: '1' } }
  const api = new Api(token, (async () => json({ revision: 'r', selected: 'b', models: [model] })) as typeof fetch)
  assert.deepEqual((await api.models()).models[0], model)
  const invalid = new Api(token, (async () => json({ revision: 'r', selected: 'b', models: [{ ...model, protocol: 'unknown' }] })) as typeof fetch)
  await assert.rejects(invalid.models(), ApiError)
})
test('configuration check rejects claims of implicit network verification', async () => {
  const api = new Api(token, (async (_url, init) => { assert.deepEqual(JSON.parse(String(init?.body)), { modelId: 'b' }); return json({ valid: true, networkChecked: false, requestSent: false }) }) as typeof fetch)
  assert.equal((await api.checkModel('b')).requestSent, false)
  const invalid = new Api(token, (async () => json({ valid: true, networkChecked: true, requestSent: true })) as typeof fetch)
  await assert.rejects(invalid.checkModel('b'), ApiError)
})

const questionFixture = { questionId: 'q1', sessionId: 'task_1', turnId: 'turn1', callId: 'call1', question: 'Choose', mode: 'single', options: [{ id: 'a', label: 'A', description: '', recommended: true }, { id: 'b', label: 'B', description: '', recommended: false }], allowText: true, state: 'PENDING', expiresAt: '2026-10-18T10:00:00Z' }
test('question page validates exact session and bounded state', async () => {
  const api = new Api(token, (async () => json({ questions: [questionFixture], total: 1, hasWaiting: true })) as typeof fetch)
  assert.equal((await api.questions('task_1'))[0].state, 'PENDING')
  const wrong = new Api(token, (async () => json({ questions: [{ ...questionFixture, sessionId: 'other' }], total: 1, hasWaiting: true })) as typeof fetch)
  await assert.rejects(wrong.questions('task_1'), ApiError)
})
test('lost question answer is not reposted and reconciliation is GET only', async () => {
  const calls: string[] = []
  const api = new Api(token, (async (url, init) => { calls.push(init!.method + ' ' + url); throw Error('offline') }) as typeof fetch)
  await assert.rejects(api.answerQuestion(questionFixture as import('../src/api').UserQuestion,'request1',['a'],'',false),ApiError)
  await assert.rejects(api.questions('task_1'),ApiError)
  assert.deepEqual(calls,['POST /v1/tasks/task_1/questions/q1/answer','GET /v1/tasks/task_1/questions?offset=0'])
})
test('draft saves carry revision and never use answer endpoint', async () => {
  const api = new Api(token, (async (url, init) => { assert.equal(url,'/v1/tasks/task_1/questions/q1/draft'); const body = JSON.parse(String(init?.body)); assert.equal(body.revision,3); assert.equal(body.requestId,undefined); return json({ selected: ['a'], text: 'draft', revision: 4 }) }) as typeof fetch)
  assert.equal((await api.questionDraft(questionFixture as import('../src/api').UserQuestion,['a'],'draft',3)).revision,4)
})


test('file references and links fail closed for active protocols and traversal', async () => {
  const {fileReference,linkTarget}=await import('../src/content.ts')
  assert.deepEqual(fileReference('src/Main.java#L42'),{path:'src/Main.java',line:42})
  assert.deepEqual(fileReference('src/测试.java:7'),{path:'src/测试.java',line:7})
  for(const path of ['../secret.txt','/etc/passwd','C:/secret.txt','javascript:alert(1)','data:text/html,x','file:///x','src/a.ts?x'])
    assert.equal(linkTarget(path),'blocked',path)
  assert.equal(linkTarget('https://example.test/docs'),'external')
  assert.equal(linkTarget('https://user:secret@example.test/docs'),'blocked')
})
test('content pages reject missing sanitization, zero progress and overlong pages',async()=>{
  const {contentPage}=await import('../src/api.ts')
  const valid={text:'abc',offset:0,nextOffset:3,more:false,totalCharacters:3,contentSha256:'a'.repeat(64),startLine:1,source:'recorded-event',sanitized:true}
  assert.equal(contentPage(valid).text,'abc')
  for(const value of [{...valid,sanitized:false},{...valid,nextOffset:4},{...valid,text:'',nextOffset:0,more:true},{...valid,text:'x'.repeat(16385),nextOffset:16385,totalCharacters:16385}])
    assert.throws(()=>contentPage(value),ApiError)
})
test('inspection is scoped GET and rejects source or session substitution',async()=>{
  const calls: {path:string;init:RequestInit}[]=[]
  const sample={text:'abc',offset:0,nextOffset:3,more:false,totalCharacters:3,contentSha256:'a'.repeat(64),startLine:1,source:'recorded-event',sanitized:true,sessionId:'task_1',eventId:'event_1'}
  let body=sample
  const api=new Api(token,(async(path,init)=>{calls.push({path:String(path),init:init!});return json(body)}) as typeof fetch)
  assert.equal((await api.content('task_1','content','event_1')).text,'abc')
  assert.equal(calls[0].init.method,'GET');assert.equal(calls[0].init.body,undefined)
  body={...sample,sessionId:'other'};await assert.rejects(api.content('task_1','content','event_1'),ApiError)
})
test('temporary display rejects oversized content and cross-session responses',async()=>{
  let body={sessionId:'task_1',source:'temporary-model-stream',state:'STREAMING',text:'pending',sourceEvent:'event_1',truncated:false}
  const api=new Api(token,(async()=>json(body)) as typeof fetch)
  assert.equal((await api.display('task_1')).state,'STREAMING')
  body={...body,text:'x'.repeat(65537)};await assert.rejects(api.display('task_1'),ApiError)
  body={...body,text:'',sessionId:'other'};await assert.rejects(api.display('task_1'),ApiError)
})

test('tool projection association includes the turn even for imported duplicate ids',async()=>{
  const {toolAssociation}=await import('../src/content.ts')
  assert.notEqual(toolAssociation({turnId:'first',callId:'same'}),toolAssociation({turnId:'second',callId:'same'}))
  assert.equal(toolAssociation({callId:'legacy'}),':legacy')
})

test('skill catalog validates names hashes tool association and never posts', async () => {
  const skill = { id:'guide', description:'Guide', root:'project', directory:'guide', text:'body', sha256:'a'.repeat(64), tool:'skill_guide__load', untrusted:true }
  let body: unknown = { snapshot:true, skills:[skill] }
  const calls: string[] = []
  const api = new Api(token, (async (url, init) => { calls.push(init!.method + ' ' + url); return json(body) }) as typeof fetch)
  assert.equal((await api.skills('default'))[0].id, 'guide')
  for (const skills of [[skill,skill],[{...skill,tool:'run_command'}],[{...skill,id:'../outside'}],[{...skill,sha256:'invalid'}],[{...skill,untrusted:false}]]) {
    body = { snapshot:true, skills }; await assert.rejects(api.skills('default'), ApiError)
  }
  assert.ok(calls.every(c => c === 'GET /v1/skills?profile=default'))
})

test('actual tool inventory rejects duplicate names invalid effects and false records', async () => {
  const tool = { name:'mcp_notes__read_resource', description:'Untrusted', sideEffect:'READ_ONLY' }
  let body: unknown = { recorded:true, tools:[tool] }
  const api = new Api(token, (async () => json(body)) as typeof fetch)
  assert.equal((await api.tools('task_1')).tools[0].sideEffect, 'READ_ONLY')
  for (const v of [{recorded:true,tools:[tool,tool]},{recorded:true,tools:[{...tool,sideEffect:'ALLOW_ALL'}]},{recorded:false,tools:[tool]}]) {
    body = v; await assert.rejects(api.tools('task_1'), ApiError)
  }
})

test('content search validates source and exact workspace session archive scope', async () => {
  const input={text:'alpha',mode:'tokens' as const,profile:'default',view:'active',sessionId:'task_1',cursor:'',limit:20,rebuild:false}
  const hit={profile:'default',sessionId:'task_1',eventId:'event_1',turnId:'',name:'Search',archived:false,type:'user.message',timestamp:'2026-10-18T00:00:00Z',snippet:'alpha',score:1}
  const sample={source:'sanitized-session-content',matches:[hit],total:1,more:false,next:'',snapshotId:'snapshot_1',reusedEvents:0,rebuiltSessions:1}
  let body:unknown=sample
  const api=new Api(token,(async()=>json(body)) as typeof fetch)
  assert.equal((await api.searchContent(input)).matches[0].eventId,'event_1')
  for(const change of [{profile:'other'},{sessionId:'other'},{archived:true},{score:0},{timestamp:'invalid'}]) {
    body={...sample,matches:[{...hit,...change}]};await assert.rejects(api.searchContent(input),ApiError)
  }
  body={...sample,source:'raw-log'};await assert.rejects(api.searchContent(input),ApiError)
})

test('content search rejects duplicate matches and nonprogressing pagination', async () => {
  const input={text:'alpha',mode:'phrase' as const,profile:'',view:'all',sessionId:'',cursor:'',limit:20,rebuild:false}
  const hit={profile:'default',sessionId:'task_1',eventId:'event_1',turnId:'',name:'Search',archived:false,type:'user.message',timestamp:'2026-10-18T00:00:00Z',snippet:'alpha',score:100}
  let body:unknown
  const api=new Api(token,(async()=>json(body)) as typeof fetch)
  for(const value of [{matches:[hit,hit],total:2,more:false,next:''},{matches:[],total:1,more:true,next:'cursor'},{matches:[hit],total:1,more:false,next:'cursor'},{matches:[hit],total:4097,more:false,next:''}]) {
    body={source:'sanitized-session-content',snapshotId:'snapshot_1',reusedEvents:0,rebuiltSessions:0,...value}
    await assert.rejects(api.searchContent(input),ApiError)
  }
})
