import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
// The mini-program package is compiled as ESM by uni-app; use an explicit module URL in Node.
const source = readFileSync(new URL('../utils/dealer-booking.js', import.meta.url), 'utf8')
const { isDealer, bookingPayload, validateBooking } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
const valid = () => ({ taskType: '空调安装', description: '安装设备', customerName: '客户', customerPhone: '13800000000', addressArea: ['湖北省', '武汉市', '江汉区'], addressDetail: '测试地址' })

test('dealer and sales can book; a secondary role cannot bypass the single-role contract', () => {
  for (const role of ['admin', 'customer', 'installer', '', null]) assert.equal(isDealer({ role }), false)
  assert.equal(isDealer({ role: 'DEALER' }), true)
  assert.equal(isDealer({ role: 'SALES' }), true)
  assert.equal(isDealer({ role: 'customer', roles: ['DEALER'] }), false)
  assert.equal(isDealer({ roles: 'DEALER' }), false)
})
test('appointment payload excludes server-owned fields and uses ordered file IDs', () => {
  const form = { ...valid(), masterIds: [99], dealerUserId: 3, orderSource: 'ADMIN', customerUserId: 2, status: 'REVIEWED' }
  const payload = bookingPayload(form, [{ id: 4 }, { id: '7' }], 'same-request')
  assert.deepEqual(Object.keys(payload).sort(), ['requestId', 'taskType', 'description', 'customerName', 'customerPhone', 'addressArea', 'addressDetail', 'salesUserId', 'fileIds'].sort())
  assert.equal(payload.salesUserId, null)
  assert.deepEqual(payload.fileIds, [4, 7])
  assert.equal(payload.requestId, 'same-request')
  assert.notEqual(payload.addressArea, form.addressArea)
  assert.equal(bookingPayload({ ...form, salesUserId: 12 }, [], 'other').salesUserId, 12)
})
test('required fields, exact task values and phone validation prevent invalid requests', () => {
  assert.equal(validateBooking(valid(), []), '')
  for (const field of ['description', 'customerName', 'customerPhone', 'addressDetail', 'taskType']) {
    assert.notEqual(validateBooking({ ...valid(), [field]: ' ' }, []), '')
  }
  assert.notEqual(validateBooking({ ...valid(), addressArea: ['湖北省'] }, []), '')
  assert.notEqual(validateBooking({ ...valid(), customerPhone: '12345678901' }, []), '')
  assert.notEqual(validateBooking({ ...valid(), description: '字'.repeat(1001) }, []), '')
})
test('zero or nine completed attachments work; incomplete or invalid uploads block submission', () => {
  const files = Array.from({ length: 9 }, (_, index) => ({ id: index + 1, status: 'done' }))
  assert.equal(validateBooking(valid(), files), '')
  assert.notEqual(validateBooking(valid(), [...files, { id: 10, status: 'done' }]), '')
  for (const status of ['waiting', 'uploading', 'failed']) assert.notEqual(validateBooking(valid(), [{ id: 1, status }]), '')
  for (const id of [0, -1, null, 'bad', 1.5]) assert.notEqual(validateBooking(valid(), [{ id, status: 'done' }]), '')
})

// Exercise the actual page handlers with injected API/uni boundaries, without a live server.
// Vue is supplied by the admin workspace's installed dependencies.
const require = createRequire(new URL('../../admin/package.json', import.meta.url))
const { ref, reactive, computed } = require('vue')
const pageSource = readFileSync(new URL('../packageA/dealer-booking/dealer-booking.vue', import.meta.url), 'utf8')
const pageScript = pageSource.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
const helpers = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
function page({ role = 'dealer', create, upload, salespersonOptions } = {}) {
  const state = { selection: null }
  const uni = { chooseImage: options => { state.selection = options }, showToast() {}, previewImage() {}, showModal() {}, switchTab() {} }
  const names = ['ref', 'reactive', 'computed', 'onShow', 'onUnload', 'authApi', 'dealerBookingApi', 'salespersonApi', 'uploadApi', 'getAuthToken', 'uni', ...Object.keys(helpers)]
  const values = [ref, reactive, computed, () => {}, () => {}, { getUserInfo: async () => ({ code: 200, data: { role } }) }, { create },
    { options: salespersonOptions || (async () => ({ code: 200, data: { list: [{ id: 12, name: '销售乙' }] } })) },
    { uploadImage: upload, deleteTemporary: async () => ({ code: 200 }) }, () => 'test-session', uni, ...Object.values(helpers)]
  const instance = new Function(...names, `${pageScript}\nreturn { form, files, allowed, checking, locked, result, error, salespersons, salesKeyword, salespersonTotal, checkAccess, loadSalespersons, loadMoreSalespersons, selectSalesperson, chooseImages, upload, submit };`)(...values)
  Object.assign(instance.form, valid())
  return { ...instance, state }
}

