// Test-only causal snapshots. Never retain draft text, URLs, headers or task bodies.
export function installModelInputTrace() {
  const rows = [], nodes = new WeakMap(); let serial = 0, queued = false
  const sample = kind => {
    const input = document.querySelector('textarea[aria-label="后续消息"]')
    if (!input) return
    if (!nodes.has(input)) nodes.set(input, ++serial)
    const propsKey = Object.keys(input).find(key => key.startsWith('__reactProps$'))
    const props = propsKey ? input[propsKey] : undefined
    const button = [...document.querySelectorAll('button')].find(node => node.textContent === '发送后续消息')
    rows.push({ kind, timeMs: Math.round(performance.now()), mount: nodes.get(input),
      domLength: input.value.length, reactLength: typeof props?.value === 'string' ? props.value.length : null,
      inputDisabled: input.disabled, sendDisabled: button ? button.disabled : null,
      draftActionPresent: [...document.querySelectorAll('button')].some(node => node.textContent === '丢弃草稿') })
    if (rows.length > 64) rows.shift()
  }
  for (const kind of ['beforeinput', 'input', 'change', 'focusin']) document.addEventListener(kind, event => {
    if (event.target?.getAttribute?.('aria-label') !== '后续消息') return
    sample(kind); queueMicrotask(() => sample(kind + ':microtask'))
    requestAnimationFrame(() => sample(kind + ':frame'))
  }, true)
  new MutationObserver(() => {
    if (queued) return; queued = true
    queueMicrotask(() => { queued = false; sample('mutation') })
  }).observe(document, { subtree: true, childList: true, attributes: true, attributeFilter: ['disabled'] })
  window.__jhModelInputTrace = rows
}
