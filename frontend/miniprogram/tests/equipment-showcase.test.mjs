import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const read = relative => readFileSync(new URL('../' + relative, import.meta.url), 'utf8')
const source = read('packageA/equipment-showcase/equipment-catalog.js')
const helpers = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
const require = createRequire(new URL('../../admin/package.json', import.meta.url))
const vue = require('vue')
const compiler = require('@vue/compiler-sfc')
const pageSource = read('packageA/equipment-showcase/equipment-showcase.vue')
const pageScript = pageSource.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
const categories = () => [
  { id: 4, code: 'air-conditioning-equipment', name: '空调设备', parentId: null, enabled: true },
  { id: 22, code: 'lczz-equipment', name: '设备', parentId: null, enabled: true },
  ...['空调', '新风', '净水', '采暖'].map((name, index) => ({ id: 70 + index, code: 'eq-' + index, name, parentId: 22, sortOrder: index * 10, enabled: true })),
  { id: 80, code: 'other-air', name: '空调', parentId: 4, enabled: true }
]

function page() {
  const hooks = {}
  const calls = { products: [], categories: [], navigation: [], refreshStops: 0 }
  let categoryReply = async () => ({ code: 200, data: categories() })
  let productReply = async params => ({ code: 200, data: {
    list: Array.from({ length: 6 }, (_, index) => ({ id: (params.page - 1) * 6 + index + 1, title: '设备' })), total: 12
  } })
  const names = ['ref', 'computed', ...['onLoad', 'onShow', 'onUnload', 'onReachBottom', 'onPullDownRefresh', 'onShareAppMessage'], 'consumablesApi', ...Object.keys(helpers), 'uni']
  const values = [vue.ref, vue.computed,
    ...['onLoad', 'onShow', 'onUnload', 'onReachBottom', 'onPullDownRefresh', 'onShareAppMessage'].map(name => fn => { hooks[name] = fn }),
    {
      getCategories: async options => { calls.categories.push(options); return categoryReply() },
      getList: async (params, options) => { calls.products.push({ ...params }); assert.equal(options.loading, false); return productReply(params) }
    }, ...Object.values(helpers),
    { navigateTo: options => calls.navigation.push(options), stopPullDownRefresh: () => { calls.refreshStops++ } }]
  const state = new Function(...names, pageScript + '\nreturn { root,tabs,selectedType,searchKeyword,activeKeyword,products,page,total,listLoading,categoryError,listError,loadStatus,refreshCatalog,performSearch,clearSearch,switchTab,showAll,retry,openDetail };')(...values)
  return { ...state, calls, hooks, load: () => hooks.onLoad(), categories: reply => { categoryReply = reply }, productsReply: reply => { productReply = reply } }
}

test('device tabs come from the real equipment parent and grow beyond four child categories', () => {
  const extra = { id: 99, code: 'heat-pump', name: '热泵', parentId: '22', sortOrder: 50, enabled: true }
  const catalog = helpers.buildEquipmentCatalog([...categories(), extra, { ...extra, id: 100, code: 'off', name: '停用', enabled: false }])
  assert.equal(catalog.root.id, 22)
  assert.deepEqual(catalog.tabs.map(tab => tab.name), ['全部', '空调', '新风', '净水', '采暖', '热泵'])
  assert.equal(helpers.equipmentQuery(null, catalog.tabs[0], ''), null)
  assert.equal(helpers.equipmentQuery(catalog.root, { type: 'other-air', id: 80, parentId: 4 }, ''), null)
  assert.equal(helpers.buildEquipmentCatalog(categories().filter(item => item.id !== 22)).root, null)
  const renamed = categories().map(item => item.id === 22 ? { ...item, name: '新设备名称' } : item)
  assert.equal(helpers.buildEquipmentCatalog(renamed).root.id, 22)
})

test('all, child, search, paging and refresh keep equipment scope and the existing detail route', async () => {
  const view = page(); await view.load()
  assert.equal(view.products.value.length, 6)
  await view.hooks.onReachBottom()
  assert.equal(view.products.value.length, 12)
  await view.hooks.onReachBottom()
  assert.equal(view.calls.products.length, 2)
  assert.deepEqual(view.calls.products.map(call => call.page), [1, 2])
  await view.switchTab('eq-1')
  view.searchKeyword.value = '  新风设备  '; await view.performSearch()
  assert.equal(view.calls.products.at(-1).keyword, '新风设备')
  assert.equal(view.calls.products.at(-1).categoryId, 71)
  await view.hooks.onPullDownRefresh()
  assert.equal(view.calls.products.at(-1).page, 1)
  assert.equal(view.calls.products.at(-1).categoryId, 71)
  assert.equal(view.calls.refreshStops, 1)
  await view.showAll()
  assert.equal(view.calls.products.at(-1).category, 'lczz-equipment')
  assert.equal(view.calls.products.at(-1).categoryId, undefined)
  assert.equal(view.calls.products.at(-1).keyword, undefined)
  assert.ok(view.calls.products.every(call => call.categoryRootId === 22 && call.category))
  view.openDetail({ id: 9 })
  assert.equal(view.calls.navigation[0].url, '/packageA/goos-details/goos-details?id=9')
})

