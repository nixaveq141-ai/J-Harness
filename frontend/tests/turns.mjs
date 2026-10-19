import { browserType as chromium, stoppedHostDiagnostic } from './runtime.mjs'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { createHash, randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'

const root = resolve(import.meta.dirname, '../..'), out = join(root, '.build-cache'), workspace = join(out, `M10.5.3-turns-${Date.now()}`)
const config = join(workspace, 'fixture.json'), jar = join(root, 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar')
const java = join(process.env.JAVA_HOME, 'bin', process.platform==='win32'?'java.exe':'java'), token = 'turn_browser_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ', key = `synthetic-turn-${randomUUID()}`
const checks = [], screenshots = [], wire = [], errors = [], holds = new Map()
const stoppedOrigins = new Set(), transportDiagnostics = []
let host, base, output = '', browser, context, page, id, submissionPosts = 0
const wait = ms => new Promise(r => setTimeout(r, ms))
async function until(test, timeout = 30000) { const start = Date.now(); while (Date.now()-start < timeout) { if (await test()) return; await wait(80) }; throw new Error('Turn fixture condition timed out') }
function check(name, condition = true) { assert.ok(condition, name); checks.push({ name, passed: true }); console.log('PASS', name) }
const provider = createServer(async (req, response) => {
  let text = ''; for await (const chunk of req) text += chunk
  const body = JSON.parse(text), last = body.messages.findLastIndex(m => m.role === 'user'), goal = body.messages[last]?.content ?? ''
  const toolsDone = body.messages.slice(last+1).some(m => m.role === 'tool'), n = wire.length
  wire.push({ authenticated: req.headers.authorization === `Bearer ${key}`, body })
  const send = () => {
    if (response.destroyed) return
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify({ id: `fixture-${n}`, model: 'turn-fixture', choices: [{ index: 0, finish_reason: toolsDone ? 'stop' : 'tool_calls', message: toolsDone ? { role: 'assistant', content: 'Verified read for this input.' } : { role: 'assistant', content: null, tool_calls: [{ id: `read-${n}`, type: 'function', function: { name: 'read_file', arguments: JSON.stringify({ path: 'note.txt' }) } }] } }], usage: { prompt_tokens: 20, completion_tokens: 5, total_tokens: 25 } }))
  }
  if (String(goal).startsWith('slow-') && !toolsDone) holds.set(String(goal), { response, send })
  else send()
})
await mkdir(workspace, { recursive: true }); await writeFile(join(workspace, 'note.txt'), 'turn evidence\n')
await new Promise(r => provider.listen(0, '127.0.0.1', r))
await writeFile(config, JSON.stringify({ baseUrl: `http://127.0.0.1:${provider.address().port}`, model: 'turn-fixture', apiKeyEnv: 'JHARNESS_TURN_KEY', maxSteps: 3, durationSeconds: 90, requestTimeoutSeconds: 80, ecosystem: { web: { port: 0 } } }))
const env = { ...process.env, JHARNESS_WEB_TOKEN: token, JHARNESS_TURN_KEY: key }
async function start() {
  base = undefined; output = ''
  host = spawn(java, ['-Dfile.encoding=UTF-8', '-jar', jar, '--workspace', workspace, '--config', config, '--require-read', 'note.txt', 'serve'], { cwd: root, env, stdio: ['pipe','pipe','pipe'], windowsHide: true })
  host.stdout.on('data', d => { output += d; base = output.match(/WEB (http:\/\/127\.0\.0\.1:\d+)/)?.[1] }); host.stderr.on('data', d => { output += d })
  await until(() => base || host.exitCode !== null); assert.ok(base, 'Fixture host failed to start')
}
async function stop() { if (host && host.exitCode === null) { stoppedOrigins.add(base); host.stdin.end(':quit\n'); await until(() => host.exitCode !== null); assert.equal(host.exitCode, 0) } }
async function api(path, body, client = context.request) {
  const r = await client.fetch(base+path, { method: body === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}`, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, ...(body === undefined ? {} : { data: body }) })
  return { status: r.status(), body: await r.json() }
}
const taskPath = suffix => `/v1/tasks/${id}${suffix}`
async function settled(turns) {
  await until(async () => (await api(taskPath(''))).body.phase === 'FINISHED' && (await events()).filter(e => e.type === 'turn.started').length >= turns)
}
async function events() {
  const rows = []; let after = ''
  do { const value = (await api(taskPath(`/events?limit=100${after ? '&after='+after : ''}`))).body; rows.push(...value.events); after = value.more ? value.next : '' } while (after)
  return rows
}
async function connected() { await page.goto(base); await page.locator('.connection-state').filter({ hasText: '本机服务已连接' }).waitFor(); if (id && await page.evaluate(() => sessionStorage.getItem('jh-selected')) !== id) { await page.locator('.task-item').first().waitFor(); await page.locator('.task-item').first().click() } }
async function send(text, kind = 'message', twice = false) {
  await page.getByLabel('后续消息', { exact: true }).fill(text); await page.getByLabel('消息方式', { exact: true }).selectOption(kind)
  const button = page.getByRole('button', { name: '发送后续消息', exact: true })
  if (twice) await button.evaluate(e => { e.click(); e.click() }); else await button.click()
  await until(async () => await page.getByLabel('后续消息', { exact: true }).inputValue() === '')
}
async function queue() { const q = page.locator('.turn-queue'); await q.waitFor(); if (!(await q.evaluate(e => e.open))) await q.locator('summary').click(); return q }
async function shot(name) { const path = join(out, `M10.5.3-${name}.png`); await page.screenshot({ path, fullPage: true }); screenshots.push({ path: path.replace(root+'\\','').replace(root+'/',''), sha256: createHash('sha256').update(await readFile(path)).digest('hex') }) }
async function maintain(args) {
  const process = spawn(java, ['-jar', jar, '--workspace', workspace, ...args], { cwd: root, env, stdio: ['ignore','pipe','pipe'], windowsHide: true }); let text = ''
  process.stdout.on('data', d => { text += d }); process.stderr.on('data', d => { text += d })
  await until(() => process.exitCode !== null); assert.equal(process.exitCode, 0, text.replaceAll(key,'[REDACTED]'))
}
try {
  await start(); browser = await chromium.launch(process.env.JHARNESS_BROWSER_CHANNEL ? { channel: process.env.JHARNESS_BROWSER_CHANNEL } : {})
  context = await browser.newContext({ viewport: { width: 1440, height: 1100 } }); page = await context.newPage(); page.on('pageerror', e => {if(stoppedHostDiagnostic(e.message,stoppedOrigins))transportDiagnostics.push(e.message);else errors.push(e.message)})
  page.on('request', r => { if (r.method() === 'POST' && /\/turns$/.test(r.url())) submissionPosts++ })
  await connected(); await page.getByLabel('任务目标').fill('first input'); await page.getByRole('button', { name: '创建任务', exact: true }).click()
  await until(async () => id = await page.evaluate(() => sessionStorage.getItem('jh-selected'))); await settled(1)
  await page.getByLabel('后续消息', { exact: true }).waitFor()
  check('completed session retains a continuation composer', await page.getByRole('heading').first().isVisible())
  await page.getByLabel('后续消息', { exact: true }).fill(`private draft ${key}`)
  await until(async () => await page.getByRole('button', { name: '新建任务', exact: true }).isDisabled() && await page.getByRole('button', { name: '工作台设置', exact: true }).isDisabled())
  check('unsent draft protects navigation and never enters browser storage', await page.getByRole('button', { name: '新建任务', exact: true }).isDisabled() && await page.getByRole('button', { name: '工作台设置', exact: true }).isDisabled() && !JSON.stringify(await page.evaluate(() => ({ ...localStorage, ...sessionStorage }))).includes(key))
  await page.getByRole('button', { name: '丢弃草稿', exact: true }).click()
  await send('second input', 'message', true); await settled(2)
  check('double click sends one independently admitted continuation', submissionPosts === 1 && (await api(taskPath('/turns'))).body.total === 1)
  let drop = true
  await page.route('**/v1/tasks/*/turns', async route => { if (drop && route.request().method() === 'POST') { drop = false; await route.fetch(); await route.abort('failed') } else await route.continue() })
  await page.getByLabel('后续消息', { exact: true }).fill(`third input ${key}`); await page.getByRole('button', { name: '发送后续消息', exact: true }).click()
  await page.getByRole('button', { name: '核对消息受理', exact: true }).waitFor(); assert.equal(await page.getByLabel('后续消息', { exact: true }).isDisabled(), true); assert.equal(await page.getByLabel('消息方式', { exact: true }).isDisabled(), true); await settled(3)
  const postsBefore = submissionPosts; page.once('dialog', d => void d.accept()); await page.reload(); await page.getByRole('button', { name: '核对消息受理', exact: true }).waitFor()
  await page.getByRole('button', { name: '核对消息受理', exact: true }).click(); await until(async () => !(await page.getByRole('button', { name: '核对消息受理', exact: true }).count()))
  check('lost response and refresh reconcile by GET without duplicate POST', submissionPosts === postsBefore && await page.getByLabel('后续消息', { exact: true }).inputValue() === '')
  const third = await events(), starts = third.filter(e => e.type === 'turn.started')
  check('three actual SDK turns share original session and distinct loop turn identities', starts.length === 3 && new Set(starts.map(e => e.turnId)).size === 3 && starts.every(e => e.sessionId === id) && (await api('/v1/tasks?view=all')).body.historyCount === 1)
  check('prepared request history includes prior turns with secrets sanitized', wire.some(w => w.body.messages.filter(m => m.role === 'user').length === 3) && !JSON.stringify(third).includes(key) && !JSON.stringify(wire.map(w => w.body)).includes(key))
  await send('slow-current'); await until(() => holds.has('slow-current'))
  await send('cancel this queued input'); await send('future supplement', 'supplement')
  await queue(); const cancelledRow = page.locator('.turn-queue li').filter({ hasText: '第 5 次输入' }); await cancelledRow.getByRole('button', { name: '取消排队', exact: true }).click()
  await until(async () => (await api(taskPath('/turns'))).body.submissions.some(s => s.sequence === 5 && s.state === 'CANCELLED'))
  check('busy input queue supports cancellation and defers supplements', !JSON.stringify(wire.at(-1).body).includes('future supplement'))
  await page.locator('.timeline').evaluate(e => { e.scrollTop = 0; e.dispatchEvent(new Event('scroll', { bubbles: true })) })
  holds.get('slow-current').send(); await settled(5)
  await until(async () => (await page.locator('.topbar').innerText()).includes('已完成'))
  check('background queue settlement preserves reading position in earlier history', await page.locator('.timeline').evaluate(e => e.scrollTop < 60))
  check('supplement applies at next full-turn boundary and cancelled text never applies', wire.some(w => w.body.messages.some(m => m.role === 'user' && m.content === 'future supplement')) && !wire.some(w => w.body.messages.some(m => m.role === 'user' && m.content === 'cancel this queued input')))
  const secondContext = await browser.newContext(), beforeRace = wire.length
  const race = { requestId: 'two-clients', text: 'one message from two clients', kind: 'message' }
  const results = await Promise.all([api(taskPath('/turns'),race),api(taskPath('/turns'),race,secondContext.request)])
  assert.ok(results.every(r => r.status === 202)); await settled(6)
  check('two concurrent clients receive the same submission and one execution', results[0].body.submission.submissionId === results[1].body.submission.submissionId && wire.length === beforeRace+2)
  await secondContext.close(); await until(async () => (await page.locator('.topbar').innerText()).includes('已完成')); await shot('turns-desktop')
  await page.getByRole('button', { name: '切换深色主题', exact: true }).click(); await wait(300); await shot('turns-dark')
  await page.setViewportSize({ width: 390, height: 844 }); await shot('turns-mobile')
  check('mobile continuation has no horizontal overflow and keyboard input is reachable', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth) && await page.getByLabel('后续消息', { exact: true }).isVisible())
  await page.getByLabel('后续消息', { exact: true }).focus(); await page.keyboard.type('discard me'); await page.getByRole('button', { name: '丢弃草稿', exact: true }).click()
  await page.setViewportSize({ width: 1440, height: 1100 })
  await send('slow-exit'); await until(() => holds.has('slow-exit')); await send('paused until explicit start')
  await stop(); const atExit = wire.length; await start(); await connected()
  await page.getByLabel('后续消息', { exact: true }).waitFor(); await queue()
  check('restart does not replay active or queued messages and UNKNOWN blocks continuing', wire.length === atExit && await page.getByLabel('后续消息', { exact: true }).isDisabled() && (await api(taskPath('/turns'))).body.submissions.at(-1).state === 'PAUSED')
  await stop(); await until(() => holds.get('slow-exit').response.destroyed)
  const raw = (await readFile(join(workspace, '.jharness/sessions', `${id}.jsonl`), 'utf8')).trim().split('\n').slice(1).map(JSON.parse)
  const pendingRequest = raw.filter(e => e.type === 'model.requested').at(-1)
  const reason = 'Synthetic loopback fixture: owned host stopped normally, provider socket closed before held response, no tools sent for pending request, no live executor; inspect exact request and preserve failure, no replay.'
  await maintain(['sessions','verify-request','--session',id,'--event',pendingRequest.eventId,'--reason',reason])
  await maintain(['sessions','verify-web','--session',id,'--web-catalog','web-catalog','--reason',reason])
  await start(); await connected(); await queue()
  check('offline verification leaves queued message paused until explicit operator action', wire.length === atExit && (await api(taskPath('/turns'))).body.submissions.at(-1).state === 'PAUSED')
  await page.getByRole('button', { name: '开始这条消息', exact: true }).click(); await settled(8)
  check('explicit start continues same session once after recovery', (await api(taskPath('/turns'))).body.submissions.at(-1).state === 'FINISHED' && wire.length === atExit+2 && (await api('/v1/tasks?view=all')).body.historyCount === 1)
  const safety={errors:errors.map(e=>String(e).replaceAll(key,'[REDACTED]').replaceAll(token,'[REDACTED]')),authenticated:wire.every(w=>w.authenticated),outputRedacted:!output.includes(key),storageRedacted:!JSON.stringify(await page.evaluate(()=>({...localStorage,...sessionStorage}))).includes(key)}
  if(safety.errors.length||!safety.authenticated||!safety.outputRedacted||!safety.storageRedacted)console.log('SAFETY_DIAGNOSTIC',JSON.stringify(safety))
  check('no browser errors or credential leaks in persistent UI storage', safety.errors.length===0&&safety.authenticated&&safety.outputRedacted&&safety.storageRedacted)
  await stop()
  await writeFile(join(out,'M10.5.3-turns-results.json'),JSON.stringify({ status:'PASS',capturedAt:new Date().toISOString(),checks,screenshots,transportDiagnostics,jarSha256:createHash('sha256').update(await readFile(jar)).digest('hex'),browserEngine:process.env.JHARNESS_BROWSER_ENGINE??'chromium',browserVersion:browser.version(),paidRequests:0,hostStopped:host.exitCode===0,modelRequests:wire.length },null,2)+'\n')
} catch (e) {
  console.log('BROWSER_DIAGNOSTIC',JSON.stringify(errors).replaceAll(key,'[REDACTED]').replaceAll(token,'[REDACTED]'))
  if (page && !page.isClosed()) await page.screenshot({ path:join(out,'M10.5.3-turns-failure.png'),fullPage:true })
  await writeFile(join(out,'M10.5.3-turns-failure.txt'),(String(e.stack)+'\n'+output).replaceAll(key,'[REDACTED]').replaceAll(token,'[REDACTED]')); throw e
} finally { await stop().catch(()=>host?.kill()); await browser?.close(); provider.closeAllConnections(); await new Promise(r=>provider.close(r)) }
