// Failure metadata is restricted to reviewed case names and fixed categories.
// Raw worker messages, requests, paths and environment values never leave here.
import { readFileSync } from 'node:fs'
const cases=JSON.parse(readFileSync(new URL('../../scripts/contracts/m1059-component-checks.json',import.meta.url),'utf8')).sdk
export function failureDiagnostic(language,code,output,error) {
  if(!Object.hasOwn(cases,language))throw Error('Unsupported fixture language')
  const allowed=new Set(cases[language])
  const completed=output.split(/\r?\n/).filter(line=>line.startsWith('PASS ')&&allowed.has(line.slice(5))).map(line=>line.slice(5))
  const category=error.includes('Fixture timeout')?'FIXTURE_TIMEOUT':error.includes('AssertionError')?'ASSERTION_FAILURE':error.includes('RpcError')?'RPC_FAILURE':'WORKER_FAILURE'
  const codes=['BACKPRESSURE_LIMIT','CLIENT_CLOSED','EXPIRED_HISTORY_LIMIT','INVALID_REQUEST','INVALID_RESPONSE','INVALID_TIMEOUT','PROCESS_CLEANUP_UNKNOWN','PROCESS_DISCONNECTED','PROCESS_START_FAILED','REQUEST_TIMEOUT_ACCEPTANCE_UNKNOWN','RPC_REQUEST_LIMIT','VERSION_UNSUPPORTED']
  return {language,workerExitCode:Number.isInteger(code)?code:null,category,completedChecks:new Set(completed).size,lastCompleted:completed.at(-1)??null,observedKnownCodes:codes.filter(code=>error.includes(code))}
}
