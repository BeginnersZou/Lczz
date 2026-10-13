const fs = require('node:fs')
const assert = require('node:assert/strict')
const path = require('node:path')
const { createRequire } = require('node:module')
// Use the installed admin build toolchain only to verify the mini-program SFC.
const requireVue = createRequire(path.resolve(__dirname, '../frontend/admin/package.json'))
const { parse, compileScript, compileTemplate } = requireVue('@vue/compiler-sfc')
const vue = requireVue('vue')
const source = fs.readFileSync(path.resolve(__dirname, '../frontend/miniprogram/packageA/order-detail/order-detail.vue'), 'utf8')
function compiled(weixin) {
  const prepared = source.replace(/\/\/ #ifdef MP-WEIXIN([\s\S]*?)\/\/ #endif/g, (_, code) => weixin ? code : '')
    .replace(/\/\/ #ifndef MP-WEIXIN([\s\S]*?)\/\/ #endif/g, (_, code) => weixin ? '' : code)
  const { descriptor, errors } = parse(prepared)
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'issue99' })
  const template = compileTemplate({ source: descriptor.template.content, filename: 'order-detail.vue', id: 'issue99', compilerOptions: { bindingMetadata: script.bindings } })
  assert.deepEqual(template.errors, [])
  const imports = { 'vue': 'vue', '@dcloudio/uni-app': 'hooks', '@/api/api.js': 'api', '@/utils/auth-guard.js': 'guard' }
  let code = script.content.replace(/import\s*\{([\s\S]*?)\}\s*from\s*['"]([^'"]+)['"]/g,
    (_, names, name) => `const {${names}} = ${imports[name]};`)
  code = code.replace('export default', 'return')
  return new Function('vue', 'hooks', 'api', 'guard', 'uni', 'wx', code)
}
const componentFactory = compiled(true)
compiled(false)
function fixture(role = 'customer', statusCode = 'IN_PROGRESS', customerUserId = 3) {
  const state = { id: 99, customerUserId, statusCode, status: '处理中' }
  const calls = { confirm: [], progress: [], progressConfirm: [], materials: [], modals: [], toast: [] }
  const hook = {}
  let confirmReply, progressReply, detailReply, progressConfirmReply
  let records = [{ id: 1, description: '历史施工记录', contentVersion: 'a'.repeat(64), customerConfirmed: false, confirmationStatus: 'PENDING' }]
  const api = {
    authApi: { getUserInfo: async () => ({ code: 200, data: { id: 3, role } }) },
    orderApi: {
      getDetail: async () => detailReply || ({ code: 200, data: { ...state } }),
      getProgress: async () => ({ code: 200, data: records.map(item => ({ ...item })) }),
      getMaterials: async id => { calls.materials.push(id); return { code: 200, data: null } },
      confirmCompletion: async id => {
        calls.confirm.push(id)
        if (confirmReply) return await confirmReply()
        Object.assign(state, { statusCode: 'PENDING_REVIEW', status: '已完成', customerConfirmedAt: '2026-09-04 11:00:00' })
        return { code: 200, data: { ...state } }
      },
      submitProgress: async (id, data) => { calls.progress.push({ id, data }); return progressReply ? await progressReply() : { code: 200 } },
      confirmProgress: async (orderId, id, version) => {
        calls.progressConfirm.push({ orderId, id, version })
        if (progressConfirmReply) return progressConfirmReply()
        const record = records.find(item => item.id === id)
        Object.assign(record, { customerConfirmed: true, confirmationStatus: 'CONFIRMED', customerConfirmedAt: '2026-10-13 11:00:00' })
        return { code: 200, data: { ...record } }
      }
    }, consumablesApi: {
      getList: async () => ({ code: 200, data: { list: [{
        id: 8, title: 'PPR弯头', category: 'PPR管材',
        skus: [{ id: 18, specLabel: '25mm', unit: '个', stock: 12, enabled: true }]
      }] } })
    }, uploadApi: { deleteTemporary: () => {} }, resolveMediaUrl: value => value
  }
  const hooks = Object.fromEntries(['onLoad', 'onShow', 'onBackPress', 'onUnload'].map(name => [name, fn => { hook[name] = fn }]))
  const uni = { getSystemInfoSync: () => ({ windowHeight: 844 }), showModal: options => calls.modals.push(options), showToast: options => calls.toast.push(options) }
  const component = componentFactory(vue, hooks, api, { requireLogin: () => true }, uni, { getWindowInfo: uni.getSystemInfoSync })
  const view = component.setup({}, { expose() {} })
  view.orderId.value = '99'
  return { view, calls, state, hook, load: () => view.loadOrderDetails(), setConfirm: fn => { confirmReply = fn }, setProgress: fn => { progressReply = fn }, setDetail: response => { detailReply = response },
    setProgressConfirm: fn => { progressConfirmReply = fn }, setRecords: value => { records = value } }
}
const tick = () => new Promise(resolve => setImmediate(resolve))
async function main() {
  for (const role of ['customer', 'dealer', 'installer', 'admin']) {
    for (const state of ['PENDING_VISIT', 'IN_PROGRESS', 'PENDING_REVIEW', 'REVIEWED', 'CANCELLED']) {
      const test = fixture(role, state); await test.load()
      assert.equal(test.view.canConfirmCompletion.value, role === 'customer' && state === 'IN_PROGRESS')
      assert.equal(test.view.canOperateProgress.value, role === 'installer' && ['PENDING_VISIT', 'IN_PROGRESS'].includes(state))
      assert.equal(test.view.canReview.value, role === 'customer' && state === 'PENDING_REVIEW')
      assert.equal(test.calls.materials.length, role === 'installer' ? 1 : 0)
      if (!test.view.canConfirmCompletion.value) { await test.view.handleConfirmCompletion(); assert.equal(test.calls.modals.length, 0) }
    }
  }
  const unbound = fixture('customer', 'IN_PROGRESS', 7); await unbound.load()
  assert.equal(unbound.view.canConfirmCompletion.value, false)
  await unbound.view.handleConfirmCompletion(); assert.equal(unbound.calls.confirm.length, 0)
  assert.match(source, /class="section-card material-request-card" v-if="isInstaller"/)
  console.log('PASS: four roles across five states; only installers render and load order materials')

  const customer = fixture(); await customer.load()
  let action = customer.view.handleConfirmCompletion()
  assert.equal(customer.calls.modals[0].content, '是否确认订单已完成？确认后施工进度将封存，您可继续评价本次服务。')
  await customer.view.handleConfirmCompletion(); assert.equal(customer.calls.modals.length, 1)
  customer.calls.modals[0].success({ confirm: false }); await action
  assert.equal(customer.calls.confirm.length, 0); assert.equal(customer.view.confirmingCompletion.value, false)
  action = customer.view.handleConfirmCompletion()
  customer.calls.modals[1].success({ confirm: true }); await action
  assert.deepEqual(customer.calls.confirm, ['99'])
  assert.equal(customer.view.canConfirmCompletion.value, false)
  assert.equal(customer.view.canReview.value, true)
  assert.equal(customer.view.orderInfo.value.customerConfirmedAt, '2026-09-04 11:00:00')
  assert.equal(customer.view.progressRecords.value[0].description, '历史施工记录')
  console.log('PASS: exact confirmation prompt, cancel, double click, success, history and review entry')

  const network = fixture(); await network.load()
  network.setConfirm(async () => { throw Error('offline') })
  action = network.view.handleConfirmCompletion(); network.calls.modals[0].success({ confirm: true }); await action
  assert.equal(network.view.confirmingCompletion.value, false); assert.equal(network.view.canConfirmCompletion.value, true)
  assert.equal(network.calls.toast.at(-1).title, '确认失败，请刷新订单后重试')
  const conflict = fixture(); await conflict.load()
  conflict.setConfirm(async () => { conflict.state.statusCode = 'PENDING_REVIEW'; return { code: 409 } })
  action = conflict.view.handleConfirmCompletion(); conflict.calls.modals[0].success({ confirm: true }); await action
  assert.equal(conflict.view.canConfirmCompletion.value, false); assert.equal(conflict.view.canReview.value, true)
  conflict.setDetail({ code: 403 }); await conflict.hook.onShow(); assert.equal(conflict.view.detailError.value.title, '暂无访问权限')
  conflict.setDetail(null); await conflict.hook.onShow(); assert.equal(conflict.view.detailError.value, null)
  console.log('PASS: network failure, stale 409 reconciliation, page-show permission failure and recovery')

  const installer = fixture('installer'); await installer.load()
  assert.equal(installer.view.detailError.value, null)
  await installer.view.openToolPopup()
  assert.equal(installer.view.popupTools.value[0].title, 'PPR弯头')
  assert.equal(installer.view.popupTools.value[0].skus[0].id, 18)
  console.log('PASS: empty material request remains editable and selectable materials load')
  installer.view.progressDescription.value = '完成现场施工'
  action = installer.view.handleProgressSubmit()
  await installer.view.handleProgressSubmit(); assert.equal(installer.calls.modals.length, 1)
  installer.calls.modals[0].success({ confirm: true }); await action
  assert.equal(installer.calls.progress[0].data.description, '完成现场施工')
  assert.deepEqual(installer.calls.progress[0].data.fileIds, [])
  assert.ok(installer.calls.progress[0].data.requestId)
  assert.equal(installer.calls.confirm.length, 0)
  installer.view.progressDescription.value = '第二条'
  installer.setProgress(async () => { installer.state.statusCode = 'PENDING_REVIEW'; return { code: 409 } })
  action = installer.view.handleProgressSubmit(); installer.calls.modals[1].success({ confirm: true }); await action
  assert.equal(installer.view.canOperateProgress.value, false)
  await installer.view.handleProgressSubmit(); assert.equal(installer.calls.progress.length, 2)
  assert.equal(installer.view.hasUnsavedChanges.value, false)
  const modalRace = fixture('installer'); await modalRace.load(); modalRace.view.progressDescription.value = '待提交进度'
  action = modalRace.view.handleProgressSubmit(); modalRace.state.statusCode = 'PENDING_REVIEW'
  await modalRace.hook.onShow(); modalRace.calls.modals[0].success({ confirm: true }); await action
  assert.equal(modalRace.calls.progress.length, 0)
  console.log('PASS: installer progress-only submission, duplicate prevention, sealed-state refresh and modal race')
  const acknowledge = fixture(); await acknowledge.load()
  assert.equal(acknowledge.view.canConfirmCompletion.value, true) // Pending progress never blocks final completion.
  action = acknowledge.view.handleConfirmProgress(acknowledge.view.progressRecords.value[0])
  await acknowledge.view.handleConfirmProgress(acknowledge.view.progressRecords.value[0])
  assert.equal(acknowledge.calls.modals.length, 1)
  acknowledge.calls.modals[0].success({ confirm: false }); await action
  assert.equal(acknowledge.calls.progressConfirm.length, 0)
  action = acknowledge.view.handleConfirmProgress(acknowledge.view.progressRecords.value[0])
  acknowledge.calls.modals[1].success({ confirm: true }); await action
  assert.equal(acknowledge.calls.progressConfirm.length, 1)
  assert.equal(acknowledge.view.progressRecords.value[0].customerConfirmed, true)
  assert.equal(acknowledge.view.orderInfo.value.statusCode, 'IN_PROGRESS')
  for (const role of ['admin', 'installer', 'dealer']) {
    const denied = fixture(role); await denied.load()
    await denied.view.handleConfirmProgress(denied.view.progressRecords.value[0])
    assert.equal(denied.calls.modals.length, 0); assert.equal(denied.calls.progressConfirm.length, 0)
  }
  const changed = fixture(); await changed.load()
  changed.setProgressConfirm(async () => {
    changed.setRecords([{ id: 1, description: '新内容', contentVersion: 'b'.repeat(64), customerConfirmed: false, confirmationStatus: 'CHANGED' }])
    return { code: 409, msg: '内容已变更' }
  })
  action = changed.view.handleConfirmProgress(changed.view.progressRecords.value[0]); changed.calls.modals[0].success({ confirm: true }); await action
  assert.equal(changed.calls.progressConfirm[0].version, 'a'.repeat(64))
  assert.equal(changed.view.progressRecords.value[0].contentVersion, 'b'.repeat(64)); assert.equal(changed.view.progressRecords.value[0].customerConfirmed, false)
  const empty = fixture(); await empty.load(); empty.setProgressConfirm(async () => ({ code: 200 }))
  action = empty.view.handleConfirmProgress(empty.view.progressRecords.value[0]); empty.calls.modals[0].success({ confirm: true }); await action
  assert.equal(empty.view.progressRecords.value[0].customerConfirmed, false)
  assert.notEqual(empty.calls.toast.at(-1)?.icon, 'success')
  const retry = fixture('installer'); await retry.load(); retry.view.progressDescription.value = '同一条进度'
  retry.setProgress(async () => ({ code: -1 })); action = retry.view.handleProgressSubmit(); retry.calls.modals[0].success({ confirm: true }); await action
  const original = retry.calls.progress[0].data
  retry.setProgress(async () => ({ code: 200 })); action = retry.view.handleProgressSubmit(); retry.calls.modals[1].success({ confirm: true }); await action
  assert.deepEqual(retry.calls.progress[1].data, original); assert.equal(retry.view.progressRequest.value, null)
  assert.equal(retry.view.canOperateProgress.value, true)
  console.log('PASS: independent progress acknowledgement, secondary confirmation, version conflict, roles and identical retry payload')
  await tick()
  console.log('PASS: WeChat/non-WeChat SFC script and template compilation; actual Vue component logic')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
