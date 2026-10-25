import { JHarnessClient } from '../typescript/src/index.ts'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
for(const name of ["JAVA_HOME","JHARNESS_SDK_JAR","JHARNESS_SDK_WORKSPACE"])if(!process.env[name])throw Error("Set "+name+" before running the SDK example")
function requiredId(value:unknown):string{if(typeof value!=="string"||!value)throw Error("SDK response omitted required identity");return value}
const client=await JHarnessClient.start({java:join(process.env.JAVA_HOME!,process.platform==='win32'?'bin/java.exe':'bin/java'),jar:process.env.JHARNESS_SDK_JAR!,workspace:process.env.JHARNESS_SDK_WORKSPACE!,config:fileURLToPath(new URL('./demo.json',import.meta.url))})
try{
  const opened=await client.openSession()
  const receipt=await client.sendInput(requiredId(opened.handle),'Inspect README.md','typescript-demo-input')
  const id=requiredId(receipt.sessionId),end=Date.now()+35000
  let state=await client.result(id)
  while(!['FINISHED','VERIFIED','UNKNOWN'].includes(String(state.phase))&&Date.now()<end){await new Promise(r=>setTimeout(r,100));state=await client.result(id)}
  if(state.phase==='UNKNOWN')console.log('Outcome unknown: retain typescript-demo-input/sessionId; call client.call("request.get", params) before any resubmission')
  else if(!['FINISHED','VERIFIED'].includes(String(state.phase)))throw Error('Example did not finish within its budget; inspect the existing session')
  console.log(state)
  console.log(await client.search('README',{sessionId:id}))
  console.log(await client.report(id))
}finally{await client.close()}
