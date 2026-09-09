const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const requireVue = createRequire(path.resolve(__dirname, '../frontend/admin/package.json'))
const vue = requireVue('vue')
const { parse, compileScript, compileTemplate } = requireVue('@vue/compiler-sfc')
const read = file => fs.readFileSync(path.resolve(__dirname, '../frontend/miniprogram', file), 'utf8')
const baseUrl = 'https://example.test/api/v1'
const products = [
  { id: 1, name: 'PVC管', category: ['管材'], skus: [
    { id: 11, specLabel: '25mm', unit: '米', stock: 10, enabled: true },
    { id: 12, specLabel: '35mm', unit: '米', stock: 8, enabled: true }
  ] },
  { id: 2, name: '铜管', category: ['管材'], skus: [
    { id: 21, specLabel: '6mm', unit: '米', stock: 5, enabled: true }
  ] }
]
const emptyEnvelope = { code: 200, message: 'success', requestId: 'empty', timestamp: '2026-09-09T08:56:58Z' }
const oldEmpty = { code: 404, error: 'MATERIAL_REQUEST_NOT_FOUND', message: '该订单尚未提交耗材申请', requestId: 'legacy-empty' }
const pickupFailure = { code: 503, error: 'PICKUP_PHONE_NOT_CONFIGURED', message: '取货联系电话尚未配置，请联系管理员', requestId: 'pickup-error' }

// Run the real API and HTTP modules. Only platform IO, auth storage and configuration are supplied.
function transport(reply) {
  const calls = { requests: [], toasts: [], modals: [], tabs: [] }
  const uni = {
    showLoading() {}, hideLoading() {},
    getSystemInfoSync: () => ({ windowHeight: 844 }),
    showToast: value => calls.toasts.push(value),
    showModal: value => calls.modals.push(value),
    switchTab: value => calls.tabs.push(value),
    request(options) {
      // Expose the platform boundary: undefined GET values would become literal query strings.
      const url = new URL(options.url)
      if (options.method === 'GET') {
        Object.entries(options.data || {}).forEach(([key, value]) => url.searchParams.set(key, String(value)))
      }
      calls.requests.push({ url, method: options.method, data: options.data, header: options.header })
      const result = reply(url, options)
      options.success({ statusCode: result.code, data: result })
      options.complete()
    }
  }
  const requestCode = read('utils/request.js').replace(/^import .+$/gm, '').replace('export default http', 'return http')
  const http = new Function('baseUrl', 'clearAuthSession', 'getAuthToken', 'showLoginChoice', 'uni', requestCode)(
    baseUrl, () => {}, () => 'test-token', () => {}, uni)
  const timeCode = read('utils/time.js').replace(/export /g, '')
  const formatDateTime = new Function(timeCode + '\nreturn formatDateTime')()
  const source = read('api/api.js')
  const exports = [...source.matchAll(/export const (\w+)/g)].map(match => match[1])
  const apiCode = source.replace(/^import .+$/gm, '').replace(/export const /g, 'const ')
  const api = new Function('http', 'baseUrl', 'formatDateTime', apiCode + '\nreturn {' + exports.join(',') + '}')(
    http, baseUrl, formatDateTime)
  return { api, http, uni, calls }
}

