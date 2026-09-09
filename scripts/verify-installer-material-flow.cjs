const fs = require('node:fs')
const assert = require('node:assert/strict')
const path = require('node:path')
const { createRequire } = require('node:module')

const requireVue = createRequire(path.resolve(__dirname, '../frontend/admin/package.json'))
const { parse, compileScript, compileTemplate } = requireVue('@vue/compiler-sfc')
const vue = requireVue('vue')
const cartSource = fs.readFileSync(path.resolve(__dirname,
  '../frontend/miniprogram/packageA/material-cart/material-cart.vue'), 'utf8')
const orderDetailSource = fs.readFileSync(path.resolve(__dirname,
  '../frontend/miniprogram/packageA/order-detail/order-detail.vue'), 'utf8')

function compileCart() {
  const { descriptor, errors } = parse(cartSource)
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'issue113-cart' })
  const template = compileTemplate({
    source: descriptor.template.content,
    filename: 'material-cart.vue',
    id: 'issue113-cart',
    compilerOptions: { bindingMetadata: script.bindings }
  })
  assert.deepEqual(template.errors, [])
  const imports = {
    vue: 'vue',
    '@dcloudio/uni-app': 'hooks',
    '@/api/api.js': 'api'
  }
  let code = script.content.replace(/import\s*\{([\s\S]*?)\}\s*from\s*['"]([^'"]+)['"]/g,
    (_, names, name) => `const {${names}} = ${imports[name]};`)
  code = code.replace('export default', 'return')
  return new Function('vue', 'hooks', 'api', 'uni', code)
}

function fixture(responses) {
  const calls = { modals: [], requests: [], toasts: [], tabs: [] }
  const hooks = {}
  const api = {
    installerMaterialApi: {
      getCart: async () => ({ code: 200, data: { items: [{
        id: 7, productName: '铜管', specLabel: '1.2', unit: '米', stock: 6,
        quantity: 2, available: true
      }] } }),
      submitOrder: async requestId => {
        calls.requests.push(requestId)
        return responses.shift()
      }
    }
  }
  const uni = {
    showModal: options => calls.modals.push(options),
    showToast: options => calls.toasts.push(options),
    switchTab: options => calls.tabs.push(options)
  }
  const component = compileCart()(vue, { onShow: fn => { hooks.onShow = fn } }, api, uni)
  return { view: component.setup({}, { expose() {} }), hooks, calls }
}

async function submit(view, calls) {
  const pending = view.confirmSubmit()
  const modal = calls.modals.at(-1)
  assert.equal(modal.confirmText, '确认提交')
  modal.success({ confirm: true })
  await pending
}

function assertSfcCompiles(source, filename, id) {
  const platformSource = source
    .replace(/\s*\/\/ #ifdef MP-WEIXIN\r?\n([\s\S]*?)\s*\/\/ #endif/, '\n$1')
    .replace(/\s*\/\/ #ifndef MP-WEIXIN\r?\n[\s\S]*?\s*\/\/ #endif/, '')
  const { descriptor, errors } = parse(platformSource, { filename })
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id })
  const template = compileTemplate({
    source: descriptor.template.content,
    filename,
    id,
    compilerOptions: { bindingMetadata: script.bindings }
  })
  assert.deepEqual(template.errors, [])
}

async function main() {
  assertSfcCompiles(orderDetailSource, 'order-detail.vue', 'material-order-detail')
  const test = fixture([
    { code: 503, msg: '暂时失败' },
    { code: 200, data: { id: 12, orderNo: 'A202609080001' } }
  ])
  await test.hooks.onShow()
  assert.equal(test.view.items.value.length, 1)

  await submit(test.view, test.calls)
  assert.equal(test.view.items.value.length, 1)
  assert.equal(test.calls.tabs.length, 0)
  assert.equal(test.calls.toasts.at(-1).title, '暂时失败')

  await submit(test.view, test.calls)
  assert.equal(test.calls.requests.length, 2)
  assert.equal(test.calls.requests[0], test.calls.requests[1])
  assert.equal(test.view.items.value.length, 0)
  assert.deepEqual(test.calls.tabs, [{ url: '/pages/index/index' }])
  assert.equal(test.calls.toasts.at(-1).title, '取货申请已提交')

  assert.doesNotMatch(cartSource, /self-order-detail\/self-order-detail/)
  assert.match(orderDetailSource, /t:\s*Date\.now\(\)/)
  assert.match(orderDetailSource, /await fetchTools\(\)/)
  assert.match(orderDetailSource, /progressRecords\.value\.length > 0/)
  assert.match(orderDetailSource, /首次提交施工进度后将不能继续修改/)
  assert.match(orderDetailSource, /selectedSkuIds\[tool\.id\] === sku\.id/)
  assert.match(orderDetailSource, /materialSavedSignature/)
  assert.match(orderDetailSource, /请先保存耗材清单，再提交施工进度/)
  console.log('PASS: material list refresh, SKU selection, editable request and cart feedback are wired')
}

main().catch(error => {
  console.error(error)
  process.exitCode = 1
})
