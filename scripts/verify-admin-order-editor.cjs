const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const { webcrypto } = require('node:crypto')
const requireAdmin = createRequire(path.resolve(__dirname, '../frontend/admin/package.json'))
const vue = requireAdmin('vue')
const { parse, compileScript, compileTemplate } = requireAdmin('@vue/compiler-sfc')
const { parse: parseScript } = requireAdmin('@babel/parser')

function compile(name) {
  const filename = path.resolve(__dirname, `../frontend/admin/src/views/orders/${name}.vue`)
  const { descriptor, errors } = parse(fs.readFileSync(filename, 'utf8'), { filename })
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: name })
  const template = compileTemplate({ source: descriptor.template.content, filename, id: name,
    compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [])
  const edits = []
  for (const node of parseScript(script.content, { sourceType: 'module' }).program.body) {
    if (node.type === 'ImportDeclaration') {
      edits.push({ start: node.start, end: node.end, value: node.specifiers.map(specifier => {
        const module = `__imports[${JSON.stringify(node.source.value)}]`
        return `const ${specifier.local.name} = ${module}[${JSON.stringify(specifier.type === 'ImportDefaultSpecifier' ? 'default' : specifier.imported.name)}];`
      }).join('\n') })
    } else if (node.type === 'ExportDefaultDeclaration') edits.push({ start: node.start, end: node.declaration.start, value: 'globalThis.__component = ' })
  }
  let content = script.content
  for (const edit of edits.sort((a, b) => b.start - a.start)) content = content.slice(0, edit.start) + edit.value + content.slice(edit.end)
  return { content, filename }
}

function fixture(name, props = {}, modules = {}, globals = {}) {
  const mounted = [], unmounted = [], messages = [], navigations = [], confirmations = []
  const scope = vue.effectScope()
  const imports = {
    vue: { ...vue, onMounted: cb => mounted.push(cb), onBeforeUnmount: cb => unmounted.push(cb) },
    'vue-router': { useRoute: () => ({ params: { id: 42 }, query: {} }), useRouter: () => ({
      push: route => navigations.push(route), back: () => navigations.push('back') }) },
    'element-plus': { ElMessage: Object.fromEntries(['success', 'error', 'warning', 'info'].map(type => [type, text => messages.push({ type, text })])),
      ElMessageBox: { confirm: async text => { confirmations.push(text) }, prompt: async () => ({ value: '取消原因' }) } },
    '@element-plus/icons-vue': {},
    '@/composables/useUnsavedChanges': { useUnsavedChanges() {} },
    '@/utils/format': { formatDateTime: value => value, formatDateTimeInput: value => value.replace('+08:00', ''), parseDateTime: value => new Date(value) },
    '@/api/regions': { getRegionTreeApi: async () => [{ value: '湖北省', children: [] }] },
    './OrderOrigin.vue': { default: {} }, './OrderEditorFiles.vue': { default: {} }, './OrderEditorMaterials.vue': { default: {} },
    ...modules
  }
  const context = { __imports: imports, console, crypto: webcrypto, window: { history: { state: {} } },
    FormData: class { append() {} }, URL: { createObjectURL: file => `blob:${file.name}`, revokeObjectURL() {} }, ...globals }
  const compiled = compile(name)
  vm.runInNewContext(compiled.content, context, { filename: compiled.filename })
  const emitted = []
  const view = scope.run(() => context.__component.setup(props, { expose() {}, emit: (event, data) => {
    emitted.push({ event, data })
    if (event === 'update:modelValue') props.modelValue = data
  } }))
  return { view, messages, navigations, confirmations, emitted, mount: async () => { await Promise.all(mounted.map(cb => cb())); await vue.nextTick() },
    stop: () => { scope.stop(); unmounted.forEach(cb => cb()) } }
}
function editorFixture(options = {}) {
  let fail = null
  const saves = [], binds = [], masterLoads = []
  const data = {
    revision: 'a'.repeat(64), editable: options.editable !== false,
    order: { id: 42, orderNo: 'WO42', statusCode: options.status || 'IN_PROGRESS', taskType: '空调安装', description: '原描述',
      customerName: '客户', customerPhone: '13915500003', addressArea: ['湖北省', '武汉市', '洪山区'], addressDetail: '施工地址',
      orderStartTime: '2026-10-12T10:00:00+08:00', orderEndTime: '2026-10-12T11:00:00+08:00', selectedMasterList: [{ id: 7 }],
      fileList: [{ id: 100, url: '/order100', thumbnailUrl: '/thumb100' }] },
    progress: [{ id: 1, installerUserId: 7, type: 'PROGRESS', submittedAt: '2026-10-12T10:36:00+08:00',
      description: '原施工记录', images: [{ id: 101, url: '/site101', thumbnailUrl: '/thumb101' }] },
      { id: 2, installerUserId: 7, type: 'PROGRESS', description: '第二条记录', images: [] }],
    materialRequest: options.noMaterials ? null : { id: 10, statusCode: 'DONE', statusLabel: '已备货', remark: '原备注',
      materials: [{ id: 50, productId: 5, skuId: 9, name: '铜管', spec: '6mm', count: 2, stock: 8, unit: '米' }] }
  }
  const result = fixture('OrderForm', {}, {
    '@/api/orders': { getAdminOrderEditorApi: async () => structuredClone(data), saveAdminOrderEditorApi: async (id, body) => {
      saves.push(structuredClone(body)); if (fail) throw fail; return {} },
      addOrderApi: async () => ({ id: 43 }), bindOrderFileApi: async (...args) => binds.push(args),
      getMasterListApi: async () => { masterLoads.push(true); return [{ id: 8, masterName: '新师傅' }] }, cancelOrderApi: async () => {} }
  })
  result.view.orderFormRef.value = { validate: async () => true }
  return { ...result, saves, binds, masterLoads, data, fail: error => { fail = error } }
}