test('returning from details preserves the list and dynamic category removal falls back to scoped all', async () => {
  const view = page(); await view.load(); await view.hooks.onShow()
  await view.switchTab('eq-1'); await view.hooks.onReachBottom()
  const items = view.products.value; const requests = view.calls.products.length
  await view.hooks.onShow()
  assert.equal(view.calls.products.length, requests)
  assert.equal(view.products.value, items)
  assert.equal(view.selectedType.value, 'eq-1')
  view.categories(async () => ({ code: 200, data: [...categories().filter(item => item.code !== 'eq-1'),
    { id: 99, code: 'heat-pump', name: '热泵', parentId: 22, sortOrder: 50, enabled: true }] }))
  await view.hooks.onShow()
  assert.equal(view.selectedType.value, 'all')
  assert.equal(view.tabs.value.at(-1).name, '热泵')
  assert.equal(view.calls.products.at(-1).categoryRootId, 22)
  assert.equal(view.calls.products.at(-1).category, 'lczz-equipment')
  view.categories(async () => ({ code: 200, data: [] }))
  const before = view.calls.products.length; await view.hooks.onShow()
  assert.equal(view.root.value, null)
  assert.equal(view.products.value.length, 0)
  assert.equal(view.calls.products.length, before)
})

test('stale page or category responses cannot overwrite a newer selection', async () => {
  const view = page(); await view.load()
  let resolveOld
  view.productsReply(() => new Promise(resolve => { resolveOld = resolve }))
  const oldPage = view.hooks.onReachBottom()
  view.productsReply(async () => ({ code: 200, data: { list: [{ id: 99, title: '新风' }], total: 1 } }))
  await view.switchTab('eq-1')
  resolveOld({ code: 200, data: { list: [{ id: 80, title: '旧分类' }], total: 12 } }); await oldPage
  assert.deepEqual(view.products.value.map(item => item.id), [99])
  assert.equal(view.listLoading.value, false)
  assert.equal(view.selectedType.value, 'eq-1')
  let resolveCategories
  view.categories(() => new Promise(resolve => { resolveCategories = resolve }))
  const oldCategories = view.refreshCatalog(true)
  view.categories(async () => ({ code: 200, data: [] })); await view.refreshCatalog(true)
  resolveCategories({ code: 200, data: categories() }); await oldCategories
  assert.equal(view.root.value, null)
  assert.equal(view.products.value.length, 0)
})

test('a filter selected while return-to-page categories are loading is applied after they finish', async () => {
  const view = page(); await view.load(); await view.hooks.onShow()
  let finishCategories
  view.categories(() => new Promise(resolve => { finishCategories = resolve }))
  const returning = view.hooks.onShow()
  await view.switchTab('eq-2')
  view.searchKeyword.value = '净水'; await view.performSearch()
  assert.equal(view.products.value.length, 0)
  finishCategories({ code: 200, data: categories() }); await returning
  assert.equal(view.calls.products.at(-1).categoryId, 72)
  assert.equal(view.calls.products.at(-1).keyword, '净水')
  assert.equal(view.calls.products.at(-1).categoryRootId, 22)
  assert.equal(view.products.value.length, 6)
})

test('category/list failures retry safely; missing equipment never calls an unscoped list', async () => {
  const view = page()
  view.categories(async () => ({ code: 503, msg: '暂时不可用' })); await view.load()
  assert.equal(view.calls.products.length, 0); assert.equal(view.categoryError.value, '暂时不可用')
  view.categories(async () => ({ code: 200, data: categories() })); await view.retry()
  view.productsReply(async () => ({ code: 503, msg: '设备服务不可用' })); await view.performSearch()
  assert.equal(view.listError.value, '设备服务不可用')
  view.productsReply(async () => ({ code: 200, data: { list: [], total: 0 } })); await view.retry()
  assert.equal(view.listError.value, ''); assert.equal(view.loadStatus.value, 'noMore')
  view.categories(async () => ({ code: 200, msg: 'success', data: {} })); await view.refreshCatalog(true)
  assert.equal(view.categoryError.value, '分类数据异常，请重试')
  const missing = page(); missing.categories(async () => ({ code: 200, data: categories().filter(item => item.id !== 22) }))
  await missing.load(); assert.equal(missing.calls.products.length, 0)
  await missing.hooks.onPullDownRefresh(); assert.equal(missing.calls.products.length, 0); assert.equal(missing.calls.refreshStops, 1)
})

test('page unload ignores pending results and homepage equipment entry opens the independent route', async () => {
  const view = page(); await view.load()
  let resolveReply
  view.productsReply(() => new Promise(resolve => { resolveReply = resolve }))
  const pending = view.performSearch(); view.hooks.onUnload()
  resolveReply({ code: 200, data: { list: [{ id: 999 }], total: 1 } }); await pending
  assert.equal(view.products.value.length, 0)
  const homeScript = read('pages/index/index.vue').match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
  const navigation = []
  const hookNames = ['onMounted', 'onShow', 'onReachBottom', 'onPullDownRefresh', 'onShareAppMessage', 'onShareTimeline']
  const home = new Function('ref', 'computed', ...hookNames, 'authApi', 'consumablesApi', 'getAuthToken', 'isDealer', 'uni', homeScript + '\nreturn { functionList,handleItemClick };')(
    vue.ref, vue.computed, ...hookNames.map(() => () => {}), {}, {}, () => null, () => false,
    { navigateTo: value => navigation.push(value) })
  const entry = home.functionList.value.find(item => item.title === '设备展示')
  assert.ok(entry); home.handleItemClick(entry)
  assert.equal(navigation[0].url, '/packageA/equipment-showcase/equipment-showcase')
})

