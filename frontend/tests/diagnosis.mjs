import { chromium } from 'playwright'
import { spawn } from 'node:child_process'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join, resolve, delimiter } from 'node:path'
import { createHash } from 'node:crypto'
import assert from 'node:assert/strict'

const root = resolve(import.meta.dirname, '../..'), out = join(root, '.build-cache'), run = `M105-diagnosis-${Date.now()}`
const jar = join(root, 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'), urlFile = join(out, `${run}.url`), runtimeFile = join(out, `${run}.json`)
const cp = [join(root, 'harness-testkit/target/test-classes'), join(root, 'harness-testkit/target/classes'), jar].join(delimiter)
const java = join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
await mkdir(out, { recursive: true })
const host = spawn(java, ['-Dfile.encoding=UTF-8', '-cp', cp, 'io.jharness.testkit.M10BrowserHost', join(out, run), urlFile, runtimeFile], { cwd: root, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true })
let output = '', browser, page, context, base
host.stdout.on('data', d => { output += d }); host.stderr.on('data', d => { output += d })
const checks = [], shots = [], errors = [], requests = []
const wait = ms => new Promise(r => setTimeout(r, ms))
async function until(test, ms = 20000) { const start = Date.now(); while (Date.now() - start < ms) { if (await test()) return; await wait(100) } throw new Error('Fixture condition timed out') }
function check(name, condition = true) { assert.ok(condition, name); checks.push({ name, passed: true }); console.log('PASS', name) }
async function api(path, body) {
  const r = await context.request.fetch(base + path, { method: body === undefined ? 'GET' : 'POST', headers: { Authorization: 'Bearer m10_browser_fixture_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, ...(body === undefined ? {} : { data: body }) })
  return { status: r.status(), body: await r.json() }
}
async function newTask(profile) {
  const prior = await page.evaluate(() => sessionStorage.getItem('jh-selected'))
  await page.getByRole('button', { name: '新建任务', exact: true }).click()
  await page.getByLabel('任务配置').selectOption(profile)
  await page.getByLabel('任务目标').fill('Synthetic diagnosis fixture')
  await page.getByRole('button', { name: '创建任务', exact: true }).click()
  await until(async () => { const id = await page.evaluate(() => sessionStorage.getItem('jh-selected')); return id && id !== prior })
  return page.evaluate(() => sessionStorage.getItem('jh-selected'))
}
async function ended(id) { await until(async () => ['FINISHED', 'UNKNOWN'].includes((await api(`/v1/tasks/${id}`)).body.phase)); await page.getByRole('region', { name: '执行诊断', exact: true }).waitFor() }
async function shot(name) { const path = join(out, `M10.5.1-${name}.png`); await page.screenshot({ path, fullPage: true }); shots.push({ path: path.replace(root + '\\', '').replace(root + '/', ''), sha256: createHash('sha256').update(await readFile(path)).digest('hex') }) }
try {
  await until(() => existsSync(urlFile))
  base = await readFile(urlFile, 'utf8')
  browser = await chromium.launch(process.env.JHARNESS_BROWSER_CHANNEL ? { channel: process.env.JHARNESS_BROWSER_CHANNEL } : {})
  context = await browser.newContext({ viewport: { width: 1440, height: 960 }, acceptDownloads: true }); page = await context.newPage()
  page.on('pageerror', e => errors.push(e.message))
  page.on('request', r => requests.push({ path: new URL(r.url()).pathname, method: r.method() }))
  await page.goto(base); await page.getByText('本机服务已连接', { exact: true }).waitFor()
  const shell = await newTask('shell')
  await page.getByRole('button', { name: '批准本次操作', exact: true }).click(); await ended(shell)
  const panel = page.getByRole('region', { name: '执行诊断', exact: true })
  await panel.getByText('命令主体未启动', { exact: true }).waitFor()
  await page.getByRole('button', { name: '执行诊断', exact: true }).click()
  check('real ARGV shell validation explains body not started and explicit modes', (await panel.innerText()).includes('SHELL_REQUIRED') && (await panel.innerText()).includes('页面不会自动切换'))
  const shellFacts = (await api(`/v1/tasks/${shell}/diagnosis`)).body
  check('original correlation and timeout are preserved', shellFacts.issues.some(i => i.code === 'SHELL_REQUIRED' && i.callId === 'browser-command' && i.timeoutMs === 200 && i.bodyEntered === true && i.commandStarted === 'no'))
  await shot('shell')
  const timeout = await newTask('timeout')
  await page.getByRole('button', { name: '批准本次操作', exact: true }).click(); await ended(timeout)
  await panel.getByText('命令主体已启动', { exact: true }).waitFor()
  const facts = (await api(`/v1/tasks/${timeout}/diagnosis`)).body
  check('actual timed out Java process reports partial cleanup and uncertainty', facts.task.phase === 'UNKNOWN' && !facts.sdkFactsSettled && facts.issues.some(i => i.category === 'TIMEOUT' && i.cleanupStatus === 'partial' && i.observedClean && i.effects === 'possible' && i.commandDurationMs >= 200))
  check('UI never equates environment closure or empty residuals to task success', (await panel.innerText()).includes('采样中未见残留，不保证所有进程已结束') && await panel.getByRole('heading', { name: '为什么结果待核实' }).count() === 1)
  const submissions = requests.filter(r => r.path === '/v1/tasks' && r.method === 'POST').length
  const before = (await api(`/v1/tasks/${timeout}/events?limit=100`)).body.events.map(e => e.eventId)
  await panel.getByText('打开安全核实向导', { exact: true }).click()
  const download = page.waitForEvent('download'); await panel.getByRole('button', { name: '下载离线核实交接' }).click()
  const path = join(out, 'M10.5.1-handoff.json'); await (await download).saveAs(path)
  const handoff = JSON.parse(await readFile(path, 'utf8'))
  check('handoff contains exact task catalog and read-only checklist', handoff.association.sessionId === timeout && handoff.association.catalogId === 'browser-catalog' && handoff.handoff.readOnly && handoff.handoff.automaticReplay === false && handoff.handoff.steps.some(s => s.includes('独占')))
  await panel.getByRole('button', { name: '重新读取诊断' }).click(); await panel.getByText('打开安全核实向导', { exact: true }).waitFor()
  check('guide download and refresh append no facts and never replay', JSON.stringify(before) === JSON.stringify((await api(`/v1/tasks/${timeout}/events?limit=100`)).body.events.map(e => e.eventId)) && submissions === requests.filter(r => r.path === '/v1/tasks' && r.method === 'POST').length && (await api(`/v1/tasks/${timeout}`)).body.phase === 'UNKNOWN')
  await panel.getByText('打开安全核实向导', { exact: true }).click()
  await shot('guide')
  await page.getByRole('button', { name: '切换深色主题', exact: true }).click(); await shot('dark')
  await page.setViewportSize({ width: 390, height: 844 }); await page.getByRole('button', { name: '执行诊断', exact: true }).click(); await shot('mobile')
  check('narrow dark diagnostic and guide do not overflow', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  const guide = panel.locator('.verification-guide > summary'); await guide.focus(); await page.keyboard.press('Enter')
  check('verification guide is keyboard accessible', !(await panel.locator('.verification-guide').evaluate(e => e.open)))
  await page.setViewportSize({ width: 1440, height: 960 })
  const expired = await newTask('write'); await page.getByRole('button', { name: '批准本次操作', exact: true }).waitFor(); await ended(expired)
  await panel.getByText('审批已过期，未获批准；迟到的批准不会生效。', { exact: true }).waitFor()
  check('actual approval expiry is shown as denied and body not entered', (await api(`/v1/tasks/${expired}/diagnosis`)).body.issues.some(i => i.category === 'DENIED' && i.bodyEntered === false && i.approvalReason === 'Approval expired'))
  const cancelled = await newTask('cancel')
  await until(async () => (await api(`/v1/tasks/${cancelled}/events?limit=100`)).body.events.some(e => e.type === 'model.requested'))
  await page.getByRole('button', { name: '取消任务', exact: true }).click(); await ended(cancelled)
  check('cancelled model request retains pending recovery facts', (await api(`/v1/tasks/${cancelled}/diagnosis`)).body.pending.pendingRequests.length > 0 && (await panel.innerText()).includes('已请求取消'))
  await page.reload(); await page.getByText('本机服务已连接', { exact: true }).waitFor(); await panel.waitFor()
  check('refresh restores diagnosis without task submission or unsafe verification', requests.filter(r => r.path === '/v1/tasks' && r.method === 'POST').length === 4 && !requests.some(r => /verify/.test(r.path) && r.method === 'POST'))
  check('no browser runtime errors', errors.length === 0)
  const version = browser.version(); await browser.close(); browser = undefined
  host.stdin.end(); await until(() => existsSync(runtimeFile))
  const runtime = JSON.parse(await readFile(runtimeFile, 'utf8'))
  await until(() => host.exitCode !== null)
  check('fixture closes with zero paid requests', runtime.paidRequests === 0 && host.exitCode === 0)
  await writeFile(join(out, 'M10.5.1-diagnosis-results.json'), JSON.stringify({ status: 'PASS', capturedAt: new Date().toISOString(), checks, screenshots: shots, browserVersion: version, jarSha256: createHash('sha256').update(await readFile(jar)).digest('hex'), runtime, requests }, null, 2))
} catch (e) {
  if (page && !page.isClosed()) await page.screenshot({ path: join(out, 'M10.5.1-diagnosis-failure.png') })
  await writeFile(join(out, 'M10.5.1-diagnosis-failure.txt'), String(e.stack) + '\n' + output)
  throw e
} finally { await browser?.close(); host.stdin.end(); await wait(500); if (host.exitCode === null) host.kill() }
