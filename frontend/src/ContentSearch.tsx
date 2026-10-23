import { useEffect, useRef, useState } from 'react'
import { Api, ApiError, type ContentResults } from './api'
import { Button } from './upstream/Button'
import { Input } from './upstream/Input'
import { ContentReader } from './ContentPanel'

export function ContentSearch({ api, profile, view, currentSession, workspaces, onOpen, onBack }: { api: Api; profile: string; view: string; currentSession: string; workspaces: Record<string,string>; onOpen: (id:string)=>Promise<void>; onBack:()=>void }) {
  const [query,setQuery]=useState(''),[mode,setMode]=useState<'tokens'|'phrase'>('tokens'),[selectedProfile,setProfile]=useState(profile),[selectedView,setView]=useState(view),[single,setSingle]=useState(false)
  const [result,setResult]=useState<ContentResults|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState(''),[source,setSource]=useState('')
  const request=useRef<AbortController|null>(null), lock=useRef(false)
  useEffect(()=>{lock.current=false;setBusy(false);setResult(null);setSource('');setError('');return()=>{request.current?.abort()}},[api,currentSession])
  function change(update:()=>void) { request.current?.abort(); lock.current=false;setBusy(false);setResult(null);setSource('');setError('');update() }
  async function search(more=false,rebuild=false) {
    if(lock.current||!query.trim())return
    const controller=new AbortController();request.current=controller;lock.current=true;setBusy(true);setError('')
    if(!more){setResult(null);setSource('')}
    try {
      const value=await api.searchContent({text:query,mode,profile:selectedProfile,view:selectedView,sessionId:single&&currentSession?currentSession:'',cursor:more&&result?result.next:'',limit:20,rebuild},controller.signal)
      if(controller.signal.aborted)return
      if(more&&result&&(value.snapshotId!==result.snapshotId||value.total!==result.total||value.matches.length+result.matches.length>value.total||value.more&&value.next===result.next||value.matches.some(h=>result.matches.some(old=>old.eventId===h.eventId&&old.sessionId===h.sessionId))))throw new ApiError('INVALID_RESPONSE')
      setResult(more&&result?{...value,matches:[...result.matches,...value.matches]}:value)
    } catch(e) {
      if(controller.signal.aborted)return
      const labels:Record<string,string>={SEARCH_CURSOR_EXPIRED:'结果快照已过期或范围已变化，请重新查询。',SEARCH_SCOPE_LIMIT:'当前范围超过256个会话，请选择工作区或仅查询当前会话。',SEARCH_INDEX_LIMIT:'会话正文超过索引容量限制，请使用原会话事件分页查看。',SEARCH_RESULT_LIMIT:'匹配结果超过4096条，请缩小查询条件。',SEARCH_BUSY:'索引正由其他请求更新，请稍后重新查询。',SEARCH_UNAVAILABLE:'该工作区未启用内容检索。',SEARCH_SCOPE_DENIED:'当前会话不在所选工作区或归档范围内。',CANCELLED:'查询达到时间限制，请缩小范围后重试。',INVALID_SEARCH:'请输入有效词语或连续短语（最多256字符）。'}
      setError(e instanceof ApiError?e.code==='INVALID_RESPONSE'?'服务数据与检索协议不符，请核对版本后重新读取。':labels[e.code]??'内容查询未完成，请重新查询。':'连接中断；不会自动重新查询。')
    } finally { if(!controller.signal.aborted){lock.current=false;setBusy(false)} }
  }
  return <section className="content-search-page" aria-label="内容检索">
    <div className="settings-heading"><div><h2>内容检索</h2><p className="muted">查询授权工作区中的脱敏会话正文，按相关性、时间和来源稳定排序。</p></div><Button onClick={onBack}>返回任务</Button></div>
    <form onSubmit={e=>{e.preventDefault();void search()}}>
      <label>内容关键词<Input aria-label="内容关键词" value={query} maxLength={256} onChange={e=>change(()=>setQuery(e.target.value))} placeholder="搜索消息、工具结果或问题回答…"/></label>
      <div className="content-search-controls">
        <label>匹配方式<select value={mode} onChange={e=>change(()=>setMode(e.target.value as 'tokens'|'phrase'))}><option value="tokens">所有词匹配</option><option value="phrase">连续短语</option></select></label>
        <label>检索工作区<select value={selectedProfile} onChange={e=>change(()=>setProfile(e.target.value))}><option value="">所有已注册工作区</option>{Object.entries(workspaces).map(([id,name])=><option value={id} key={id}>{name}</option>)}</select></label>
        <label>归档范围<select value={selectedView} onChange={e=>change(()=>setView(e.target.value))}><option value="active">未归档</option><option value="archived">已归档</option><option value="all">全部会话</option></select></label>
        <label className="settings-check"><input type="checkbox" disabled={!currentSession} checked={single&&!!currentSession} onChange={e=>change(()=>setSingle(e.target.checked))}/>仅当前会话</label>
        <Button type="submit" variant="primary" disabled={busy||!query.trim()}>检索内容</Button>
      </div>
    </form>
    <p className="field-help">词语忽略大小写，全部匹配；汉字逐字匹配。连续短语要求原文顺序及空格一致。每页20条，结果快照保留2分钟；新的事件须重新查询。</p>
    <details><summary>索引维护</summary><p className="muted">索引可丢弃，仅从脱敏事实正文重建，不改变会话日志或执行任务。</p><Button disabled={busy||!query.trim()} onClick={()=>void search(false,true)}>重建范围索引并查询</Button></details>
    {busy&&<p role="status">正在检索… <Button onClick={()=>change(()=>{})}>停止读取</Button></p>}
    {error&&<p role="status" className="inline-error">{error}</p>}
    {result&&<><p role="status">找到 {result.total} 条正文匹配，已显示 {result.matches.length} 条。{result.total===0?'请调整关键词或范围。':''}</p><ol className="content-search-results">{result.matches.map(h=><li key={h.sessionId+':'+h.eventId}>
      <div className="content-search-result-heading"><strong>{h.name||`会话 ${h.sessionId.slice(-8)}`}</strong><span>{workspaces[h.profile]??h.profile} · {h.archived?'已归档':'未归档'}</span></div>
      <p className="search-snippet">{h.snippet}</p><small className="muted">{h.type} · {new Date(h.timestamp).toLocaleString()} · 匹配分 {h.score}</small><code className="content-path">{h.sessionId} / {h.eventId}{h.turnId?` / ${h.turnId}`:''}</code>
      <div className="content-actions"><Button onClick={()=>{void onOpen(h.sessionId).catch(()=>setError('来源会话已不可用，请重新查询。'))}}>打开来源会话</Button><Button aria-expanded={source===h.sessionId+":"+h.eventId} onClick={()=>setSource(source===h.sessionId+":"+h.eventId?'':h.sessionId+":"+h.eventId)}>读取来源事件</Button></div>
      {source===h.sessionId+":"+h.eventId&&<ContentReader api={api} id={h.sessionId} kind="content" target={h.eventId}/>}
    </li>)}</ol>{result.more&&<Button disabled={busy} onClick={()=>void search(true)}>加载更多正文匹配</Button>}</>}
  </section>
}
