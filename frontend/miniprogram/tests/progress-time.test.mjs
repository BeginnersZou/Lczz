import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

process.env.TZ = 'Asia/Shanghai'

const timeSource = readFileSync(new URL('../utils/time.js', import.meta.url), 'utf8')
const { formatDateTime } = await import(`data:text/javascript;base64,${Buffer.from(timeSource).toString('base64')}`)
const apiSource = readFileSync(new URL('../api/api.js', import.meta.url), 'utf8')
const pageSource = readFileSync(new URL('../packageA/order-detail/order-detail.vue', import.meta.url), 'utf8')

test('UTC progress time converts across Beijing midnight without losing the actual date', () => {
  assert.equal(formatDateTime('2026-10-11T16:30:00Z'), '2026-10-12 00:30')
  assert.equal(formatDateTime('2026-10-12T02:36:00Z'), '2026-10-12 10:36')
})

test('progress files normalize and display the backend upload instant', () => {
  assert.match(apiSource, /uploadedAt: formatDateTime\(value\.uploadedAt \|\| value\.createdAt \|\| ''\)/)
  assert.match(pageSource, /class="record-media-time"/)
  assert.match(pageSource, /\{\{ media\.uploadedAt \}\}/)
})
