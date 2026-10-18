export function fileReference(value:string):{path:string;line:number}|null {
  const match=/^(.+?)(?:(?:#L|:)(\d+))?$/.exec(value)
  if(!match)return null
  const path=match[1].replace(/\\/g,'/')
  if(path.length>512||/[:?#\u0000-\u001f]/.test(path)||path.startsWith('/')||path.split('/').some(s=>!s||s==='..')||!/\.[a-zA-Z0-9]{1,12}$/.test(path))return null
  return {path,line:match[2]?Math.min(1000000,Number(match[2])):1}
}
export function linkTarget(value:string):'external'|'file'|'blocked' {
  if(fileReference(value))return 'file'
  try{const u=new URL(value);return ['http:','https:'].includes(u.protocol)&&!u.username&&!u.password?'external':'blocked'}catch{return 'blocked'}
}

export function toolAssociation(event:{turnId?:string|null;callId?:string|null}) {return (event.turnId??'')+':'+(event.callId??'')}
