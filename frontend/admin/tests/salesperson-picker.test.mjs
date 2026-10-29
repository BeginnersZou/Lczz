import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { ref } = require('vue')
const source = readFileSync(new URL('../src/views/orders/OrderForm.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1]
const picker = script.match(/const salespersons = ref\(\[\]\)([\s\S]*?)\/\/ 图片只在草稿中增删/)[0]
  .replace(/\/\/ 图片只在草稿中增删$/, '')

function setup(getSalespersonOptionsApi) {
  const form = { salesUserId: null }
  return new Function('ref', 'getSalespersonOptionsApi', 'form',
    `${picker}\nreturn { form, salespersons, salespersonError, salespersonLoading, salespersonPage, searchSalespersons, loadMoreSalespersons, retrySalespersons };`)(
    ref, getSalespersonOptionsApi, form)
}

test('PC salesperson picker loads every page and preserves a selected person during search', async () => {
  const calls = []
  const view = setup(async ({ page, pageSize, keyword }) => {
    calls.push({ page, pageSize, keyword })
    const list = keyword ? [{ id: 65, name: '销售65' }]
      : Array.from({ length: Math.max(0, Math.min(pageSize, 65 - (page - 1) * pageSize)) },
        (_, index) => ({ id: (page - 1) * pageSize + index + 1, name: `销售${(page - 1) * pageSize + index + 1}` }))
    return { list, total: keyword ? 1 : 65 }
  })
  await view.searchSalespersons()
  await view.loadMoreSalespersons('bottom')
  await view.loadMoreSalespersons('bottom')
  assert.equal(view.salespersons.value.length, 65)
  assert.equal(view.salespersons.value.at(-1).id, 65)
  assert.deepEqual(calls.map(call => call.page), [1, 2, 3])
  await view.loadMoreSalespersons('bottom')
  assert.equal(calls.length, 3)
  await view.searchSalespersons('销售65')
  assert.deepEqual(view.salespersons.value.map(person => person.id), [65])
})

test('PC salesperson picker retries a failed page without losing prior options', async () => {
  let failed = false
  const view = setup(async ({ page }) => {
    if (page === 2 && !failed) { failed = true; throw new Error('network') }
    return { list: [{ id: page, name: `销售${page}` }], total: 60 }
  })
  await view.searchSalespersons()
  await view.loadMoreSalespersons('bottom')
  assert.match(view.salespersonError.value, /销售人员加载失败/)
  assert.deepEqual(view.salespersons.value.map(person => person.id), [1])
  await view.retrySalespersons()
  assert.equal(view.salespersonError.value, '')
  assert.deepEqual(view.salespersons.value.map(person => person.id), [1, 2])
})

test('PC retains an existing selection when search returns no match or fails', async () => {
  const view = setup(async ({ keyword }) => {
    if (keyword === 'error') throw { response: { status: 404 } }
    return { list: keyword ? [] : [{ id: 12, name: '销售乙' }], total: keyword ? 0 : 1 }
  })
  await view.searchSalespersons()
  view.form.salesUserId = 12
  await view.searchSalespersons('no-match')
  assert.equal(view.salespersons.value[0].name, '销售乙')
  assert.equal(view.salespersons.value[0].retained, true)
  await view.searchSalespersons('error')
  assert.equal(view.form.salesUserId, 12)
  assert.equal(view.salespersons.value[0].name, '销售乙')
  assert.match(view.salespersonError.value, /暂未开放/)
})

test('PC discards stale search results and never displays an old failure for a newer search', async () => {
  const pending = []
  const view = setup(() => new Promise((resolve, reject) => pending.push({ resolve, reject })))
  const first = view.searchSalespersons('old')
  const second = view.searchSalespersons('new')
  pending[1].resolve({ list: [{ id: 2, name: '新销售' }], total: 1 })
  await second
  pending[0].reject(new Error('old request failure'))
  await first
  assert.equal(view.salespersonError.value, '')
  assert.equal(view.salespersons.value[0].id, 2)
  assert.equal(view.salespersonLoading.value, false)
})

test('PC uses structured person options and an explicit optional-field hint', () => {
  assert.match(source, /popper-class="salesperson-select-popper"/)
  assert.match(source, /class="salesperson-avatar"/)
  assert.match(source, /class="salesperson-phone"/)
  assert.match(source, /当前已选/)
  assert.match(source, /不指定不影响提交/)
  const teleportedStyles = source.match(/<style lang="scss">([\s\S]*?)<\/style>/)?.[1]
  assert.match(teleportedStyles, /\.salesperson-select-popper\s*\{/)
  assert.match(teleportedStyles, /\.salesperson-option\s*\{/)
  assert.match(teleportedStyles, /max-height: min\(280px, 40vh\)/)
  assert.match(source, /strategy: 'fixed'/)
})
