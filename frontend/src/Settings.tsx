/* THESIS: Manage connections and trusted extensions alongside tasks.
 * OWN-WORLD: Existing DSH theme, Input, Button, Tag and neutral dividers.
 * STORY: Choose a connection, edit its endpoint and secret, then explicitly save.
 * FIRST VIEWPORT: Back action, two tabs, connection list and labelled form.
 * FORM: Local workbench extension; inherited visual system, no new concept seed. */
import { useEffect, useRef, useState } from 'react'
import { Api, ApiError, type SettingsData, type ModelConnection } from './api'
import { Button } from './upstream/Button'
import { Input } from './upstream/Input'
import { Tag } from './upstream/Tag'
import { SegmentedTabs, type SegmentedTab } from './upstream/SegmentedTabs'

type Tab = 'models' | 'plugins'
const tabs: [SegmentedTab<Tab>, ...SegmentedTab<Tab>[]] = [
  { value: 'models', label: '模型与连接', id: 'settings-models', panelId: 'settings-panel-models' },
  { value: 'plugins', label: '插件与扩展', id: 'settings-plugins', panelId: 'settings-panel-plugins' },
]
const extensionTypes = [
  { key: 'mcp', name: 'MCP 服务', help: '配置 stdio 或 HTTP 服务。任务启动时连接，工具执行仍需策略与审批。', example: [{ id: 'docs', transport: 'http', url: 'http://127.0.0.1:3001/mcp', timeoutSeconds: 15, effects: {} }] },
  { key: 'skills', name: 'Skills', help: '注册工作区内的技能目录、资源及受控脚本。保存配置不会运行脚本。', example: [{ id: 'guide', directory: 'skills/guide', resources: { guide: 'SKILL.md' }, scripts: {} }] },
  { key: 'subagents', name: '只读子任务', help: '为模型提供可调用的只读调查角色；不能授予写入权限。', example: { reviewer: { prompt: 'Read-only investigation', tools: ['read_file'], requireRead: ['README.md'], maxSteps: 4, durationSeconds: 30 } } },
  { key: 'webSources', name: '网页来源', help: '为网页抓取工具配置固定来源名称和 URL。', example: { documentation: 'https://example.com/docs' } },
] as const
function message(e: unknown) {
  if (!(e instanceof ApiError)) return '操作未完成，请检查本机服务。'
  const labels: Record<string, string> = {
    SETTINGS_CONFLICT: '设置已在其他页面更新。请重新读取后修改。', WORKSPACE_BUSY: '有运行中或待核实的任务，请结束任务或先离线核实。',
    INVALID_SETTINGS: '请检查字段格式、唯一连接 ID 和扩展配置。', SECRET_CONFIG: '配置中包含密钥。请只在 API Key 栏填写密钥。',
    CREDENTIAL_STORE_UNAVAILABLE: '当前系统尚未提供凭据库适配。可使用已有环境变量连接。', NOT_FOUND: '当前宿主未启用设置管理，请使用最新的 web-ui.cmd 启动。',
    COST_BUDGET_UNAVAILABLE: '启用费用上限时，请填写该模型的价格和输入 token 上限。', INVALID_CONFIG: '配置校验未通过，请检查数值和扩展字段。',
  }
  return labels[e.code] ?? '保存或读取未完成。请重新读取核对结果，再决定是否重试；不会自动重发。'
}
export function Settings({ api, onBack, onGuardChange }: { api: Api; onBack: () => void; onGuardChange: (guarded: boolean) => void }) {
  const [data, setData] = useState<SettingsData | null>(null), [tab, setTab] = useState<Tab>('models')
  const [editing, setEditing] = useState(''), [keys, setKeys] = useState<Record<string, string>>({})
  const [error, setError] = useState(''), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false), [dirty, setDirty] = useState(false)
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const alive = useRef(true), lock = useRef(false)
  useEffect(() => { onGuardChange(dirty || busy); return () => onGuardChange(false) }, [dirty, busy, onGuardChange])
  useEffect(() => {
    if (!dirty && !busy) return
    const protect = (event: BeforeUnloadEvent) => { event.preventDefault() }
    window.addEventListener('beforeunload', protect)
    return () => window.removeEventListener('beforeunload', protect)
  }, [dirty, busy])
  useEffect(() => { alive.current = true; void load(); return () => { alive.current = false } }, [api])
  function apply(v: SettingsData) { setData(v); setEditing(v.selected); setKeys({}); setDirty(false); setDrafts(Object.fromEntries(extensionTypes.map(x => [x.key, JSON.stringify(v.extensions[x.key] ?? (x.key === 'mcp' || x.key === 'skills' ? [] : {}), null, 2)]))) }
  async function load() {
    if (lock.current) return
    lock.current = true; setBusy(true); setError('')
    try { const v = await api.settings(); if (alive.current) apply(v) } catch (e) { if (alive.current) setError(message(e)) }
    finally { lock.current = false; if (alive.current) setBusy(false) }
  }
  function update(patch: Partial<ModelConnection>) {
    if (!data) return
    setData({ ...data, models: data.models.map(m => {
      if (m.id !== editing) return m
      const changedModel = patch.model !== undefined && patch.model !== m.model || patch.baseUrl !== undefined && patch.baseUrl !== m.baseUrl
      return { ...m, ...patch, ...(changedModel && m.pricing.maxCost !== undefined ? { pricing: { ...m.pricing, inputPerMillion: '', outputPerMillion: '' } } : {}) }
    }) }); setDirty(true); setNotice('')
  }
  async function save() {
    if (!data || lock.current) return
    let extensions: Record<string, unknown>
    try { extensions = { ...data.extensions, ...Object.fromEntries(extensionTypes.map(x => [x.key, JSON.parse(drafts[x.key] ?? '{}')])) } }
    catch { setError('扩展配置不是有效 JSON，请检查后再保存。'); setTab('plugins'); return }
    const pendingKeys = Object.fromEntries(Object.entries(keys).filter(([, value]) => value !== ''))
    if (Object.keys(pendingKeys).length > 4) { setError('一次最多更新 4 个连接的密钥，请分次保存。'); return }
    lock.current = true; setBusy(true); setError(''); setNotice(''); setKeys({})
    try { const v = await api.saveSettings({ ...data, extensions }, pendingKeys); if (alive.current) { apply(v); setNotice('设置已保存，将用于后续新任务。') } }
    catch (e) { if (alive.current) setError(message(e)) }
    finally { lock.current = false; if (alive.current) setBusy(false) }
  }
  const model = data?.models.find(m => m.id === editing)
  return <section className="settings-page" aria-label="工作台设置">
    <div className="settings-heading"><div><h2>工作台设置</h2><p className="muted">管理模型连接与任务可用的扩展。</p></div><Button onClick={onBack} disabled={busy || dirty}>返回任务</Button></div>
    <SegmentedTabs items={tabs} value={tab} onChange={setTab} label="设置分类" />
    {error && <p className="inline-error" role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    {busy && <p role="status">正在处理设置…</p>}
    {!data ? <Button onClick={() => void load()} disabled={busy}>重新读取</Button> : <>
      <div role="tabpanel" id={`settings-panel-${tab}`} aria-labelledby={`settings-${tab}`}>
        {tab === 'models' ? <div className="settings-models"><div className="connection-list">
          <div className="settings-list-heading"><strong>模型连接</strong><Button disabled={busy || data.models.length >= 16} onClick={() => {
            const id = `model-${crypto.randomUUID().slice(0, 8)}`
            setData({ ...data, models: [...data.models, { id, label: '新连接', baseUrl: 'https://api.deepseek.com', model: '', apiKeyEnv: 'JHARNESS_API_KEY', keyConfigured: false, pricing: {}, disableThinking: false }] }); setEditing(id); setDirty(true)
          }}>添加连接</Button></div>
          {data.models.map(m => <button className={`settings-connection ${m.id === editing ? 'selected' : ''}`} key={m.id} disabled={busy} onClick={() => setEditing(m.id)}><span>{m.label}</span><small>{m.model || '尚未填写模型'}</small>{m.id === data.selected && <Tag>默认</Tag>}</button>)}
        </div>{model && <fieldset className="connection-editor" disabled={busy}>
          <label>连接名称<Input value={model.label} maxLength={80} onChange={e => update({ label: e.target.value })} /></label>
          <label>API URL<Input type="url" value={model.baseUrl} maxLength={512} onChange={e => update({ baseUrl: e.target.value })} /></label><p className="field-help">填写 OpenAI 兼容服务的基础地址，例如 https://api.deepseek.com；不要附带密钥。</p>
          <label>模型名称<Input value={model.model} maxLength={128} placeholder="服务端的模型 ID" onChange={e => update({ model: e.target.value })} /></label>
          <label>API Key<Input type="password" autoComplete="off" spellCheck={false} maxLength={1024} value={keys[model.id] ?? ''} disabled={data.credentialStore === 'unavailable'} placeholder={model.keyConfigured ? '已配置；留空保留原密钥' : '输入 API Key'} onChange={e => { setKeys({ ...keys, [model.id]: e.target.value }); setDirty(true) }} /></label><p className="field-help">{data.credentialStore === 'unavailable' ? '本系统凭据库尚未适配。' : `密钥保存到 ${data.credentialStore}，页面不回显原文。`} 留空使用已存密钥或下方环境变量。</p>
          <details><summary>环境变量与费用限制</summary><label>备用密钥环境变量<Input value={model.apiKeyEnv} maxLength={128} onChange={e => update({ apiKeyEnv: e.target.value })} /></label>
            <label className="settings-check"><input type="checkbox" checked={model.disableThinking} onChange={e => update({ disableThinking: e.target.checked })} />发送 thinking: disabled（仅服务支持时）</label>
            <p className="field-help">价格需由你核实。切换服务或模型时，请同步修改价格。未启用费用上限时，仍受任务步数和时限约束。</p>
            <label className="settings-check"><input type="checkbox" checked={model.pricing.maxCost !== undefined} onChange={e => update({ pricing: e.target.checked ? { currency: 'USD', maxCost: '1', inputPerMillion: '', outputPerMillion: '', inputTokenCeiling: 100000 } : {} })} />启用费用上限</label>
            {model.pricing.maxCost !== undefined && <><label>币种<select value={String(model.pricing.currency ?? 'USD')} onChange={e => update({ pricing: { ...model.pricing, currency: e.target.value } })}><option>USD</option><option>CNY</option></select></label>
              {([['maxCost', '单任务费用上限'], ['inputPerMillion', '每百万输入 token 价格'], ['outputPerMillion', '每百万输出 token 价格'], ['inputTokenCeiling', '输入 token 预留上限']] as const).map(([k, label]) => <label key={k}>{label}<Input type="number" min="0" value={String(model.pricing[k] ?? '')} onChange={e => update({ pricing: { ...model.pricing, [k]: k === 'inputTokenCeiling' ? Number(e.target.value) : e.target.value } })} /></label>)}</>}
          </details>
          <div className="settings-actions"><Button variant="outline" disabled={model.id === data.selected} onClick={() => { setData({ ...data, selected: model.id }); setDirty(true) }}>设为默认连接</Button><Button disabled={data.models.length < 2} onClick={() => { const models = data.models.filter(m => m.id !== model.id); setData({ ...data, models, selected: data.selected === model.id ? models[0]!.id : data.selected }); setEditing(models[0]!.id); setKeys(old => { const next = { ...old }; delete next[model.id]; return next }); setDirty(true) }}>删除连接</Button></div>
        </fieldset>}</div> : <div className="settings-extensions">
          <div className="extension-row"><div><h3>任务规划</h3><p className="muted">允许模型调用 Todo / Plan 工具；计划通过不等于执行审批。</p></div><label className="settings-check"><input type="checkbox" checked={data.extensions.planning === true} disabled={busy} onChange={e => { setData({ ...data, extensions: { ...data.extensions, planning: e.target.checked } }); setDirty(true) }} />启用任务规划</label></div>
          <div className="extension-row"><div><h3>上下文压缩</h3><p className="muted">使用现有提取式压缩器，保留完整协议组与事实记录。</p></div><label className="settings-check"><input type="checkbox" checked={data.extensions.compactContext === true} disabled={busy} onChange={e => { setData({ ...data, extensions: { ...data.extensions, compactContext: e.target.checked } }); setDirty(true) }} />启用上下文压缩</label></div>
          {extensionTypes.map(x => <details className="extension-config" key={x.key}><summary>{x.name}</summary><p className="muted">{x.help}</p><label>{x.name}配置<textarea aria-label={`${x.name}配置`} rows={9} disabled={busy} spellCheck={false} value={drafts[x.key] ?? ''} onChange={e => { setDrafts({ ...drafts, [x.key]: e.target.value }); setDirty(true) }} /></label><details><summary>配置示例</summary><pre>{JSON.stringify(x.example, null, 2)}</pre></details></details>)}
          <details className="extension-config"><summary>核心组件（由宿主管理）</summary><p className="muted">会话、权限、工具管线和环境等核心组件随 SDK 装配，不能在这里任意卸载。此处不提供插件市场或任意 JAR 安装。</p><div className="core-plugins">{data.corePlugins.map(p => <Tag key={p}>{p}</Tag>)}</div></details>
          <p className="muted">保存后，扩展在新任务启动时加载。MCP 工具和 Skills 由模型按任务调用，仍使用原有权限、审批和事件展示。</p>
        </div>}
      </div>
      <footer className="settings-save"><span className="muted">{dirty ? '有未保存的修改' : '当前设置已同步'} · 保存不会发送模型请求</span><div><Button disabled={busy} onClick={() => void load()}>放弃修改并重新读取</Button><Button variant="primary" disabled={busy || !dirty} onClick={() => void save()}>保存设置</Button></div></footer>
      {data.credentialCleanupPending && <p role="status">设置已保存，但旧凭据清理未完成。可在 Windows 凭据管理器中检查 J-Harness 项。</p>}
    </>}
  </section>
}
