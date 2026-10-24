// Manual root-cause diagnostic, outside the M10.5.9 acceptance case count.
// Run: node tests/diagnostics/webkit-navigation.mjs (headless WebKit only).
import assert from 'node:assert/strict'
import http from 'node:http'
import { webkit } from 'playwright'
import { trackNavigationReads } from '../runtime.mjs'

const readPath='/v1/tasks/probe/questions?offset=0', sockets=new Set()
let readStarted
const server=http.createServer((request,response)=>{
  if(request.url===readPath){response.writeHead(200,{'Content-Type':'application/json'});response.write('{"questions":');readStarted?.();return}
  if(request.url==='/next'){setTimeout(()=>response.end('replacement document'),500);return}
  response.end(`<script>
    const report=value=>console.info('PROBE:'+value);
    window.addEventListener('error',event=>report('window-error:'+event.message));
    window.addEventListener('unhandledrejection',event=>report('unhandled-rejection:'+String(event.reason)));
    window.read=async()=>{try {await (await fetch('${readPath}')).json()}catch(error){report('caught:'+String(error))}};
    ${request.url==='/?late=1'?"window.addEventListener('beforeunload',()=>setTimeout(()=>{void window.read()},25));":''}
  </script>`)
})
server.on('connection',socket=>{sockets.add(socket);socket.on('close',()=>sockets.delete(socket))})
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
let browser
try {
  browser=await webkit.launch({headless:true})
  const origin=`http://127.0.0.1:${server.address().port}`
  for(const late of [false,true]) {
    const page=await browser.newPage(), events=[], errors=[], navigation=trackNavigationReads(page)
    page.on('console',message=>{if(message.text().startsWith('PROBE:'))events.push(message.text().slice(6))})
    page.on('pageerror',error=>errors.push({message:error.message,expected:navigation.diagnostic(error.message)}))
    await page.goto(`${origin}/?late=${late?1:0}`)
    const started=new Promise(resolve=>readStarted=resolve)
    await page.evaluate(()=>{void window.read()})
    await started
    await navigation.during(()=>page.goto(origin+'/next'))
    assert.equal(events.filter(value=>/^(window-error|unhandled-rejection):/.test(value)).length,0)
    assert.equal(events.filter(value=>value.startsWith('caught:')).length,late?2:1)
    assert.equal(errors.length,late?1:0)
    assert.ok(errors.every(error=>error.expected))
    console.log(JSON.stringify({scenario:late?'one caught poll after navigation starts':'caught existing read cancelled by navigation',events,errors}))
    await page.close()
  }
  // A real uncaught exception must still reach both DOM and Playwright channels.
  const page=await browser.newPage()
  await page.goto(origin)
  const error=page.waitForEvent('pageerror')
  const reported=page.waitForEvent('console',{predicate:message=>/^PROBE:window-error:.*synthetic-unhandled-control$/.test(message.text())})
  await page.evaluate(()=>{setTimeout(()=>{throw Error('synthetic-unhandled-control')},0)})
  assert.match((await error).message,/synthetic-unhandled-control/)
  await reported
  console.log('PASS genuine uncaught exception remains observable')
} finally {
  await browser?.close()
  for(const socket of sockets)socket.destroy()
  await new Promise(resolve=>server.close(resolve))
}
