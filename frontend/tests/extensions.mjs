import { browserType as chromium, stoppedHostDiagnostic, fixtureEnvironment } from './runtime.mjs'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { mkdir, writeFile, readFile, access } from 'node:fs/promises'
import { join, resolve, relative } from 'node:path'
import { createHash, randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'

const stoppedOrigins=new Set(),transportDiagnostics=[]
const root = resolve(import.meta.dirname, '../..'), out = join(root, '.build-cache')
const workspace = join(out, `M10.5.7-extensions-${Date.now()}`), userRoot = join(out, `M10.5.7-user-${Date.now()}`)
const jar = join(root, 'harness-cli/target/harness-cli-0.1.0-SNAPSHOT.jar'), java = join(process.env.JAVA_HOME, process.platform==='win32'?'bin/java.exe':'bin/java')
const token = 'extensions_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ', secret = `synthetic-${randomUUID()}`
const scriptMode = process.platform === 'win32' ? 'POWERSHELL' : 'SH'
const scriptPath = process.platform === 'win32' ? 'check.ps1' : 'check.sh'
const checks = [], screenshots = [], wire = [], peerWire = [], errors = []
let host, base, output = '', browser, page, context, hold, parentSent = false, childSent = false, result
const wait = ms => new Promise(r => setTimeout(r, ms))
async function until(fn, ms = 30000) { const end = Date.now() + ms; while (Date.now() < end) { if (await fn()) return; await wait(100) } throw Error('Fixture timeout') }
function check(name, passed = true) { assert.ok(passed, name); checks.push({ name, passed: true }); console.log('PASS', name) }
const call = (name, args, id) => ({ id, type: 'function', function: { name, arguments: JSON.stringify(args) } })
const provider = createServer(async (req, res) => {
  let text = ''; for await (const chunk of req) text += chunk
  const body = JSON.parse(text), tools = body.tools.map(t => t.function), child = !tools.some(t => t.name === 'delegate_task')
  wire.push({ child, tools: tools.map(t => t.name), authorized: req.headers.authorization === `Bearer ${secret}` })
  let calls = []
  if (child && !childSent) { childSent = true; calls = [call('read_file', { path: 'README.md' }, 'child-read')] }
  if (!child && !parentSent) {
    parentSent = true
    const script = tools.find(t => t.name === 'skill_explicit__run_check')
    const args = Object.fromEntries(Object.entries(script.parameters.properties).map(([k, v]) => [k, v.const]))
    calls = [call('skill_guide__load', {}, 'project-load'), call('skill_userguide__load', {}, 'user-load'),
      call('skill_explicit__load', {}, 'explicit-load'), call('skill_explicit__resource', { resource: 'guide' }, 'explicit-resource'),
      call('mcp_notes__resources', {}, 'resources'), call('mcp_notes__resource_templates', {}, 'templates'),
      call('mcp_notes__read_resource', { uri: 'notes://one' }, 'read-one'), call('mcp_notes__read_resource', { uri: 'notes://item/guide' }, 'read-template'),
      call('skill_explicit__run_check', args, 'script-check'), call('mcp_notes__echo', { text: 'fixture' }, 'echo'),
      call('delegate_task', { profile: 'reviewer', goal: 'Read README.md and report facts.' }, 'delegate')]
  }
  const send = () => { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ id: 'fixture', choices: [{ index: 0, finish_reason: calls.length ? 'tool_calls' : 'stop', message: { role: 'assistant', content: calls.length ? null : 'Extension fixture complete.', ...(calls.length ? { tool_calls: calls } : {}) } }], usage: { prompt_tokens: 40, completion_tokens: 12, total_tokens: 52 } })) }
  if (!child && wire.length === 1) hold = send; else send()
})
const peer = createServer(async (req, res) => {
  if (req.method === 'DELETE') { res.writeHead(200); res.end(); return }
  let text = ''; for await (const chunk of req) text += chunk
  const rpc = JSON.parse(text); peerWire.push({ method: rpc.method, auth: req.headers.authorization ?? null, params: rpc.params })
  if (!rpc.id) { res.writeHead(202); res.end(); return }
  let value
  switch (rpc.method) {
    case 'initialize': value = { protocolVersion: '2025-11-25', capabilities: { tools: {}, resources: {} } }; break
    case 'tools/list': value = { tools: [{ name: 'echo', description: 'Untrusted fixture echo', inputSchema: { type: 'object', properties: { text: { type: 'string' } }, required: ['text'], additionalProperties: false } }] }; break
    case 'resources/list': value = rpc.params.cursor ? { resources: [{ name: 'two', uri: 'notes://two' }] } : { resources: [{ name: 'one', uri: 'notes://one' }], nextCursor: 'page2' }; break
    case 'resources/templates/list': value = { resourceTemplates: [{ name: 'item', uriTemplate: 'notes://item/{id}' }] }; break
    case 'resources/read': value = { contents: [{ uri: rpc.params.uri, mimeType: 'text/plain', text: `Untrusted ${secret}` }] }; break
    case 'tools/call': value = { content: [{ type: 'text', text: 'Echo fixture' }] }; break
    default: throw Error('Unexpected RPC ' + rpc.method)
  }
  const body = JSON.stringify({ jsonrpc: '2.0', id: rpc.id, result: value }), sse = rpc.method === 'resources/read'
  res.writeHead(200, { 'Content-Type': sse ? 'text/event-stream' : 'application/json' }); res.end(sse ? `data: ${body}\n\n` : body)
})
await mkdir(join(workspace, 'skills/guide'), { recursive: true }); await mkdir(join(workspace, 'explicit'), { recursive: true }); await mkdir(join(userRoot, 'userguide'), { recursive: true })
await writeFile(join(workspace, 'README.md'), 'Synthetic read evidence.\n')
await writeFile(join(workspace, 'skills/guide/SKILL.md'), '---\nname: guide\ndescription: Project guide\n---\nUse the configured read tools.\n')
await writeFile(join(userRoot, 'userguide/SKILL.md'), '---\nname: userguide\ndescription: User guide\n---\nUntrusted user text.\n')
await writeFile(join(workspace, 'explicit/SKILL.md'), 'Explicit skill with allowlisted resource and script.\n')
await writeFile(join(workspace, 'explicit/guide.txt'), 'Allowlisted resource.\n')
await writeFile(join(workspace, 'explicit', scriptPath), scriptMode === 'POWERSHELL'
  ? "[System.IO.File]::WriteAllText([System.IO.Path]::Combine([System.Environment]::CurrentDirectory, 'marker.txt'), 'approved')\n"
  : "printf approved > marker.txt\n")
