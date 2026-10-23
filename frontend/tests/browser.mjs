import { browserType as chromium, stoppedHostDiagnostic, captureTaskAcceptance } from './runtime.mjs'
import { spawn } from 'node:child_process'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { resolve, delimiter, join } from 'node:path'
import { createHash } from 'node:crypto'
import assert from 'node:assert/strict'

const root = resolve(import.meta.dirname, '../..'), out = join(root, '.build-cache')
const token = 'm10_browser_fixture_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ'
const run = `M10-browser-${Date.now()}`, urlFile = join(out, `${run}.url`), evidenceFile = join(out, `${run}.json`)
const cp = ['harness-testkit/target/test-classes', 'harness-testkit/target/classes', 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'].map(p => join(root, p)).join(delimiter)
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java'
await mkdir(out, { recursive: true })
const host = spawn(java, ['-Dfile.encoding=UTF-8', '-cp', cp, 'io.jharness.testkit.M10BrowserHost', join(out, run), urlFile, evidenceFile], { cwd: root, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true })
let hostOutput = ''; host.stdout.on('data', d => { hostOutput += d }); host.stderr.on('data', d => { hostOutput += d })
const wait = ms => new Promise(r => setTimeout(r, ms))
const checks = [], screenshots = [], errors = [], requests = []
const browserCredentials = new Set()
const controlledOrigins = new Set(), transportDiagnostics = []
let browser, base, context, page
const check = (name, detail = true) => { assert.ok(detail, name); checks.push({ name, passed: true }); console.log('PASS', name) }
async function until(predicate, timeout = 15000) {
  const start = Date.now(); while (Date.now() - start < timeout) { if (await predicate()) return; await wait(100) }
  throw new Error('Timed out awaiting condition')
}
async function api(path, body) {
  const r = await context.request.fetch(base + path, { method: body === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}`, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, ...(body === undefined ? {} : { data: body }) })
  return { status: r.status(), body: await r.json() }
}
try {
  await until(() => existsSync(urlFile), 20000)
  base = await readFile(urlFile, 'utf8')
  browser = await chromium.launch(process.env.JHARNESS_BROWSER_CHANNEL ? { channel: process.env.JHARNESS_BROWSER_CHANNEL } : {})
  context = await browser.newContext({ viewport: { width: 1440, height: 960 }, acceptDownloads: true })
  page = await context.newPage()
  page.on('pageerror', e => {
    if (stoppedHostDiagnostic(e.message, controlledOrigins)) transportDiagnostics.push(e.message)
    else errors.push(e.message)
  })
  page.on('response', r => { if (new URL(r.url()).pathname === '/v1/browser-session') void r.json().then(v => browserCredentials.add(v.token)).catch(() => {}) })
  page.on('request', r => { if (r.url().includes('/v1/')) requests.push({ method: r.method(), path: new URL(r.url()).pathname }) })
  async function screenshot(name) {
    const p = join(out, `M10-${name}.png`); await page.screenshot({ path: p, fullPage: true }); screenshots.push(p)
  }
  const createCount = () => requests.filter(r => r.method === 'POST' && r.path === '/v1/tasks').length
  async function connect() { await page.locator('.connection-state').filter({ hasText: '本机服务已连接' }).waitFor() }
  async function reload() {
    controlledOrigins.add(base)
    try { await page.reload(); await connect() }
    finally { controlledOrigins.delete(base) }
  }
  async function newTask(profile, goal) {
    await page.getByRole('button', { name: '新建任务', exact: true }).click()
    await page.getByLabel('任务配置').selectOption(profile)
    await page.getByLabel('任务目标').fill(goal)
    const response = await captureTaskAcceptance(page, base, () => page.getByRole('button', { name: '创建任务', exact: true }).click())
    assert.equal(response.status, 202)
    const receipt = response.body
    assert.ok(typeof receipt.taskId === 'string' && receipt.taskId)
    await until(async () => await page.evaluate(() => sessionStorage.getItem('jh-selected')) === receipt.taskId && await page.locator('.task-item.selected').count() === 1)
    return receipt.taskId
  }
  async function ended(id) { await until(async () => ['FINISHED', 'UNKNOWN', 'VERIFIED'].includes((await api(`/v1/tasks/${id}`)).body.phase)) }
  await page.goto(base)
  await screenshot('connection')
  const invalid = await context.request.get(base + '/v1/info', { headers: { Authorization: 'Bearer invalid_token_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ' } })
  check('invalid API credentials rejected while browser enters automatically', invalid.status() === 401 && createCount() === 0 && await page.getByLabel('本机连接凭据').count() === 0)
  await connect(); await screenshot('welcome')
  const separator = page.getByRole('separator', { name: '调整导航宽度' })
  const width = Number(await separator.getAttribute('aria-valuenow'))
  await separator.focus(); await page.keyboard.press('ArrowRight')
  assert.equal(Number(await separator.getAttribute('aria-valuenow')), width + 16)
  await page.keyboard.press('ArrowLeft')
  check('sidebar resizing supports keyboard and bounded original geometry')
  const hoverButton = page.getByRole('button', { name: '切换深色主题', exact: true })
  const idleFill = await hoverButton.evaluate(el => getComputedStyle(el).backgroundColor)
  await hoverButton.hover()
  const hoverFill = await hoverButton.evaluate(el => getComputedStyle(el).backgroundColor)
  await page.mouse.down()
  const activeFill = await hoverButton.evaluate(el => getComputedStyle(el).backgroundColor)
  // Move off the button before releasing, so this check doesn't switch themes.
  await page.mouse.move(600, 100); await page.mouse.up()
  check('original hover and pointer-pressed feedback retained', idleFill !== hoverFill && hoverFill !== activeFill)
  await page.getByLabel('任务配置').selectOption('write')
  await page.getByLabel('任务目标').fill('写入 browser-note.txt，要求人工批准')
  const before = createCount()
  await page.getByRole('button', { name: '创建任务', exact: true }).evaluate(b => { b.click(); b.click() })
  await page.getByRole('button', { name: '批准本次操作', exact: true }).waitFor()
  const id = await page.evaluate(() => sessionStorage.getItem('jh-selected'))
  const approval = (await api(`/v1/tasks/${id}/approvals`)).body.approvals[0]
  const wrong = await api(`/v1/tasks/${id}/approvals/${approval.approvalId}`, { approved: true, reason: 'wrong association test', callId: 'wrong-call' })
  check('wrong approval association is rejected by actual Java API', wrong.status === 409)
  await screenshot('approval')
  await page.getByRole('button', { name: '批准本次操作', exact: true }).click()
  await ended(id)
  await page.getByText('已同步全部记录', { exact: true }).waitFor()
  check('duplicate click creates exactly one actual task and approval writes real file', createCount() === before + 1 && await readFile(join(out, run, 'write/browser-note.txt'), 'utf8') === '来自浏览器的修改')
  await page.getByRole('button', { name: '打开任务详情', exact: true }).click()
  await page.getByRole('tab', { name: '报告', exact: true }).click()
  await page.getByRole('button', { name: '下载事实报告', exact: true }).waitFor()
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载事实报告', exact: true }).click()
  const download = await downloadPromise
  const reportPath = join(out, 'M10-browser-report.json'); await download.saveAs(reportPath)
  const report = JSON.parse(await readFile(reportPath, 'utf8'))
  check('downloaded report is based on actual recorded facts', report.source === 'session-events' && report.facts.some(e => e.type === 'tool.completed' && e.payload.status === 'SUCCESS'))
  await screenshot('report')
  await page.getByRole('tab', { name: 'Diff', exact: true }).click()
  await page.getByText('该会话没有记录 Diff。页面不会自动运行 Git。', { exact: true }).waitFor()
  check('missing Diff is explicit without running Git')
  await page.getByRole('tab', { name: '查询', exact: true }).click()
  await page.getByLabel('查询会话内容').fill('tool')
  await page.route('**/v1/tasks/*/search', async route => { const response = await route.fetch(); await wait(600); await route.fulfill({ response }) })
  await page.getByRole('button', { name: '查询', exact: true }).click()
  assert.equal(await page.getByLabel('查询会话内容').isDisabled(), true)
  await page.getByText(/已找到 [1-9]/).waitFor()
  await page.unroute('**/v1/tasks/*/search')
  check('in-flight query cannot mix changed keywords with an old cursor')
  check('full sanitized session query reads real events')
  const countBeforeRefresh = createCount()
  await reload()
  assert.equal(await page.getByLabel('本机连接凭据').count(), 0)
  await connect(); await page.getByText('已同步全部记录', { exact: true }).waitFor()
  check('refresh automatically reconnects and never resubmits task', createCount() === countBeforeRefresh)
  const deniedId = await newTask('write', '拒绝本次文件操作')
  await page.getByRole('button', { name: '拒绝', exact: true }).waitFor()
  await page.getByRole('button', { name: '拒绝', exact: true }).click(); await ended(deniedId)
  check('denial recorded by actual tool pipeline', (await api(`/v1/tasks/${deniedId}/report`)).body.facts.some(e => e.type === 'tool.completed' && e.payload.status === 'DENIED'))
  const expiredId = await newTask('write', '等待审批过期')
  await page.getByRole('button', { name: '批准本次操作', exact: true }).waitFor()
  const exp = (await api(`/v1/tasks/${expiredId}/approvals`)).body.approvals[0]
  await ended(expiredId)
  await until(async () => await page.getByRole('button', { name: '批准本次操作', exact: true }).count() === 0)
  check('expired approval removed and cannot execute', (await api(`/v1/tasks/${expiredId}/approvals/${exp.approvalId}`, { approved: true, reason: 'late', callId: exp.callId })).status === 409)
  const diffId = await newTask('diff', '展示已有仓库差异')
  await ended(diffId)
  await page.getByRole('button', { name: '打开任务详情', exact: true }).click()
  await page.getByRole('tab', { name: 'Diff', exact: true }).click()
  await page.getByText('记录的 git_diff 工具结果', { exact: true }).waitFor()
  check('Diff displays actual recorded git result', JSON.stringify((await api(`/v1/tasks/${diffId}/diff`)).body).includes('a real recorded diff'))
  await screenshot('diff')
  await page.getByRole('button', { name: '关闭任务详情', exact: true }).first().click()
  const cancelId = await newTask('cancel', '等待取消并验证断线只恢复读取')
  await until(async () => (await api(`/v1/tasks/${cancelId}/events?limit=100`)).body.events.some(e => e.type === 'model.requested'))
  const countBeforeOffline = createCount()
  controlledOrigins.add(base)
  try { await context.setOffline(true); await wait(2300) }
  finally { await context.setOffline(false); controlledOrigins.delete(base) }
  await page.getByRole('button', { name: '重载历史', exact: true }).click()
  check('offline recovery never creates a new task', createCount() === countBeforeOffline)
  let cursorReads = 0
  let rejectedStreams = 0
  let reloadedHistory = false
  const cursorTrace = []
  await page.route('**/v1/tasks/*/events?*', async route => {
    if (new URL(route.request().url()).pathname === `/v1/tasks/${cancelId}/events`) { cursorReads++; cursorTrace.push({at:Date.now(),accept:route.request().headers().accept}) }
    if (route.request().headers().accept === 'text/event-stream' && reloadedHistory) { rejectedStreams++; await route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ error: 'UNKNOWN_CURSOR' }) }) }
    else { if (route.request().headers().accept !== 'text/event-stream') reloadedHistory = true; await route.continue() }
  })
  const rejectedBeforeReload = rejectedStreams
  await page.getByRole('button', { name: '重载历史', exact: true }).click()
  await until(() => rejectedStreams > rejectedBeforeReload)
  await page.getByText('续读已暂停', { exact: true }).waitFor()
  // Observe intercepted requests for this session, avoiding delayed page events from old aborted reads.
  const pausedReads = cursorReads
  await wait(2400)
  if(cursorReads!==pausedReads||createCount()!==countBeforeOffline) console.log('CURSOR_DIAGNOSTIC',JSON.stringify({pausedReads,cursorReads,beforePosts:countBeforeOffline,afterPosts:createCount(),cursorTrace}))
  check('invalid cursor pauses stream rather than silently replaying', cursorReads === pausedReads && createCount() === countBeforeOffline)
  await page.unroute('**/v1/tasks/*/events?*')
  await page.getByRole('button', { name: '重载历史', exact: true }).click()
  await page.getByRole('button', { name: '取消任务', exact: true }).click(); await ended(cancelId)
  check('cancel routed through actual Java runtime', (await api(`/v1/tasks/${cancelId}`)).body.cancelRequested === true)
  const unknownId = await newTask('unknown', '核查不明结果')
  await ended(unknownId)
  await page.getByText('部分操作可能已发生，当前不能安全地自动重试。请停止旧宿主，检查实际资源，再通过 CLI 离线核实；本页面不会重放任务。', { exact: true }).waitFor()
  check('UNKNOWN has explicit offline recovery guidance and no replay control', (await api(`/v1/tasks/${unknownId}`)).body.phase === 'UNKNOWN')
  await screenshot('unknown')
  // Drop the response after actual server acceptance. Reconciliation must be GET-only.
  let drop = true
  await page.route('**/v1/tasks', async route => {
    if (drop && route.request().method() === 'POST') { drop = false; await route.fetch(); await route.abort('failed') } else await route.continue()
  })
  await page.getByRole('button', { name: '新建任务', exact: true }).click()
  await page.getByLabel('任务配置').selectOption('read'); await page.getByLabel('任务目标').fill('响应丢失但后台已经接收')
  const beforeDrop = createCount()
  await page.getByRole('button', { name: '创建任务', exact: true }).click()
  await page.getByRole('button', { name: '核对提交状态', exact: true }).waitFor()
  await until(async () => !(await page.getByRole('button', { name: '核对提交状态', exact: true }).isDisabled()))
  await reload()
  assert.equal(await page.getByRole('button', { name: '创建任务', exact: true }).isDisabled(), true)
  await page.getByRole('button', { name: '核对提交状态', exact: true }).click()
  await until(async () => (await page.evaluate(() => sessionStorage.getItem('jh-pending'))) === null)
  await page.unroute('**/v1/tasks')
  check('lost submit response survives refresh and reconciles existing task with no POST replay', createCount() === beforeDrop + 1)
  const htmlId = await newTask('html', '显示不可信 HTML 内容 fixture-secret-do-not-display')
  await ended(htmlId); await page.getByText('<img src=x onerror="window.injected=true">', { exact: true }).first().waitFor()
  check('model HTML is rendered as text', await page.evaluate(() => !window.injected && document.querySelectorAll('#root img').length === 0))
  check('configured secret is redacted from browser event text', !(await page.locator('body').innerText()).includes('fixture-secret-do-not-display'))
  const largeId = await newTask('large', '大正文省略边界')
  await ended(largeId)
  await page.getByRole('button', { name: /^展开执行细节/ }).click()
  await page.getByText('大事件正文已省略，仅保留摘要和哈希。', { exact: true }).first().waitFor()
  check('large payload omission is visible')
  const stored = await page.evaluate(() => JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }))
  const visible = await page.locator('body').innerText()
  check('token absent from URL, storage, page text and downloaded report', browserCredentials.size > 0 && [token, ...browserCredentials].every(secret => typeof secret === 'string' && !stored.includes(secret) && !page.url().includes(secret) && !visible.includes(secret) && !JSON.stringify(report).includes(secret)))
  await page.getByRole('button', { name: '切换深色主题', exact: true }).click()
  assert.equal(await page.locator('body').getAttribute('data-ds-dark-theme'), '')
  await screenshot('dark')
  check('dark theme uses original theme tokens')
  await page.getByRole('button', { name: '切换浅色主题', exact: true }).click()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '展开任务导航', exact: true }).waitFor()
  await page.getByRole('button', { name: '打开任务详情', exact: true }).click()
  await page.getByRole('tab', { name: '详情', exact: true }).focus(); await page.keyboard.press('End')
  assert.equal(await page.getByRole('tab', { name: '产物', exact: true }).getAttribute('aria-selected'), 'true')
  await page.keyboard.press('Home')
  await page.keyboard.press('ArrowRight')
  await page.keyboard.press('ArrowRight')
  await page.keyboard.press('ArrowRight')
  assert.equal(await page.getByRole('tab', { name: '查询', exact: true }).getAttribute('aria-selected'), 'true')
  let chosenBounds, indicatorBounds
  await until(async () => {
    chosenBounds = await page.getByRole('tab', { name: '查询', exact: true }).boundingBox()
    indicatorBounds = await page.locator('.inspector [role=tablist] > span').boundingBox()
    return chosenBounds && indicatorBounds && Math.abs(chosenBounds.x - indicatorBounds.x) < 2 && Math.abs(chosenBounds.width - indicatorBounds.width) < 2
  })
  check('mobile tab indicator aligns with selected keyboard tab', Math.abs(chosenBounds.x - indicatorBounds.x) < 2 && Math.abs(chosenBounds.width - indicatorBounds.width) < 2)
  await screenshot('mobile')
  check('mobile has no viewport overflow and tabs support keyboard', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  await page.emulateMedia({ reducedMotion: 'reduce' })
  check('reduced motion disables original tab transition', await page.locator('[role=tab]').first().evaluate(el => getComputedStyle(el).transitionDuration === '0s'))
  check('no uncaught browser exceptions', errors.length === 0)
  const browserVersion = browser.version()
  console.log('Browser version', browserVersion)
  await browser.close(); browser = null
  host.stdin.end()
  await until(() => existsSync(evidenceFile), 10000)
  const runtime = JSON.parse(await readFile(evidenceFile, 'utf8'))
  check('keyless runtime reports zero paid requests', runtime.paidRequests === 0 && runtime.writeExists)
  const sourceFiles = await import('node:child_process').then(({ execFileSync }) => execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '--', 'frontend', 'harness-web', 'harness-testkit/src/test/java/io/jharness/testkit/M10BrowserHost.java', 'harness-testkit/src/test/java/io/jharness/testkit/M10WebTest.java'], { cwd: root }).toString().trim().split(/\r?\n/))
  const sourceSha256 = Object.fromEntries(await Promise.all([...new Set(sourceFiles)].filter(p => !p.startsWith('frontend/dist/') && !p.includes('node_modules')).sort().map(async p => [p, createHash('sha256').update(await readFile(join(root, p))).digest('hex')])))
  const final = { status: 'PASS', capturedAt: new Date().toISOString(), testedOrigin: base, browserEngine:process.env.JHARNESS_BROWSER_ENGINE??'chromium', browserVersion, browserChannel: process.env.JHARNESS_BROWSER_CHANNEL ?? process.env.JHARNESS_BROWSER_ENGINE ?? 'chromium', checks, runtime, sourceSha256,
    requests: { taskSubmissions: createCount(), total: requests.length }, browserErrors: errors, transportDiagnostics,
    screenshots: await Promise.all(screenshots.map(async p => ({ path: p.replace(root + '\\', '').replace(root + '/', ''), sha256: createHash('sha256').update(await readFile(p)).digest('hex') }))),
    jarSha256: createHash('sha256').update(await readFile(join(root, 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'))).digest('hex') }
  await writeFile(join(out, 'M10-browser-results.json'), JSON.stringify(final, null, 2) + '\n')
} catch (e) {
  if (page && !page.isClosed()) await page.screenshot({ path: join(out, 'M10-failure.png'), fullPage: true })
  const text = page && !page.isClosed() ? await page.locator('body').innerText().catch(() => '') : ''
  await writeFile(join(out, 'M10-browser-failure.txt'), (String(e.stack) + '\n' + hostOutput + '\n' + text + '\n' + JSON.stringify(errors)).replaceAll(token, '[REDACTED]'))
  throw e
} finally {
  if (browser) await browser.close()
  host.stdin.end(); await wait(500)
  if (host.exitCode === null) host.kill()
}
