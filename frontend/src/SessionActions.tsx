import { useEffect, useRef, useState } from 'react'
import { Api, ApiError, isTerminal, uncertain, type Task } from './api'
import { Button } from './upstream/Button'
import { Input } from './upstream/Input'

export function SessionActions({ api, task, lastEvent, onUpdated, onFork, onGuard }: { api: Api; task: Task; lastEvent: string; onUpdated: (task: Task) => void; onFork: (event: string) => Promise<void>; onGuard: (guarded: boolean) => void }) {
  const [name, setName] = useState(task.name), [event, setEvent] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('')
  const running = useRef(false)
  const dirty = name !== task.name
  const previousName = useRef(task.name)
  useEffect(() => { const previous=previousName.current;setName(old => old === previous ? task.name : old); previousName.current = task.name }, [task.taskId, task.name])
  useEffect(() => { onGuard(dirty || busy); return () => onGuard(false) }, [dirty, busy, onGuard])
  useEffect(() => { if (!dirty && !busy) return; const stop = (e: BeforeUnloadEvent) => e.preventDefault(); window.addEventListener('beforeunload', stop); return () => window.removeEventListener('beforeunload', stop) }, [dirty, busy])
  const mutable = isTerminal(task) && !uncertain(task)
  async function run(action: () => Promise<void>) {
    if (running.current) return; running.current = true; setBusy(true); setError(''); setNotice('')
    try { await action() } catch (e) {
      const labels: Record<string, string> = { SESSION_CONFLICT: '会话已变化，请重读后再修改。', WORKSPACE_BUSY: '工作区仍有运行或待核实任务，不能归档、分叉或导出。', RECOVERY_REQUIRED: '该前缀仍有未决事实，请先安全核实。', EXPORT_LIMIT: '完整导出超过页面限额，请停止宿主后使用 CLI sessions export。' }
      setError(e instanceof ApiError ? e.code==='INVALID_RESPONSE'?'服务数据或导出完整性校验失败，未生成可信导出。':labels[e.code] ?? '操作未确认，请重读核对；不会自动重发。' : '操作未完成，请检查连接。')
    } finally { running.current = false; setBusy(false) }
  }
  const save = (patch: Partial<Task>) => run(async () => { const value = await api.metadata(task, { name, pinned: task.pinned, archived: task.archived, ...patch }); onUpdated(value); setName(value.name); setNotice('会话信息已保存。') })
  return <details className="session-actions"><summary>会话管理{dirty ? ' · 名称待保存' : ''}</summary>
    <div className="session-controls"><label>会话名称<Input aria-label="会话名称" value={name} maxLength={80} disabled={busy} onChange={e => setName(e.target.value)} /></label><Button disabled={busy || !dirty || !name.trim()} onClick={() => void save({ name })}>保存名称</Button><Button disabled={busy || dirty} onClick={() => void save({ pinned: !task.pinned })}>{task.pinned ? '取消置顶' : '置顶会话'}</Button><Button disabled={busy || dirty} onClick={() => void run(async () => { const value = await api.detail(task.taskId); onUpdated(value); setName(value.name); setNotice('已重新读取会话。') })}>重读会话</Button></div>
    <div className="session-controls"><Button disabled={busy || dirty || !mutable} onClick={() => void save({ archived: !task.archived })}>{task.archived ? '恢复会话' : '归档会话'}</Button><Button disabled={busy || dirty || !mutable} onClick={() => void run(async () => {
      const value = await api.exportSession(task.taskId)
      const digest = [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value.content)))].map(v => v.toString(16).padStart(2, '0')).join('')
      if (digest !== value.sha256) throw new ApiError('INVALID_RESPONSE')
      const url = URL.createObjectURL(new Blob([value.content], { type: 'application/x-ndjson' })), link = document.createElement('a'); link.href = url; link.download = `${task.taskId}.jsonl`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); setNotice('已导出完整事件快照；产物文件需另行保留。')
    })}>导出会话</Button></div>
    <label>分叉截止事件<Input aria-label="分叉截止事件" value={event || lastEvent} maxLength={64} disabled={busy || !mutable} onChange={e => setEvent(e.target.value)} /></label><p className="muted">默认使用最后一条已读取事件；分叉复制历史前缀与产物，不复制工作区文件、不执行模型。未决前缀会被拒绝。</p><Button disabled={busy || dirty || !mutable || !(event || lastEvent)} onClick={() => void run(() => onFork(event || lastEvent))}>分叉会话</Button>
    {!mutable && <p className="muted">运行中或结果待核实的会话不能归档、分叉或导出；命名与置顶不解除工作区占用。</p>}
    {error && <p className="inline-error" role="status">{error}</p>}{notice && <p role="status">{notice}</p>}
  </details>
}
