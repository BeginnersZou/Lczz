const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const assert = require('node:assert/strict')
const { createRequire } = require('node:module')

const requireAdmin = createRequire(path.resolve(__dirname, '../frontend/admin/package.json'))
const { parse, compileScript, compileTemplate } = requireAdmin('@vue/compiler-sfc')
const { parse: parseScript } = requireAdmin('@babel/parser')
const vue = requireAdmin('vue')

function setupView(relativePath, imports, globals = {}) {
  const filename = path.resolve(__dirname, '../frontend/admin/src/views', relativePath)
  const { descriptor, errors } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'stock-warning' })
  const template = compileTemplate({
    source: descriptor.template.content, filename, id: 'stock-warning',
    compilerOptions: { bindingMetadata: script.bindings }
  })
  assert.deepEqual(template.errors, [])
  const edits = []
  for (const node of parseScript(script.content, { sourceType: 'module' }).program.body) {
    if (node.type === 'ImportDeclaration') {
      const declarations = node.specifiers.map(specifier => {
        const module = `__imports[${JSON.stringify(node.source.value)}]`
        if (specifier.type === 'ImportDefaultSpecifier') return `const ${specifier.local.name} = ${module}.default;`
        if (specifier.type === 'ImportNamespaceSpecifier') return `const ${specifier.local.name} = ${module};`
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
  const mounted = []
  const context = {
    ...globals,
    __imports: {
      ...imports,
      vue: { ...vue, onMounted: callback => mounted.push(callback), onUnmounted() {} }
    }
  }
  vm.runInNewContext(compiled, context, { filename })
  return {
    view: context.__component.setup({}, { expose() {} }),
    mount: async () => {
      await Promise.all(mounted.map(callback => callback()))
      await new Promise(setImmediate)
    }
  }
}

async function main() {
  const routes = []
  const dashboard = setupView('Dashboard.vue', {
    'vue-router': { useRouter: () => ({ push: route => routes.push(route) }) },
    'echarts/core': { use() {} },
    'echarts/charts': {}, 'echarts/components': {}, 'echarts/renderers': {},
    '@element-plus/icons-vue': {},
    '@/api/dashboard': { getOverviewApi: async () => ({ lowStock: 4 }) },
    '@/utils/format': { formatNumber: value => value }
  })
  await dashboard.view.loadOverview()
  assert.equal(dashboard.view.stats.lowStock, 4)
  dashboard.view.goToStat(dashboard.view.statCards.value.find(card => card.key === 'lowStock'))
  assert.equal(routes[0].name, 'Consumables')
  assert.equal(routes[0].query.stockStatus, 'warning', 'the alert card must include empty stock')

  const calls = { list: [], exports: [], routes: [] }
  const rows = Array.from({ length: 4 }, (_, index) => ({ id: index + 1, name: `无库存耗材${index}`, stock: 0 }))
  const consumables = setupView('consumables/consumableslist.vue', {
    'vue-router': {
      useRoute: () => ({ query: routes[0].query }),
      useRouter: () => ({ replace: route => { calls.routes.push(route); return Promise.resolve() } })
    },
    '@element-plus/icons-vue': {},
    'element-plus': { ElMessage: { success() {} } },
    '@/utils/format': { formatDateTime: value => value },
    '@/api/consumables': {
      getConsumablesListApi: async params => { calls.list.push(params); return { list: rows, total: rows.length } },
      getConsumableCategoriesApi: async () => [],
      exportConsumablesApi: async params => { calls.exports.push(params); return {} }
    }
  }, {
    window: { URL: { createObjectURL: () => 'blob:test', revokeObjectURL() {} } },
    document: { createElement: () => ({ click() {} }), body: { appendChild() {}, removeChild() {} } }
  })
  await consumables.mount()
  assert.equal(calls.list.length, 1, 'entering consumables must request the warning list')
  assert.equal(calls.list[0].stockStatus, 'warning')
  assert.equal(consumables.view.stockStatus.value, 'warning')
  assert.equal(consumables.view.total.value, dashboard.view.stats.lowStock)
  assert.equal(consumables.view.pagedList.value.length, 4)
  assert.equal(consumables.view.loadError.value, '')
  console.log('PASS: dashboard alert opens and loads the four empty-stock products')

  consumables.view.handleCurrentChange(2)
  await vue.nextTick()
  assert.equal(calls.list.at(-1).page, 2)
  assert.equal(calls.list.at(-1).stockStatus, 'warning')
  assert.equal(calls.routes.at(-1).query.stockStatus, 'warning')
  await consumables.view.handleBatchExport()
  assert.equal(calls.exports[0].stockStatus, 'warning', 'export must preserve the selected stock filter')
  console.log('PASS: pagination, route persistence and export retain the warning filter')

  for (const stockStatus of ['low', 'empty', 'normal']) {
    consumables.view.stockStatus.value = stockStatus
    await consumables.view.handleSearch()
    assert.equal(calls.list.at(-1).stockStatus, stockStatus)
    assert.equal(calls.list.at(-1).page, 1)
  }
  await consumables.view.handleReset()
  assert.equal(calls.list.at(-1).stockStatus, undefined)
  assert.equal(calls.routes.at(-1).query.stockStatus, undefined)
  console.log('PASS: separate stock filters and reset remain available')
}

main().catch(error => { console.error(error); process.exitCode = 1 })
