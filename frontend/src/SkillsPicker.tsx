import { useEffect, useRef, useState } from 'react'
import { Api, ApiError, type SkillEntry } from './api'
import { Button } from './upstream/Button'
import { useLifetime } from './lifecycle'

export function SkillsPicker({ api, profile, draft, onChange, disabled }: { api: Api; profile: string; draft: string; onChange: (text: string) => void; disabled: boolean }) {
  const [skills, setSkills] = useState<SkillEntry[]>([]), [open, setOpen] = useState(false), [busy, setBusy] = useState(false), [loaded, setLoaded] = useState(false), [error, setError] = useState('')
  const request = useRef<AbortController | null>(null)
  useEffect(() => { setSkills([]); setLoaded(false); setError(''); setBusy(false); setOpen(false); return () => request.current?.abort() }, [api, profile])
  async function load() {
    if(disabled||!profile)return
    request.current?.abort(); const controller = new AbortController(); request.current = controller
    setBusy(true); setError(''); setOpen(true);setSkills([]);setLoaded(false)
    try { const value = await api.skills(profile, controller.signal); if (!controller.signal.aborted) { setSkills(value); setLoaded(true) } }
    catch (e) { if (!controller.signal.aborted) setError(e instanceof ApiError && e.code === 'SKILL_NAME_COLLISION' ? '技能重名。请在设置中为技能保留唯一名称。' : e instanceof ApiError && e.code === 'SKILL_COMMAND_CONFLICT' ? '技能名与命令冲突。请修改技能名称后重新读取。' : '技能目录读取失败。请核对已保存的根目录、frontmatter、文件大小及路径权限，再重新读取。') }
    finally { if (!controller.signal.aborted) setBusy(false) }
  }
  const prefix = draft.match(/^\/([A-Za-z0-9_-]*)$/)?.[1]
  useEffect(() => { if (!disabled && profile && prefix !== undefined && !loaded && !busy && !error) void load(); else if (prefix !== undefined) setOpen(true) }, [prefix, loaded, busy, error, api, profile, disabled])
  const candidates = prefix === undefined ? skills : skills.filter(s => s.id.startsWith(prefix))
  return <div className="skills-picker">
    <Button disabled={disabled || busy || !profile} onClick={() => open ? setOpen(false) : void load()} aria-expanded={open}>技能目录{busy ? ' · 正在读取' : ''}</Button>
    {open && <div className="skill-directory" aria-label="技能候选">
      <p className="muted">来自已保存的显式允许根。选择只填入草稿；发送后由模型调用受控加载工具，仍需原有策略和审批。当前运行轮次保留装配时的快照。</p>
      {error && <p role="status" className="inline-error">{error}</p>}
      {loaded && !candidates.length && <p className="muted">{skills.length ? '没有匹配的技能。' : '尚无可用技能，请在设置中配置 Skills 或技能目录发现。'}</p>}
      {candidates.map(s => <div className="skill-entry" key={s.id}><Button disabled={disabled || busy || !loaded} onClick={() => { onChange(`/${s.id} ${draft.replace(/^\/[A-Za-z0-9_-]*\s*/, '')}`); setOpen(false) }}>使用 /{s.id}</Button><p>{s.description || '未提供描述'}</p><small className="muted">{s.root} / {s.directory} · {s.tool}</small><details><summary>查看技能快照</summary><p className="muted">不可信技能文本 · SHA256 {s.sha256}</p><pre>{s.text}</pre></details></div>)}
      <Button disabled={busy || disabled} onClick={() => void load()}>重新扫描已保存目录</Button>
    </div>}
  </div>
}

export function ToolInventory({ api, taskId }: { api: Api; taskId: string }) {
  const [value, setValue] = useState<Awaited<ReturnType<Api['tools']>> | null>(null), [busy, setBusy] = useState(false), [error, setError] = useState('')
  const life = useLifetime(api,taskId)
  useEffect(() => { setValue(null);setError('');setBusy(false) }, [api, taskId])
  async function load() {
    setBusy(true); setError('')
    try { const v = await api.tools(taskId); if (life.alive) setValue(v) }
    catch { if (life.alive) setError('工具清单读取失败，请重新读取。') }
    finally { if (life.alive) setBusy(false) }
  }
  return <details className="tool-inventory"><summary>实际装配工具</summary><p className="muted">最近一次成功 SDK 装配的持久清单。配置已保存不表示服务已连接，下一次装配后清单才更新。</p><Button disabled={busy} onClick={() => void load()}>{busy ? '正在读取…' : '读取实际工具清单'}</Button>{error && <p role="status">{error}</p>}{value && <>{!value.recorded ? <p>尚无成功装配记录。</p> : <ul>{value.tools.map(t => <li key={t.name}><code>{t.name}</code> · {t.sideEffect}<p>{t.description}</p></li>)}</ul>}</>}</details>
}
