const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const assert = require('node:assert/strict')
const { createRequire } = require('node:module')

const requireAdmin = createRequire(path.resolve(__dirname, '../frontend/admin/package.json'))
const { parse, compileScript, compileTemplate } = requireAdmin('@vue/compiler-sfc')
const { parse: parseScript } = requireAdmin('@babel/parser')
const vue = requireAdmin('vue')

function evaluateModule(source, filename, imports) {
  const edits = []
  const names = []
  for (const node of parseScript(source, { sourceType: 'module' }).program.body) {
    if (node.type === 'ImportDeclaration') {
      const declarations = node.specifiers.map(specifier => {
        const module = `__imports[${JSON.stringify(node.source.value)}]`
        if (specifier.type === 'ImportDefaultSpecifier') return `const ${specifier.local.name} = ${module}.default;`
        if (specifier.type === 'ImportNamespaceSpecifier') return `const ${specifier.local.name} = ${module};`
        return `const ${specifier.local.name} = ${module}[${JSON.stringify(specifier.imported.name)}];`
      })
      edits.push({ start: node.start, end: node.end, value: declarations.join('\n') })
    } else if (node.type === 'ExportDefaultDeclaration') {
      edits.push({ start: node.start, end: node.declaration.start, value: 'globalThis.__default = ' })
    } else if (node.type === 'ExportNamedDeclaration') {
      assert.equal(node.declaration.type, 'FunctionDeclaration')
      names.push(node.declaration.id.name)
      edits.push({ start: node.start, end: node.declaration.start, value: '' })
    }
  }
  let compiled = source
  for (const edit of edits.sort((a, b) => b.start - a.start)) {
    compiled = compiled.slice(0, edit.start) + edit.value + compiled.slice(edit.end)
  }
  const context = { __imports: imports }
  vm.runInNewContext(`${compiled}\nglobalThis.__exports = { ${names.join(', ')} };`, context, { filename })
  return { ...context.__exports, default: context.__default }
}

function loadModule(relativePath, imports) {
  const filename = path.resolve(__dirname, '../frontend/admin/src', relativePath)
  return evaluateModule(fs.readFileSync(filename, 'utf8'), filename, imports)
}

function findColumn(node, label) {
  if (!node || typeof node !== 'object') return null
  if (Array.isArray(node)) {
    for (const child of node) {
      const found = findColumn(child, label)
      if (found) return found
    }
    return null
  }
  // Table columns require row/scope arguments, so only invoke the selected slot.
  if (node.type?.name === 'el-table-column') return node.props.label === label ? node.children.default : null
  return findColumn(typeof node.children?.default === 'function' ? node.children.default() : node.children, label)
}

function textOf(node) {
  if (Array.isArray(node)) return node.map(textOf).join('')
  if (node == null) return ''
  return typeof node === 'object' ? textOf(node.children) : String(node)
}

async function main() {
  let response = {
    list: [
      { id: 1, siteName: '五张图片案例', imageCount: 5,
        coverImage: { id: 11, url: '/original', originalUrl: '/original', thumbnailUrl: '/thumbnail' } },
      { id: 2, siteName: '兼容旧详情格式', images: [{ id: 21, url: '/one' }, { id: 22, url: '/two' }] },
      { id: 3, siteName: '旧接口未返回数量', coverImage: { id: 31, url: '/cover' } },
      { id: 4, siteName: '确实没有图片', imageCount: 0 }
    ], total: 4
  }
  const calls = []
  const request = async options => { calls.push(options); return response }
  const filesApi = loadModule('api/files.js', { '@/utils/request': { default: request } })
  const casesApi = loadModule('api/cases.js', {
    '@/utils/request': { default: request }, './files': filesApi
  })
  const filename = path.resolve(__dirname, '../frontend/admin/src/views/cases/CaseList.vue')
  const { descriptor, errors } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'case-count' })
  const template = compileTemplate({
    source: descriptor.template.content, filename, id: 'case-count',
    compilerOptions: { bindingMetadata: script.bindings }
  })
  assert.deepEqual(template.errors, [])
  const mounted = []
  const component = evaluateModule(script.content, filename, {
    vue: { ...vue, onMounted: callback => mounted.push(callback) },
    'vue-router': { useRouter: () => ({ push() {}, replace() {} }) },
    'element-plus': { ElMessage: { success() {} }, ElMessageBox: { confirm: async () => {} } },
    '@element-plus/icons-vue': {}, '@/api/cases': casesApi,
    '@/utils/format': { formatDateTime: value => value }
  }).default
  const view = component.setup({}, { expose() {} })
  const render = evaluateModule(template.code, filename, {
    vue: { ...vue, resolveComponent: name => ({ name }), resolveDirective: () => ({}), withDirectives: node => node }
  }).render
  await Promise.all(mounted.map(callback => callback()))
  assert.equal(view.loadError.value, '')
  assert.equal(view.rows.value.length, 4)
  assert.equal(view.rows.value[0].images.length, 0, 'summary responses omit the full image array')
  const tree = () => render(vue.proxyRefs(view), [], {}, vue.proxyRefs(view), {}, {})
  const countSlot = findColumn(tree(), '案例图片')
  assert.ok(countSlot, 'the actual case count column must render')
  const labels = view.rows.value.map(row => textOf(countSlot({ row })))
  assert.deepEqual(Array.from(labels), ['5 张', '2 张', '-', '0 张'])
  assert.equal(calls.length, 1, 'case counts must come from the list without per-row detail requests')
  console.log('PASS: actual case list renders server counts, legacy image arrays, unknown counts and true zero')

  const coverSlot = findColumn(tree(), '封面')
  const cover = coverSlot({ row: view.rows.value[0] })[0]
  assert.equal(cover.props.src, '/thumbnail')
  assert.equal((cover.props['preview-src-list'] || cover.props.previewSrcList)[0], '/original')
  console.log('PASS: case covers keep optimized thumbnails and original previews')

  response = { list: [{ id: 1, siteName: '新增图片后', imageCount: 6 }], total: 1 }
  await view.loadCases()
  const refreshedSlot = findColumn(tree(), '案例图片')
  assert.equal(textOf(refreshedSlot({ row: view.rows.value[0] })), '6 张')
  assert.equal(calls.length, 2)
  assert.ok(calls.every(call => call.url === '/cases/list'))
  console.log('PASS: refreshing the list displays updated image counts')
}

main().catch(error => { console.error(error); process.exitCode = 1 })
