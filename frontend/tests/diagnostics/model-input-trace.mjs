import { webkit } from 'playwright'
import assert from 'node:assert/strict'
import { installModelInputTrace } from '../model-input-trace.mjs'

const browser = await webkit.launch()
try {
  const context = await browser.newContext()
  await context.addInitScript(installModelInputTrace)
  const page = await context.newPage()
  await page.goto('about:blank')
  await page.setContent('<textarea aria-label="后续消息"></textarea><button disabled>发送后续消息</button>')
  // setContent replaces the synthetic document; install against that document.
  await page.evaluate(installModelInputTrace)
  await page.evaluate(() => {
    const input = document.querySelector('textarea')
    input.__reactProps$test = { value: '' }
  })
  await page.locator('textarea').fill('synthetic-trace-secret-canary')
  await page.waitForTimeout(30)
  let rows = await page.evaluate(() => window.__jhModelInputTrace)
  assert.ok(rows.some(row => row.domLength > 0 && row.reactLength === 0 && row.sendDisabled))
  assert.ok(!JSON.stringify(rows).includes('synthetic-trace-secret-canary'))
  await page.evaluate(() => {
    const old = document.querySelector('textarea'), next = old.cloneNode()
    old.replaceWith(next); next.__reactProps$test = { value: 'next' }; next.value = 'next'
    next.dispatchEvent(new Event('input', { bubbles: true }))
    for (let i = 0; i < 100; i++) next.dispatchEvent(new Event('change', { bubbles: true }))
  })
  await page.waitForTimeout(30)
  rows = await page.evaluate(() => window.__jhModelInputTrace)
  assert.ok(rows.length <= 64)
  assert.ok(rows.some(row => row.mount === 2 && row.domLength === 4 && row.reactLength === 4))
  await context.close()
  console.log('PASS WebKit trace distinguishes DOM/React divergence and remount; bounded and no draft text retained')
} finally { await browser.close() }