function component(file, env) {
  const source = read(file)
    .replace(/\/\/ #ifdef MP-WEIXIN([\s\S]*?)\/\/ #endif/g, '$1')
    .replace(/\/\/ #ifndef MP-WEIXIN([\s\S]*?)\/\/ #endif/g, '')
  const { descriptor, errors } = parse(source)
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'material-http' })
  const template = compileTemplate({ source: descriptor.template.content, filename: file, id: 'material-http',
    compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [])
  const imports = { vue: 'vue', '@dcloudio/uni-app': 'hooks', '@/api/api.js': 'api', '@/utils/auth-guard.js': 'guard' }
  const code = script.content.replace(/import\s*\{([\s\S]*?)\}\s*from\s*['"]([^'"]+)['"]/g,
    (_, names, name) => `const {${names}} = ${imports[name]};`).replace('export default', 'return')
  const hooks = Object.fromEntries(['onLoad', 'onShow', 'onBackPress', 'onUnload'].map(key => [key, () => {}]))
  const factory = new Function('vue', 'hooks', 'api', 'guard', 'uni', 'wx', code)
  return factory(vue, hooks, env.api, { requireLogin: () => true }, env.uni,
    { getWindowInfo: env.uni.getSystemInfoSync }).setup({}, { expose() {} })
}

function orderFixture(material = emptyEnvelope) {
  const env = transport(url => {
    switch (url.pathname) {
      case '/api/v1/auth/info': return { code: 200, data: { id: 7, role: 'INSTALLER' } }
      case '/api/v1/orders/detail/8': return { code: 200, data: { id: 8, statusCode: 'PENDING_VISIT' } }
      case '/api/v1/orders/8/progress': return { code: 200, data: [] }
      case '/api/v1/orders/8/materials': return material
      case '/api/v1/consumables/list': {
        const keyword = url.searchParams.get('keyword')
        const list = products.filter(product => !keyword || product.name.includes(keyword))
        return { code: 200, data: { list, total: list.length } }
      }
      default: throw Error('Unexpected request: ' + url)
    }
  })
  const view = component('packageA/order-detail/order-detail.vue', env)
  view.orderId.value = '8'
  return { ...env, view }
}

const cases = {
  async 'GET omits absent parameters, preserves false/zero, and POST keeps explicit null'() {
    const env = transport(() => ({ code: 200, data: {} }))
    const query = { page: 1, keyword: undefined, category: null, empty: '', enabled: false, offset: 0 }
    await env.http.get('/consumables/list', query)
    const { url } = env.calls.requests[0]
    for (const key of ['keyword', 'category', 'empty']) assert.equal(url.searchParams.has(key), false, key)
    assert.equal(url.searchParams.get('enabled'), 'false')
    assert.equal(url.searchParams.get('offset'), '0')
    assert.equal(Object.hasOwn(query, 'keyword'), true, 'caller data must not be mutated')
    const body = { remark: null, enabled: false, quantity: 0, description: '' }
    await env.http.post('/example', body)
    assert.deepEqual(env.calls.requests[1].data, body)
  },
  async 'first open, search, clear and reopen load products through the real HTTP boundary'() {
    const env = orderFixture()
    await env.view.openToolPopup()
    assert.equal(env.calls.requests.at(-1).url.searchParams.has('keyword'), false)
    assert.equal(env.view.filteredTools.value.length, 2)
    env.view.searchKeyword.value = '铜管'
    await env.view.fetchTools()
    assert.equal(env.calls.requests.at(-1).url.searchParams.get('keyword'), '铜管')
    assert.equal(env.view.filteredTools.value.length, 1)
    env.view.searchKeyword.value = ''
    await env.view.fetchTools()
    assert.equal(env.calls.requests.at(-1).url.searchParams.has('keyword'), false)
    assert.equal(env.view.filteredTools.value.length, 2)
    env.view.searchKeyword.value = '没有匹配'
    await env.view.openToolPopup()
    assert.equal(env.view.filteredTools.value.length, 2)
    const product = env.view.filteredTools.value[0]
    assert.equal(env.view.getSelectedSku(product), null)
    env.view.selectToolSku(product, product.skus[0])
    env.view.addToolToCart(env.view.getSelectedSku(product))
    env.view.selectToolSku(product, product.skus[1])
    env.view.addToolToCart(env.view.getSelectedSku(product))
    env.view.confirmTools()
    assert.deepEqual(env.view.toolList.value.map(item => item.skuId), [11, 12])
  },
  async '200 without data or with null data does not create or lock a material request'() {
    for (const response of [emptyEnvelope, { ...emptyEnvelope, data: null }]) {
      const env = orderFixture(response)
      await env.view.loadOrderDetails()
      assert.equal(env.view.detailError.value, null)
      assert.equal(env.view.materialRequest.value, null)
      assert.equal(Boolean(env.view.materialReadonly.value), false)
      assert.equal(env.view.toolList.value.length, 0)
    }
  },
  async 'only legacy empty-material 404 becomes an empty draft; real errors remain failures'() {
    const env = orderFixture(oldEmpty)
    const result = await env.api.orderApi.getMaterials(8)
    assert.equal(result.code, 200)
    assert.equal(result.data, null)
    assert.equal(env.calls.toasts.length, 0)
    await env.view.loadOrderDetails()
    assert.equal(env.view.detailError.value, null)
    assert.equal(Boolean(env.view.materialReadonly.value), false)
    for (const failure of [
      { code: 404, error: 'ORDER_NOT_FOUND', message: '订单不存在' },
      { code: 404, message: 'Not Found' },
      { code: 403, error: 'FORBIDDEN', message: '无权访问' },
      { code: 500, error: 'INTERNAL_ERROR', message: '服务异常' }
    ]) {
      const test = orderFixture({ ...failure, requestId: 'failure-id' })
      const response = await test.api.orderApi.getMaterials(8, { silent: true })
      assert.equal(response.code, failure.code)
      assert.equal(response.error, failure.error)
      assert.equal(response.requestId, 'failure-id')
      await test.view.loadOrderDetails()
      assert.notEqual(test.view.detailError.value, null)
    }
  },
  async 'flat list compatibility excludes envelope metadata'() {
    const env = transport(() => ({ code: 200, list: [], total: 0, requestId: 'flat', timestamp: 'now' }))
    const response = await env.http.get('/example')
    assert.deepEqual(response.data, { list: [], total: 0 })
    assert.equal(response.requestId, 'flat')
  },
  async 'real 503 cart response retains items and message; retry sends same token and succeeds'() {
    const replies = [pickupFailure, { code: 200, data: { id: 12, orderNo: 'A-12' } }]
    const env = transport((url, options) => {
      if (url.pathname === '/api/v1/installer/cart') return { code: 200, data: { items: [
        { id: 3, productName: 'PVC管', quantity: 2, stock: 10, available: true }
      ] } }
      assert.equal(url.pathname, '/api/v1/installer/self-orders')
      assert.equal(options.method, 'POST')
      assert.ok(options.data.requestId)
      assert.equal(options.header.Authorization, 'Bearer test-token')
      return replies.shift()
    })
    const view = component('packageA/material-cart/material-cart.vue', env)
    await view.loadCart()
    let action = view.confirmSubmit()
    env.calls.modals.at(-1).success({ confirm: true })
    await action
    assert.equal(env.calls.toasts.at(-1).title, pickupFailure.message)
    assert.equal(view.items.value.length, 1)
    assert.equal(view.submitting.value, false)
    assert.equal(env.calls.tabs.length, 0)
    action = view.confirmSubmit()
    env.calls.modals.at(-1).success({ confirm: true })
    await action
    const submitted = env.calls.requests.filter(request => request.method === 'POST')
    assert.equal(submitted[0].data.requestId, submitted[1].data.requestId)
    assert.equal(view.items.value.length, 0)
    assert.deepEqual(env.calls.tabs, [{ url: '/pages/index/index' }])
  }
}

;(async () => {
  for (const [name, run] of Object.entries(cases)) {
    try { await run(); console.log('PASS: ' + name) }
    catch (error) { process.exitCode = 1; console.error('FAIL: ' + name); console.error(error.message) }
  }
})()
