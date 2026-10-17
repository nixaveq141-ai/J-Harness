import { useCallback, useEffect, useRef, useState, type FormEvent, type PointerEvent } from 'react'
import { Api, ApiError, isTerminal, uncertain, mergeEvents, object, type Task, type Approval, type SessionEvent, type Page } from './api'
import { Button } from './upstream/Button'
import { Input } from './upstream/Input'
import { Tag } from './upstream/Tag'
import { SegmentedTabs, type SegmentedTab } from './upstream/SegmentedTabs'
import { TextShimmer } from './upstream/TextShimmer'
import { Settings } from './Settings'
import { DiagnosisPanel } from './DiagnosisPanel'
import frameCss from './upstream/AppFrame.module.css'
import sidebarCss from './upstream/SidebarRoot.module.css'
import { clampWidth, SIDEBAR_DEFAULT, SIDEBAR_MIN, SIDEBAR_MAX } from './upstream/columns'

const labels: Record<string, string> = { ACCEPTED: '已接收', RUNNING: '进行中', CLOSING: '正在清理',
  FINISHED: '已结束', COMPLETED: '已完成', SUCCESS: '成功', FAILED: '失败', CANCELLED: '已取消',
  DENIED: '已拒绝', LIMIT_REACHED: '达到限制', UNKNOWN: '结果待核实', VERIFIED: '已离线核实', CREATED: '已创建' }
const errorLabels: Record<string, string> = {
  UNAUTHORIZED: '本机会话已失效，请重新连接。',
  API_KEY_MISSING: '默认模型尚未配置 API Key，请打开设置补充密钥。',
  LOCAL_CLIENT_REQUIRED: '请求未通过本机同源检查，请使用服务输出的原始地址。',
  CONNECTION_LOST: '连接中断。读取可以重连；任务提交不会自动重发。',
  UNKNOWN_CURSOR: '事件游标已失效，已暂停续读。请点击“重载历史”。',
  STALE_APPROVAL: '审批已过期、已处理或关联不匹配，已刷新审批列表。',
  WORKSPACE_BUSY: '该工作区仍被任务占用，请等待结束或离线核实。',
  ACTIVE_LIMIT: '运行中的任务已达到上限。', TASK_LIMIT: '任务记录已达到当前宿主上限。',
  SUBMISSION_NOT_FOUND: '暂未找到该提交记录，请稍后再次核对，不要重复创建。',
  INVALID_RESPONSE: '服务返回的数据不符合接口约定，已暂停本次操作。',
  RESPONSE_LIMIT: '响应超过显示限额，请使用分页查询。', STREAM_LIMIT: '实时读取已达上限，请稍后重连。',
}
const errorText = (e: unknown) => e instanceof ApiError ? errorLabels[e.code] ?? `操作未完成（${e.code}）。` : '操作未完成，请检查连接后重试读取。'
const pretty = (value: unknown) => JSON.stringify(value, null, 2)
function storage(key: string, value?: string): string {
  try { if (value !== undefined) { if (value) sessionStorage.setItem(key, value); else sessionStorage.removeItem(key) }
    return sessionStorage.getItem(key) ?? '' } catch { return '' }
}
const sleep = (ms: number, signal: AbortSignal) => new Promise<void>(resolve => {
  if (signal.aborted) { resolve(); return }
  const done = () => { clearTimeout(timer); signal.removeEventListener('abort', done); resolve() }
  const timer = setTimeout(done, ms); signal.addEventListener('abort', done, { once: true })
})
function Icon({ name }: { name: 'plus' | 'panel' | 'arrow' | 'close' | 'search' | 'moon' | 'file' | 'chevron' | 'stop' }) {
  const paths = { plus: 'M12 5v14M5 12h14', panel: 'M3 4h18v16H3zM9 4v16', arrow: 'M12 19V5m-6 6 6-6 6 6',
    close: 'm6 6 12 12M18 6 6 18', search: 'm16 16 5 5M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0',
    moon: 'M20 14a8 8 0 0 1-10-10 8.5 8.5 0 1 0 10 10', file: 'M14 3H5v18h14V8zM14 3v6h5', chevron: 'm9 5 7 7-7 7', stop: 'M6 6h12v12H6z' }
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>
}
function Status({ value }: { value: Task }) {
  const unknown = uncertain(value)
  return <Tag tone={unknown ? 'warning' : value.status === 'COMPLETED' ? 'success' : value.status === 'FAILED' ? 'danger' : 'neutral'}>
    {value.phase === 'VERIFIED' ? '已离线核实' : unknown ? '结果待核实' : labels[value.status] ?? labels[value.phase] ?? value.status}
  </Tag>
}
const eventLabels: Record<string, string> = { 'tool.requested': '请求工具', 'tool.completed': '工具结果',
  'model.requested': '发送模型请求', 'model.responded': '收到模型响应', 'approval.requested': '等待审批',
  'approval.decided': '审批决定', 'turn.started': '开始任务', 'turn.ended': '本轮结束',
  'assistant.message': '助手', 'user.message': '任务目标', 'config.selected': '应用可信配置',
  'tool.intent': '记录执行意图', 'tool.verified': '操作已核实', 'delivery.registered': '产物记录' }
