import { spawn, type ChildProcessWithoutNullStreams } from 'node:child_process'
import { randomUUID } from 'node:crypto'

export type Json = null | boolean | number | string | Json[] | { [key: string]: Json }
export type ObjectValue = { [key: string]: Json }
export class RpcError extends Error {
  code: string
  constructor(code: string) { super(code); this.name = 'RpcError'; this.code = code }
}
export interface Options { java: string; jar: string; workspace: string; config?: string; env?: NodeJS.ProcessEnv; timeoutMs?: number; protocolVersion?: string }
const object = (value: unknown): value is ObjectValue => !!value && typeof value === 'object' && !Array.isArray(value)
function parse(text:string): unknown {
  const stack:(Set<string>|null)[]=[]
  for(let i=0;i<text.length;i++){
    const c=text[i]
    if(c==='{'||c==='['){stack.push(c==='{'?new Set():null);if(stack.length>32)throw Error()}
    else if(c==='}'||c===']')stack.pop()
    else if(c==='"'){
      const start=i++;while(i<text.length){if(text[i]==='\\')i++;else if(text[i]==='"')break;i++}
      let next=i+1;while(/\s/.test(text[next]??'x'))next++
      if(text[next]===':'){const keys=stack.at(-1),key:string=JSON.parse(text.slice(start,i+1));if(!keys||keys.has(key))throw Error();keys.add(key)}
    }
  }
  return JSON.parse(text)
}

