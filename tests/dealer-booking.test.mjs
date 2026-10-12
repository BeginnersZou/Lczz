import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
// The mini-program package is compiled as ESM by uni-app; use an explicit module URL in Node.
const source = readFileSync(new URL('../utils/dealer-booking.js', import.meta.url), 'utf8')
const { isDealer, bookingPayload, validateBooking } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
const valid = () => ({ taskType: '空调安装', description: '安装设备', customerName: '客户', customerPhone: '13800000000', addressArea: ['湖北省', '武汉市', '江汉区'], addressDetail: '测试地址' })

test('only dealer role can enter, including multi-role accounts', () => {
  for (const role of ['admin', 'customer', 'installer', '', null]) assert.equal(isDealer({ role }), false)
  assert.equal(isDealer({ role: 'DEALER' }), true)
  assert.equal(isDealer({ role: 'customer', roles: ['DEALER'] }), true)
  assert.equal(isDealer({ roles: 'DEALER' }), false)
})
test('appointment payload excludes server-owned fields and uses ordered file IDs', () => {
  const form = { ...valid(), masterIds: [99], dealerUserId: 3, orderSource: 'ADMIN', customerUserId: 2, status: 'REVIEWED' }
  const payload = bookingPayload(form, [{ id: 4 }, { id: '7' }], 'same-request')
  assert.deepEqual(Object.keys(payload).sort(), ['requestId', 'taskType', 'description', 'customerName', 'customerPhone', 'addressArea', 'addressDetail', 'fileIds'].sort())
  assert.deepEqual(payload.fileIds, [4, 7])
  assert.equal(payload.requestId, 'same-request')
  assert.notEqual(payload.addressArea, form.addressArea)
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
function page({ role = 'dealer', create, upload } = {}) {
  const state = { selection: null }
  const uni = { chooseImage: options => { state.selection = options }, showToast() {}, previewImage() {}, showModal() {}, switchTab() {} }
  const names = ['ref', 'reactive', 'computed', 'onShow', 'onUnload', 'authApi', 'dealerBookingApi', 'uploadApi', 'getAuthToken', 'uni', ...Object.keys(helpers)]
  const values = [ref, reactive, computed, () => {}, () => {}, { getUserInfo: async () => ({ code: 200, data: { role } }) }, { create }, { uploadImage: upload, deleteTemporary: async () => ({ code: 200 }) }, () => 'test-session', uni, ...Object.values(helpers)]
  const instance = new Function(...names, `${pageScript}\nreturn { form, files, allowed, checking, locked, result, error, checkAccess, chooseImages, upload, submit };`)(...values)
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
