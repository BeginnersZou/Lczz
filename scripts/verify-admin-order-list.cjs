const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const assert = require('node:assert/strict')
const { createRequire } = require('node:module')

const requireAdmin = createRequire(path.resolve(__dirname, '../frontend/admin/package.json'))
const { parse, compileScript, compileTemplate } = requireAdmin('@vue/compiler-sfc')
const { parse: parseScript } = requireAdmin('@babel/parser')
const vue = requireAdmin('vue')
const filename = path.resolve(__dirname, '../frontend/admin/src/views/orders/OrderList.vue')
const source = fs.readFileSync(filename, 'utf8')
const { descriptor, errors } = parse(source, { filename })
assert.deepEqual(errors, [])
const script = compileScript(descriptor, { id: 'admin-order-list' })
const template = compileTemplate({
  source: descriptor.template.content,
  filename,
  id: 'admin-order-list',
  compilerOptions: { bindingMetadata: script.bindings }
})
assert.deepEqual(template.errors, [])

// Evaluate the actual compiled setup in an isolated context. Missing imports
// must fail just as they do in the browser, even when Vite builds successfully.
const edits = []
for (const node of parseScript(script.content, { sourceType: 'module' }).program.body) {
  if (node.type === 'ImportDeclaration') {
    const declarations = node.specifiers.map(specifier => {
      const module = `__imports[${JSON.stringify(node.source.value)}]`
      if (specifier.type === 'ImportDefaultSpecifier') {
        return `const ${specifier.local.name} = ${module}.default;`
      }
      if (specifier.type === 'ImportNamespaceSpecifier') {
        return `const ${specifier.local.name} = ${module};`
      }
      return `const ${specifier.local.name} = ${module}[${JSON.stringify(specifier.imported.name)}];`
    })
    edits.push({ start: node.start, end: node.end, value: declarations.join('\n') })
  } else if (node.type === 'ExportDefaultDeclaration') {
    edits.push({ start: node.start, end: node.declaration.start, value: 'globalThis.__component = ' })
  }
}
let compiled = script.content
for (const edit of edits.sort((a, b) => b.start - a.start)) {
  compiled = compiled.slice(0, edit.start) + edit.value + compiled.slice(edit.end)
}

function fixture(query = {}) {
  const mounted = []
  const calls = { list: [], routes: [], reviews: [] }
  let listReply = { list: [{ id: 1, orderNo: 'WO-TEST-1', customerName: '测试客户' }], total: 1 }
  let reviewReply = null
  const context = {
    __imports: {
      vue: { ...vue, onMounted: callback => mounted.push(callback) },
      'vue-router': {
        useRoute: () => ({ query }),
        useRouter: () => ({
          replace: route => { calls.routes.push(route); return Promise.resolve() },
          push: () => Promise.resolve()
        })
      },
      'element-plus': { ElMessage: { success() {}, error() {}, warning() {} } },
      '@element-plus/icons-vue': {},
      '@/api/orders': {
        getOrderListApi: async params => {
          calls.list.push(params)
          if (listReply instanceof Error) throw listReply
          return listReply
        },
        cancelOrderApi: async () => {},
        exportOrdersApi: async () => {},
        getOrderEvaluationApi: async id => { calls.reviews.push(id); return reviewReply }
      },
      '@/utils/format': { formatDateTime: value => value, formatPhone: value => value },
      './OrderOrigin.vue': { default: {} }
    }
  }
  vm.runInNewContext(compiled, context, { filename })
  const view = context.__component.setup({}, { expose() {} })
  return {
    view,
    calls,
    mount: () => Promise.all(mounted.map(callback => callback())),
    setList: reply => { listReply = reply },
    setReview: reply => { reviewReply = reply }
  }
}

async function main() {
  const initial = fixture()
  assert.equal(initial.calls.list.length, 0)
  await initial.mount()
  assert.equal(initial.calls.list.length, 1, 'mounting the order list must request orders')
  assert.equal(initial.view.orders.value[0].orderNo, 'WO-TEST-1')
  assert.equal(initial.view.total.value, 1)
  assert.equal(initial.view.loadError.value, '')
  assert.equal(initial.view.tableLoading.value, false)
  assert.equal(initial.view.reviewImages.value.length, 0)
  console.log('PASS: actual order list setup succeeds and mount loads orders')

  const filtered = fixture({ keyword: '客户', projectAddress: '工地', status: 'IN_PROGRESS', page: '2', pageSize: '20' })
  await filtered.mount()
  assert.deepEqual({ ...filtered.calls.list[0] }, {
    keyword: '客户', projectAddress: '工地', status: 'IN_PROGRESS', page: 2, pageSize: 20
  })
  filtered.view.searchKeyword.value = 'WO-TEST-1'
  filtered.view.handleSearch()
  await vue.nextTick()
  assert.equal(filtered.calls.list.at(-1).page, 1)
  assert.equal(filtered.calls.list.at(-1).keyword, 'WO-TEST-1')
  console.log('PASS: route filters and searches reach the order API')

  const recovery = fixture()
  recovery.setList(new Error('offline'))
  await recovery.mount()
  assert.ok(recovery.view.loadError.value)
  assert.equal(recovery.view.tableLoading.value, false)
  recovery.setList({ list: [], total: 0 })
  await recovery.view.loadList()
  assert.equal(recovery.calls.list.length, 2)
  assert.equal(recovery.view.loadError.value, '')
  assert.equal(recovery.view.orders.value.length, 0)
  console.log('PASS: list failure is visible and retry recovers')

  initial.setReview({
    imageFiles: [{ id: 9, thumbnailUrl: '/thumbnail', originalUrl: '/original', url: '/original' }],
    images: ['/legacy']
  })
  await initial.view.handleViewReview({ id: 1 })
  assert.equal(initial.calls.reviews[0], 1)
  assert.equal(initial.view.reviewImages.value[0].thumbnailUrl, '/thumbnail')
  assert.equal(initial.view.reviewPreviewImages.value[0], '/original')
  initial.setReview({ images: ['/legacy'] })
  await initial.view.handleViewReview({ id: 1 })
  assert.equal(initial.view.reviewImages.value[0].url, '/legacy')
  assert.equal(initial.view.reviewPreviewImages.value[0], '/legacy')
  initial.setReview(null)
  await initial.view.handleViewReview({ id: 1 })
  assert.equal(initial.view.reviewImages.value.length, 0)
  assert.equal(initial.view.reviewPreviewImages.value.length, 0)
  console.log('PASS: review thumbnails, original preview, legacy images and empty reviews')
}

main().catch(error => { console.error(error); process.exitCode = 1 })