test('flow: multi-image selection retains all files, failed upload retries and blocks premature submit', async () => {
  let shouldFail = true
  const submitted = []
  const instance = page({
    upload: async path => path === 'second.jpg' && shouldFail ? { code: -1 } : { code: 200, data: { id: path === 'first.jpg' ? 11 : 12 } },
    create: async payload => { submitted.push(payload); return { code: 200, data: { id: 5, orderNo: 'TEST-5' } } }
  })
  await instance.checkAccess()
  instance.chooseImages()
  const selectionPromise = instance.state.selection.success({ tempFiles: [{ path: 'first.jpg', size: 100 }, { path: 'second.jpg', size: 100 }] })
  instance.state.selection.complete()
  await selectionPromise
  assert.deepEqual(instance.files.value.map(file => file.status), ['done', 'failed'])
  await instance.submit()
  assert.equal(submitted.length, 0)
  shouldFail = false
  await instance.upload(instance.files.value[1])
  await instance.submit()
  assert.deepEqual(submitted[0].fileIds, [11, 12])
  assert.equal(instance.result.value.orderNo, 'TEST-5')
})

test('flow: concurrent clicks are blocked and timeout retries reuse identical request', async () => {
  const requests = []
  let complete
  const instance = page({ create: payload => { requests.push(payload); return new Promise(resolve => { complete = resolve }) } })
  await instance.checkAccess()
  const first = instance.submit()
  await instance.submit()
  assert.equal(requests.length, 1)
  complete({ code: -1, msg: 'timeout' })
  await first
  assert.equal(instance.locked.value, true)
  const retry = instance.submit()
  assert.strictEqual(requests[1], requests[0])
  complete({ code: 200, data: { id: 6, orderNo: 'TEST-6' } })
  await retry
  assert.equal(instance.result.value.id, 6)
})

test('flow: non-dealer cannot submit; missing backend never produces success', async () => {
  let calls = 0
  const create = async () => { calls++; return { code: 404 } }
  const customer = page({ role: 'customer', create })
  await customer.checkAccess()
  await customer.submit()
  assert.equal(calls, 0)
  const dealer = page({ create })
  await dealer.checkAccess()
  await dealer.submit()
  assert.equal(dealer.result.value, null)
  assert.equal(dealer.locked.value, false)
  assert.match(dealer.error.value, /暂未开放/)
})

test('flow: sales creator chooses another salesperson without auto-assignment', async () => {
  const calls = []
  const sales = page({ role: 'sales', create: async (payload, role) => {
    calls.push({ payload, role }); return { code: 200, data: { id: 8, orderNo: 'SALES-8' } }
  } })
  await sales.checkAccess()
  assert.equal(sales.allowed.value, true)
  sales.form.salesUserId = 12
  await sales.submit()
  assert.equal(calls[0].role, 'sales')
  assert.equal(calls[0].payload.salesUserId, 12)
})

test('salesperson picker loads beyond first page and keeps search results separate', async () => {
  const calls = []
  const instance = page({
    salespersonOptions: async params => {
      calls.push({ ...params })
      if (params.keyword === '乙') return { code: 200, data: { list: [{ id: 7, name: '销售乙' }], total: 1 } }
      if (params.page === 1) return { code: 200, data: {
        list: Array.from({ length: 100 }, (_, index) => ({ id: index + 1, name: `销售${index + 1}` })), total: 101
      } }
      return { code: 200, data: { list: [{ id: 101, name: '末页销售', maskedPhone: '138****0001' }], total: 101 } }
    }
  })
  await instance.checkAccess()
  assert.equal(instance.salespersons.value.length, 100)
  instance.salesKeyword.value = '乙'
  await instance.loadMoreSalespersons()
  assert.equal(calls[1].keyword, '')
  assert.equal(instance.salespersons.value.length, 101)
  instance.selectSalesperson({ detail: { value: 100 } })
  assert.equal(instance.form.salesUserId, 101)
  assert.match(instance.salespersons.value[100].displayName, /138\*\*\*\*0001/)
  await instance.loadSalespersons()
  assert.equal(instance.salespersons.value.length, 1)
  assert.equal(calls[2].keyword, '乙')
})
