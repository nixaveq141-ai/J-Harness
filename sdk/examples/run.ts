import { JHarnessClient } from '../typescript/src/index.ts'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
const client=await JHarnessClient.start({java:join(process.env.JAVA_HOME!,process.platform==='win32'?'bin/java.exe':'bin/java'),jar:process.env.JHARNESS_SDK_JAR!,workspace:process.env.JHARNESS_SDK_WORKSPACE!,config:fileURLToPath(new URL('./demo.json',import.meta.url))})
try{
  const opened=await client.openSession()
  const receipt=await client.sendInput(String(opened.handle),'Inspect README.md','typescript-demo-input')
  const id=String(receipt.sessionId),end=Date.now()+35000
  while(!['FINISHED','UNKNOWN'].includes(String((await client.result(id)).phase))&&Date.now()<end)await new Promise(r=>setTimeout(r,100))
  console.log(await client.result(id))
  console.log(await client.search('README',{sessionId:id}))
  console.log(await client.report(id))
}finally{await client.close()}
