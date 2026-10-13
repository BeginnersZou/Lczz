import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const read = file => readFileSync(new URL('../' + file, import.meta.url), 'utf8')
const require = createRequire(new URL('../../admin/package.json', import.meta.url))
const vue = require('vue'), compiler = require('@vue/compiler-sfc')
const helpers = await import(`data:text/javascript;base64,${Buffer.from(read('utils/recipient.js')).toString('base64')}`)
const info = { recipientName: '王收货', recipientPhone: '13800000088', recipientAddress: '武汉市测试路8号' }
const address = { id: 1, isDefault: true, ...info }
const script = file => read(file).match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }

function cart() {
  let token = 'session-a'
  const calls = { modals: [], requests: [], navigation: [], toasts: [], tabs: [] }, hooks = {}
  let cartReply = async () => ({ code: 200, data: { items: [{ id: 7, productName: '铜管', quantity: 2, stock: 6, available: true }] } })
  let addressReply = async () => ({ code: 200, data: [address] })
  let orderReply = async () => ({ code: 200, data: { id: 10 } })
  const view = new Function('ref', 'computed', 'onShow', 'installerMaterialApi', 'recipientAddressApi', 'getAuthToken',
    ...Object.keys(helpers), 'uni', script('packageA/material-cart/material-cart.vue') +
    '\nreturn { items,recipient,submittedPayload,submitRequestId,loading,submitting,addressError,canSubmit,loadCart,loadDefaultAddress,openAddresses,confirmSubmit,confirmClear };')(
    vue.ref, vue.computed, fn => { hooks.onShow = fn },
    { getCart: () => cartReply(), submitOrder: (data, options) => { calls.requests.push({ data, options }); return orderReply() } },
    { list: () => addressReply() }, () => token, ...Object.values(helpers),
    { showModal: value => calls.modals.push(value), showToast: value => calls.toasts.push(value), switchTab: value => calls.tabs.push(value),
      navigateTo: value => { calls.navigation.push(value); value.success?.({ eventChannel: { emit() {} } }) } })
  return { ...view, calls, hooks, token: value => { token = value }, cartReply: value => { cartReply = value },
    addressReply: value => { addressReply = value }, orderReply: value => { orderReply = value } }
}

async function submit(view, confirm = true) {
  const pending = view.confirmSubmit()
  view.calls.modals.at(-1)?.success({ confirm })
  await pending
}

function addressPage(mode) {
  let token = 'session-a'
  const calls = { selected: [], back: 0, create: [], update: [], defaults: [], delete: [], modals: [] }, hooks = {}, events = {}
  let listReply = async () => ({ code: 200, data: [address] })
  let createReply = async data => ({ code: 200, data: { id: 2, ...data } })
  const channel = { on: (name, fn) => { events[name] = fn }, emit: (name, value) => calls.selected.push({ name, value }) }
  const view = new Function('ref', 'getCurrentInstance', 'onLoad', 'onShow', 'onUnload', 'recipientAddressApi', 'getAuthToken',
    ...Object.keys(helpers), 'uni', script('packageA/recipient-addresses/recipient-addresses.vue') +
    '\nreturn { addresses,choosing,editing,editingId,form,saveToBook,makeDefault,formError,listError,busy,loadAddresses,startNew,startEdit,saveForm,choose,setDefault,remove };')(
    vue.ref, () => ({ proxy: { getOpenerEventChannel: () => channel } }),
    ...['onLoad', 'onShow', 'onUnload'].map(name => fn => { hooks[name] = fn }),
    { list: () => listReply(), create: data => { calls.create.push(data); return createReply(data) },
      update: async (id, data) => { calls.update.push({ id, data }); return { code: 200, data: { id, ...data } } },
      setDefault: async id => { calls.defaults.push(id); return { code: 200 } },
      remove: async id => { calls.delete.push(id); return { code: 200 } } }, () => token, ...Object.values(helpers),
    { showToast() {}, navigateBack: () => { calls.back++ }, showModal: value => calls.modals.push(value) })
  hooks.onLoad({ mode })
  return { ...view, calls, hooks, events, token: value => { token = value }, listReply: value => { listReply = value }, createReply: value => { createReply = value } }
}

test('recipient validation trims values and supports mobile and landline without accepting missing fields', () => {
  assert.deepEqual(helpers.normalizeRecipient({ recipientName: ' 王收货 ', recipientPhone: '138 0000 0088', recipientAddress: ' 武汉市测试路8号 ' }), info)
  assert.equal(helpers.recipientError(info), '')
  assert.equal(helpers.recipientError({ ...info, recipientPhone: '027-82710326' }), '')
  for (const invalid of [{}, { ...info, recipientName: ' ' }, { ...info, recipientName: 'x'.repeat(65) },
    { ...info, recipientPhone: 'abc' }, { ...info, recipientAddress: 'x'.repeat(501) }]) assert.ok(helpers.recipientError(invalid))
})

