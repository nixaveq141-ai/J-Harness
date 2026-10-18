import { chromium } from 'playwright'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { mkdir, writeFile, readFile, readdir } from 'node:fs/promises'
import { join, resolve, delimiter } from 'node:path'
import { createHash, randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'

const root = resolve(import.meta.dirname, '../..'), out = join(root, '.build-cache')
const workspace = join(out, `M10-settings-${Date.now()}`), configFile = join(workspace, 'config.json')
const jar = join(root, 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar')
const java = join(process.env.JAVA_HOME, 'bin/java.exe')
const token = 'settings_browser_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ', secret = `synthetic-${randomUUID()}`
const checks = [], wire = [], screenshots = [], pageErrors = []
let host, hostOutput = '', base, browser, page, context, held, block = true, toolSent = false, permissionProbe = false, result
const wait = ms => new Promise(r => setTimeout(r, ms))
async function until(predicate, timeout = 20000) { const start = Date.now(); while (Date.now() - start < timeout) { if (await predicate()) return; await wait(100) } throw new Error('Timed out awaiting fixture condition') }
function check(name, condition = true) { assert.ok(condition, name); checks.push({ name, passed: true }); console.log('PASS', name) }
const provider = createServer(async (request, response) => {
  let text = ''; for await (const chunk of request) text += chunk
  const body = JSON.parse(text)
  wire.push({ model: body.model, authorized: request.headers.authorization === `Bearer ${secret}`, tools: body.tools?.map(t => t.function.name) ?? [], thinking: body.thinking ?? null })
  const reply = () => {
    if (response.writableEnded) return
    const tool = !toolSent; toolSent = true
    response.writeHead(200, { 'Content-Type': 'application/json' })
    const calls = permissionProbe ? [{ id: 'permission-write', type: 'function', function: { name: 'write_file', arguments: JSON.stringify({ path: 'permission-blocked.txt', content: 'must not execute' }) } }] : [{ id: 'settings-todo', type: 'function', function: { name: 'todo_list', arguments: '{}' } }, { id: 'settings-skill', type: 'function', function: { name: 'skill_guide__load', arguments: '{}' } }]
    response.end(JSON.stringify({ id: 'fixture', model: body.model, choices: [{ index: 0, finish_reason: tool ? 'tool_calls' : 'stop', message: tool ? { role: 'assistant', content: null, tool_calls: calls } : { role: 'assistant', content: 'Local fixture complete.' } }], usage: { prompt_tokens: 30, completion_tokens: 10, total_tokens: 40 } }))
  }
  if (block) held = reply; else reply()
})
await mkdir(join(workspace, 'skills/guide'), { recursive: true })
await writeFile(join(workspace, 'skills/guide/SKILL.md'), '# Guide\nSynthetic read-only fixture.\n')
await new Promise(r => provider.listen(0, '127.0.0.1', r))
const endpoint = `http://127.0.0.1:${provider.address().port}`
await writeFile(configFile, JSON.stringify({ baseUrl: endpoint, model: 'initial', apiKeyEnv: 'M10_ABSENT_TEST_KEY', disableThinking: true, maxSteps: 3, durationSeconds: 30, requestTimeoutSeconds: 15, ecosystem: { web: { port: 0 } } }))
async function start() {
  hostOutput = ''; base = undefined
  host = spawn(java, ['-Dfile.encoding=UTF-8', '-jar', jar, '--workspace', workspace, '--config', configFile, 'serve'], { cwd: root, env: { ...process.env, JHARNESS_WEB_TOKEN: token }, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true })
  host.stdout.on('data', d => { hostOutput += d; base = hostOutput.match(/WEB (http:\/\/127\.0\.0\.1:\d+)/)?.[1] })
  host.stderr.on('data', d => { hostOutput += d })
  await until(() => base || host.exitCode !== null)
  assert.ok(base, 'Host startup failed')
}
async function stop() { if (host && host.exitCode === null) { host.stdin.end(':quit\n'); await until(() => host.exitCode !== null); assert.equal(host.exitCode, 0) } }
async function api(path, body, auth = true, extra = {}) {
  const response = await context.request.fetch(base + path, { method: body === undefined ? 'GET' : 'POST', headers: { ...(auth ? { Authorization: `Bearer ${token}` } : {}), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...extra }, ...(body === undefined ? {} : { data: body }), timeout: 45000 })
  return { status: response.status(), body: await response.json() }
}
const saveBody = v => ({ revision: v.revision, models: v.models, selected: v.selected, extensions: v.extensions, apiKeys: {} })
async function connect() { await page.goto(base); await page.getByRole('button', { name: '工作台设置', exact: true }).click(); await page.getByText('模型连接', { exact: true }).waitFor() }
async function shot(name) { await wait(300); const path = join(out, `M10-settings-${name}.png`); await page.screenshot({ path, fullPage: true }); screenshots.push({ path: path.replace(root + '\\', ''), sha256: createHash('sha256').update(await readFile(path)).digest('hex') }) }
try {
  await start()
  browser = await chromium.launch(process.env.JHARNESS_BROWSER_CHANNEL ? { channel: process.env.JHARNESS_BROWSER_CHANNEL } : {})
  context = await browser.newContext({ viewport: { width: 1440, height: 960 } }); page = await context.newPage()
  page.on('pageerror', e => pageErrors.push(e.message))
  check('settings rejects unauthenticated operator', (await api('/v1/settings', undefined, false)).status === 401)
  check('settings rejects cross-origin mutation', (await api('/v1/settings', {}, true, { Origin: 'https://evil.invalid' })).status === 403)
  check('missing key rejected before task admission', (await api('/v1/tasks', { profile: 'default', goal: 'fixture', requestId: 'no-key' })).body.error === 'API_KEY_MISSING' && (await api('/v1/tasks')).body.tasks.length === 0)
  await connect()
  await page.getByRole('button', { name: '添加连接', exact: true }).click()
  await page.getByLabel('连接名称', { exact: true }).fill('本机测试连接')
  await page.getByLabel('API URL', { exact: true }).fill(endpoint)
  await page.getByLabel('模型名称', { exact: true }).fill('settings-model')
  await page.getByLabel('API Key', { exact: true }).fill(secret)
  check('unsaved settings guard task navigation', await page.getByRole('button', { name: '新建任务', exact: true }).isDisabled() && await page.getByRole('button', { name: '返回任务', exact: true }).isDisabled())
  await page.getByRole('button', { name: '设为默认连接', exact: true }).click()
  await page.getByRole('tab', { name: '插件与扩展', exact: true }).click()
  await page.getByLabel('启用任务规划').check()
  await page.locator('.extension-config').filter({ has: page.locator('summary', { hasText: /^Skills$/ }) }).locator('summary').first().click()
  await page.locator('.extension-config').filter({ has: page.locator('summary', { hasText: /^Skills$/ }) }).getByText('高级 JSON', { exact: true }).click()
  await page.getByLabel('Skills配置', { exact: true }).fill(JSON.stringify([{ id: 'guide', directory: 'skills/guide', resources: {}, scripts: {} }], null, 2))
  await shot('plugins')
  await page.getByRole('button', { name: '保存设置', exact: true }).click()
  await page.getByText('设置已保存，将用于后续新消息。', { exact: true }).waitFor()
  const saved = (await api('/v1/settings')).body
  const selected = saved.models.find(m => m.id === saved.selected)
  check('browser creates selects and persists model and plugin configuration', selected.model === 'settings-model' && selected.keyConfigured && saved.extensions.planning && saved.extensions.skills[0].id === 'guide')
  check('responses and disk exclude key and credential target', !JSON.stringify(saved).includes(secret) && !JSON.stringify(saved).includes('credentialRef') && !(await readFile(join(workspace, '.jharness/settings/workbench.json'), 'utf8')).includes(secret))
  check('stale revision conflicts', (await api('/v1/settings', { ...saveBody(saved), revision: 'stale' })).status === 409)
  check('invalid endpoint rejected', (await api('/v1/settings', { ...saveBody(saved), models: saved.models.map(m => m.id === saved.selected ? { ...m, baseUrl: 'https://user:password@example.com' } : m) })).status === 400)
  await page.getByRole('tab', { name: '模型与连接', exact: true }).click()
  await shot('models')
  check('secret input cleared and browser storage empty of secret', await page.getByLabel('API Key', { exact: true }).inputValue() === '' && !(await page.evaluate(() => JSON.stringify({ localStorage, sessionStorage }))).includes(secret))
  await page.getByRole('button', { name: '返回任务', exact: true }).click()
  await page.getByLabel('任务目标').fill('Use the configured todo and skill tools in this synthetic fixture.')
  await page.getByRole('button', { name: '创建任务', exact: true }).click()
  await until(() => wire.length === 1)
  check('new task uses saved model URL and vault key', wire[0].model === 'settings-model' && wire[0].authorized)
  check('new connection does not inherit provider specific thinking parameter', wire[0].thinking === null)
  check('configured plugin tools reach actual model request', wire[0].tools.includes('todo_list') && wire[0].tools.includes('skill_guide__load'))
  check('active task rejects settings changes', (await api('/v1/settings', saveBody(saved))).status === 409)
  block = false; held(); held = undefined
  let task
  await until(async () => { task = (await api('/v1/tasks')).body.tasks[0]; return task?.phase === 'FINISHED' })
  const events = (await api(`/v1/tasks/${task.taskId}/events?limit=100`)).body.events
  check('configured plugins execute through SDK and durable events', events.some(e => e.type === 'tool.completed' && e.payload.tool === 'todo_list' && e.payload.status === 'SUCCESS') && events.some(e => e.type === 'tool.completed' && e.payload.tool === 'skill_guide__load' && e.payload.status === 'SUCCESS'))
  check('event output excludes raw API key', !JSON.stringify(events).includes(secret))
  await page.getByRole('button', { name: '工作台设置', exact: true }).click()
  await page.getByRole('tab', { name: '权限', exact: true }).click()
  await page.getByLabel('权限模式', { exact: true }).selectOption('read-only')
  await page.getByRole('button', { name: '保存设置', exact: true }).click()
  await page.getByText('设置已保存，将用于后续新消息。', { exact: true }).waitFor()
  await shot('permissions')
  check('browser permission selection persists a bounded mode', (await api('/v1/settings')).body.policy === 'read-only' && (await api('/v1/settings', { ...saveBody((await api('/v1/settings')).body), policy: 'allow-all' })).status === 400)
  permissionProbe = true; toolSent = false
  await page.getByRole('button', { name: '返回任务', exact: true }).click()
  await page.getByRole('button', { name: '新建任务', exact: true }).click()
  await page.getByLabel('任务目标').fill('Read-only policy fixture; model will attempt a write.')
  await page.getByRole('button', { name: '创建任务', exact: true }).click()
  await until(async () => { const id = await page.evaluate(() => sessionStorage.getItem('jh-selected')); return id && id !== task.taskId })
  const permissionId = await page.evaluate(() => sessionStorage.getItem('jh-selected'))
  await until(async () => (await api(`/v1/tasks/${permissionId}`)).body.phase === 'FINISHED')
  const permissionEvents = (await api(`/v1/tasks/${permissionId}/events?limit=100`)).body.events
  check('selected permission reaches real SDK and blocks model write before body', permissionEvents.some(e => e.type === 'config.selected' && e.payload.resolved.policy === 'read-only') && permissionEvents.some(e => e.type === 'tool.completed' && e.callId === 'permission-write' && e.payload.status === 'DENIED' && e.payload.bodyEntered === false) && !((await readdir(workspace)).includes('permission-blocked.txt')))
  await stop(); check('host stops without printing key', !hostOutput.includes(secret))
  await start(); await connect()
  const restored = (await api('/v1/settings')).body
  check('restart restores connection and system credential', restored.selected === saved.selected && restored.models.find(m => m.id === saved.selected).keyConfigured)
  check('restart retains read-only permission and old snapshot stays default', restored.policy === 'read-only' && events.some(e => e.type === 'config.selected' && e.payload.resolved.policy === 'default'))
  await page.getByRole('tab', { name: '权限', exact: true }).click()
  await page.getByRole('button', { name: '切换深色主题', exact: true }).click(); await shot('dark')
  await page.setViewportSize({ width: 390, height: 844 }); await shot('mobile')
  check('mobile settings have no document overflow', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  await page.setViewportSize({ width: 1440, height: 960 })
  await page.getByRole('tab', { name: '模型与连接', exact: true }).click()
  await page.getByLabel('连接名称', { exact: true }).fill('已编辑连接')
  await page.getByRole('button', { name: '保存设置', exact: true }).click(); await page.getByText('设置已保存，将用于后续新消息。', { exact: true }).waitFor()
  check('existing connection edited without key resubmission', (await api('/v1/settings')).body.models.find(m => m.id === saved.selected).label === '已编辑连接')
  await page.getByRole('button', { name: '删除连接', exact: true }).click(); await page.getByRole('button', { name: '保存设置', exact: true }).click(); await page.getByText('设置已保存，将用于后续新消息。', { exact: true }).waitFor()
  check('connection deletion persists and clears managed credential', (await api('/v1/settings')).body.models.length === 1 && !(await api('/v1/settings')).body.credentialCleanupPending)
  // UI-only unavailable-service state. Native availability is validated by the separate OS probe.
  await page.route('**/v1/settings', async route => {
    const response = await route.fetch()
    await route.fulfill({ response, json: { ...(await response.json()), credentialStore: 'unavailable' } })
  })
  await page.getByRole('button', { name: '放弃修改并重新读取', exact: true }).click()
  await page.getByText('系统凭据服务暂不可用，请解锁或启动服务后重启工作台。', { exact: false }).waitFor()
  check('unavailable service explains recovery and disables vault input', await page.getByLabel('API Key', { exact: true }).isDisabled() && await page.getByText('留空使用已存密钥或下方环境变量。', { exact: false }).isVisible())
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByText('系统凭据服务暂不可用，请解锁或启动服务后重启工作台。', { exact: false }).scrollIntoViewIfNeeded()
  await shot('unavailable-mobile')
  check('unavailable recovery copy fits mobile settings', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  await page.unroute('**/v1/settings')
  check('no browser runtime errors', pageErrors.length === 0)
  result = { date: new Date().toISOString(), platform: process.platform, browser: browser.version(), checks, screenshots, modelRequests: wire.length, paidRequests: 0, jarSha256: createHash('sha256').update(await readFile(jar)).digest('hex') }
} finally {
  block = false; held?.()
  await stop().catch(() => { host?.kill() })
  await browser?.close()
  provider.closeAllConnections(); await new Promise(r => provider.close(r))
  const cleanup = spawn(java, ['-cp', [join(root, 'harness-cli/target/test-classes'), jar].join(delimiter), 'io.jharness.cli.WorkbenchCredentialCleanup', workspace], { cwd: root, stdio: 'pipe', windowsHide: true })
  let cleanupOutput = ''; cleanup.stdout.on('data', d => { cleanupOutput += d }); cleanup.stderr.on('data', d => { cleanupOutput += d })
  const code = await new Promise(r => cleanup.on('exit', r)); assert.equal(code, 0, 'Credential cleanup failed'); console.log('FIXTURE_CREDENTIALS_CLEAN')
}
await writeFile(join(out, 'M10-settings-browser-results.json'), JSON.stringify({ ...result, fixtureCredentialsCleaned: true }, null, 2))
