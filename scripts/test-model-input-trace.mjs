import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import {installModelInputTrace} from '../frontend/tests/model-input-trace.mjs'

for(const inputLabel of ['后续消息','任务目标'])test(`bounded text-free input trace serializes for ${inputLabel}`,()=>{
 const canary='private-draft-canary',listeners={}
 const input={value:canary,disabled:false,__reactProps$test:{value:canary},getAttribute:()=>inputLabel}
 const buttonLabel=inputLabel==='任务目标'?'创建任务':'发送后续消息'
 const window={}
 const context={window,performance:{now:()=>1},queueMicrotask:f=>f(),requestAnimationFrame:f=>f(),
  MutationObserver:class{observe(){}},
  document:{querySelector:selector=>selector===`textarea[aria-label="${inputLabel}"]`?input:null,
   querySelectorAll:()=>[{textContent:buttonLabel,disabled:false}],addEventListener:(name,fn)=>{listeners[name]=fn}}}
 vm.runInNewContext(`(${installModelInputTrace.toString()})(${JSON.stringify({inputLabel})})`,context)
 listeners.input({target:{getAttribute:()=> 'unrelated-secret-input'}})
 assert.equal(window.__jhModelInputTrace.length,0)
 for(let i=0;i<100;i++)listeners.input({target:input})
 const rows=window.__jhModelInputTrace
 assert.equal(rows.length,64)
 assert.ok(rows.every(row=>row.domLength===canary.length&&row.reactLength===canary.length&&row.mount===1&&row.sendDisabled===false))
 assert.ok(!JSON.stringify(rows).includes(canary))
})