await new Promise(r => provider.listen(0, '127.0.0.1', r)); await new Promise(r => peer.listen(0, '127.0.0.1', r))
const config = join(workspace, 'config.json'), endpoint = `http://127.0.0.1:${provider.address().port}`, peerUrl = `http://127.0.0.1:${peer.address().port}/mcp`
await writeFile(config, JSON.stringify({ baseUrl: endpoint, model: 'extensions-fixture', maxSteps: 6, durationSeconds: 120, requestTimeoutSeconds: 20, ecosystem: { web: { port: 0, approvalSeconds: 60 } } }))
async function start() {
  output = ''; base = undefined
  host = spawn(java, ['-Dfile.encoding=UTF-8', '-jar', jar, '--workspace', workspace, '--config', config, 'serve'], { cwd: root, env: fixtureEnvironment({ JHARNESS_WEB_TOKEN: token, JHARNESS_API_KEY: secret }), stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true })
  const receive = d => { output += d; base = output.match(/WEB (http:\/\/127\.0\.0\.1:\d+)/)?.[1] }
  host.stdout.on('data', receive); host.stderr.on('data', receive); await until(() => base || host.exitCode !== null); assert.ok(base, output)
}
async function stop() { if (host?.exitCode === null) { stoppedOrigins.add(base);host.stdin.end(':quit\n'); await until(() => host.exitCode !== null); assert.equal(host.exitCode, 0) } }
async function api(path, data, auth = true) { const res = await context.request.fetch(base + path, { method: data === undefined ? 'GET' : 'POST', headers: { ...(auth ? { Authorization: `Bearer ${token}` } : {}), ...(data === undefined ? {} : { 'Content-Type': 'application/json' }) }, ...(data === undefined ? {} : { data }) }); return { status: res.status(), body: await res.json() } }
const saveBody = v => ({ revision: v.revision, models: v.models, selected: v.selected, extensions: v.extensions, policy: v.policy, apiKeys: {} })
const group = name => page.locator('.extension-config').filter({ has: page.locator('summary', { hasText: new RegExp(`^${name}$`) }) })
async function shot(name) { const path = join(out, `M10.5.7-${name}.png`); await page.screenshot({ path, fullPage: true }); screenshots.push({ path: relative(root,path).replaceAll('\\','/'), sha256: createHash('sha256').update(await readFile(path)).digest('hex') }) }
try {
  await start(); browser = await chromium.launch(process.env.JHARNESS_BROWSER_CHANNEL ? { channel: process.env.JHARNESS_BROWSER_CHANNEL } : {})
  context = await browser.newContext({ viewport: { width: 1440, height: 960 } }); page = await context.newPage(); page.on('pageerror',e=>{if(stoppedHostDiagnostic(e.message,stoppedOrigins))transportDiagnostics.push(e.message);else errors.push(e.message)})
  check('skill catalog requires authentication', (await api('/v1/skills?profile=default', undefined, false)).status === 401)
  check('skill catalog rejects unregistered profile and arbitrary path', (await api('/v1/skills?profile=missing')).status === 404 && (await api('/v1/skills?profile=default&path=outside')).status === 400)
  await page.goto(base); await page.getByRole('button', { name: '工作台设置', exact: true }).click(); await page.getByRole('tab', { name: '插件与扩展' }).click()
  const mcp = group('MCP 服务'); await mcp.locator('summary').first().click(); await mcp.getByRole('button', { name: '添加MCP 服务', exact: true }).click()
  await mcp.getByLabel('标识 ID', { exact: true }).fill('notes'); await mcp.getByLabel('服务 URL', { exact: true }).fill(peerUrl)
  check('configuration editing does not connect MCP or request model', !peerWire.length && !wire.length)
  const skills = group('Skills'); await skills.locator('summary').first().click(); await skills.getByRole('button', { name: '添加技能', exact: true }).click()
  await skills.getByLabel('标识 ID', { exact: true }).fill('explicit'); await skills.getByLabel('技能目录', { exact: true }).fill('explicit')
  await skills.getByRole('button', { name: '添加资源白名单', exact: true }).click(); await skills.getByLabel('名称', { exact: true }).fill('guide'); await skills.getByLabel('资源相对路径').fill('guide.txt')
  await skills.getByRole('button', { name: '添加脚本白名单', exact: true }).click(); await skills.getByLabel('名称', { exact: true }).nth(1).fill('check'); await skills.getByLabel('脚本相对路径').fill(scriptPath); await skills.getByRole('combobox', { name: /^脚本模式/ }).selectOption(scriptMode)
  await skills.getByLabel('脚本超时（秒）', {exact:true}).fill('10')
  const roots = group('技能目录发现'); await roots.locator('summary').first().click(); await roots.getByRole('button', { name: '添加技能根', exact: true }).click()
  await roots.getByLabel('标识 ID', { exact: true }).fill('project'); await roots.getByLabel('允许扫描的目录').fill('skills')
  await roots.getByRole('button', { name: '添加技能根', exact: true }).click(); await roots.getByLabel('标识 ID', { exact: true }).nth(1).fill('user'); await roots.getByLabel('根目录来源').nth(1).selectOption('user'); await roots.getByLabel('允许扫描的目录').nth(1).fill(userRoot)
  const sub = group('只读子任务'); await sub.locator('summary').first().click(); await sub.getByRole('button', { name: '添加只读角色', exact: true }).click(); await sub.getByLabel('标识 ID', { exact: true }).fill('reviewer'); await sub.getByLabel('必读文件（每行一个工作区相对路径）').fill('README.md')
  await shot('forms-desktop')
  await page.getByRole('button', { name: '保存设置', exact: true }).click(); await page.getByText('设置已保存，将用于后续新消息。', { exact: true }).waitFor()
  let saved = (await api('/v1/settings')).body
  check('forms persist MCP skills resources scripts roots and read-only profile', saved.extensions.mcp[0].id === 'notes' && saved.extensions.skills[0].scripts.check.mode === scriptMode && saved.extensions.skills[0].scripts.check.timeoutSeconds === 10 && saved.extensions.skills[0].scripts.check.path === scriptPath && saved.extensions.skills[0].resources.guide === 'guide.txt' && saved.extensions.skillRoots.length === 2 && saved.extensions.subagents.reviewer.tools.join() === 'read_file')
  check('saving configuration performs no execution', !wire.length && !peerWire.length)
  await roots.getByText('高级 JSON', { exact: true }).click(); const raw = roots.getByLabel('技能目录发现配置', { exact: true }); await raw.fill('{broken')
  check('invalid advanced JSON keeps corrective input and explains form state', await roots.getByText('高级 JSON 格式或条目类型无效。请在下方修正后继续编辑表单。', { exact: true }).isVisible())
  let invalidPosts=0
  const watchInvalid=request=>{if(request.method()==='POST'&&request.url()===base+'/v1/settings')invalidPosts++}
  page.on('request',watchInvalid)
  await page.getByRole('button', { name: '保存设置', exact: true }).click()
  const invalidAlert=page.getByRole('region',{name:'工作台设置',exact:true}).getByRole('alert')
  await invalidAlert.getByText('扩展配置不是有效 JSON，请检查后再保存。',{exact:true}).waitFor()
  page.off('request',watchInvalid)
  check('malformed JSON never reaches settings save', await invalidAlert.innerText() === '扩展配置不是有效 JSON，请检查后再保存。' && invalidPosts===0)
  await page.getByRole('button', { name: '放弃修改并重新读取', exact: true }).click(); await until(async () => !(await page.getByRole('button', { name: '返回任务', exact: true }).isDisabled()))
  const invalid = saveBody(saved); invalid.extensions = { ...invalid.extensions, skillRoots: [{ id: 'escape', kind: 'project', directory: '../outside' }] }
  check('server rejects traversal root before saving', (await api('/v1/settings', invalid)).status === 400)
  await page.getByRole('button', { name: '返回任务', exact: true }).click(); await page.getByLabel('任务目标').waitFor()
  if(await page.getByLabel('任务配置').count())await page.getByLabel('任务配置').selectOption('default')
  else assert.deepEqual((await api('/v1/info')).body.profiles,['default'])
  const catalog=await api('/v1/skills?profile=default');assert.equal(catalog.status,200,JSON.stringify(catalog.body));assert.ok(catalog.body.skills.some(s=>s.id==='guide'),JSON.stringify(catalog.body))
  await page.getByLabel('任务目标').fill('/gu')
  await page.getByRole('button', { name: '使用 /guide', exact: true }).waitFor(); check('slash prefix offers matching scanned skills', await page.getByRole('button', { name: '使用 /userguide', exact: true }).count() === 0)
  await page.getByText('查看技能快照', { exact: true }).click(); check('frontmatter snapshot is viewable without model request', await page.getByText('不可信技能文本 · SHA256', { exact: false }).isVisible() && !wire.length)
  await page.getByRole('button', { name: '使用 /guide', exact: true }).click(); check('candidate only inserts draft', await page.getByLabel('任务目标').inputValue() === '/guide ' && !wire.length)
  await page.getByRole('button', { name: '技能目录', exact: true }).click(); await page.getByRole('button', { name: '使用 /explicit', exact: true }).waitFor(); await page.getByRole('button', { name: '使用 /userguide', exact: true }).waitFor(); check('explicit and user skills are available in catalog', await page.getByRole('button', { name: '使用 /explicit', exact: true }).isVisible() && await page.getByRole('button', { name: '使用 /userguide', exact: true }).isVisible())
  await shot('skills-desktop'); await page.getByRole('button', { name: '技能目录', exact: true }).click()
  await page.getByRole('button', { name: '创建任务', exact: true }).click(); await until(() => !!hold)
  const task = (await api('/v1/tasks')).body.tasks[0]
  check('registered extension tools reach real SDK model request', ['skill_guide__load', 'skill_userguide__load', 'skill_explicit__run_check', 'mcp_notes__read_resource', 'delegate_task'].every(t => wire[0].tools.includes(t)) && wire[0].authorized)
  check('running task blocks settings mutation', (await api('/v1/settings', saveBody(saved))).body.error === 'WORKSPACE_BUSY')
  check('unauthenticated MCP service connects without false empty credential error', peerWire.every(w => w.auth === null))
  hold(); hold = undefined
  const approved = []
  await until(async () => {
    const approvals = (await api(`/v1/tasks/${task.taskId}/approvals`)).body.approvals
    if (approvals.length) {
      const a = approvals[0]
      if (a.tool === 'skill_explicit__run_check') check('skill script does not execute before approval', await access(join(workspace, 'explicit/marker.txt')).then(() => false, () => true))
      await page.getByRole('button', { name: '批准本次操作', exact: true }).first().click(); approved.push(a.tool)
    }
    const state=(await api(`/v1/tasks/${task.taskId}`)).body
    assert.notEqual(state.phase,'UNKNOWN','Extension task needs recovery: '+JSON.stringify(state))
    return state.phase === 'FINISHED'
  }, 90000)
  const events = []; let after = ''
  for (;;) { const value = (await api(`/v1/tasks/${task.taskId}/events?limit=100${after ? '&after=' + after : ''}`)).body; events.push(...value.events); if (!value.more) break; after = value.next }
  const completed = events.filter(e => e.type === 'tool.completed')
  check('skill scripts MCP tools and delegated roles keep explicit approvals', ['skill_explicit__run_check', 'mcp_notes__echo', 'delegate_task'].every(t => approved.includes(t)))
  check('approved script executes in configured workspace', await readFile(join(workspace, 'explicit/marker.txt'), 'utf8') === 'approved')
  check('project user skill resource MCP resource and template calls actually complete', ['skill_guide__load', 'skill_userguide__load', 'skill_explicit__resource', 'mcp_notes__resources', 'mcp_notes__resource_templates', 'mcp_notes__read_resource', 'mcp_notes__echo', 'delegate_task'].every(t => completed.some(e => e.payload.tool === t && e.payload.status === 'SUCCESS')))
  check('MCP resource pagination and JSON SSE reads use real wire', peerWire.filter(w => w.method === 'resources/list').length === 2 && peerWire.filter(w => w.method === 'resources/read').length === 2)
  check('subagent uses independent read-only model request', wire.some(w => w.child && w.tools.includes('read_file') && !w.tools.includes('write_file')))
  check('event and MCP resource text secrets are redacted', !JSON.stringify(events).includes(secret))
  const inventory = (await api(`/v1/tasks/${task.taskId}/tools`)).body
  check('actual persisted inventory matches assembled extension tools', inventory.recorded && inventory.tools.some(t => t.name === 'mcp_notes__read_resource' && t.sideEffect === 'READ_ONLY'))
  await page.getByText('实际装配工具', { exact: true }).click(); await page.getByRole('button', { name: '读取实际工具清单', exact: true }).click(); await page.locator('.tool-inventory').getByText('mcp_notes__read_resource', { exact: true }).waitFor()
  check('actual tools visible in browser', await page.locator('.tool-inventory').getByText('skill_guide__load', { exact: true }).isVisible())
  await until(async () => !await page.getByRole('button', { name: '取消任务', exact: true }).isVisible())
  await page.setViewportSize({ width: 390, height: 844 }); await shot('tools-mobile'); check('extension controls fit mobile viewport', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  await stop(); await start(); await page.goto(base)
  check('skill roots survive host restart', (await api('/v1/skills?profile=default')).body.skills.length === 3)
  check('actual tool list survives host restart without reconnecting MCP', (await api(`/v1/tasks/${task.taskId}/tools`)).body.recorded && peerWire.filter(w => w.method === 'initialize').length === 1)
  await page.setViewportSize({ width: 1440, height: 960 })
  await page.getByRole('button', { name: '工作台设置', exact: true }).click(); await page.getByRole('tab', { name: '插件与扩展' }).click()
  const editable = group('MCP 服务'); await editable.locator('summary').first().click()
  await editable.getByRole('button', { name: '添加MCP 服务', exact: true }).click(); await editable.getByRole('button', { name: '移除MCP 服务', exact: true }).nth(1).click()
  check('form add and remove preserve remaining configured entry', await editable.getByLabel('标识 ID', { exact: true }).count() === 1 && await editable.getByLabel('标识 ID', { exact: true }).inputValue() === 'notes')
  await editable.getByLabel('传输方式').selectOption('stdio'); await editable.getByLabel('启动参数（每行一个，首行为可执行文件）').fill('example.exe\n--fixture')
  await editable.getByText('高级 JSON', { exact: true }).click()
  const commandDraft = JSON.parse(await editable.getByLabel('MCP 服务配置', { exact: true }).inputValue())[0]
  check('stdio form preserves argv boundaries and clears HTTP fields', commandDraft.command.join('|') === 'example.exe|--fixture' && !('url' in commandDraft))
  await page.getByRole('button', { name: '放弃修改并重新读取', exact: true }).click(); await until(async () => !await page.getByRole('button', { name: '返回任务', exact: true }).isDisabled())
  await page.setViewportSize({ width: 390, height: 844 }); await group('MCP 服务').getByLabel('服务 URL', { exact: true }).scrollIntoViewIfNeeded(); await shot('forms-mobile')
  check('extension forms fit mobile viewport', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  saved = (await api('/v1/settings')).body
  const oldWire = wire.length, oldInit = peerWire.filter(w => w.method === 'initialize').length
  const missing = saveBody(saved); missing.extensions = { ...missing.extensions, mcp: [{ ...missing.extensions.mcp[0], tokenEnv: 'M1057_ABSENT_CREDENTIAL' }] }
  check('missing credential configuration saves without connecting service', (await api('/v1/settings', missing)).status === 200 && peerWire.filter(w => w.method === 'initialize').length === oldInit)
  const rejected = await api('/v1/tasks', { profile: 'default', goal: 'Missing MCP credential fixture', requestId: 'missing-credential' })
  check('missing MCP credential rejects task before model or peer request', rejected.status === 400 && rejected.body.error === 'MCP_CREDENTIAL_MISSING' && wire.length === oldWire && peerWire.filter(w => w.method === 'initialize').length === oldInit)
  const current = (await api('/v1/settings')).body, disconnected = saveBody(current)
  disconnected.extensions = { ...disconnected.extensions, mcp: [{ ...saved.extensions.mcp[0], url: 'http://127.0.0.1:1/mcp', timeoutSeconds: 1 }] }
  await api('/v1/settings', disconnected)
  const unavailable = (await api('/v1/tasks', { profile: 'default', goal: 'Disconnected MCP fixture', requestId: 'disconnected' })).body
  await until(async () => (await api(`/v1/tasks/${unavailable.taskId}`)).body.phase === 'UNKNOWN')
  check('disconnected assembly keeps uncertainty without model request or successful inventory', wire.length === oldWire && (await api(`/v1/tasks/${unavailable.taskId}/tools`)).body.recorded === false)
  check('unknown assembly blocks settings and never replays external tool', (await api('/v1/settings', saveBody((await api('/v1/settings')).body))).body.error === 'WORKSPACE_BUSY' && peerWire.filter(w => w.method === 'tools/call').length === 1)
  check('no browser exceptions', errors.length === 0)
  result = { status: 'PASS', capturedAt: new Date().toISOString(), checks, screenshots,transportDiagnostics, browserEngine:process.env.JHARNESS_BROWSER_ENGINE??'chromium',browserVersion: browser.version(), browserChannel: process.env.JHARNESS_BROWSER_CHANNEL ?? process.env.JHARNESS_BROWSER_ENGINE ?? 'chromium', jarSha256: createHash('sha256').update(await readFile(jar)).digest('hex'), paidRequests: 0, modelRequests: wire.length, mcpMethods: peerWire.map(w => w.method) }
} catch (e) {
  if (page) { await page.screenshot({ path: join(out, 'M10.5.7-failure.png'), fullPage: true }); await writeFile(join(out, 'M10.5.7-failure.txt'), (String(e.stack) + '\n' + output + '\n' + await page.locator('body').innerText()).replaceAll(secret, '[REDACTED]').replaceAll(token, '[REDACTED]')) }
  throw e
} finally { hold?.(); await stop().catch(() => host?.kill()); await browser?.close(); for (const server of [provider, peer]) { server.closeAllConnections(); await new Promise(r => server.close(r)) } }
await writeFile(join(out, 'M10.5.7-extensions-results.json'), JSON.stringify(result, null, 2))
