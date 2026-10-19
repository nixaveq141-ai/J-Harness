import { browserType as chromium, stoppedHostDiagnostic } from './runtime.mjs'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { createHash, randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'

const stoppedOrigins=new Set(),transportDiagnostics=[]
const root = resolve(import.meta.dirname, '../..'), out = join(root, '.build-cache'), run = `M10.5.2-history-${Date.now()}`
const baseDir = join(out, run), primary = join(baseDir, 'primary'), secondary = join(baseDir, 'secondary'), config = join(primary, 'host-config.json')
const jar = join(root, 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'), java = join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
const key = `synthetic-history-${randomUUID()}`, token = 'history_browser_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ'
const checks = [], screenshots = [], wire = [], errors = []
let host, hostOutput = '', base, browser, context, page
const wait = ms => new Promise(r => setTimeout(r, ms))
async function until(test, timeout = 25000) { const start = Date.now(); while (Date.now() - start < timeout) { if (await test()) return; await wait(80) } throw new Error('History fixture condition timed out') }
function check(name, condition = true) { assert.ok(condition, name); checks.push({ name, passed: true }); console.log('PASS', name) }
const provider = createServer(async (req, response) => {
  let text = ''; for await (const chunk of req) text += chunk
  const body = JSON.parse(text), hasResult = body.messages.some(m => m.role === 'tool')
  const outside = body.messages.some(m => m.role === 'user' && String(m.content).includes('outside-root'))
  wire.push({ authenticated: req.headers.authorization === `Bearer ${key}`, model: body.model })
  response.writeHead(200, { 'Content-Type': 'application/json' })
  response.end(JSON.stringify({ id: 'history-fixture', model: body.model, choices: [{ index: 0, finish_reason: hasResult ? 'stop' : 'tool_calls', message: hasResult ? { role: 'assistant', content: 'Recorded local read.' } : { role: 'assistant', content: null, tool_calls: [{ id: 'history-read', type: 'function', function: { name: 'read_file', arguments: JSON.stringify({ path: outside ? '../outside.txt' : 'note.txt' }) } }] } }], usage: { prompt_tokens: 20, completion_tokens: 5, total_tokens: 25 } }))
})
await mkdir(primary, { recursive: true }); await mkdir(secondary, { recursive: true })
await writeFile(join(primary, 'note.txt'), 'alpha workspace source\n'); await writeFile(join(secondary, 'note.txt'), 'beta workspace source\n')
await writeFile(join(baseDir, 'outside.txt'), 'outside sentinel must not be read\n')
await new Promise(r => provider.listen(0, '127.0.0.1', r))
await writeFile(config, JSON.stringify({ baseUrl: `http://127.0.0.1:${provider.address().port}`, model: 'history-fixture', apiKeyEnv: 'JHARNESS_HISTORY_KEY', maxSteps: 3, durationSeconds: 20, requestTimeoutSeconds: 5, ecosystem: { web: { port: 0, maxTasks: 32 } } }))
async function start() {
  base = undefined; hostOutput = ''
  host = spawn(java, ['-Dfile.encoding=UTF-8', '-jar', jar, '--workspace', primary, '--config', config, '--require-read', 'note.txt', 'serve'], { cwd: root, env: { ...process.env, JHARNESS_WEB_TOKEN: token, JHARNESS_HISTORY_KEY: key }, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true })
  host.stdout.on('data', d => { hostOutput += d; base = hostOutput.match(/WEB (http:\/\/127\.0\.0\.1:\d+)/)?.[1] }); host.stderr.on('data', d => { hostOutput += d })
  await until(() => base || host.exitCode !== null); assert.ok(base, 'History host failed: ' + hostOutput)
}
async function stop() { if (host && host.exitCode === null) { stoppedOrigins.add(base);host.stdin.end(':quit\n'); await until(() => host.exitCode !== null); assert.equal(host.exitCode, 0) } }
async function api(path, body) {
  const r = await context.request.fetch(base + path, { method: body === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}`, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, ...(body === undefined ? {} : { data: body }), timeout: 30000 })
  return { status: r.status(), body: await r.json() }
}
async function connected() { await page.goto(base); await page.locator('.connection-state').filter({ hasText: '本机服务已连接' }).waitFor() }
async function ended(id) { await until(async () => ['FINISHED', 'UNKNOWN', 'VERIFIED'].includes((await api(`/v1/tasks/${id}`)).body.phase)); assert.equal((await api(`/v1/tasks/${id}`)).body.phase, 'FINISHED') }
async function create(goal) {
  const prior = await page.evaluate(() => sessionStorage.getItem('jh-selected'))
  await page.getByRole('button', { name: '新建任务', exact: true }).click(); await page.getByLabel('任务目标').fill(goal); await page.getByRole('button', { name: '创建任务', exact: true }).click()
  await until(async () => { const id = await page.evaluate(() => sessionStorage.getItem('jh-selected')); return id && id !== prior })
  const id = await page.evaluate(() => sessionStorage.getItem('jh-selected')); await ended(id); return id
}
async function shot(name) { const path = join(out, `M10.5.2-${name}.png`); await page.screenshot({ path, fullPage: true }); screenshots.push({ path: path.replace(root + '\\', '').replace(root + '/', ''), sha256: createHash('sha256').update(await readFile(path)).digest('hex') }) }
async function manage() { const panel = page.locator('.session-actions'); await panel.waitFor(); if (!(await panel.evaluate(e => e.open))) await panel.locator('summary').click(); return panel }
try {
  await start(); browser = await chromium.launch(process.env.JHARNESS_BROWSER_CHANNEL ? { channel: process.env.JHARNESS_BROWSER_CHANNEL } : {})
  context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true }); page = await context.newPage(); page.on('pageerror',e=>{if(stoppedHostDiagnostic(e.message,stoppedOrigins))transportDiagnostics.push(e.message);else errors.push(e.message)})
  await connected()
  await page.getByText('注册工作区', { exact: true }).click()
  await page.getByLabel('工作区名称', { exact: true }).fill('Beta project'); await page.getByLabel('工作区绝对目录', { exact: true }).fill(secondary); await page.getByRole('button', { name: '注册并选择', exact: true }).click()
  await until(async () => (await api('/v1/workspaces')).body.workspaces.length === 2)
  const workspaces = (await api('/v1/workspaces')).body.workspaces, beta = workspaces.find(w => w.name === 'Beta project').id
  await until(async () => await page.getByLabel('浏览工作区').inputValue() === beta)
  check('operator registers canonical workspace and selects it without model calls', wire.length === 0 && await page.getByLabel('任务配置').inputValue() === beta)
  check('duplicate and metadata root registration are rejected', (await api('/v1/workspaces', { path: secondary, name: 'alias' })).status === 409 && (await api('/v1/workspaces', { path: join(primary, '.jharness'), name: 'metadata' })).status >= 400)
  const betaTask = await create('Read beta project')
  const betaEvents = (await api(`/v1/tasks/${betaTask}/events?limit=100`)).body.events
  check('actual SDK read is bound to selected root', JSON.stringify(betaEvents).includes('beta workspace source') && !JSON.stringify(betaEvents).includes('alpha workspace source'))
  let panel = await manage()
  await page.getByLabel('会话名称', { exact: true }).fill('Beta investigation')
  await until(async () => await page.getByRole('button', { name: '新建任务', exact: true }).isDisabled())
  check('unsaved session name protects navigation', await page.getByRole('button', { name: '新建任务', exact: true }).isDisabled())
  await page.getByRole('button', { name: '保存名称', exact: true }).click(); await page.getByRole('heading', { name: 'Beta investigation', exact: true }).waitFor()
  await page.getByRole('button', { name: '置顶会话', exact: true }).click(); await page.getByRole('button', { name: '取消置顶', exact: true }).waitFor()
  check('rename and pin are persisted facts', (await api(`/v1/tasks/${betaTask}`)).body.name === 'Beta investigation' && (await api(`/v1/tasks/${betaTask}`)).body.pinned)
  const download = page.waitForEvent('download'); await page.getByRole('button', { name: '导出会话', exact: true }).click(); const exportPath = join(out, 'M10.5.2-session-export.jsonl'); await (await download).saveAs(exportPath)
  const exported = await readFile(exportPath, 'utf8')
  check('export is complete valid JSONL with original associations and no credential', exported.trim().split('\n').every(line => JSON.parse(line).sessionId === betaTask) && exported.includes('beta workspace source') && !exported.includes(key) && !exported.includes(token))
  const beforeFork = wire.length
  await page.getByRole('button', { name: '分叉会话', exact: true }).click()
  await until(async () => await page.evaluate(() => sessionStorage.getItem('jh-selected')) !== betaTask)
  const fork = await page.evaluate(() => sessionStorage.getItem('jh-selected')); await ended(fork); panel = await manage()
  check('fork creates independent session without executing model', (await api(`/v1/tasks/${fork}`)).body.name === 'Beta investigation \u00b7 \u5206\u53c9' && fork !== betaTask && wire.length === beforeFork && (await api(`/v1/tasks/${fork}/events?limit=100`)).body.events.some(e => e.type === 'session.forked' && e.payload.parentSessionId === betaTask))
  await page.getByRole('button', { name: '归档会话', exact: true }).click(); await page.getByRole('button', { name: '恢复会话', exact: true }).waitFor()
  check('archival preserves history and switches archived view', (await api(`/v1/tasks/${fork}`)).body.archived && (await api(`/v1/tasks/${fork}/events?limit=100`)).body.events.some(e => e.type === 'session.archived'))
  await page.getByRole('button', { name: '恢复会话', exact: true }).click(); await page.getByRole('button', { name: '归档会话', exact: true }).waitFor()
  check('restoration retains all original facts', !(await api(`/v1/tasks/${fork}`)).body.archived && (await api(`/v1/tasks/${fork}/events?limit=100`)).body.events.some(e => e.type === 'session.restored'))
  await shot('session-management')
  await page.getByLabel('浏览工作区').selectOption('default')
  const alphaTask = await create('Read primary project')
  check('workspace switch changes next SDK root and filtered history', JSON.stringify((await api(`/v1/tasks/${alphaTask}/events?limit=100`)).body).includes('alpha workspace source') && (await api('/v1/tasks?profile=default')).body.tasks.every(t => t.profile === 'default'))
  const outsideTask = await create('outside-root read must fail')
  const outsideEvents = (await api(`/v1/tasks/${outsideTask}/events?limit=100`)).body.events
  check('model cannot escape root or read sibling sentinel', outsideEvents.some(e => e.type === 'tool.completed' && e.payload.status !== 'SUCCESS') && !JSON.stringify(outsideEvents).includes('outside sentinel must not be read') && await readFile(join(baseDir, 'outside.txt'), 'utf8') === 'outside sentinel must not be read\n')
  const ids = []
  for (let i = 0; i < 65; i++) {
    const value = await api('/v1/tasks', { profile: 'default', goal: `history cycle ${i}`, requestId: `browser-history-${i}` }); assert.equal(value.status, 202)
    ids.push(value.body.taskId); await ended(value.body.taskId)
  }
  const first = (await api('/v1/tasks?profile=default&limit=24&view=all')).body
  const seen = new Set(first.tasks.map(t => t.taskId)); let next = first.next
  while (next) { const value = (await api(`/v1/tasks?profile=default&limit=24&view=all&after=${next}`)).body; value.tasks.forEach(t => { assert.ok(!seen.has(t.taskId)); seen.add(t.taskId) }); next = value.next }
  check('65 completed production CLI tasks exceed hot limit and page without loss', ids.every(id => seen.has(id)) && first.hotCount <= 32 && first.hotLimit === 32 && first.historyCount >= 65)
  await until(async () => (await page.locator('.task-item').first().textContent()).includes(ids.at(-1).slice(-64, -56)) && await page.getByRole('button', { name: '下一页会话' }).isEnabled())
  const newest = await page.locator('.task-item').first().textContent()
  await page.getByRole('button', { name: '下一页会话' }).evaluate(e => { e.click(); e.click() }); await until(async () => await page.getByRole('button', { name: '上一页会话' }).isEnabled() && await page.locator('.task-item').first().textContent() !== newest)
  check('browser reads bounded next page', await page.locator('.history-pages > span').textContent() === '2' && await page.locator('.task-item').count() <= 32 && await page.getByRole('button', { name: '上一页会话' }).isEnabled())
  await page.getByRole('button', { name: '上一页会话' }).click(); await until(async () => await page.locator('.task-item').first().textContent() === newest)
  await shot('history-pages')
  await page.getByRole('button', { name: '切换深色主题', exact: true }).click(); await shot('dark')
  await page.setViewportSize({ width: 390, height: 844 }); await page.getByRole('button', { name: '展开任务导航', exact: true }).click(); await shot('mobile')
  check('narrow navigation and workspace controls have no document overflow', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  const select = page.getByLabel('浏览工作区'); await select.focus(); await page.keyboard.press('Home'); await page.keyboard.press('Tab')
  check('workspace picker is keyboard focusable', await select.evaluate(e => e.tagName === 'SELECT'))
  await page.setViewportSize({ width: 1440, height: 1000 })
  const beforeRestart = wire.length; await stop(); await start(); await connected()
  check('restart restores registry, labels and complete history without model request', (await api('/v1/workspaces')).body.workspaces.some(w => w.id === beta) && (await api(`/v1/tasks/${betaTask}`)).body.name === 'Beta investigation' && wire.length === beforeRestart)
  const duplicate = await api('/v1/tasks', { profile: 'default', goal: 'history cycle 0', requestId: 'browser-history-0' })
  check('cold duplicate submission after restart returns original without execution', duplicate.body.taskId === ids[0] && wire.length === beforeRestart)
  const after = await api('/v1/tasks', { profile: 'default', goal: 'new after restart', requestId: 'after-restart' }); assert.equal(after.status, 202); await ended(after.body.taskId)
  check('restart permits further tasks while retaining original session', (await api(`/v1/tasks/${betaTask}`)).body.name === 'Beta investigation')
  check('no browser exceptions or credential leaks', errors.length === 0 && !hostOutput.includes(key) && !JSON.stringify(await page.evaluate(() => ({ localStorage, sessionStorage }))).includes(key) && wire.every(w => w.authenticated))
  const version = browser.version(); await stop()
  await writeFile(join(out, 'M10.5.2-history-results.json'), JSON.stringify({ status: 'PASS', capturedAt: new Date().toISOString(), browserEngine:process.env.JHARNESS_BROWSER_ENGINE??'chromium',browserVersion: version, checks, screenshots,transportDiagnostics, completedCycles: ids.length, modelRequests: wire.length, paidRequests: 0, hostStopped: host.exitCode === 0, jarSha256: createHash('sha256').update(await readFile(jar)).digest('hex') }, null, 2))
} catch (e) {
  if (page && !page.isClosed()) await page.screenshot({ path: join(out, 'M10.5.2-history-failure.png') })
  await writeFile(join(out, 'M10.5.2-history-failure.txt'), String(e.stack) + '\n' + hostOutput.replaceAll(key, '[REDACTED]').replaceAll(token, '[REDACTED]'))
  throw e
} finally { await stop().catch(() => host?.kill()); await browser?.close(); provider.closeAllConnections(); await new Promise(r => provider.close(r)) }
