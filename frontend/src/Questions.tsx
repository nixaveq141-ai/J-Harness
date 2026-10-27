import { useCallback, useEffect, useRef, useState } from 'react'
import { Api, ApiError, type UserQuestion } from './api'
import { Button } from './upstream/Button'
import { Tag } from './upstream/Tag'
import { useLifetime } from './lifecycle'
import { pendingMarker as marker } from './pendingMarker'

const labels: Record<string,string> = { PENDING: '等待回答', SUBMITTED: '正在核对回答', ANSWERED: '已回答', SKIPPED: '已跳过', TIMED_OUT: '已超时 · 未收到答案', CANCELLED: '已取消', INTERRUPTED: '等待已中断 · 不会自动重提' }

export function Questions({ api, id, onGuard, onWaiting }: { api: Api; id: string; onGuard: (value: boolean) => void; onWaiting: (value: boolean) => void }) {
  const [offset,setOffset] = useState(0), [total,setTotal] = useState(0), [hasWaiting,setHasWaiting] = useState(false)
  const [guards,setGuards] = useState<Record<string,boolean>>({})
  const guard = useCallback((id: string, value: boolean) => setGuards(old=>old[id]===value?old:{...old,[id]:value}),[])
  useEffect(()=>{ onGuard(Object.values(guards).some(Boolean));return()=>onGuard(false) },[guards,onGuard])
  const [rows,setRows] = useState<UserQuestion[]>([]), [error,setError] = useState('')
  useEffect(()=>{onWaiting(hasWaiting);return()=>onWaiting(false)},[hasWaiting,onWaiting])
  useEffect(() => {
    let stopped = false, reading = false, failures=0,nextRead=0
    async function read() { if (stopped || reading || document.hidden || Date.now()<nextRead) return; reading = true
      try { const value = await api.questionPage(id,offset); if (!stopped) { failures=0;setRows(value.questions);setTotal(value.total);setHasWaiting(value.hasWaiting);setError('') } }
      catch (e) { nextRead=Date.now()+Math.min(30000,700*2**Math.min(++failures,6));if (!stopped && !(e instanceof ApiError && e.status===404)) setError('问题读取中断。重连只读取状态，不会自动回答。') }
      finally { reading = false }
    }
    void read(); const timer = setInterval(() => void read(),700)
    return () => { stopped = true; clearInterval(timer) }
  },[api,id,offset])
  if (!rows.length && !error) return null
  return <section className="questions" aria-label="模型问题">{error && <p role="status">{error}</p>}{rows.map(q => <QuestionCard key={q.questionId} api={api} value={q} onGuard={guard} />)}{total>8 && <div className="question-actions"><Button disabled={offset===0 || Object.values(guards).some(Boolean)} onClick={()=>setOffset(Math.max(0,offset-8))}>返回较新问题</Button><Button disabled={offset+8>=total || Object.values(guards).some(Boolean)} onClick={()=>setOffset(offset+8)}>查看更早问题</Button></div>}</section>
}
function QuestionCard({ api, value: q, onGuard }: { api: Api; value: UserQuestion; onGuard: (id: string,value: boolean) => void }) {
  const key = 'jh-answer-' + q.sessionId + '-' + q.questionId
  const [selected,setSelected] = useState<string[]>(() => q.draft?.selected ?? q.options.filter(o=>o.recommended).map(o=>o.id))
  const [text,setText] = useState(q.draft?.text ?? ''), [pending,setPending] = useState(() => marker(key))
  const [busy,setBusy] = useState(false), [error,setError] = useState(''), [notice,setNotice] = useState(q.draft ? '草稿已从当前宿主恢复' : '')
  const [dirty,setDirty] = useState(false), [checking,setChecking] = useState(false)
  const saving = useRef(false), revision = useRef(q.draftRevision ?? 0), [draftTick,setDraftTick] = useState(0)
  const lock = useRef(false), edit = useRef(0), card = useRef<HTMLElement>(null)
  const life=useLifetime(api,q.sessionId,q.questionId),attempt=useRef(pending),savingTask=useRef<Promise<unknown>|null>(null)
  const open = q.state==='PENDING', waiting = !!pending || q.state==='SUBMITTED'
  useEffect(()=>{saving.current=false;savingTask.current=null;lock.current=false;setBusy(false);setChecking(false);revision.current=q.draftRevision??0},[api,q.sessionId,q.questionId])
  useEffect(()=>{ onGuard(q.questionId,open && (dirty || busy));return()=>onGuard(q.questionId,false) },[onGuard,q.questionId,open,dirty,busy])
  useEffect(()=>{ if(open)card.current?.scrollIntoView({block:'nearest'}) },[])
  useEffect(()=>{
    if (!['PENDING','SUBMITTED'].includes(q.state)) { marker(key,'');setPending('');setDirty(false) }
  },[q.state,key])
  useEffect(()=>{
    if(!dirty || !open || waiting) return
    const generation = edit.current
    const timer=setTimeout(()=>{
      if (saving.current) return
      saving.current=true
      savingTask.current=api.questionDraft(q,selected,text,revision.current).then(saved=>{
        if(!life.alive)return
        revision.current=saved.revision
        if(life.alive && generation===edit.current){setDirty(false);setNotice('草稿已保存到当前宿主内存，刷新可恢复')}
      }).catch(e=>{
        if(life.alive && generation===edit.current)setNotice(e instanceof ApiError && e.code==='DRAFT_CONFLICT'?'另一页面修改了草稿，请刷新读取；本页修改尚未保存。':'草稿尚未保存；请保持此页，或修改后重试保存')
      }).finally(()=>{if(!life.alive)return;saving.current=false;if(life.alive && generation!==edit.current)setDraftTick(t=>t+1)})
    },350)
    return()=>clearTimeout(timer)
  },[api,dirty,selected,text,open,waiting,draftTick])
  useEffect(()=>{ if(!dirty && !busy)return;const protect=(e:BeforeUnloadEvent)=>e.preventDefault();window.addEventListener('beforeunload',protect);return()=>window.removeEventListener('beforeunload',protect) },[dirty,busy])
  function changed() { edit.current++;setDirty(true);setError('');setNotice('草稿尚未保存') }
  async function answer(skip: boolean) {
    if(lock.current || !open || waiting) return
    lock.current=true;setBusy(true);setError('')
    try {
      if(savingTask.current)await savingTask.current
      if(!life.alive)return
      // Flush the exact answer snapshot before any admission can lose its response.
      const saved=await api.questionDraft(q,selected,text,revision.current);
      if(!life.alive)return
      revision.current=saved.revision
      const id=attempt.current||crypto.randomUUID();if(marker(key,id)!==id)throw new ApiError('PENDING_MARKER_LIMIT');attempt.current=id;setPending(id)
      try { if(sessionStorage.getItem(key)!==id)setNotice('本页核对标记无法保存；请保持页面，刷新后先查问题状态。') }catch{setNotice('本页核对标记无法保存；请保持页面，刷新后先查问题状态。')}
      const receipt=await api.answerQuestion(q,id,skip?[]:selected,skip?'':text,skip);if(life.alive){setDirty(false);setNotice(receipt.state==='SUBMITTED'?'已接收，正在核对持久记录':'回答状态已记录')}
    }
    catch(e) { if(life.alive){setError(e instanceof ApiError && e.code==='PENDING_MARKER_LIMIT'?'待核对请求过多，请先核对已有请求。':e instanceof ApiError && e.code==='INVALID_ANSWER'?'答案格式无效，请检查选项或文字。':'回答未确认，请核对状态；不会自动重发。');if(e instanceof ApiError && ['INVALID_ANSWER','QUESTION_CLOSED','QUESTION_NOT_FOUND','QUESTION_ASSOCIATION_MISMATCH','ANSWER_CONFLICT'].includes(e.code)){setPending('');marker(key,'')}} }
    finally { if(life.alive){lock.current=false;setBusy(false)} }
  }
  if(!open && q.state!=='SUBMITTED') return <details className="question-card question-closed"><summary>{q.question} · {labels[q.state]}</summary>{q.state==='ANSWERED' && <p>{(q.selected??[]).map(id=>q.options.find(o=>o.id===id)?.label??id).join('、')}{q.text ? ' '+q.text : ''}</p>}<p className="muted">问题回答不授权工具操作。轮次 {q.turnId.slice(0,8)}</p></details>
  return <article className="question-card" ref={card} aria-label="待回答问题">
    <div className="question-title"><Tag>{labels[q.state]}</Tag><span>模型提问</span></div><h3>{q.question}</h3>
    <p className="field-help">回答只提供信息，不批准写入、命令或计划。推荐项尚未提交。</p>
    <fieldset disabled={!open || waiting || busy}><legend>{q.mode==='single'?'请选择一项':q.mode==='multiple'?'可选择多项':'填写你的回答'}</legend>
      {q.options.map(o=><label className="question-option" key={o.id}><input type={q.mode==='single'?'radio':'checkbox'} name={q.questionId} checked={selected.includes(o.id)} onChange={()=>{setSelected(old=>q.mode==='single'?[o.id]:old.includes(o.id)?old.filter(v=>v!==o.id):[...old,o.id]);changed()}} /><span><strong>{o.label}</strong>{o.recommended && <small>推荐</small>}{o.description && <span>{o.description}</span>}</span></label>)}
      {q.allowText && <label className="question-text">文字回答{q.mode!=='text'?'（可补充）':''}<textarea aria-label="问题文字回答" maxLength={4096} rows={3} value={text} onChange={e=>{setText(e.target.value);changed()}} /></label>}
    </fieldset>
    <p className="muted question-deadline">截止 {new Date(q.expiresAt).toLocaleTimeString()} · 超时不视为同意</p>
    <div className="question-actions"><Button variant="outline" disabled={!open || waiting || busy} onClick={()=>void answer(true)}>跳过问题</Button><Button variant="primary" disabled={!open || waiting || busy || !selected.length && !text.trim()} onClick={()=>void answer(false)}>提交回答</Button></div>
    {waiting && <div role="status">回答待核对，不会自动重发。<Button disabled={checking || busy} onClick={()=>{setChecking(true);void api.questionStatus(q.sessionId,q.questionId).then(row=>{if(!life.alive)return;setNotice(row ? '服务器状态：'+labels[row.state] : '未找到问题');if(row?.state==='PENDING')setNotice('暂未看到回答记录；可继续核对，或明确清除本页标记。')}).catch(()=>{if(life.alive)setError('读取失败，请保持待核对状态。')}).finally(()=>{if(life.alive)setChecking(false)})}}>核对回答</Button>{q.state==='PENDING' && <Button disabled={busy || checking} onClick={()=>{marker(key,'');setPending('');setNotice('已清除本页标记，未撤回后台可能已经接收的回答。')}}>清除本页标记</Button>}</div>}
    {error && <p className="inline-error" role="alert">{error}</p>}{notice && <p className="question-notice" role="status">{notice}</p>}
  </article>
}
