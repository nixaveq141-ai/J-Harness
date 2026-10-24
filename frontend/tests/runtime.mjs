import { chromium, firefox, webkit } from 'playwright'
// Fixture hosts receive only their explicit synthetic credentials. Unrelated runner
// credentials can redact fixture vocabulary and protocol keys (for example root).
export function fixtureEnvironment(overrides) {
  const inherited=Object.fromEntries(Object.entries(process.env).filter(([name])=>!/(API_KEY|ACCESS_KEY_ID|SECRET_ACCESS_KEY|ACCESS_KEY|PRIVATE_KEY|CREDENTIALS|TOKEN|SECRET|PASSWORD|PASS)$/i.test(name)))
  return {...inherited,...overrides}
}
// Consume the real response before Chromium can discard its network body. Forward
// the original UI request once, with no retries/redirects or replacement response.
export async function captureOriginalResponse(page, base, trigger, path = '/v1/tasks') {
  let accept,reject
  const received=new Promise((resolve,fail)=>{accept=resolve;reject=fail})
  const url=base+path
  const capture=async route=>{
    if(route.request().method()!=='POST'){await route.continue();return}
    try {
      const response=await route.fetch({maxRetries:0,maxRedirects:0,timeout:30000})
      const body=await response.json(),status=response.status()
      await route.fulfill({response});accept({status,body})
    } catch(error) {await route.abort();reject(error)}
  }
  await page.route(url,capture)
  const timer=setTimeout(()=>reject(Error('Original UI response not received within 30s')),30000)
  try {const [receipt]=await Promise.all([received,Promise.resolve().then(trigger)]);return receipt}
  finally {clearTimeout(timer);await page.unroute(url,capture)}
}
export const captureTaskAcceptance = captureOriginalResponse
export const engine = process.env.JHARNESS_BROWSER_ENGINE ?? 'chromium'
if (!['chromium','firefox','webkit'].includes(engine)) throw Error('Unsupported browser engine')
const selected = {chromium,firefox,webkit}[engine]
export const browserType = {
  launch(options = {}) {
    if (engine !== 'chromium' && options.channel) throw Error('A Chromium channel cannot select another engine')
    const executable = engine === 'firefox' ? process.env.JHARNESS_FIREFOX_EXECUTABLE : undefined
    return selected.launch({...options,...(executable?{executablePath:executable}:{})})
  }
}

// WebKit reports interrupted read-only polls as diagnostics while a fixture host is stopped.
export function stoppedHostDiagnostic(message, origins) {
  const match=/^(?:Fetch API cannot load )?((?:http: ?\/{1,2}|\/)127\.0\.0\.1:\d+\/[^ ]+) due to access control checks\.$/.exec(message)
  if(!match)return false
  try {
    const url=new URL(match[1].startsWith('/')?'http:/'+match[1]:match[1].replace(/^http: ?\/{1,2}/,'http://'))
    const read=url.pathname==='/v1/models'&&!url.search
      || url.pathname==='/v1/tasks'&&url.search==='?limit=32&view=active'
      || /^\/v1\/tasks\/[A-Za-z0-9_-]+(?:\/(?:approvals|diagnosis|display|questions|tools))?$/.test(url.pathname)
      || /^\/v1\/tasks\/[A-Za-z0-9_-]+\/events$/.test(url.pathname)&&url.search.startsWith('?limit=')
      || /^\/v1\/tasks\/[A-Za-z0-9_-]+\/turns$/.test(url.pathname)&&url.search.startsWith('?offset=')
    return url.hostname==='127.0.0.1'&&origins.has(url.origin)&&read
  } catch {return false}
}

// Exact browser-observed reads from the outgoing document, during a bounded
// deliberate navigation. This never exempts unrelated writes or live-page errors.
export function navigationReadDiagnostic(message, requests) {
  return requests.some(request => {
    if (request.method !== 'GET') return false
    try {
      const url = new URL(request.url)
      return stoppedHostDiagnostic(message, new Set([url.origin]))
        && message.endsWith(url.pathname + url.search + ' due to access control checks.')
    } catch { return false }
  })
}

// Network completion does not prove JavaScript consumed a response body: WebKit
// can report its cancelled consumption when the document unloads. Keep observed
// URLs for that document, including polls started while beforeunload is handled.
// Collect from the outgoing document until its main-frame navigation commits;
// requests from the replacement document never enter this navigation snapshot.
export function trackNavigationReads(page) {
  const observed = new Map()
  let operation = null, last = null
  page.on('request', request => {
    const url = request.url()
    if (request.method() !== 'GET' || request.isNavigationRequest()) {
      observed.delete(url); operation?.reads.delete(url); return
    }
    const read = { method: 'GET', url }
    observed.set(read.url, read)
    if (operation?.collecting && new URL(read.url).origin === operation.origin) operation.reads.set(read.url, read)
    else if (operation && !operation.collecting) {
      // A fresh document's read takes precedence over any old URL association.
      for (const [prior, value] of operation.reads) if (value.url === read.url) operation.reads.delete(prior)
    }
  })
  page.on('framenavigated', frame => {
    if (frame === page.mainFrame()) {
      observed.clear()
      if (operation?.collecting) { operation.collecting = false; operation.committed = Date.now() }
    }
  })
  return {
    async during(action) {
      if (operation) throw Error('Overlapping controlled navigation')
      const origin = new URL(page.url()).origin
      operation = { origin, started: Date.now(), collecting: true, reads: new Map([...observed].filter(([, read]) => new URL(read.url).origin === origin)) }
      try { return await action() }
      finally { last = { ...operation, finished: Date.now(), reads: [...operation.reads.values()] }; operation = null }
    },
    diagnostic(message) { return !!operation && navigationReadDiagnostic(message, [...operation.reads.values()]) },
    snapshot() { return operation ? { ...operation, reads: [...operation.reads.values()] } : last },
  }
}
