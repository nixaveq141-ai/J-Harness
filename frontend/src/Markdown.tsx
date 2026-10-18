import { Fragment, useState } from 'react'
import { Button } from './upstream/Button'

/* Existing neutral reading surface: text first, bounded code, explicit source actions;
   native focus, no fetched imagery or executable HTML. */
import { fileReference, linkTarget } from './content'
export function CodeBlock({text,language='',startLine=1}:{text:string;language?:string;startLine?:number}) {
  const [notice,setNotice]=useState(''),lines=text.split('\n')
  return <div className="code-block"><div className="code-toolbar"><span>{language||'文本'}</span><Button disabled={!text} aria-label="复制代码" onClick={()=>{void (navigator.clipboard?navigator.clipboard.writeText(text):Promise.reject(new Error('unavailable'))).then(()=>setNotice('已复制')).catch(()=>setNotice('复制失败，请选择文本复制'))}}>复制</Button></div><pre tabIndex={0} aria-label="代码内容"><code>{lines.length>2000?text:lines.map((line,i)=><span className="code-line" key={i}><span className="line-number" aria-hidden="true">{startLine+i}</span><span>{line||'\u200b'}{i<lines.length-1?'\n':''}</span></span>)}</code></pre>{notice&&<small role="status">{notice}</small>}</div>
}
function Inline({text,onFile}:{text:string;onFile?:(path:string,line:number)=>void}) {
  const pattern=/(`[^`\n]{1,2048}`|\*\*[^*\n]{1,2048}\*\*|\*[^*\n]{1,2048}\*|\[[^\]\n]{1,512}\]\([^\s)]{1,2048}\))/g
  const pieces=[];let end=0,match:RegExpExecArray|null,n=0
  while((match=pattern.exec(text))&&n++<2048){
    if(match.index>end)pieces.push(text.slice(end,match.index))
    const token=match[0],key=String(match.index)
    if(token.startsWith('`'))pieces.push(<code key={key}>{token.slice(1,-1)}</code>)
    else if(token.startsWith('**'))pieces.push(<strong key={key}>{token.slice(2,-2)}</strong>)
    else if(token.startsWith('*'))pieces.push(<em key={key}>{token.slice(1,-1)}</em>)
    else{const split=token.indexOf(']('),label=token.slice(1,split),target=token.slice(split+2,-1),kind=linkTarget(target),ref=fileReference(target)
      pieces.push(kind==='external'?<a key={key} href={target} target="_blank" rel="noopener noreferrer">{label} ↗</a>:kind==='file'&&onFile&&ref?<button key={key} className="file-reference" onClick={()=>onFile(ref.path,ref.line)}>{label}</button>:<span key={key}>{label}（链接不可用）</span>)}
    end=pattern.lastIndex
  }
  pieces.push(text.slice(end));return <>{pieces}</>
}
export function Markdown({text,onFile}:{text:string;onFile?:(path:string,line:number)=>void}) {
  const lines=text.slice(0,65536).split('\n'),blocks=[];let index=0
  while(index<lines.length){
    const line=lines[index]
    if(line.startsWith('```')){const language=/^```([A-Za-z0-9_+-]{0,32})/.exec(line)?.[1]??'';let end=index+1;while(end<lines.length&&!lines[end].startsWith('```'))end++;blocks.push(<CodeBlock key={index} language={language} text={lines.slice(index+1,end).join('\n')}/>);index=end+1;continue}
    const heading=/^(#{1,6})\s+(.+)$/.exec(line)
    if(heading){blocks.push(<div role="heading" aria-level={heading[1].length} className={'md-heading md-h'+heading[1].length} key={index}><Inline text={heading[2]} onFile={onFile}/></div>);index++;continue}
    if(/^\s*([-*]|\d+\.)\s+/.test(line)){
      const ordered=/^\s*\d+\./.test(line),items=[],first=index
      while(index<lines.length&&/^\s*([-*]|\d+\.)\s+/.test(lines[index])){items.push(<li key={index}><Inline text={lines[index].replace(/^\s*([-*]|\d+\.)\s+/,'')} onFile={onFile}/></li>);index++}
      blocks.push(ordered?<ol key={first}>{items}</ol>:<ul key={first}>{items}</ul>);continue
    }
    if(line.startsWith('> ')){blocks.push(<blockquote key={index}><Inline text={line.slice(2)} onFile={onFile}/></blockquote>);index++;continue}
    if(!line.trim()){index++;continue}
    let end=index+1;while(end<lines.length&&lines[end].trim()&&!/^(#{1,6}\s|```|> |\s*([-*]|\d+\.)\s)/.test(lines[end]))end++
    blocks.push(<p key={index}>{lines.slice(index,end).map((part,i)=><Fragment key={i}>{i>0&&<br/>}<Inline text={part} onFile={onFile}/></Fragment>)}</p>);index=end
  }
  return <div className="markdown">{blocks}{text.length>65536&&<p className="muted">仅展示前65536字符，请按来源回读完整正文。</p>}</div>
}
