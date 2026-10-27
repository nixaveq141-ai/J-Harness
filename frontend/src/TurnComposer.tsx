import { ModelPicker } from './ModelPicker'
import { useEffect, useRef, useState } from 'react'
import { Api, ApiError, uncertain, type Task, type TurnSubmission } from './api'
import { Button } from './upstream/Button'
import { SkillsPicker, ToolInventory } from './SkillsPicker'
import { useLifetime } from './lifecycle'
import { pendingMarker as marker, isPendingMarkerPersisted } from './pendingMarker'

const states: Record<string, string> = { QUEUED: '已排队', PAUSED: '已暂停 · 需显式开始', RUNNING: '正在执行', UNKNOWN: '结果待核实', FINISHED: '本轮已结算', CANCELLED: '已取消排队', VERIFIED: '已离线核实' }
export function TurnComposer({ api, task, onUpdated, onHistory, onGuard }: { api: Api; task: Task; onUpdated: (task: Task) => void; onHistory: () => void; onGuard: (value: boolean) => void }) {
  const key = `jh-turn-${task.taskId}`
  const [modelId, setModelId] = useState('')
  const [draft, setDraft] = useState(''), [kind, setKind] = useState<'message' | 'supplement'>('message')
  const [pending, setPending] = useState(() => marker(key)), [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('')
  const [rows, setRows] = useState<TurnSubmission[]>([]), [offset, setOffset] = useState(0), [next, setNext] = useState(0), [more, setMore] = useState(false), [notFound, setNotFound] = useState(false)
  const lock = useRef(false), callbacks = useRef({ onUpdated, onHistory }); callbacks.current = { onUpdated, onHistory }
  const life=useLifetime(api,task.taskId),epoch=useRef(0)
  const [pollError,setPollError]=useState('')
  function publish(value:Task){if(life.alive){epoch.current++;callbacks.current.onUpdated(value)}}
  useEffect(()=>{lock.current=false;setBusy(false);setRows([]);setPollError('');setError('');setNotice('');setNotFound(false);setPending(marker(key))},[api,task.taskId,key])
  useEffect(() => { onGuard(!!draft || busy); return () => onGuard(false) }, [draft, busy, onGuard])
  useEffect(() => { if (!draft && !busy) return; const stop = (e: BeforeUnloadEvent) => e.preventDefault(); window.addEventListener('beforeunload', stop); return () => window.removeEventListener('beforeunload', stop) }, [draft, busy])
  useEffect(() => {
    let stopped = false, reading = false
    async function read() {
      if (reading || stopped) return; reading = true
      try {
        const revision=epoch.current,apiVersion=api.stateVersion,page = await api.turns(task.taskId, offset)
        if (stopped) return
        if(revision!==epoch.current||apiVersion!==api.stateVersion||lock.current)return
        setRows(page.submissions); setMore(page.more); setNext(page.next)
        const detail = await api.detail(task.taskId); if (!stopped&&revision===epoch.current&&apiVersion===api.stateVersion&&!lock.current){publish(detail);setPollError('')}
      } catch (e) { if (!stopped) {setPollError(e instanceof ApiError && e.status === 401 ? '连接已失效，请重新连接。' : '消息队列或任务状态读取失败；不会自动重发消息。');if(e instanceof ApiError&&e.status===401)stopped=true} }
      finally { reading = false }
    }
    void read(); const timer = setInterval(() => void read(), 1200)
    return () => { stopped = true; clearInterval(timer) }
  }, [api, task.taskId, offset])
  async function run(action: () => Promise<void>) {
    if (lock.current) return; lock.current = true; epoch.current++;setBusy(true); setError(''); setNotice('')
    try { await action() } catch (e) {
      if(!life.alive)return
      const labels: Record<string, string> = { MCP_CREDENTIAL_MISSING: '本条消息的 MCP 凭据缺失，请恢复原宿主环境后重启；不会改用匿名连接。', API_KEY_MISSING: '本条消息的原凭据不可用。请恢复该凭据，或取消排队后重新提交；不会切换模型。', CREDENTIAL_STORE_UNAVAILABLE: '原系统凭据服务不可用，请解锁或启动服务后重启。消息保持暂停，不会改用其他密钥。', MODEL_SELECTION_REQUIRED: '这条旧消息没有模型快照，请取消排队后重新提交并选择模型。', UNKNOWN_MODEL: '所选连接已不存在，请重新打开设置核对连接。', QUEUE_LIMIT: '待执行队列已满（最多8条）或已达到会话256条后续消息配额。', QUEUE_PAUSED: '队列已暂停，请先明确开始或取消已有消息。', QUEUE_ORDER_CONFLICT: '请先处理队首消息。', RECOVERY_REQUIRED: '会话仍有未决事实，请先按执行诊断离线核实。', WORKSPACE_BUSY: '当前工作区正在执行，暂时不能开始该消息。', SESSION_ARCHIVED: '请先恢复会话。', SUBMISSION_ALREADY_STARTED: '消息已开始，不能再取消排队；请使用取消当前轮次。', SUBMISSION_NOT_FOUND: '未找到受理记录；不会自动重新提交。' }
      setError(e instanceof ApiError ? labels[e.code] ?? '操作未确认，请读取受理状态后再决定。' : '连接中断，请核对受理状态；不会自动重发。')
    } finally { if(life.alive){epoch.current++;lock.current = false; setBusy(false)} }
  }
  async function submit() {
    if (!draft.trim() || pending || lock.current) return
    const id = crypto.randomUUID(); if(marker(key,id)!==id){setError('待核对请求过多，请先核对已有请求。');return} setPending(id); setNotFound(false)
    await run(async () => {
      try {
        const value = await api.submitTurn(task.taskId, id, draft.trim(), kind, modelId)
        if(!life.alive)return
        marker(key, ''); setPending(''); setDraft(''); publish(value.task); callbacks.current.onHistory(); setOffset(0); setNotice('消息已受理，实际轮次与状态请查看队列。')
      } catch (e) {
        if (life.alive&&e instanceof ApiError && ['INVALID_INPUT','INVALID_MESSAGE','UNKNOWN_MODEL','API_KEY_MISSING','QUEUE_LIMIT','QUEUE_PAUSED','SESSION_ARCHIVED','RECOVERY_REQUIRED','WORKSPACE_BUSY','MODEL_SELECTION_REQUIRED','MCP_CREDENTIAL_MISSING'].includes(e.code)) { marker(key, ''); setPending('') }
        throw e
      }
    })
  }
  return <section className="turn-composer composer" aria-label="继续会话">
    <div className="turn-heading"><strong>继续会话</strong><span>{task.modelName ? `当前轮模型：${task.modelName} · ${task.modelProtocol}` : '同一会话追加新轮次'}</span></div>
    <textarea aria-label="后续消息" placeholder={uncertain(task) ? '请先核实未决执行结果' : '继续追问，或补充下一轮需求…'} value={draft} rows={2} maxLength={16384} disabled={busy || !!pending || uncertain(task) || task.archived} onChange={e => setDraft(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && !e.nativeEvent.isComposing) { e.preventDefault(); void submit() } }} />
    <SkillsPicker api={api} profile={task.profile} draft={draft} onChange={setDraft} disabled={busy || !!pending || uncertain(task) || task.archived} />
    <ToolInventory api={api} taskId={task.taskId} />
    <div className="turn-toolbar"><ModelPicker api={api} value={modelId} onChange={setModelId} disabled={busy || !!pending} label="下一条消息模型" /><label>消息方式 <select aria-label="消息方式" value={kind} disabled={busy || !!pending} onChange={e => setKind(e.target.value as 'message' | 'supplement')}><option value="message">下一轮消息</option><option value="supplement">补充指令</option></select></label><Button disabled={busy || !!pending || !draft.trim() || uncertain(task) || task.archived} onClick={() => void submit()}>发送后续消息</Button>{draft && <Button disabled={busy || !!pending} onClick={() => setDraft('')}>丢弃草稿</Button>}</div>
    <p className="muted turn-hint">忙碌时排队，补充指令也在当前轮结束后的下一轮应用。未发送草稿只留在本页，刷新会丢失。</p>
    {pending && <div className="turn-pending" role="status">本次消息待核对，不会自动重发。{!isPendingMarkerPersisted(key,pending)&&<p>本页核对标记无法保存；请保持页面，刷新后先读取消息队列核对。</p>}<Button disabled={busy} onClick={() => void run(async () => {
      try { const value = await api.turnStatus(task.taskId, pending); if(!life.alive)return;marker(key, ''); setPending(''); setDraft(''); publish(value.task); callbacks.current.onHistory(); setNotice(`已找到受理记录：${states[value.submission.state]}`) }
      catch (e) { if (life.alive && e instanceof ApiError && e.code === 'SUBMISSION_NOT_FOUND') setNotFound(true); throw e }
    })}>核对消息受理</Button>{notFound && <Button disabled={busy} onClick={() => { marker(key, ''); setPending(''); setNotFound(false); setNotice('已清除本页标记；未撤销任何可能已受理的执行。重新提交由你决定。') }}>清除本页标记</Button>}</div>}
    {rows.length > 0 && <details className="turn-queue"><summary>消息队列与轮次（本页 {rows.length} 条）</summary><ol>{rows.map((row, index) => <li key={row.submissionId}><span>第 {row.sequence} 次输入 · {row.kind === 'supplement' ? '补充指令' : '消息'} · {states[row.state]}{row.preview && <small>{row.preview}</small>}{row.turnId && <small>轮次 {row.turnId.slice(0, 8)}</small>}</span>{['QUEUED','PAUSED'].includes(row.state) && <div>{row.state === 'PAUSED' && <Button disabled={busy || offset>0 || index !== 0 && rows.slice(0,index).some(i => ['QUEUED','PAUSED'].includes(i.state)) || uncertain(task)} onClick={() => void run(async () => { const value = await api.turnAction(task.taskId, row.submissionId, 'start'); if(!life.alive)return;publish(value.task); callbacks.current.onHistory() })}>开始这条消息</Button>}<Button disabled={busy} onClick={() => void run(async () => { const value=await api.turnAction(task.taskId, row.submissionId, 'cancel');if(!life.alive)return;if(value.submission.state!=='CANCELLED')throw new ApiError('INVALID_RESPONSE');publish(value.task);setNotice('已取消排队；未撤销已经发生的操作。') })}>取消排队</Button></div>}</li>)}</ol><div className="turn-toolbar"><Button disabled={busy || offset === 0} onClick={() => setOffset(Math.max(0,offset-32))}>上一页消息</Button><Button disabled={busy || !more} onClick={() => setOffset(next)}>下一页消息</Button></div></details>}
    {pollError&&<p role="status">{pollError}</p>}{error && <p className="inline-error" role="status">{error}</p>}{notice && <p role="status">{notice}</p>}
  </section>
}