test('real public HTTP transport retains the root/child filters and does not send login credentials', async () => {
  let sent
  const uni = { showLoading() {}, hideLoading() {}, request: options => {
    sent = options
    options.success({ statusCode: 200, data: { code: 200, data: { list: [], total: 0 } } })
    options.complete()
  } }
  const requestCode = read('utils/request.js').replace(/^import .+$/gm, '').replace('export default http', 'return http')
  const http = new Function('baseUrl', 'clearAuthSession', 'getAuthToken', 'showLoginChoice', 'uni', requestCode)(
    'https://example.test/api/v1', () => {}, () => 'private-token', () => {}, uni)
  const imageCode = read('utils/product-images.js').replace(/export const /g, 'const ')
  const apiCode = read('api/api.js').replace(/^import .+$/gm, '').replace(/export const /g, 'const ')
  const api = new Function('http', 'baseUrl', 'formatDateTime', imageCode + '\n' + apiCode + '\nreturn consumablesApi')(
    http, 'https://example.test/api/v1', value => value)
  await api.getList({ categoryRootId: 22, categoryId: 71, category: 'eq-1', page: 2, pageSize: 6, keyword: '新风' }, { loading: false })
  assert.deepEqual(sent.data, { categoryRootId: 22, categoryId: 71, category: 'eq-1', page: 2, pageSize: 6, keyword: '新风' })
  assert.equal(sent.header.Authorization, undefined)
})

test('equipment/home/grid scripts and templates compile, and the page route enables refresh', () => {
  for (const relative of ['packageA/equipment-showcase/equipment-showcase.vue', 'pages/index/index.vue', 'components/ProductGrid.vue']) {
    const { descriptor, errors } = compiler.parse(read(relative))
    assert.deepEqual(errors, [])
    const script = compiler.compileScript(descriptor, { id: 'equipment160' })
    const template = compiler.compileTemplate({ source: descriptor.template.content, filename: relative, id: 'equipment160', compilerOptions: { bindingMetadata: script.bindings } })
    assert.deepEqual(template.errors, [])
  }
  const routes = JSON.parse(read('pages.json')).subPackages.flatMap(group => group.pages.map(page => ({ ...page, fullPath: group.root + '/' + page.path })))
  const route = routes.find(page => page.fullPath === 'packageA/equipment-showcase/equipment-showcase')
  assert.equal(route.style.navigationBarTitleText, '设备展示')
  assert.equal(route.style.enablePullDownRefresh, true)
})

test('the shared grid renders actual cards, keeps first-four thumbnail loading and shows skeletons', async () => {
  const { renderToString } = require('@vue/server-renderer')
  const { descriptor } = compiler.parse(read('components/ProductGrid.vue'))
  const script = compiler.compileScript(descriptor, { id: 'grid160' })
  const observedImages = []
  const image = { props: ['src', 'eager', 'mode'], setup: props => {
    observedImages.push({ ...props }); return () => vue.h('image', { src: props.src })
  } }
  const component = new Function('image', script.content.replace(/^import LazyProductImage .+$/m, 'const LazyProductImage = image').replace('export default', 'return'))(image)
  const template = compiler.compileTemplate({ source: descriptor.template.content, filename: 'ProductGrid.vue', id: 'grid160',
    compilerOptions: { bindingMetadata: script.bindings, isCustomElement: tag => ['view', 'text', 'up-icon'].includes(tag) } })
  const renderCode = template.code.replace(/import\s*\{([\s\S]*?)\}\s*from\s*['"]vue['"]/g,
    (_, names) => 'const {' + names.replace(/\bas\b/g, ':') + '} = vue')
    .replace('export function render', 'return function render')
  component.render = new Function('vue', renderCode)(vue)
  const items = Array.from({ length: 6 }, (_, index) => ({ id: index + 1, title: '设备' + index, image: index < 5 ? '/thumbnail/' + index : '', skus: [{ enabled: true, stock: 1 }] }))
  const html = await renderToString(vue.createSSRApp(component, { items, fallbackCategory: '设备' }))
  assert.equal((html.match(/class="product-card"/g) || []).length, 6)
  assert.ok(html.includes('有库存'))
  assert.deepEqual(observedImages.map(image => image.eager), [true, true, true, true, false])
  assert.ok(observedImages.every(image => image.src.startsWith('/thumbnail/')))
  const skeleton = await renderToString(vue.createSSRApp(component, { loading: true, items: [] }))
  assert.equal((skeleton.match(/class="skeleton-card"/g) || []).length, 4)
})