test('cart applies default, allows manual or saved selection and includes recipient in final confirmation', async () => {
  const view = cart(); await view.loadCart()
  assert.deepEqual(view.recipient.value, info)
  view.openAddresses('manual'); assert.match(view.calls.navigation[0].url, /mode=manual$/)
  view.calls.navigation[0].events.recipientSelected({ ...info, recipientName: '另一收货人' })
  await view.hooks.onShow(); assert.equal(view.recipient.value.recipientName, '另一收货人')
  await submit(view, false); assert.equal(view.calls.requests.length, 0); assert.equal(view.items.value.length, 1)
  await submit(view)
  assert.match(view.calls.modals.at(-1).content, /另一收货人/)
  assert.match(view.calls.modals.at(-1).content, /13800000088/)
  assert.equal(view.calls.requests[0].data.recipientName, '另一收货人')
  assert.equal(view.items.value.length, 0)
})

test('missing or invalid receipt blocks submit and an address-list error still permits manual entry', async () => {
  const view = cart(); view.addressReply(async () => ({ code: 500 })); await view.loadCart()
  await view.confirmSubmit(); assert.equal(view.calls.modals.length, 0); assert.equal(view.calls.requests.length, 0)
  assert.ok(view.addressError.value)
  view.openAddresses('manual'); view.calls.navigation[0].events.recipientSelected(info)
  await submit(view); assert.equal(view.calls.requests.length, 1)
})

test('network retry preserves request and receipt even when the server has already cleared the cart', async () => {
  const view = cart(); await view.loadCart(); view.orderReply(async () => ({ code: -1, msg: '网络异常' }))
  await submit(view)
  const original = { ...view.calls.requests[0].data }
  assert.equal(view.items.value.length, 1)
  view.openAddresses('manual'); assert.equal(view.calls.navigation.length, 0)
  view.confirmClear(); assert.equal(view.calls.modals.length, 1)
  view.cartReply(async () => ({ code: 200, data: { items: [] } })); await view.loadCart()
  assert.equal(view.canSubmit.value, true)
  view.orderReply(async () => ({ code: 200, data: { id: 9 } })); await submit(view)
  assert.deepEqual(view.calls.requests[1].data, original)
  assert.equal(view.submitRequestId.value, '')
})

test('a definite validation rejection unlocks recipient editing and a later request gets a new token', async () => {
  const view = cart(); await view.loadCart(); view.orderReply(async () => ({ code: 400, msg: '校验失败' })); await submit(view)
  assert.equal(view.submitRequestId.value, '')
  view.openAddresses('select'); view.calls.navigation[0].events.recipientSelected({ ...info, recipientName: '修正收货人' })
  view.orderReply(async () => ({ code: 200 })); await submit(view)
  assert.notEqual(view.calls.requests[0].data.requestId, view.calls.requests[1].data.requestId)
  assert.equal(view.calls.requests[1].data.recipientName, '修正收货人')
})

test('late default responses and selections cannot overwrite a manual choice or cross a changed login session', async () => {
  const view = cart(), late = deferred(); view.addressReply(() => late.promise)
  const pending = view.loadCart(); await Promise.resolve(); await Promise.resolve()
  view.openAddresses('manual'); view.calls.navigation[0].events.recipientSelected({ ...info, recipientName: '临时收货' })
  late.resolve({ code: 200, data: [address] }); await pending
  assert.equal(view.recipient.value.recipientName, '临时收货')
  view.token('session-b'); view.addressReply(async () => ({ code: 200, data: [] })); await view.loadCart()
  view.calls.navigation[0].events.recipientSelected(info)
  assert.equal(view.recipient.value.recipientName, '')
})

test('manual entry can be used without writing the address book, or optionally saved and selected', async () => {
  const view = addressPage('manual'); assert.equal(view.saveToBook.value, false)
  view.events.initialRecipient(info); await view.saveForm()
  assert.equal(view.calls.create.length, 0); assert.equal(view.calls.selected[0].name, 'recipientSelected')
  assert.deepEqual(view.calls.selected[0].value, info)
  const saved = addressPage('manual'); saved.form.value = { ...info }; saved.saveToBook.value = true; saved.makeDefault.value = true
  await saved.saveForm(); assert.deepEqual(saved.calls.create[0], { ...info, isDefault: true }); assert.equal(saved.calls.back, 1)
})

