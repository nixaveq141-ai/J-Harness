// Read-only verification of the original browser-command output. Never rebind its hash.
import {resolve} from 'node:path'
import {requireThat,validateMatrix} from './workbench-evidence.mjs'
const root=resolve(import.meta.dirname,'..'),engines=process.argv.slice(2)
requireThat(engines.length&&new Set(engines).size===engines.length&&engines.every(e=>['chromium','firefox','webkit'].includes(e)),'Invalid engine matrix')
const matrix=await validateMatrix(root)
requireThat(JSON.stringify([...engines].sort())===JSON.stringify([...matrix.engines].sort()),'Requested engines differ from original matrix')
console.log(JSON.stringify({status:'PASS',action:'readonly-finalization',runId:matrix.runId,capturedAt:matrix.capturedAt,finalizedAt:new Date().toISOString(),records:matrix.records.length,checks:matrix.records.reduce((n,r)=>n+r.checks,0)}))
