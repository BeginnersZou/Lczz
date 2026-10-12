import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const apiSource = readFileSync(new URL('../api/api.js', import.meta.url), 'utf8')
const pageUrl = new URL('../packageA/order-detail/order-detail.vue', import.meta.url)
const pageSource = readFileSync(pageUrl, 'utf8')

test('progress media unbind sends the relation as query parameters', () => {
  assert.match(apiSource, /unbindProgressMedia:\s*\(id, progressId/)
  assert.match(apiSource, /businessType=PROGRESS&businessId=\$\{encodeURIComponent\(progressId\)\}&usageType=PROGRESS/)
})

test('only admin receives progress image maintenance controls', () => {
  assert.match(pageSource, /const isAdmin = computed\(\(\) => userRole\.value === 'admin'\)/)
  assert.match(pageSource, /v-if="isAdmin"/)
  assert.match(pageSource, /chooseAdminProgressImages\(record\)/)
  assert.match(pageSource, /removeAdminProgressImage\(record, media\)/)
  assert.match(pageSource, /businessType: 'PROGRESS'/)
  assert.match(pageSource, /usageType: 'PROGRESS'/)
})

test('order detail page compiles with admin media actions', () => {
  const require = createRequire(new URL('../../admin/package.json', import.meta.url))
  const { parse, compileTemplate } = require('@vue/compiler-sfc')
  const parsed = parse(pageSource, { filename: pageUrl.pathname })
  assert.deepEqual(parsed.errors, [])
  const template = compileTemplate({
    source: parsed.descriptor.template.content,
    filename: pageUrl.pathname,
    id: 'issue-156'
  })
  assert.deepEqual(template.errors, [])
})