async function main() {
  const basic = editorFixture({ status: 'REVIEWED' })
  await basic.mount()
  assert.equal(basic.view.formIsDirty.value, false)
  assert.equal(basic.view.progressEntries.value.length, 2)
  assert.equal(basic.view.materialDraft.value.items[0].quantity, 2)
  assert.equal(basic.view.form.orderStartTime, '2026-10-12T10:00:00')
  basic.view.form.description = '更正基础信息'
  await vue.nextTick()
  assert.equal(basic.view.formIsDirty.value, true)
  await basic.view.handleSubmit()
  assert.equal(basic.saves.length, 1)
  assert.equal(basic.saves[0].order.description, '更正基础信息')
  for (const untouched of ['progress', 'materials', 'attachmentFileIds']) assert.equal(untouched in basic.saves[0], false)
  assert.equal(basic.binds.length, 0)
  assert.equal(basic.navigations.length, 1)
  basic.stop()
  console.log('PASS: actual Vue edit form loads completed orders, preserves unmodified sections and saves atomically')

  const progress = editorFixture()
  await progress.mount()
  progress.view.progressEntries.value[0].description = '施工更正'
  progress.view.progressEntries.value[0].files = [{ id: 102, url: '/new' }, { id: 101, url: '/old' }]
  await vue.nextTick()
  assert.equal(progress.saves.length, 0, 'draft edits must not save or bind early')
  assert.equal(progress.binds.length, 0)
  await progress.view.handleSubmit()
  assert.equal(progress.saves[0].progress.length, 1)
  assert.deepEqual([...progress.saves[0].progress[0].fileIds], [102, 101])
  assert.equal('materials' in progress.saves[0], false)
  progress.stop()
  console.log('PASS: edited progress preserves file order and sends only the changed record')

  const remark = editorFixture()
  await remark.mount()
  remark.view.materialDraft.value.remark = '仅更正备注'
  await vue.nextTick()
  assert.equal(remark.view.materialsChanged.value, false)
  await remark.view.handleSubmit()
  assert.equal(remark.confirmations.length, 0)
  assert.equal(remark.saves[0].materials.items[0].quantity, 2)
  remark.stop()

  const retry = editorFixture({ status: 'PENDING_REVIEW' })
  await retry.mount()
  retry.view.materialDraft.value.items[0].quantity = 4
  await vue.nextTick()
  retry.fail(new Error('网络超时'))
  await retry.view.handleSubmit()
  assert.equal(retry.view.materialsChanged.value, true)
  assert.equal(retry.navigations.length, 0)
  assert.equal(retry.messages.some(m => m.type === 'success'), false)
  assert.ok(retry.view.submitError.value)
  retry.fail(null)
  await retry.view.handleSubmit()
  assert.equal(retry.saves[0].requestId, retry.saves[1].requestId, 'same failed attempt must retain its idempotency key')
  assert.ok(retry.confirmations[0].includes('重新备货'))
  retry.stop()
  console.log('PASS: material changes warn about preparation; remark-only changes do not; retries reuse the submission ID')

  const uploads = editorFixture({ noMaterials: true })
  await uploads.mount()
  uploads.view.form.fileList.push({ uid: 'upload', uploading: true, url: '' })
  await uploads.view.handleSubmit()
  assert.equal(uploads.saves.length, 0)
  uploads.view.form.fileList.at(-1).uploading = false
  uploads.view.form.fileList.at(-1).error = true
  await uploads.view.handleSubmit()
  assert.equal(uploads.saves.length, 0)
  uploads.view.form.fileList.pop()
  uploads.view.form.description = '无需耗材也能保存'
  await vue.nextTick()
  await uploads.view.handleSubmit()
  assert.equal(uploads.saves.length, 1)
  assert.equal('materials' in uploads.saves[0], false)
  uploads.stop()
  console.log('PASS: uploading/failed images block save; orders without materials can save other sections')

  const cancelled = editorFixture({ editable: false, status: 'CANCELLED' })
  await cancelled.mount()
  await cancelled.view.handleSubmit()
  assert.equal(cancelled.saves.length, 0)
  cancelled.stop()
  const conflict = editorFixture()
  await conflict.mount()
  conflict.view.form.description = '过期修改'
  await vue.nextTick()
  conflict.fail({ response: { status: 409, data: { error: 'ORDER_EDIT_CONFLICT', message: '订单已变化，请重新加载' } } })
  await conflict.view.handleSubmit()
  assert.equal(conflict.view.saveConflict.value, true)
  assert.equal(conflict.navigations.length, 0)
  await conflict.view.reloadAfterConflict()
  assert.equal(conflict.view.form.description, '原描述')
  assert.equal(conflict.view.formIsDirty.value, false)
  conflict.stop()
  console.log('PASS: cancelled orders are read-only and conflicts are visible with explicit reload')

  for (const status of ['PENDING_ASSIGNMENT', 'PENDING_VISIT', 'IN_PROGRESS', 'PENDING_REVIEW', 'REVIEWED']) {
    const allowed = editorFixture({ status })
    await allowed.mount()
    assert.equal(allowed.view.canChangeInstaller.value, true)
    await allowed.view.openMasterDialog()
    allowed.view.removeSelectedMaster(0)
    assert.equal(allowed.view.selectedMasterList.value.length, 0)
    allowed.view.handleMasterSelection([{ id: 8 }])
    await allowed.view.confirmSelectMaster()
    assert.equal(allowed.masterLoads.length, 1)
    assert.equal(allowed.saves.length, 0, 'selecting a different installer only changes the draft')
    await allowed.view.handleSubmit()
    assert.deepEqual([...allowed.saves[0].order.masterIds], [8])
    allowed.stop()
  }
  const cancelledReassignment = editorFixture({ status: 'CANCELLED', editable: false })
  await cancelledReassignment.mount()
  assert.equal(cancelledReassignment.view.canChangeInstaller.value, false)
  await cancelledReassignment.view.openMasterDialog()
  assert.equal(cancelledReassignment.masterLoads.length, 0)
  cancelledReassignment.stop()
  const changedState = editorFixture({ status: 'PENDING_VISIT' })
  await changedState.mount()
  await changedState.view.openMasterDialog()
  changedState.view.handleMasterSelection([{ id: 8 }])
  await changedState.view.confirmSelectMaster()
  changedState.data.order.statusCode = 'IN_PROGRESS'
  changedState.fail({ response: { status: 409, data: { error: 'ORDER_EDIT_CONFLICT', message: '订单已变化，请重新加载' } } })
  await changedState.view.handleSubmit()
  assert.equal(changedState.view.saveConflict.value, true)
  assert.equal(changedState.navigations.length, 0)
  await changedState.view.reloadAfterConflict()
  assert.equal(changedState.view.canChangeInstaller.value, true)
  assert.equal(changedState.view.masterDialogVisible.value, false)
  assert.deepEqual([...changedState.view.selectedMasterList.value.map(master => master.id)], [7])
  assert.equal(changedState.view.formIsDirty.value, false)
  changedState.stop()
  console.log('PASS: installer selection supports active states; rejected stale changes reload the current installer without saving early')

  const materialProps = vue.reactive({ modelValue: { items: [{ productId: 5, skuId: 9, quantity: 2, stock: 8 }], remark: '' },
    request: { materials: [{ skuId: 9, count: 2 }] }, disabled: false })
  const filterCalls = []
  const picker = fixture('OrderEditorMaterials', materialProps, {
    '@/api/consumables': { getConsumableCategoriesApi: async () => [{ id: 1, name: '材料' }, { id: 2, parentId: 1, name: '管材' }],
      getConsumablesListApi: async params => { filterCalls.push(params); return { total: 1, list: [{ id: 5, name: '铜管',
        skus: [{ id: 9, specLabel: '6mm', stock: 8, unit: '米', enabled: true }, { id: 10, specLabel: '8mm', stock: 4, enabled: true }] }] } } }
  })
  await picker.view.openPicker()
  assert.equal(picker.view.available({ skuId: 9, stock: 8 }), 10)
  assert.equal(picker.view.categories.value[0].children[0].id, 2)
  picker.view.keyword.value = '铜管'
  picker.view.categoryPath.value = [1, 2]
  await picker.view.search()
  assert.equal(filterCalls.at(-1).category, 2)
  assert.equal(filterCalls.at(-1).keyword, '铜管')
  picker.view.toggle(picker.view.skuRows.value[1], true)
  assert.equal(materialProps.modelValue.items.length, 1, 'picker changes remain local until confirmed')
  picker.view.confirm()
  assert.equal(materialProps.modelValue.items.length, 2)
  picker.stop()
  console.log('PASS: material picker searches, builds parent/child categories and selects concrete SKUs without early mutation')

  let uploadFail = true
  const fileProps = vue.reactive({ modelValue: [{ id: 12, url: '/old', previewUrl: '/thumb', uid: 12 }], disabled: false })
  const fileEditor = fixture('OrderEditorFiles', fileProps, {
    '@/api/files': { uploadFileApi: async (_, options) => { options.onUploadProgress({ loaded: 50, total: 100 });
      if (uploadFail) throw new Error('offline'); return { id: 13, url: '/new', thumbnailUrl: '/new-thumb' } } }
  })
  fileEditor.view.replacementIndex.value = 0
  await fileEditor.view.selectFiles({ target: { files: [{ name: 'site.png', type: 'image/png', size: 100 }], value: '' } })
  assert.equal(fileProps.modelValue[0].error, true)
  assert.equal(fileProps.modelValue[0].previous.id, 12)
  uploadFail = false
  await fileEditor.view.upload(fileProps.modelValue[0])
  assert.equal(fileProps.modelValue[0].id, 13)
  assert.equal(fileProps.modelValue[0].error, false)
  assert.equal(fileProps.modelValue[0].percent, 100)
  fileEditor.stop()
  console.log('PASS: image replacement shows progress/failure, retains the old draft and recovers on retry')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
