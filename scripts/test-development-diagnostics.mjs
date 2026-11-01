import assert from 'node:assert/strict'
import { developmentFailureFacts } from '../frontend/tests/development-diagnostics.mjs'
const secret='DO_NOT_PERSIST_SECRET_BODY'
const rows=Array.from({length:100},()=>({type:'tool.completed',callId:'development-5',payload:{status:'FAILED',error:{code:'NONZERO_EXIT',message:secret},value:{exitCode:1,stdout:secret},arguments:secret}}))
const result=developmentFailureFacts({error:secret},rows)
assert.equal(result.errorCode,'OTHER');assert.equal(result.events.length,64)
assert.equal(result.events[0].code,'NONZERO_EXIT');assert.equal(result.events[0].exitCode,1)
assert.equal(JSON.stringify(result).includes(secret),false)
assert.equal(developmentFailureFacts({error:'WORKSPACE_CHECKPOINT_FAILED'},[]).errorCode,'WORKSPACE_CHECKPOINT_FAILED')
const cleanup=developmentFailureFacts({},[{type:'tool.completed',callId:'development-5',payload:{status:'UNKNOWN',error:{code:'CLEANUP_INCOMPLETE'},value:{reason:'PROCESS_LIMIT',trackedPids:[12345,23456],residualPids:[]}}}]).events[0]
assert.equal(cleanup.code,'CLEANUP_INCOMPLETE');assert.equal(cleanup.reason,'PROCESS_LIMIT');assert.equal(cleanup.trackedPidCount,2)
assert.equal(JSON.stringify(cleanup).includes('12345'),false)
assert.equal(developmentFailureFacts({},[{type:'tool.completed',payload:{value:{reason:secret}}}]).events[0].reason,'OTHER')
console.log('PASS bounded event codes and no arbitrary body persistence')