/** Owns one child. Timeout/EOF never retries input; retain requestId and reconcile explicitly. */
export class JHarnessClient {
  readonly process: ChildProcessWithoutNullStreams
  capabilities: ObjectValue = {}
  private pending = new Map<string, { resolve: (v:ObjectValue)=>void; reject:(e:RpcError)=>void; timer:ReturnType<typeof setTimeout> }>()
  private expired = new Set<string>()
  private buffer = Buffer.alloc(0)
  private closed = false
  private closing = false
  private timeout: number
  private exit: Promise<void>
  private constructor(options: Options) {
    this.timeout = options.timeoutMs ?? 15000
    if(this.timeout<100||this.timeout>60000)throw new RpcError('INVALID_TIMEOUT')
    this.process = spawn(options.java, ['-Dfile.encoding=UTF-8','-jar',options.jar,'--workspace',options.workspace,...(options.config?['--config',options.config]:[]),'sdk-stdio'], {env:options.env??process.env,windowsHide:true,stdio:'pipe'})
    this.exit = new Promise(resolve=>this.process.once('close',()=>{this.fail('PROCESS_DISCONNECTED');resolve()}))
    this.process.on('error',()=>this.fail('PROCESS_START_FAILED'))
    this.process.stdin.on('error',()=>this.fail('PROCESS_DISCONNECTED'))
    this.process.stderr.resume() // Drain without retaining or echoing sensitive child diagnostics.
    this.process.stdout.on('data',(chunk:Buffer)=>this.read(chunk))
  }
  static async start(options: Options) {
    const client=new JHarnessClient(options)
    try {
      client.capabilities=await client.call('initialize',{protocolVersion:options.protocolVersion??'1.0'})
      if(client.capabilities.protocolVersion!=='1.0')throw new RpcError('VERSION_UNSUPPORTED')
      return client
    } catch(e) { await client.close(); throw e }
  }
  call(method: string, params: ObjectValue = {}, timeoutMs=this.timeout): Promise<ObjectValue> {
    if(this.closed||this.closing&&method!=='shutdown')return Promise.reject(new RpcError('CLIENT_CLOSED'))
    if(timeoutMs<100||timeoutMs>60000)return Promise.reject(new RpcError('INVALID_TIMEOUT'))
    if(this.pending.size>=32)return Promise.reject(new RpcError('BACKPRESSURE_LIMIT'))
    const id=randomUUID(),frame=Buffer.from(JSON.stringify({jsonrpc:'2.0',id,method,params})+'\n')
    if(frame.byteLength-1>65536)return Promise.reject(new RpcError('RPC_REQUEST_LIMIT'))
    return new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>{this.pending.delete(id);this.expired.add(id);if(this.expired.size>64)this.expired.delete(this.expired.values().next().value!);reject(new RpcError('REQUEST_TIMEOUT_ACCEPTANCE_UNKNOWN'))},timeoutMs)
      this.pending.set(id,{resolve,reject,timer})
      this.process.stdin.write(frame,error=>{if(error)this.fail('PROCESS_DISCONNECTED')})
    })
  }
  private read(chunk: Buffer) {
    this.buffer=Buffer.concat([this.buffer,chunk])
    for(;;){
      const end=this.buffer.indexOf(10)
      if(end<0){if(this.buffer.length>524288){this.fail('INVALID_RESPONSE');this.process.kill()}return}
      if(end>524288){this.fail('INVALID_RESPONSE');this.process.kill();return}
      const frame=this.buffer.subarray(0,end);this.buffer=this.buffer.subarray(end+1)
      try {
        const response:unknown=parse(new TextDecoder('utf-8',{fatal:true}).decode(frame))
        if(!object(response)||response.jsonrpc!=='2.0'||typeof response.id!=='string'||Object.keys(response).sort().join()!==(response.error!==undefined?'error,id,jsonrpc':'id,jsonrpc,result'))throw Error()
        if(this.expired.delete(response.id))continue
        const item=this.pending.get(response.id);if(!item)throw Error()
        this.pending.delete(response.id);clearTimeout(item.timer)
        if(response.error!==undefined){
          if(!object(response.error)||!object(response.error.data)||typeof response.error.data.code!=='string'||!/^[A-Z0-9_]{1,64}$/.test(response.error.data.code)){item.reject(new RpcError('INVALID_RESPONSE'));throw Error()}
          item.reject(new RpcError(response.error.data.code))
        } else if(object(response.result))item.resolve(response.result)
        else {item.reject(new RpcError('INVALID_RESPONSE'));throw Error()}
      } catch {this.fail('INVALID_RESPONSE');this.process.kill();return}
    }
  }
  private fail(code: string) { this.closed=true;for(const p of this.pending.values()){clearTimeout(p.timer);p.reject(new RpcError(code))}this.pending.clear() }
  async close() {
    if(this.closing)return this.exit
    this.closing=true
    try{if(!this.closed)await this.call('shutdown',{},3000)}catch{}
    this.process.stdin.end()
    const exited=await Promise.race([this.exit.then(()=>true),new Promise<boolean>(resolve=>{const t=setTimeout(()=>resolve(false),5000);t.unref()})])
    if(!exited){this.process.kill();await Promise.race([this.exit,new Promise<void>(resolve=>{const t=setTimeout(resolve,2000);t.unref()})])}
    this.fail('CLIENT_CLOSED')
  }
  openSession(profile='default',sessionId?:string){return this.call('session.open',{profile,...(sessionId?{sessionId}:{})})}
  listSessions(profile='',view='all',after=''){return this.call('sessions.list',{view,...(profile?{profile}:{}),...(after?{after}:{})})}
  sendInput(handle:string,text:string,requestId:string=randomUUID(),options:{modelId?:string;kind?:'message'|'supplement'}={}){return this.call('input.send',{handle,text,requestId,...options})}
  events(sessionId:string,after='',limit=32){return this.call('events.list',{sessionId,limit,...(after?{after}:{})})}
  questions(sessionId:string,offset=0){return this.call('questions.list',{sessionId,offset})}
  answer(question:ObjectValue,selected:string[]=[],text='',skip=false,requestId:string=randomUUID()){return this.call('question.answer',{sessionId:question.sessionId,questionId:question.questionId,turnId:question.turnId,callId:question.callId,requestId,selected,text,skip})}
  approvals(sessionId:string){return this.call('approvals.list',{sessionId})}
  decide(sessionId:string,approval:ObjectValue,approved:boolean,reason:string){return this.call('approval.decide',{sessionId,approvalId:approval.approvalId,callId:approval.callId,approved,reason})}
  cancel(sessionId:string){return this.call('session.cancel',{sessionId})}
  result(sessionId:string){return this.call('session.result',{sessionId})}
  report(sessionId:string){return this.call('session.report',{sessionId})}
  artifact(sessionId:string,artifactId:string,offset=0,version=''){return this.call('session.artifact',{sessionId,artifactId,offset,...(version?{version}:{})})}
  search(text:string,options:{mode?:'tokens'|'phrase';profile?:string;view?:string;sessionId?:string;cursor?:string;limit?:number;rebuild?:boolean}={}){return this.call('content.search',{text,mode:'tokens',profile:'',view:'active',sessionId:'',cursor:'',limit:20,rebuild:false,...options})}
}
