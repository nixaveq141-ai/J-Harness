// Bounded synthetic-fixture diagnostics. No error text, tool output or arguments.
const codes=new Set(['WORKSPACE_CHECKPOINT_FAILED','QUEUE_CHECKPOINT_FAILED','RESOURCE_CLEANUP_UNKNOWN','SESSION_UNKNOWN','SHELL_REQUIRED','NONZERO_EXIT','PROCESS_LIMIT','WRITE_CONFLICT','READ_REQUIRED','TIMEOUT','CANCELLED','DENIED','BUDGET_EXCEEDED','INTERNAL_ERROR','TASK_EXECUTION_UNCERTAIN','TASK_DISPLAY_UNCERTAIN','TASK_CLOSE_UNCERTAIN','RECOVERY_REQUIRED','SUBMISSION_NOT_CONFIRMED','UNKNOWN_SIDE_EFFECT'])
const statuses=new Set(['SUCCESS','FAILED','UNKNOWN','CANCELLED','TIMEOUT','DENIED','COMPLETED','RUNNING'])
for(const code of ['CLEANUP_INCOMPLETE','PROCESS_SERVICE_CLOSED','PROCESS_IO','OUTPUT_IO'])codes.add(code)
const reasons=new Set(['EXIT','CANCELLED','TIMEOUT','PROCESS_LIMIT','PROCESS_SERVICE_CLOSED','PROCESS_IO','OUTPUT_IO'])
export function developmentFailureFacts(task,events){
  return {errorCode:codes.has(task?.error)?task.error:'OTHER',
    events:events.filter(e=>['tool.completed','turn.ended'].includes(e.type)).slice(-64).map(e=>({
      type:e.type,call:/^development-\d{1,2}$/.test(e.callId??'')?e.callId:'OTHER',
      status:statuses.has(e.payload?.status)?e.payload.status:'OTHER',
      code:codes.has(e.payload?.error?.code)?e.payload.error.code:'OTHER',
      reason:reasons.has(e.payload?.value?.reason)?e.payload.value.reason:'OTHER',
      cleanupStatus:['complete','partial','failed','not_started'].includes(e.payload?.value?.cleanupStatus)?e.payload.value.cleanupStatus:'OTHER',
      observedClean:typeof e.payload?.value?.observedClean==='boolean'?e.payload.value.observedClean:null,
      residualPidCount:Array.isArray(e.payload?.value?.residualPids)?e.payload.value.residualPids.length:null,
      trackedPidCount:Array.isArray(e.payload?.value?.trackedPids)?e.payload.value.trackedPids.length:null,
      exitCode:Number.isInteger(e.payload?.value?.exitCode)?e.payload.value.exitCode:null}))}
}
