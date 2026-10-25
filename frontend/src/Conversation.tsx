import { toolAssociation } from './content'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Api, isTerminal, type SessionEvent, type Task } from './api'
import { Markdown } from './Markdown'
import { ContentReader } from './ContentPanel'
import { Button } from './upstream/Button'
import { Tag } from './upstream/Tag'
const obj=(v:unknown):Record<string,unknown>=>v&&typeof v==='object'&&!Array.isArray(v)?v as Record<string,unknown>:{}
const pretty=(v:unknown)=>JSON.stringify(v,null,2)
const statuses:Record<string,string>={SUCCESS:'成功',FAILED:'失败',DENIED:'已拒绝',CANCELLED:'已取消',TIMEOUT:'已超时',UNKNOWN:'结果待核实',COMPLETED:'已完成',LIMIT_REACHED:'达到限制'}
const names:Record<string,string>={read_file:'读取文件',list_directory:'浏览目录',write_file:'写入文件',edit_file:'编辑文件',apply_patch:'应用补丁',execute_command:'执行命令',git_diff:'读取Git差异',read_artifact:'读取产物',ask_user:'向你提问'}
export function Conversation({api,task,events,onInspect,onFile,renderEvent}:{api:Api;task:Task;events:SessionEvent[];onInspect:(e:SessionEvent)=>void;onFile:(path:string,line:number)=>void;renderEvent:(e:SessionEvent)=>ReactNode}) {
  const [all,setAll]=useState(false),results=new Map<string,SessionEvent>(),requests=new Map<string,SessionEvent>()
  for(const e of events){if(e.type==='tool.completed'&&e.callId)results.set(toolAssociation(e),e);if(e.type==='tool.requested'&&e.callId)requests.set(toolAssociation(e),e)}
  const internal=events
  return <div className="conversation"><div className="conversation-toolbar"><span>对话与执行</span><Button onClick={()=>setAll(v=>!v)} aria-expanded={all}>{all?'收起执行细节':'展开执行细节'} · {internal.length}</Button></div>
    {events.map(e=>{
      if(e.type==='user.message'||e.type==='assistant.message')return <Message key={e.eventId} api={api} id={task.taskId} event={e} onInspect={onInspect} onFile={onFile}/>
      if(e.type==='tool.requested')return <ToolCard key={e.eventId} api={api} id={task.taskId} request={e} result={e.callId?results.get(toolAssociation(e)):undefined} onInspect={onInspect} onFile={onFile}/>
      if(e.type==='tool.completed'&&(!e.callId||!requests.has(toolAssociation(e))))return <ToolCard key={e.eventId} api={api} id={task.taskId} result={e} onInspect={onInspect} onFile={onFile}/>
      return null
    })}
    <LiveDisplay api={api} task={task}/><Usage events={events}/>
    {all&&<section className="execution-details" aria-label="执行细节">{internal.map(renderEvent)}</section>}
  </div>
}
function Message({api,id,event:e,onInspect,onFile}:{api:Api;id:string;event:SessionEvent;onInspect:(e:SessionEvent)=>void;onFile:(path:string,line:number)=>void}) {
  const [read,setRead]=useState(false),user=e.type==='user.message',content=typeof e.payload.content==='string'?e.payload.content:''
  if(!content&&e.payload.omitted!==true)return null
  return <article className={'conversation-message '+(user?'from-user':'from-assistant')} aria-label={user?'用户消息':'助手消息'}><div className="message-heading"><strong>{user?'你':'助手'}</strong><time dateTime={e.timestamp}>{new Date(e.timestamp).toLocaleTimeString()}</time><Button aria-label={'查看事件 '+e.eventId} onClick={()=>onInspect(e)}>来源</Button></div>
    {content&&<Markdown text={content} onFile={onFile}/>}
    {e.payload.omitted===true&&<><p className="muted">此条消息较长，可按页读取完整记录。</p><Button onClick={()=>setRead(v=>!v)}>回读完整消息</Button></>}
    {read&&<ContentReader api={api} id={id} kind="content" target={e.eventId}/>}
  </article>
}
function ToolCard({api,id,request,result,onInspect,onFile}:{api:Api;id:string;request?:SessionEvent;result?:SessionEvent;onInspect:(e:SessionEvent)=>void;onFile:(path:string,line:number)=>void}) {
  const [read,setRead]=useState(false),[artifactRead,setArtifactRead]=useState(false),[expanded,setExpanded]=useState(false),e=result??request!,p=e.payload,r=obj(result?.payload.value)
  const tool=String(request?.payload.tool??result?.payload.tool??'工具'),status=String(result?.payload.status??'等待结果')
  const changes=(Array.isArray(r.changes)?r.changes.map(obj):[r]).filter(c=>typeof c.path==='string'&&typeof c.beforeVersion==='string'&&typeof c.afterVersion==='string')
  const body=expanded?(typeof result?.payload.content==='string'?result.payload.content:result?pretty(r):''):''
  return <article className="tool-card" aria-label={'工具卡 '+tool}><div className="tool-card-heading"><strong>{names[tool]??tool}</strong><Tag tone={status==='SUCCESS'?'success':status==='FAILED'?'danger':'neutral'}>{statuses[status]??status}</Tag><Button aria-label={'查看事件 '+e.eventId} onClick={()=>onInspect(e)}>来源</Button></div>
    <p className="tool-meta">{String(request?.payload.sideEffectLevel??result?.payload.sideEffectLevel??'影响级别未记录')}{typeof p.durationMs==='number'?' · '+p.durationMs+' ms':''}</p>
    {request&&<details><summary>调用参数与关联</summary><pre>{pretty(request.payload)}</pre><code>{request.callId}</code></details>}
    {result&&<details onToggle={event=>setExpanded(event.currentTarget.open)}><summary>查看工具结果</summary><pre>{body.slice(0,4000)}</pre>{body.length>4000&&<p className="muted">预览已截断，可回读完整事件。</p>}</details>}
    {result&&<Button onClick={()=>setRead(v=>!v)}>回读完整工具结果</Button>}
    {typeof r.path==='string'&&!changes.length&&<button className="file-reference" onClick={()=>onFile(r.path as string,1)}>{r.path}</button>}
    {changes.map((c,i)=><div className="changed-file" key={i}><button className="file-reference" onClick={()=>onFile(String(c.path),1)}>{String(c.path)}</button><span>当时记录的变更</span>{typeof c.beforeVersion==='string'&&<small>{c.beforeVersion.slice(0,10)} → {String(c.afterVersion??'未知').slice(0,10)}</small>}</div>)}
    {read&&result&&<ContentReader api={api} id={id} kind="content" target={result.eventId}/>}
    {typeof r.artifactRef==='string'&&<><Button onClick={()=>setArtifactRead(v=>!v)}>读取工具产物</Button>{artifactRead&&<ContentReader api={api} id={id} kind="artifacts" target={r.artifactRef}/>}</>}
  </article>
}
function LiveDisplay({api,task}:{api:Api;task:Task}) {
  const [value,setValue]=useState<{state:string;text:string;sourceEvent:string;truncated:boolean}|null>(null),[paused,setPaused]=useState(false),[error,setError]=useState('')
  const block=useRef<HTMLElement>(null)
  useEffect(()=>{setValue(null);setPaused(false);setError('')},[api,task.taskId])
  useEffect(()=>{
    if(paused)return
    const c=new AbortController();let reading=false
    async function read(){if(reading||c.signal.aborted)return;reading=true
      try{const v=await api.display(task.taskId,c.signal);if(!c.signal.aborted){setValue(v);setError('')}}catch{if(!c.signal.aborted)setError('实时显示读取中断；任务状态以持久记录为准。')}finally{reading=false}}
    void read();const timer=isTerminal(task)?null:setInterval(()=>void read(),350)
    return()=>{c.abort();if(timer)clearInterval(timer)}
  },[api,task.taskId,task.phase,paused])
  useEffect(()=>{const node=block.current,scroll=node?.closest('.timeline');if(node&&scroll&&scroll.scrollHeight-scroll.scrollTop-scroll.clientHeight<180)node.scrollIntoView({block:'nearest'})},[value?.text])
  if(!value&&!error)return null
  if(value?.state==='PERSISTED'||value?.state==='IDLE'||value?.state==='UNAVAILABLE')return error?<p role="status">{error}</p>:value?.state==='UNAVAILABLE'?<p role="status">临时显示不可用，任务状态与完整回答以持久记录为准；不会重发模型请求。</p>:null
  return <section className="stream-display" ref={block} aria-label="临时流式输出"><div className="content-heading"><strong>{value?.state==='INTERRUPTED'?'输出已中断':'正在生成'}</strong><Button onClick={()=>setPaused(v=>!v)}>{paused?'恢复显示':'停止显示'}</Button></div>
    <p className="muted small">{value?.state==='INTERRUPTED'?'以下片段不是完整答案，不会自动重发请求。':'临时文本尚未成为完整持久回答。停止显示不取消任务。'}</p>{value?.text?<pre>{value.text}</pre>:<p className="muted small">尚无可安全展示的文本，以完整持久记录为准。</p>}{value?.truncated&&<p>临时显示已达65536字符，完整结果请等待持久记录。</p>}{error&&<p role="status">{error}</p>}<details><summary>流来源</summary><code>{value?.sourceEvent}</code></details></section>
}
function Usage({events}:{events:SessionEvent[]}) {
  const rows=events.filter(e=>e.type==='model.observed'),end=events.filter(e=>e.type==='turn.ended').at(-1)
  if(!rows.length&&!end)return null
  return <section className="usage-summary" aria-label="用量与结束摘要">{end&&<p><strong>本轮状态：{statuses[String(end.payload.status)]??'未知'}</strong> · {String(end.payload.status)==='COMPLETED'?'记录的证据已确认目标完成':String(end.payload.reason??'').startsWith('GOAL_UNVERIFIED')?'尚无足够证据确认目标完成，请查看执行诊断。':'请按来源记录核对结果与结束原因。'}</p>}<details><summary>耗时与实际用量 · {rows.length} 次已记录请求</summary>{!rows.length&&<p>耗时、token及实际费用未记录。</p>}{rows.map((e,i)=>{const p=e.payload,u=obj(p.usage),num=(v:unknown)=>typeof v==='number'&&Number.isFinite(v)&&v>=0?String(v):'未知';return <div className="usage-row" key={e.eventId}><strong>请求 {i+1} · {String(p.model??'模型未知')}</strong><span>耗时 {num(p.elapsedMs)} ms · 输入 {num(u.prompt_tokens)} / 输出 {num(u.completion_tokens)} token</span><span>实际费用：未知（未收到提供方账单）</span><code>来源 {String(p.sourceEvent??'未知')}</code></div>})}{events.filter(e=>e.type==='budget.reserved').map(e=><p className="small" key={e.eventId}>费用预留上界 {String(e.payload.requestBound??'未知')} {String(e.payload.currency??'')}（不是实际计费）</p>)}</details></section>
}
