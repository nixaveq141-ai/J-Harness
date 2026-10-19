import { chromium, firefox, webkit } from 'playwright'
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
    const read=/^\/v1\/tasks\/[A-Za-z0-9_-]+(?:\/(?:approvals|diagnosis|display|questions|tools))?$/.test(url.pathname)
      || /^\/v1\/tasks\/[A-Za-z0-9_-]+\/events$/.test(url.pathname)&&url.search.startsWith('?limit=')
      || /^\/v1\/tasks\/[A-Za-z0-9_-]+\/turns$/.test(url.pathname)&&url.search.startsWith('?offset=')
    return url.hostname==='127.0.0.1'&&origins.has(url.origin)&&read
  } catch {return false}
}