test('address management edits, sets default, confirms delete, refreshes list and does not select an order receipt', async () => {
  const view = addressPage(); await view.hooks.onShow()
  assert.equal(view.addresses.value.length, 1)
  view.startEdit(address); view.form.value.recipientName = '修改姓名'; await view.saveForm()
  assert.equal(view.calls.update[0].id, 1); assert.equal(view.calls.update[0].data.recipientName, '修改姓名')
  await view.setDefault(address); assert.deepEqual(view.calls.defaults, [1])
  view.remove(address); await view.calls.modals[0].success({ confirm: false }); assert.equal(view.calls.delete.length, 0)
  view.remove(address); await view.calls.modals[1].success({ confirm: true }); assert.deepEqual(view.calls.delete, [1])
  assert.equal(view.calls.selected.length, 0)
})

test('failed address saves preserve input and return no selection; stale account or unloaded responses are ignored', async () => {
  const view = addressPage('manual'); view.form.value = { ...info }; view.saveToBook.value = true
  view.createReply(async () => ({ code: 500, msg: '失败' })); await view.saveForm()
  assert.equal(view.form.value.recipientName, info.recipientName); assert.equal(view.calls.selected.length, 0); assert.ok(view.formError.value)
  const late = deferred(); view.listReply(() => late.promise); const pending = view.loadAddresses()
  view.token('session-b'); late.resolve({ code: 200, data: [address] }); await pending
  assert.equal(view.addresses.value.length, 0)
  await view.saveForm(); assert.equal(view.calls.create.length, 1)
  view.hooks.onUnload(); view.choose(address); assert.equal(view.calls.selected.length, 0)
})

test('real API transports all receipt fields and address CRUD payloads', async () => {
  const calls = [], http = Object.fromEntries(['get', 'post', 'put', 'patch', 'delete'].map(method => [method, async (...args) => { calls.push({ method, args }); return { code: 200 } }]))
  const apiCode = read('api/api.js').replace(/^import .+$/gm, '').replace(/export const /g, 'const ')
  const api = new Function('http', 'baseUrl', 'formatDateTime', apiCode + '\nreturn { installerMaterialApi, recipientAddressApi }')(
    http, '', value => value)
  await api.installerMaterialApi.submitOrder({ requestId: 'r1', ...info })
  await api.recipientAddressApi.create({ ...info, isDefault: true })
  await api.recipientAddressApi.update(1, { ...info, isDefault: false })
  await api.recipientAddressApi.setDefault(1); await api.recipientAddressApi.remove(1)
  assert.deepEqual(calls[0].args[1], { requestId: 'r1', ...info })
  assert.deepEqual(calls[1].args[1], { ...info, isDefault: true })
  assert.deepEqual(calls.map(call => call.method), ['post', 'post', 'put', 'patch', 'delete'])
  assert.equal(calls[3].args[0], '/installer/addresses/1/default')
})

test('cart, address page, order details and admin preparation compile with registered addresses route', () => {
  for (const file of ['packageA/material-cart/material-cart.vue', 'packageA/recipient-addresses/recipient-addresses.vue', 'packageA/self-order-detail/self-order-detail.vue']) {
    const { descriptor, errors } = compiler.parse(read(file), { filename: file }); assert.deepEqual(errors, [])
    const compiled = compiler.compileScript(descriptor, { id: 'recipient-qa' })
    assert.deepEqual(compiler.compileTemplate({ source: descriptor.template.content, filename: file, id: 'recipient-qa', compilerOptions: { bindingMetadata: compiled.bindings } }).errors, [])
  }
  const pages = JSON.parse(read('pages.json'))
  assert.ok(pages.subPackages.find(group => group.root === 'packageA').pages.some(page => page.path === 'recipient-addresses/recipient-addresses'))
  const admin = readFileSync(new URL('../../admin/src/views/preparation/PreparationList.vue', import.meta.url), 'utf8')
  const { descriptor } = compiler.parse(admin); const compiled = compiler.compileScript(descriptor, { id: 'receipt-admin' })
  assert.deepEqual(compiler.compileTemplate({ source: descriptor.template.content, filename: 'PreparationList.vue', id: 'receipt-admin', compilerOptions: { bindingMetadata: compiled.bindings } }).errors, [])
  for (const field of Object.keys(info)) { assert.match(read('packageA/self-order-detail/self-order-detail.vue'), new RegExp('detail\\.' + field)); assert.match(admin, new RegExp('currentRow\\.' + field)) }
})