type Inspector = 'detail' | 'diff' | 'report' | 'search'
const tabs: [SegmentedTab<Inspector>, ...SegmentedTab<Inspector>[]] = [
  { value: 'detail', label: '详情', id: 'tab-detail', panelId: 'panel-detail' },
  { value: 'diff', label: 'Diff', id: 'tab-diff', panelId: 'panel-diff' },
  { value: 'report', label: '报告', id: 'tab-report', panelId: 'panel-report' },
  { value: 'search', label: '查询', id: 'tab-search', panelId: 'panel-search' },
]

export function App() {
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [settingsGuard, setSettingsGuard] = useState(false)
  const [api, setApi] = useState<Api | null>(null)
  const [connecting, setConnecting] = useState(false), [profiles, setProfiles] = useState<string[]>([])
  const [profile, setProfile] = useState(''), [tasks, setTasks] = useState<Task[]>([])
  const [selected, setSelected] = useState(() => storage('jh-selected'))
  const [events, setEvents] = useState<SessionEvent[]>([]), [approvals, setApprovals] = useState<Approval[]>([])
  const [goal, setGoal] = useState(''), [filter, setFilter] = useState(''), [error, setError] = useState('')
  const [pending, setPending] = useState(() => storage('jh-pending')), [submitting, setSubmitting] = useState(false)
  const [busy, setBusy] = useState(''), [live, setLive] = useState('连接后自动更新'), [historyKey, setHistoryKey] = useState(0)
  const [sideOpen, setSideOpen] = useState(() => window.matchMedia('(min-width: 761px)').matches), [inspectorOpen, setInspectorOpen] = useState(false)
  const [tab, setTab] = useState<Inspector>('detail'), [focusedEvent, setFocusedEvent] = useState<SessionEvent | null>(null)
  const [dark, setDark] = useState(() => { try { return localStorage.getItem('jh-theme') === 'dark' } catch { return false } })
  const [sidebarWidth, setSidebarWidth] = useState(SIDEBAR_DEFAULT), [dragging, setDragging] = useState(false)
  const [animating, setAnimating] = useState(false)
  const createLock = useRef(false), actionLock = useRef(false), generation = useRef(0), readFailures = useRef(0)
  const listEnd = useRef<HTMLDivElement>(null), autoScroll = useRef(true)
  const diagnosisAnchor = useRef<HTMLDivElement>(null), presentedDiagnosis = useRef('')
  const active = tasks.find(t => t.taskId === selected)
  const hasProblems = !!active && isTerminal(active) && (uncertain(active) || active.status !== 'COMPLETED' || events.some(e => e.type === 'tool.completed' && e.payload.status !== 'SUCCESS'))
  const currentApi = useRef(api); currentApi.current = api
  useEffect(() => { document.body.toggleAttribute('data-ds-dark-theme', dark); try { localStorage.setItem('jh-theme', dark ? 'dark' : 'light') } catch { /* optional preference */ } }, [dark])
  useEffect(() => { storage('jh-selected', selected) }, [selected])
  useEffect(() => { if (autoScroll.current) listEnd.current?.scrollIntoView({ block: 'nearest' }) }, [events.length])
  useEffect(() => {
    if (!hasProblems || !selected || presentedDiagnosis.current === selected || !autoScroll.current
      || !['已同步全部记录', '结果待核实 · 不会重放任务'].includes(live)) return
    const frame = requestAnimationFrame(() => { diagnosisAnchor.current?.scrollIntoView({ block: 'start' }); presentedDiagnosis.current = selected })
    return () => cancelAnimationFrame(frame)
  }, [hasProblems, selected, live])
  const updateTask = useCallback((task: Task) => setTasks(old => [task, ...old.filter(t => t.taskId !== task.taskId)]), [])
  const toggleSidebar = () => { setAnimating(true); setSideOpen(v => !v); setTimeout(() => setAnimating(false), 320) }
  const selectTask = (id: string) => { if (settingsGuard) return; setSettingsOpen(false); setSelected(id); setFocusedEvent(null); autoScroll.current = true; setError('') }
  useEffect(() => {
    if (!api) return
    const abort = new AbortController()
    void (async () => {
      while (!abort.signal.aborted) {
        try { const result = await api.tasks(abort.signal); if (!abort.signal.aborted) setTasks(result.reverse()) }
        catch (e) { if (!abort.signal.aborted) { setError(errorText(e)); if (e instanceof ApiError && e.status === 401) { setApi(null); break } } }
        await sleep(2000, abort.signal)
      }
    })()
    return () => abort.abort()
  }, [api])
  useEffect(() => { readFailures.current = 0 }, [api, selected])
  useEffect(() => {
    const narrow = window.matchMedia('(max-width: 760px)')
    const update = () => { if (narrow.matches) setSideOpen(false) }
    narrow.addEventListener('change', update)
    return () => narrow.removeEventListener('change', update)
  }, [])
  useEffect(() => {
    setEvents([]); setApprovals([]); setFocusedEvent(null)
    if (!api || !selected) return
    const abort = new AbortController(); let cursor = '', count = 0
    setLive('正在读取历史')
    void (async () => {
      try {
        let more = true
        while (more && !abort.signal.aborted) {
          const p = await api.events(selected, cursor, abort.signal)
          if (abort.signal.aborted) return
          setEvents(old => mergeEvents(old, p.events)); count += p.events.length; cursor = p.next; more = p.more
          if (count >= 4000) { setLive('已达到 4000 条显示上限，请使用会话查询'); return }
        }
        while (!abort.signal.aborted) {
          const t = await api.detail(selected, abort.signal)
          if (abort.signal.aborted) return
          updateTask(t)
          const a = await api.approvals(selected, abort.signal)
          if (abort.signal.aborted) return
          setApprovals(a)
          if (isTerminal(t)) {
            let more = true
            while (more && !abort.signal.aborted) {
              const p = await api.events(selected, cursor, abort.signal)
              if (abort.signal.aborted) return
              setEvents(old => mergeEvents(old, p.events)); cursor = p.next; more = p.more; count += p.events.length
              if (count >= 4000) { setLive('已达到显示上限，请使用查询'); return }
            }
            setLive(uncertain(t) ? '结果待核实 · 不会重放任务' : '已同步全部记录'); return
          }
          setLive('实时更新中')
          await api.stream(selected, cursor, e => { cursor = e.eventId; count++; setEvents(old => mergeEvents(old, [e])) }, abort.signal)
          if (count >= 4000) { setLive('已达到显示上限，请使用查询'); return }
          readFailures.current = 0
          await sleep(400, abort.signal)
        }
      } catch (e) {
        if (abort.signal.aborted) return
        readFailures.current++
        setError(errorText(e)); setLive(e instanceof ApiError && e.code === 'UNKNOWN_CURSOR' ? '续读已暂停' : '连接中断 · 仅恢复读取')
        // Restart only the read lifecycle, with de-duplication; never restart task submission.
        if (!(e instanceof ApiError && ['UNKNOWN_CURSOR', 'UNAUTHORIZED', 'INVALID_RESPONSE'].includes(e.code)) && readFailures.current <= 3) {
          await sleep(2000, abort.signal); if (!abort.signal.aborted) setHistoryKey(k => k + 1)
        }
      }
    })()
    return () => abort.abort()
  }, [api, selected, historyKey, updateTask])
  // Approvals have shorter lifetimes than the finite SSE connection, so refresh independently.
  useEffect(() => {
    if (!api || !selected || active && isTerminal(active)) return
    const abort = new AbortController()
    void (async () => { while (!abort.signal.aborted) {
      try { const a = await api.approvals(selected, abort.signal); if (!abort.signal.aborted) setApprovals(a) } catch { /* main read lifecycle reports connection failures */ }
      await sleep(900, abort.signal)
    } })()
    return () => abort.abort()
  }, [api, selected, active?.phase])
  useEffect(() => { void connect(); return () => { generation.current++ } }, [])
  async function connect() {
    if (connecting) return
    setConnecting(true); setError(''); const attempt = ++generation.current
    try {
      const candidate = await Api.connect(), info = await candidate.info()
      if (attempt !== generation.current) return
      setProfiles(info.profiles); setProfile(info.profiles[0] ?? ''); setApi(candidate); setLive('已连接')
    } catch (e) { if (attempt === generation.current) setError(errorText(e)) }
    finally { if (attempt === generation.current) setConnecting(false) }
  }
  async function submit(e: FormEvent) {
    e.preventDefault(); if (!api || !goal.trim() || pending || createLock.current) return
    const id = crypto.randomUUID(), text = goal.trim(); createLock.current = true; setSubmitting(true)
    setPending(id); storage('jh-pending', id); setError('')
    try {
      const t = await api.submit(profile, text, id)
      storage('jh-pending', ''); setPending('')
      if (currentApi.current !== api) return
      updateTask(t); selectTask(t.taskId); setGoal('')
    } catch (e) { if (currentApi.current === api) {
      if (e instanceof ApiError && e.code === 'API_KEY_MISSING') { storage('jh-pending', ''); setPending(''); setError(errorText(e)) }
      else setError(errorText(e) + ' 请核对本次提交状态。')
    } }
    finally { createLock.current = false; setSubmitting(false) }
  }
  async function reconcile() {
    if (!api || !pending || actionLock.current) return
    actionLock.current = true; setBusy('reconcile')
    try { const t = await api.submission(pending); storage('jh-pending', ''); setPending(''); if (currentApi.current === api) { updateTask(t); selectTask(t.taskId); setGoal(''); setError('') } }
    catch (e) { setError(errorText(e)) } finally { actionLock.current = false; setBusy('') }
  }
  async function decide(a: Approval, approved: boolean) {
    if (!api || !active || actionLock.current) return
    const id = selected; actionLock.current = true; setBusy(a.approvalId); setError('')
    try { await api.decide(id, a, approved, approved ? '用户批准本次操作' : '用户拒绝本次操作') }
    catch (e) { setError(errorText(e)) }
    finally { if (currentApi.current === api) { const remaining = await api.approvals(id).catch(() => []); if (storage('jh-selected') === id) setApprovals(remaining) }
      actionLock.current = false; setBusy('') }
  }
  async function cancel() {
    if (!api || !active || actionLock.current) return
    const id = selected; actionLock.current = true; setBusy('cancel')
    try { const t = await api.cancel(id); if (currentApi.current === api) updateTask(t) }
    catch (e) { setError(errorText(e)) } finally { actionLock.current = false; setBusy('') }
  }
  function drag(e: PointerEvent<HTMLDivElement>) {
    e.currentTarget.setPointerCapture(e.pointerId); setDragging(true)
  }
  const taskTitle = active ? `任务 ${active.taskId.slice(0, 8)}` : '新任务'
  return <div className={`${frameCss.frame} workbench ${sideOpen ? '' : 'sidebar-closed'} ${inspectorOpen ? 'with-inspector' : ''}`}
    data-animating={animating || undefined} data-dragging={dragging || undefined}
    style={{ '--sidebar-width': `${sidebarWidth}px` } as React.CSSProperties}>
    <a className="skip-link" href="#main-content">跳到任务内容</a>
    <aside className={`${frameCss.sidebarCol} navigation`} aria-label="任务导航">
      <div className={`${sidebarCss.root} nav-inner`}>
        <div className="brand-row"><span className="brand-mark" aria-hidden="true">J</span><strong className="nav-expanded">J-Harness</strong><Button aria-label={sideOpen ? '收起任务导航' : '展开任务导航'} title="切换导航" className="nav-toggle" onClick={toggleSidebar}><Icon name="panel" /></Button></div>
        <Button className="new-task" aria-label="新建任务" title="新建任务" disabled={settingsGuard} onClick={() => selectTask('')} icon={<Icon name="plus" />}><span className="nav-expanded">新建任务</span></Button>
        <div className="nav-expanded nav-filter"><Input aria-label="筛选任务" placeholder="搜索任务 ID 或状态" icon={<Icon name="search" />} value={filter} onChange={e => setFilter(e.target.value)} /></div>
        <div className="nav-expanded section-label">最近任务 <span>{tasks.length}</span></div>
        <nav className="task-list nav-expanded" aria-label="最近任务">
          {tasks.filter(t => `${t.taskId} ${t.profile} ${labels[t.status] ?? t.status}`.toLowerCase().includes(filter.toLowerCase())).map(t => <button disabled={settingsGuard} key={t.taskId} className={`task-item ${selected === t.taskId ? 'selected' : ''}`} aria-current={selected === t.taskId ? 'page' : undefined} onClick={() => selectTask(t.taskId)}>
            <span className="task-name"><Icon name="file" /><span>任务 {t.taskId.slice(0, 8)}</span></span><span className="task-subline">{t.profile}<Status value={t} /></span>
          </button>)}
          {api && tasks.length === 0 && <p className="muted nav-empty">还没有任务。<br />创建后会在这里保留记录。</p>}
          {!api && <p className="muted nav-empty">连接本机服务后<br />查看任务记录。</p>}
        </nav>
        <div className="nav-footer"><Button aria-label="工作台设置" title="工作台设置" disabled={!api} onClick={() => { setSettingsOpen(true); setInspectorOpen(false); if (window.matchMedia('(max-width: 760px)').matches) setSideOpen(false) }} icon={<Icon name="panel" />}><span className="nav-expanded">设置</span></Button><Button onClick={() => setDark(v => !v)} aria-label={dark ? '切换浅色主题' : '切换深色主题'} icon={<Icon name="moon" />}><span className="nav-expanded">{dark ? '浅色主题' : '深色主题'}</span></Button>
          <div className="nav-expanded connection-state"><span className={`connection-dot ${api ? 'online' : ''}`} />{api ? '本机服务已连接' : '尚未连接'}</div>
          <a className="nav-expanded license-link" href="/ui-license.txt" target="_blank" rel="noreferrer">开源许可</a></div>
      </div>
    </aside>
    <div className="resize-handle" role="separator" aria-label="调整导航宽度" aria-orientation="vertical" aria-valuemin={SIDEBAR_MIN} aria-valuemax={SIDEBAR_MAX} aria-valuenow={sidebarWidth} tabIndex={sideOpen ? 0 : -1}
      onPointerDown={drag} onPointerMove={e => { if (e.currentTarget.hasPointerCapture(e.pointerId)) setSidebarWidth(clampWidth(e.clientX, SIDEBAR_MIN, SIDEBAR_MAX)) }} onPointerUp={e => { e.currentTarget.releasePointerCapture(e.pointerId); setDragging(false) }} onLostPointerCapture={() => setDragging(false)}
      onKeyDown={e => { if (['ArrowLeft', 'ArrowRight'].includes(e.key)) { e.preventDefault(); setSidebarWidth(w => clampWidth(w + (e.key === 'ArrowLeft' ? -16 : 16), SIDEBAR_MIN, SIDEBAR_MAX)) } }} />
    <main id="main-content" className={`${frameCss.centerCol} main-column`}>
      <header className="topbar"><div className="task-heading"><h1>{taskTitle}</h1>{active && <Status value={active} />}</div><div className="top-actions"><span className="local-badge">本机工作台</span><Button aria-label={inspectorOpen ? '关闭任务详情' : '打开任务详情'} title="任务详情" onClick={() => setInspectorOpen(v => !v)}><Icon name="panel" /></Button></div></header>
      {error && <div className="error-banner" role="alert"><span>{error}</span><button aria-label="关闭提示" onClick={() => setError('')}><Icon name="close" /></button></div>}
      {!api ? <section className="welcome connection-page"><div className="intro-symbol">J</div><h2>{connecting ? '正在打开工作台…' : '本机服务暂时无法连接'}</h2><p>请保持启动窗口运行。</p><Button variant="primary" disabled={connecting} onClick={() => void connect()}>重新连接</Button></section>
        : settingsOpen ? <Settings api={api} onBack={() => setSettingsOpen(false)} onGuardChange={setSettingsGuard} /> : <>
          {pending && <div className="pending-banner" role="status"><div><strong>本次提交待核对</strong><p>为避免重复执行，已暂停创建。核对操作只读取后台记录。</p></div><Button variant="outline" disabled={!!busy || submitting} onClick={() => void reconcile()}>核对提交状态</Button>
            <button className="text-button" disabled={submitting || !!busy} onClick={() => { if (window.confirm('这只清除本页待核对记录，后台任务可能仍在执行。确定已检查任务列表并清除？')) { storage('jh-pending', ''); setPending('') } }}>清除本页记录</button></div>}
          {!selected ? <section className="welcome"><div className="intro-symbol">J</div><h2>今天，想完成什么？</h2><p>描述目标，让 J-Harness 在你的工作区中协助完成。</p><div className="suggestions">{['阅读项目结构，梳理模块职责', '检查最近的修改，指出潜在问题', '分析一个问题，并提出修复方案'].map(s => <button key={s} onClick={() => setGoal(s)}><Icon name="file" />{s}<Icon name="chevron" /></button>)}</div></section>
            : <section className="timeline" aria-label="任务事件时间线" onScroll={e => { const el = e.currentTarget; autoScroll.current = el.scrollHeight - el.scrollTop - el.clientHeight < 100 }}>
              {active && uncertain(active) && <div className="unknown-notice"><strong>结果待核实</strong><p>部分操作可能已发生，当前不能安全地自动重试。请停止旧宿主，检查实际资源，再通过 CLI 离线核实；本页面不会重放任务。</p></div>}
              {api && active && isTerminal(active) && <div ref={diagnosisAnchor} tabIndex={-1}><DiagnosisPanel key={active.taskId} api={api} task={active} refresh={historyKey} /></div>}
              {events.length === 0 && <p className="muted timeline-empty">尚无事件记录。正在读取任务状态。</p>}
              {events.map(e => <EventRow key={e.eventId} event={e} onSelect={() => { setFocusedEvent(e); setTab('detail'); setInspectorOpen(true) }} />)}
              <div ref={listEnd} />
            </section>}
          {approvals.length > 0 && <section className="approval-region" aria-label="待处理审批">{approvals.map(a => <div className="approval-card" key={a.approvalId}>
            <div className="approval-title"><Tag tone="warning">需要你的决定</Tag><strong>{a.tool}</strong><span>{a.sideEffectLevel}</span></div>
            <p>将按以下参数执行一次操作。批准仅对本次调用有效。</p><pre>{pretty(a.arguments)}</pre><details><summary>审批关联</summary><code>{a.approvalId}<br />{a.callId}</code></details>
            <div className="approval-actions"><Button variant="outline" disabled={!!busy} onClick={() => void decide(a, false)}>拒绝</Button><Button variant="primary" disabled={!!busy} onClick={() => void decide(a, true)}>{busy === a.approvalId ? '正在处理…' : '批准本次操作'}</Button></div></div>)}</section>}
          {!selected && <form className="composer" onSubmit={e => void submit(e)}><textarea aria-label="任务目标" placeholder="描述你的任务…" value={goal} maxLength={16384} onChange={e => setGoal(e.target.value)} rows={3} disabled={submitting}
            onKeyDown={e => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && !e.nativeEvent.isComposing) { e.preventDefault(); e.currentTarget.form?.requestSubmit() } }} />
            <div className="composer-toolbar"><label className="profile-picker">配置 <select aria-label="任务配置" value={profile} onChange={e => setProfile(e.target.value)}>{profiles.map(p => <option key={p}>{p}</option>)}</select></label><span className="composer-shortcut">Ctrl / ⌘ + Enter</span><Button type="submit" variant="primary" aria-label="创建任务" disabled={!goal.trim() || !profile || submitting || !!pending}><Icon name="arrow" /></Button></div></form>}
          {selected && <footer className="activity-footer"><TextShimmer active={!!active && !isTerminal(active)}>{live}</TextShimmer>{active && active.cancelRequested && <span>已请求取消，正在等待执行事实</span>}
            <div>{hasProblems && <Button onClick={() => { diagnosisAnchor.current?.scrollIntoView({ block: 'start' }); diagnosisAnchor.current?.focus({ preventScroll: true }); autoScroll.current = false }}>执行诊断</Button>}<Button onClick={() => { setError(''); setHistoryKey(k => k + 1) }}>重载历史</Button>{active && !isTerminal(active) && <Button variant="outline" icon={<Icon name="stop" />} disabled={!!busy || active.cancelRequested} onClick={() => void cancel()}>取消任务</Button>}</div></footer>}
          <div className="workspace-note">工具操作沿用工作区权限与审批 · 执行结果以记录为准</div>
        </>}
    </main>
    {inspectorOpen && <aside className="inspector" aria-label="任务详情面板"><div className="inspector-heading"><strong>任务详情</strong><Button aria-label="关闭任务详情" onClick={() => setInspectorOpen(false)}><Icon name="close" /></Button></div>
      <SegmentedTabs items={tabs} value={tab} onChange={setTab} label="任务详情分类" />
      <InspectorPanel key={`${selected}:${tab}:${api ? 'on' : 'off'}`} api={api} task={active} tab={tab} event={focusedEvent} />
    </aside>}
  </div>
}

