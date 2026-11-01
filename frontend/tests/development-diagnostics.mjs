// Bounded synthetic-fixture diagnostics. No error text, tool output or arguments.
const codes=new Set(['WORKSPACE_CHECKPOINT_FAILED','QUEUE_CHECKPOINT_FAILED','RESOURCE_CLEANUP_UNKNOWN','SESSION_UNKNOWN','SHELL_REQUIRED','NONZERO_EXIT','PROCESS_LIMIT','WRITE_CONFLICT','READ_REQUIRED','TIMEOUT','CANCELLED','DENIED','BUDGET_EXCEEDED','INTERNAL_ERROR'])
const statuses=new Set(['SUCCESS','FAILED','UNKNOWN','CANCELLED','TIMEOUT','DENIED','COMPLETED','RUNNING'])
export function developmentFailureFacts(task,events){
  return {errorCode:codes.has(task?.error)?task.error:'OTHER',
    events:events.filter(e=>['tool.completed','turn.ended'].includes(e.type)).slice(-64).map(e=>({
      type:e.type,call:/^development-\d{1,2}$/.test(e.callId??'')?e.callId:'OTHER',
      status:statuses.has(e.payload?.status)?e.payload.status:'OTHER',
      code:codes.has(e.payload?.error?.code)?e.payload.error.code:'OTHER',
      exitCode:Number.isInteger(e.payload?.value?.exitCode)?e.payload.value.exitCode:null}))}
}
