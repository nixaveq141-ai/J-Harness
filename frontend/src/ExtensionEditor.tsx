import { Button } from './upstream/Button'
import { Input } from './upstream/Input'
import { useEffect, useState } from 'react'

type Row = Record<string, unknown>
const object = (v: unknown): v is Row => !!v && typeof v === 'object' && !Array.isArray(v)
const string = (v: unknown) => typeof v === 'string' ? v : ''
const lines = (v: unknown) => Array.isArray(v) ? v.filter(x => typeof x === 'string').join('\n') : ''
const array = (v: string) => v === '' ? [] : v.split('\n')
export type ExtensionKind = 'mcp' | 'skills' | 'skillRoots' | 'subagents'

/** Both views edit the same JSON draft. Invalid advanced input is preserved for correction. */
export function ExtensionEditor({ kind, value, disabled, onChange }: { kind: ExtensionKind; value: string; disabled: boolean; onChange: (value: string) => void }) {
  const [error, setError] = useState('')
  useEffect(()=>setError(''),[value])
  let rows: Row[]
  try {
    const parsed: unknown = JSON.parse(value)
    if (kind === 'subagents') {
      if (!object(parsed) || !Object.values(parsed).every(object)) throw Error()
      rows = Object.entries(parsed).map(([id, row]) => ({ ...row as Row, id }))
    } else {
      if (!Array.isArray(parsed) || !parsed.every(object)) throw Error()
      rows = parsed
    }
  } catch { return <p className="inline-error" role="status">高级 JSON 格式或条目类型无效。请在下方修正后继续编辑表单。</p> }
  const limit = kind === 'subagents' ? 16 : 8
  function commit(next: Row[]) {
    if (kind === 'subagents') {
      if (new Set(next.map(r => r.id)).size !== next.length) { setError('该角色名称已存在，请使用唯一名称。'); return }
      onChange(JSON.stringify(Object.fromEntries(next.map(({ id, ...row }) => [String(id), row])), null, 2))
    } else onChange(JSON.stringify(next, null, 2))
    setError('')
  }
  function update(index: number, patch: Row) { commit(rows.map((row, i) => { if (i !== index) return row; const next = { ...row, ...patch }; if (next.tokenEnv === '') delete next.tokenEnv; return next })) }
  function field(row: Row, i: number, key: string, label: string, help = '') {
    if(row[key]!==undefined&&typeof row[key]!=='string')return <p role="status">{label}必须是文字，请在高级 JSON 中修正；原值保留。</p>
    return <div><label>{label}<Input value={string(row[key])} maxLength={key === 'prompt' ? 4096 : 1024} onChange={e => update(i, { [key]: e.target.value })} /></label>{help && <small className="field-help">{help}</small>}</div>
  }
  function multiline(row: Row, i: number, key: string, label: string) {
    if(row[key]!==undefined&&(!Array.isArray(row[key])||!(row[key] as unknown[]).every(v=>typeof v==='string')))return <p role="status">{label}必须是文字数组，请在高级 JSON 中修正；原值保留。</p>
    return <label>{label}<textarea rows={3} value={lines(row[key])} onChange={e => update(i, { [key]: array(e.target.value) })} /></label>
  }
  function number(row: Row, i: number, key: string, label: string, fallback: number, max: number) {
    const v=row[key]??fallback,valid=typeof v==='number'&&Number.isSafeInteger(v)&&v>=1&&v<=max
    return <label>{label}<Input type="number" min={1} max={max} aria-invalid={!valid} value={typeof v==='number'||typeof v==='string'?v:''} onChange={e => update(i, { [key]: e.target.value!==''&&Number.isSafeInteger(Number(e.target.value))?Number(e.target.value):e.target.value })} />{!valid&&<small role="status">请输入1～{max}的整数；无效草稿不会自动改成0。</small>}</label>
  }
  function mapping(row: Row, i: number, key: string, label: string, mode: 'text' | 'effect' | 'script') {
    const raw = row[key] ?? {}
    if (!object(raw)) return <p role="status">{label}格式无效，请修正高级 JSON。</p>
    const values = Object.entries(raw)
    const set = (next: [string, unknown][]) => {
      if (new Set(next.map(([name]) => name)).size !== next.length) { setError('该条目名称已存在，请使用唯一名称。'); return }
      update(i, { [key]: Object.fromEntries(next) })
    }
    return <div className="extension-mapping"><strong>{label}</strong>{values.map(([name, value], n) => <div className="extension-map-entry" key={n}>
      <label>名称<Input value={name} maxLength={40} onChange={e => set(values.map((v, x) => x === n ? [e.target.value, v[1]] : v))} /></label>
      {mode === 'effect' ? <label>工具影响<select value={string(value)} onChange={e => set(values.map((v, x) => x === n ? [name, e.target.value] : v))}>{['READ_ONLY', 'WORKSPACE_WRITE', 'SYSTEM_IMPACT'].map(x => <option key={x}>{x}</option>)}</select></label>
        : mode === 'script' ? object(value) ? <>
          <label>脚本相对路径<Input value={string(value.path)} onChange={e => set(values.map((v, x) => x === n ? [name, { ...value, path: e.target.value }] : v))} /></label>
          <label>脚本模式<select value={string(value.mode)} onChange={e => set(values.map((v, x) => x === n ? [name, { ...value, mode: e.target.value }] : v))}>{['SH', 'POWERSHELL', 'CMD'].map(x => <option key={x}>{x}</option>)}</select></label>
          <label>脚本超时（秒）<Input type="number" min={1} max={60} value={typeof value.timeoutSeconds === 'number' ? value.timeoutSeconds : 10} onChange={e => set(values.map((v, x) => x === n ? [name, { ...value, timeoutSeconds: Number(e.target.value) }] : v))} /></label>
        </> : <p>脚本格式无效，请修正高级 JSON。</p>
          : <label>资源相对路径<Input value={string(value)} onChange={e => set(values.map((v, x) => x === n ? [name, e.target.value] : v))} /></label>}
      <Button onClick={() => set(values.filter((_, x) => x !== n))}>移除此{mode === 'script' ? '脚本' : mode === 'effect' ? '影响声明' : '资源'}</Button>
    </div>)}<Button disabled={values.length >= (mode === 'script' ? 16 : 32)} onClick={() => set([...values, [`entry-${crypto.randomUUID().slice(0, 6)}`, mode === 'script' ? { path: '', mode: 'POWERSHELL', timeoutSeconds: 10 } : mode === 'effect' ? 'SYSTEM_IMPACT' : '']])}>添加{label}</Button></div>
  }
  const label = { mcp: 'MCP 服务', skills: '技能', skillRoots: '技能根', subagents: '只读角色' }[kind]
  const invalidIds=rows.some(r=>typeof r.id!=='string'||!/^[A-Za-z0-9_-]{1,24}$/.test(r.id))||new Set(rows.map(r=>r.id)).size!==rows.length
  return <fieldset className="extension-form" disabled={disabled}>
    {invalidIds&&<p role="status" className="inline-error">ID须为1～24个字母、数字、下划线或短横线，同类不能重复。请修正后保存。</p>}
    {error && <p className="inline-error" role="status">{error}</p>}
    {!rows.length && <p className="muted">尚未配置{label}。添加后填写字段，再保存设置。</p>}
    {rows.map((row, i) => <section className="extension-entry" key={i} aria-label={`${label}条目 ${i + 1}`}>
      <div className="extension-entry-heading"><strong>{string(row.id) || `新${label}`}</strong><span className="muted">配置草稿 · 保存不连接服务</span><Button onClick={() => commit(rows.filter((_, x) => x !== i))}>移除{label}</Button></div>
      {field(row, i, 'id', '标识 ID', '仅字母、数字、下划线和短横线；同类 ID 必须唯一。')}
      {kind === 'mcp' && <>
        <label>传输方式<select value={string(row.transport)} onChange={e => {
          const { command: _command, cwd: _cwd, url: _url, tokenEnv: _token, ...rest } = row
          commit(rows.map((r, x) => x === i ? { ...rest, transport: e.target.value, ...(e.target.value === 'http' ? { url: '' } : { command: [], cwd: '.' }) } : r))
        }}><option value="http">HTTP / SSE</option><option value="stdio">标准输入输出（本机进程）</option></select></label>
        {row.transport === 'stdio' ? <>{multiline(row, i, 'command', '启动参数（每行一个，首行为可执行文件）')}{field(row, i, 'cwd', '工作目录', '相对于任务工作区；这是可信宿主配置。')}</> : <>{field(row, i, 'url', '服务 URL', 'HTTPS 或本机 loopback HTTP。')}{field(row, i, 'tokenEnv', '凭据环境变量', '只填写变量名；不填表示无鉴权服务，不要在这里填写 token。')}</>}
        {number(row, i, 'timeoutSeconds', '服务超时（秒）', 15, 60)}
        <p className="field-help">未显式声明的工具按系统影响处理。服务端的只读提示不会授予权限。资源读取仅支持文本。</p>
        {mapping(row, i, 'effects', '工具影响声明', 'effect')}
      </>}
      {kind === 'skills' && <>{field(row, i, 'directory', '技能目录', '相对于任务工作区，须包含 SKILL.md。')}{mapping(row, i, 'resources', '资源白名单', 'text')}{mapping(row, i, 'scripts', '脚本白名单', 'script')}<p className="field-help">脚本需要原有策略和执行审批，技能内容不能添加权限。</p></>}
      {kind === 'skillRoots' && <><label>根目录来源<select value={string(row.kind)} onChange={e => update(i, { kind: e.target.value, directory: '' })}><option value="project">项目技能根</option><option value="user">用户技能根</option></select></label>{field(row, i, 'directory', '允许扫描的目录', row.kind === 'user' ? '显式允许的本机绝对路径；只读加载技能文本。' : '相对于任务工作区；只扫描直属目录中的 SKILL.md。')}</>}
      {kind === 'subagents' && <>{field(row, i, 'prompt', '角色说明')}
        <div className="extension-tools"><strong>允许的只读工具</strong>{['list_directory', 'read_file', 'grep', 'search_files', 'read_artifact'].map(tool => <label className="settings-check" key={tool}><input type="checkbox" checked={Array.isArray(row.tools) && row.tools.includes(tool)} onChange={e => update(i, { tools: e.target.checked ? [...(Array.isArray(row.tools) ? row.tools : []), tool] : (Array.isArray(row.tools) ? row.tools : []).filter(x => x !== tool) })} />{tool}</label>)}</div>
        {multiline(row, i, 'requireRead', '必读文件（每行一个工作区相对路径）')}{number(row, i, 'maxSteps', '最大步数', 4, 64)}{number(row, i, 'durationSeconds', '总时限（秒）', 30, 300)}
      </>}
    </section>)}
    <Button disabled={disabled || rows.length >= limit} onClick={() => {
      const id = `${kind === 'subagents' ? 'role' : 'item'}-${crypto.randomUUID().slice(0, 6)}`
      commit([...rows, kind === 'mcp' ? { id, transport: 'http', url: '', timeoutSeconds: 15, effects: {} } : kind === 'skills' ? { id, directory: '', resources: {}, scripts: {} } : kind === 'skillRoots' ? { id, kind: 'project', directory: '' } : { id, prompt: 'Read-only investigation', tools: ['read_file'], requireRead: [], maxSteps: 4, durationSeconds: 30 }])
    }}>添加{label}</Button>
    <p className="field-help">{rows.length} / {limit} 条。校验结果以保存响应为准；已保存配置在后续 SDK 装配时生效。</p>
  </fieldset>
}
