import assert from 'node:assert/strict'
import { developmentFailureFacts } from '../frontend/tests/development-diagnostics.mjs'
const secret='DO_NOT_PERSIST_SECRET_BODY'
const rows=Array.from({length:100},()=>({type:'tool.completed',callId:'development-5',payload:{status:'FAILED',error:{code:'NONZERO_EXIT',message:secret},value:{exitCode:1,stdout:secret},arguments:secret}}))
const result=developmentFailureFacts({error:secret},rows)
assert.equal(result.errorCode,'OTHER');assert.equal(result.events.length,64)
assert.equal(result.events[0].code,'NONZERO_EXIT');assert.equal(result.events[0].exitCode,1)
assert.equal(JSON.stringify(result).includes(secret),false)
assert.equal(developmentFailureFacts({error:'WORKSPACE_CHECKPOINT_FAILED'},[]).errorCode,'WORKSPACE_CHECKPOINT_FAILED')
console.log('PASS bounded event codes and no arbitrary body persistence')
