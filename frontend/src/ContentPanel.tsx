import { useEffect, useRef, useState } from 'react'
import { Api, ApiError, type ContentPage } from './api'
import { Button } from './upstream/Button'
import { CodeBlock } from './Markdown'
export interface FileSelection {path:string;line:number;serial:number}
function failure(e:unknown) {
  if(e instanceof ApiError){
    if(['CONTENT_CHANGED','FILE_CONFLICT'].includes(e.code))return '文件或脱敏版本已变化。请点击“重新读取”查看当前内容。'
    if(['PATH_DENIED','INSPECTION_DENIED','WORKSPACE_PATH_DENIED'].includes(e.code))return '此路径不在允许读取的工作区范围内。'
    if(e.code==='FILE_LIMIT')return '此文件超过1 MiB文本预览上限。'
    if(['BINARY_FILE','UNSUPPORTED_ENCODING'].includes(e.code))return '仅支持UTF-8文本与代码预览。'
    if(['ARTIFACT_NOT_FOUND','ARTIFACT_CORRUPT'].includes(e.code))return '产物已缺失或校验失败，无法读取。'
    if(e.code==='INSPECTION_UNAVAILABLE')return '当前工作区环境未提供文件查看服务。'
  }
  return '读取未完成，请检查连接后重新读取。'
}
export function ContentReader({api,id,kind,target,line=0,onClose}:{api:Api;id:string;kind:'file'|'artifacts'|'content';target:string;line?:number;onClose?:()=>void}) {
  const [page,setPage]=useState<ContentPage|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[notice,setNotice]=useState('')
  const [request,setRequest]=useState({offset:0,version:'',line,serial:0}),[past,setPast]=useState<number[]>([])
  const abort=useRef<AbortController|null>(null),downloading=useRef(false)
  const version=(p:ContentPage)=>kind==='file'?p.fileVersion??'':p.contentSha256
  useEffect(()=>{
    const c=new AbortController();abort.current=c;setBusy(true);setError('')
    void api.content(id,kind,target,request.offset,request.version,request.line,c.signal).then(v=>{if(!c.signal.aborted)setPage(v)})
      .catch(e=>{if(!c.signal.aborted){setError(failure(e));setPage(null)}}).finally(()=>{if(!c.signal.aborted)setBusy(false)})
    return()=>c.abort()
  },[api,id,kind,target,request])
  useEffect(()=>()=>abort.current?.abort(),[])
  async function download(){
    if(downloading.current||!page)return
    downloading.current=true;setBusy(true);setNotice('正在读取并校验下载内容…');setError('')
    const c=new AbortController();abort.current?.abort();abort.current=c
    try{
      let offset=0,full='';const expected=page.contentSha256
      for(let n=0;n<513;n++){
        const p=await api.content(id,kind,target,offset,version(page),0,c.signal)
        if(p.contentSha256!==expected)throw new ApiError('CONTENT_CHANGED')
        full+=p.text;if(full.length>8388608)throw new ApiError('RESPONSE_LIMIT')
        if(!p.more)break
        offset=p.nextOffset;if(n===512)throw new ApiError('RESPONSE_LIMIT')
      }
      const digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(full)))).map(n=>n.toString(16).padStart(2,'0')).join('')
      if(digest!==expected)throw new ApiError('ARTIFACT_CORRUPT')
      if(c.signal.aborted)return
      const url=URL.createObjectURL(new Blob([full],{type:'text/plain;charset=utf-8'})),a=document.createElement('a')
      a.href=url;a.download=kind==='file'?(target.split(/[\\/]/).at(-1)||'file')+'.txt':kind==='artifacts'?'artifact-'+target.slice(0,8)+'.txt':'event-'+target.slice(0,8)+'.txt'
      a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);setNotice('已校验脱敏内容SHA-256并下载')
    }catch(e){if(!c.signal.aborted)setError(failure(e))}
    finally{downloading.current=false;if(!c.signal.aborted)setBusy(false)}
  }
  return <section className="content-reader" aria-label="内容预览"><div className="content-heading"><strong>{kind==='file'?'当前文件内容':kind==='artifacts'?'已记录产物':'已记录事件正文'}</strong>{onClose&&<Button onClick={onClose}>关闭预览</Button>}</div><code className="content-path">{target}</code>
    <p className="muted small">{kind==='file'?'读取当前工作区内容；与任务当时记录的Diff可能不同。':'来源为持久记录，读取不会重放任务。'}</p>
    <div className="content-actions"><Button disabled={busy} onClick={()=>{setPast([]);setRequest({offset:0,version:'',line,serial:request.serial+1})}}>重新读取</Button><Button disabled={busy||!page} onClick={()=>void download()}>下载脱敏文本</Button>{busy&&<Button onClick={()=>{abort.current?.abort();setBusy(false);setNotice('已停止本页读取')}}>停止读取</Button>}</div>
    {busy&&<p role="status">正在读取…</p>}{error&&<p role="alert" className="inline-error">{error}</p>}{notice&&<p role="status">{notice}</p>}
    {page&&<><p className="small muted">第 {page.offset}～{page.nextOffset} 字符 / {page.totalCharacters} {page.readAt?'· 读取于 '+new Date(page.readAt).toLocaleTimeString():''}</p>
      <CodeBlock key={page.contentSha256+':'+page.offset} text={page.text} startLine={page.startLine} language={kind==='content'?'记录文本':kind==='artifacts'?'产物文本':/^[a-zA-Z0-9+-]{1,12}$/.test(target.split('.').at(-1)??'')?target.split('.').at(-1):'文本'}/>
      <div className="content-actions"><Button disabled={busy||!past.length} onClick={()=>{const offset=past.at(-1)!;setPast(p=>p.slice(0,-1));setRequest({offset,version:version(page),line:0,serial:request.serial+1})}}>上一页内容</Button><Button disabled={busy||!page.more} onClick={()=>{setPast(p=>[...p,page.offset]);setRequest({offset:page.nextOffset,version:version(page),line:0,serial:request.serial+1})}}>下一页内容</Button></div>
      <details className="content-digest"><summary>校验与来源</summary><p>脱敏内容SHA-256：<code>{page.contentSha256}</code></p>{page.fileVersion&&<p>当前文件版本：<code>{page.fileVersion}</code></p>}{page.artifactSha256&&<p>原持久产物SHA-256：<code>{page.artifactSha256}</code></p>}</details></>}
  </section>
}
export function ContentPanel({api,id,mode,selection}:{api:Api;id:string;mode:'files'|'artifacts';selection?:FileSelection|null}) {
  const [dir,setDir]=useState('.'),[entries,setEntries]=useState<{path:string;type:string}[]>([]),[limited,setLimited]=useState(false)
  const [error,setError]=useState(''),[busy,setBusy]=useState(false),[target,setTarget]=useState(selection?.path??''),[line,setLine]=useState(selection?.line??0),[input,setInput]=useState(''),[reload,setReload]=useState(0)
  useEffect(()=>{if(selection&&mode==='files'){setTarget(selection.path);setLine(selection.line)}},[selection,mode])
  useEffect(()=>{
    const c=new AbortController();setBusy(true);setError('');setEntries([])
    void(mode==='files'?api.files(id,dir,c.signal).then(v=>({entries:v.entries,limited:v.truncated})):api.artifacts(id,c.signal).then(v=>({entries:v.artifacts.map(a=>({path:a.id,type:'artifact'})),limited:v.limited})))
      .then(v=>{if(!c.signal.aborted){setEntries(v.entries);setLimited(v.limited)}}).catch(e=>{if(!c.signal.aborted)setError(failure(e))}).finally(()=>{if(!c.signal.aborted)setBusy(false)})
    return()=>c.abort()
  },[api,id,mode,dir,reload])
  return <div className="content-panel"><h2>{mode==='files'?'工作区文件':'任务产物'}</h2><p className="muted small">{mode==='files'?'只读浏览注册工作区中的文本与代码。':'仅列出本会话持久事实明确引用的产物，最多64项。'}</p>
    {mode==='files'&&<><form className="search-form" onSubmit={e=>{e.preventDefault();setTarget(input);setLine(0)}}><input aria-label="文件路径" value={input} maxLength={512} onChange={e=>setInput(e.target.value)} placeholder="相对路径，例如 src/Main.java"/><Button type="submit" disabled={!input.trim()}>查看文件</Button></form><div className="content-actions"><Button disabled={busy||dir==='.'} onClick={()=>setDir(dir.split('/').slice(0,-1).join('/')||'.')}>返回上级目录</Button><code>{dir}</code></div></>}
    <Button disabled={busy} onClick={()=>setReload(n=>n+1)}>刷新{mode==='files'?'文件树':'产物列表'}</Button>
    {busy&&<p role="status">正在读取…</p>}{error&&<p role="alert" className="inline-error">{error}</p>}
    {!busy&&!entries.length&&!error&&<p className="muted">{mode==='files'?'此目录没有可展示文件。':'尚无明确产物记录。'}</p>}
    <ul className="file-tree" aria-label={mode==='files'?'工作区文件树':'已记录产物列表'}>{entries.map(e=><li key={e.path}><button title={e.path} onClick={()=>{if(e.type==='directory')setDir(e.path);else{setTarget(e.path);setLine(0)}}}><span aria-hidden="true">{e.type==='directory'?'▸':'▤'}</span><span>{e.path}</span></button></li>)}</ul>
    {limited&&<p className="muted">列表已达上限，请进入子目录或使用明确路径查看。</p>}
    {target&&<ContentReader key={mode+':'+target+':'+line+':'+selection?.serial} api={api} id={id} kind={mode==='files'?'file':'artifacts'} target={target} line={line} onClose={()=>setTarget('')}/>}
  </div>
}