function EventRow({ event, onSelect }: { event: SessionEvent; onSelect: () => void }) {
  const [open, setOpen] = useState(false)
  const message = event.type === 'user.message' || event.type === 'assistant.message'
  const content = typeof event.payload.content === 'string' ? event.payload.content : null
  const title = eventLabels[event.type] ?? event.type
  return <article className={`event-row ${message ? 'message-row' : ''} ${event.type === 'user.message' ? 'user-row' : ''}`}>
    <div className="event-summary"><button className="event-toggle" aria-expanded={open} onClick={() => setOpen(v => !v)}><span className={`event-chevron ${open ? 'open' : ''}`}><Icon name="chevron" /></span><span>{title}</span>{typeof event.payload.tool === 'string' && <code>{event.payload.tool}</code>}{typeof event.payload.status === 'string' && <Tag>{event.payload.status}</Tag>}</button><button className="event-inspect" title="查看事件详情" aria-label={`查看事件 ${event.eventId}`} onClick={onSelect}><Icon name="file" /></button></div>
    {message && content && <div className="message-content">{content}</div>}
    {event.payload.omitted === true && <p className="muted">大事件正文已省略，仅保留摘要和哈希。</p>}
    {open && <div className="event-body"><time>{event.timestamp}</time><pre>{pretty(event.payload)}</pre></div>}
  </article>
}
function InspectorPanel({ api, task, tab, event }: { api: Api | null; task?: Task; tab: Inspector; event: SessionEvent | null }) {
  const [data, setData] = useState<unknown>(null), [error, setError] = useState(''), [loading, setLoading] = useState(false)
  const [query, setQuery] = useState(''), [result, setResult] = useState<Page | null>(null)
  const mounted = useRef(true), searchLock = useRef(false)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  useEffect(() => {
    if (!api || !task || !['report', 'diff'].includes(tab)) return
    let cancelled = false
    setLoading(true)
    void (tab === 'report' ? api.report(task.taskId) : api.diff(task.taskId)).then(v => { if (!cancelled) setData(v) })
      .catch(e => { if (!cancelled) setError(e instanceof ApiError && e.code === 'DIFF_NOT_RECORDED' ? '该会话没有记录 Diff。页面不会自动运行 Git。' : errorText(e)) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [api, task?.taskId, tab, task?.phase])
  async function search(more = false) {
    if (!api || !task || !query.trim() || searchLock.current) return
    searchLock.current = true; setLoading(true); setError('')
    try { const p = await api.search(task.taskId, query.trim(), more ? result?.next : ''); if (mounted.current) setResult(more && result ? { ...p, events: mergeEvents(result.events, p.events) } : p) }
    catch (e) { if (mounted.current) setError(errorText(e)) }
    finally { searchLock.current = false; if (mounted.current) setLoading(false) }
  }
  return <section className="inspector-body" role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`}>
    {!api || !task ? <p className="empty-detail">选择一个已连接的任务，查看执行详情。</p> : <>
      {tab === 'detail' && <><div className="detail-label">{event ? '事件记录' : '任务状态'}</div><pre>{pretty(event ?? task)}</pre><p className="muted">点击时间线右侧的详情图标，可查看完整关联信息。</p></>}
      {tab === 'search' && <><p className="muted">查询该会话的完整脱敏事件正文；大事件返回省略标识。</p><form className="search-form" onSubmit={e => { e.preventDefault(); void search() }}><Input aria-label="查询会话内容" value={query} maxLength={256} disabled={loading} placeholder="输入事件类型或内容" onChange={e => { setQuery(e.target.value); setResult(null) }} /><Button type="submit" variant="outline" disabled={loading || !query.trim()}>查询</Button></form>
        {result && <><p className="small">已找到 {result.events.length} 条匹配记录</p>{result.events.map(e => <details key={e.eventId}><summary>{e.type}</summary><pre>{pretty(e)}</pre></details>)}{result.more && <Button disabled={loading} onClick={() => void search(true)}>加载更多匹配</Button>}</>}
      </>}
      {loading && <p role="status">正在读取…</p>}{error && <p role="status" className="inline-error">{error}</p>}
      {data !== null && <><div className="detail-label">{tab === 'report' ? '基于已记录事件的事实报告' : '记录的 git_diff 工具结果'}</div>{tab === 'report' ? <FactReport value={data} /> : <RecordedDiff value={data} />}{tab === 'report' && <Button variant="outline" onClick={() => { const url = URL.createObjectURL(new Blob([pretty(data)], { type: 'application/json' })); const a = document.createElement('a'); a.href = url; a.download = `${task.taskId}-report.json`; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }}>下载事实报告</Button>}</>}
    </>}
  </section>
}

function safeObject(v: unknown): Record<string, unknown> { try { return object(v) } catch { return {} } }
function RecordedDiff({ value }: { value: unknown }) {
  const event = safeObject(safeObject(value).event), payload = safeObject(event.payload), result = safeObject(payload.value)
  const text = typeof result.stdout === 'string' ? result.stdout : typeof result.diff === 'string' ? result.diff : typeof payload.content === 'string' ? payload.content : ''
  return <><p className="small muted">来源：已记录的工具调用。{String(payload.status ?? '')}</p>
    {payload.omitted === true ? <p>该事件正文已省略，无法显示完整 Diff。</p> : text ? <pre className="diff-view">{text.split('\n').map((line, i) => <span className={line.startsWith('+') ? 'diff-add' : line.startsWith('-') ? 'diff-delete' : ''} key={i}>{line}{'\n'}</span>)}</pre> : <p className="muted">工具没有返回可展示的差异正文。</p>}
    <details><summary>查看原始记录与关联</summary><pre>{pretty(value)}</pre></details></>
}
function FactReport({ value }: { value: unknown }) {
  const report = safeObject(value), summary = safeObject(report.task)
  const facts = Array.isArray(report.facts) ? report.facts.map(safeObject) : []
  return <div className="fact-report"><h2>{summary.phase === 'VERIFIED' ? '已离线核实（不代表任务成功）' : labels[String(summary.status)] ?? String(summary.status ?? '状态未记录')}</h2>
    <p className="muted">已记录 {String(report.eventCount ?? 0)} 条事件 · 配置 {String(summary.profile ?? '')}</p>
    <h3>操作与产物</h3>{facts.length === 0 && <p className="muted">没有可用的操作结果记录。</p>}
    {facts.map((f, i) => { const p = safeObject(f.payload), result = safeObject(p.value); return <div className="report-fact" key={i}>
      <div><strong>{String(p.tool ?? eventLabels[String(f.type)] ?? f.type)}</strong>{typeof p.status === 'string' && <Tag tone={p.status === 'SUCCESS' ? 'success' : 'neutral'}>{labels[p.status] ?? p.status}</Tag>}</div>
      {typeof result.path === 'string' && <code>{result.path}</code>}
      {typeof p.path === 'string' && <code>{p.path}</code>}
      {p.omitted === true && <p className="small muted">正文已省略，见记录哈希。</p>}
      <details><summary>查看记录</summary><pre>{pretty(f)}</pre></details></div> })}
    {report.factsLimited === true && <p className="muted small">报告仅展示最近的有限结果，完整事实请使用会话查询。</p>}
    <details className="raw-report"><summary>原始事实报告</summary><pre>{pretty(value)}</pre></details>
  </div>
}
