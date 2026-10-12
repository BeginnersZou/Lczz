import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const helperSource = readFileSync(new URL('../utils/admin-preparation.js', import.meta.url), 'utf8')
const helpers = await import(`data:text/javascript;base64,${Buffer.from(helperSource).toString('base64')}`)
const apiSource = readFileSync(new URL('../api/api.js', import.meta.url), 'utf8')
const userSource = readFileSync(new URL('../pages/user/user.vue', import.meta.url), 'utf8')
const pages = JSON.parse(readFileSync(new URL('../pages.json', import.meta.url), 'utf8'))

test('preparation helper keeps mobile actions admin-only and state-safe', () => {
  assert.equal(helpers.hasAdminRole({ role: 'admin' }), true)
  assert.equal(helpers.hasAdminRole({ role: 'customer', roles: ['ADMIN'] }), true)
  assert.equal(helpers.hasAdminRole({ role: 'installer' }), false)
  assert.equal(helpers.canPrepare({ source: 'W', statusCode: 'PENDING' }), true)
  assert.equal(helpers.canPrepare({ source: 'W', statusCode: 'PREPARING' }), true)
  assert.equal(helpers.canPrepare({ source: 'W', statusCode: 'DONE' }), false)
  assert.equal(helpers.canPrepare({ source: 'A', statusCode: 'ORDERED' }), false)
})

test('preparation payload always submits the complete checkbox snapshot', () => {
  const detail = helpers.normalizePreparationDetail({ materials: [
    { id: 10, count: '2.000', checked: true },
    { id: 11, quantity: 3, checked: false }
  ] })
  assert.deepEqual(helpers.preparationPayload(detail.materials), [
    { id: 10, checked: true },
    { id: 11, checked: false }
  ])
  assert.equal(helpers.allPrepared(detail.materials), false)
  detail.materials[1].checked = true
  assert.equal(helpers.allPrepared(detail.materials), true)
})

test('mine page exposes the preparation entry only to administrators', () => {
  assert.match(userSource, /v-if="isAdmin"[^>]*@click="goToPreparation"/)
  assert.match(userSource, /<text class="func-text">订单备货<\/text>/)
  assert.match(userSource, /hasAdminRole\(userInfo\.value\)/)
  assert.match(userSource, /packageA\/preparation\/preparation-list/)
})

test('mini program uses the shared admin preparation contract', () => {
  assert.match(apiSource, /getList:\s*\(params = \{\}, options = \{\}\) => http\.get\('\/preparation\/list'/)
  assert.match(apiSource, /getDetail:\s*\(id, source = 'W'/)
  assert.match(apiSource, /prepare:\s*\(id, materials/)
  assert.match(apiSource, /finish:\s*\(id/)
  const paths = pages.subPackages.flatMap(group => group.pages.map(page => `${group.root}/${page.path}`))
  assert.ok(paths.includes('packageA/preparation/preparation-list'))
  assert.ok(paths.includes('packageA/preparation/preparation-detail'))
})

test('preparation pages compile as Vue SFC templates', () => {
  const require = createRequire(new URL('../../admin/package.json', import.meta.url))
  const { parse, compileTemplate } = require('@vue/compiler-sfc')
  for (const relative of ['../packageA/preparation/preparation-list.vue', '../packageA/preparation/preparation-detail.vue']) {
    const pageUrl = new URL(relative, import.meta.url)
    const source = readFileSync(pageUrl, 'utf8')
    const parsed = parse(source, { filename: pageUrl.pathname })
    assert.deepEqual(parsed.errors, [])
    const template = compileTemplate({ source: parsed.descriptor.template.content, filename: pageUrl.pathname, id: 'issue-165' })
    assert.deepEqual(template.errors, [])
  }
})
