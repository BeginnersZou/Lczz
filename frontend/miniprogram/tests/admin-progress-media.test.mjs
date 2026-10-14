import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { ref } from 'vue'

const apiSource = readFileSync(new URL('../api/api.js', import.meta.url), 'utf8')
const pageUrl = new URL('../packageA/order-detail/order-detail.vue', import.meta.url)
const pageSource = readFileSync(pageUrl, 'utf8')

test('admin replaces one record through the versioned progress API', () => {
  assert.match(apiSource, /replaceProgressForAdmin: async \(id, progressId, data/)
  assert.match(apiSource, /http\.put\(`\/orders\/\$\{id\}\/progress\/\$\{progressId\}`/)
  assert.match(pageSource, /contentVersion: adminDraftVersion\.value/)
  assert.match(pageSource, /progressRecords\.value = progressRecords\.value\.map\(item => item\.id === record\.id/)
})

test('only admin receives per-record text, photo, and video correction controls', () => {
  assert.match(pageSource, /const isAdmin = computed\(\(\) => userRole\.value === 'admin'\)/)
  assert.match(pageSource, /v-if="isAdmin && orderInfo\.statusCode !== 'CANCELLED'"/)
  assert.match(pageSource, /adminEditingProgressId === record\.id/)
  assert.match(pageSource, /v-model="adminDraftDescription"/)
  assert.match(pageSource, /chooseAdminProgressImages\(record\)/)
  assert.match(pageSource, /chooseAdminProgressVideos\(record\)/)
  assert.match(pageSource, /removeAdminProgressImage\(record, media\)/)
  assert.match(pageSource, /progressMediaLimitMessage\(adminDraftMedia\.value\)/)
  assert.match(pageSource, /record\.confirmationStatus !== 'ADMIN_EDITED'/)
})

test('order detail page compiles with admin media actions', () => {
  const require = createRequire(new URL('../../admin/package.json', import.meta.url))
  const { parse, compileTemplate } = require('@vue/compiler-sfc')
  const parsed = parse(pageSource, { filename: pageUrl.pathname })
  assert.deepEqual(parsed.errors, [])
  const template = compileTemplate({
    source: parsed.descriptor.template.content,
    filename: pageUrl.pathname,
    id: 'issue-156'
  })
  assert.deepEqual(template.errors, [])
})

test('admin edits the selected upload only, including text, photo and video; cancellation cleans temporary files', async () => {
  const helpers = new Function(readFileSync(new URL('../packageA/utils/progress-media.js', import.meta.url), 'utf8').replace(/export /g, '')
    + '\nreturn { PROGRESS_PICKER_BATCH, progressMediaRemaining, progressMediaLimitMessage, acceptProgressMedia }')()
  const records = ref([
    { id: 1, description: '第一次', media: [], contentVersion: 'a'.repeat(64) },
    { id: 2, description: '第二次', media: [{ id: 8, mimeType: 'image/png', url: 'old' }], contentVersion: 'b'.repeat(64) },
    { id: 3, description: '第三次', media: [], contentVersion: 'c'.repeat(64) }
  ])
  const writes = [], deleted = [], pickers = []
  let nextId = 40
  const uploadApi = {
    uploadImage: async path => ({ code: 200, data: { id: nextId++, mimeType: path.endsWith('.mp4') ? 'video/mp4' : 'image/png', url: path } }),
    deleteTemporary: id => deleted.push(id)
  }
  const orderApi = { replaceProgressForAdmin: async (orderId, id, body) => {
    writes.push({ orderId, id, body })
    return { code: 200, data: { id, description: body.description, media: body.fileIds.map(fileId => ({ id: fileId })), confirmationStatus: 'ADMIN_EDITED' } }
  } }
  const uni = { chooseMedia: options => pickers.push(options), showToast: () => {}, previewImage: () => {} }
  const block = pageSource.slice(pageSource.indexOf('\tconst adminProgressBusyId = ref(null)'), pageSource.indexOf('\tconst goEvaluate = () =>'))
  assert.ok(block.length > 1000)
  const editor = new Function('ref', 'isAdmin', 'orderInfo', 'orderId', 'progressRecords', 'uploadApi', 'orderApi', 'uni',
    'progressMediaRemaining', 'progressMediaLimitMessage', 'acceptProgressMedia', 'PROGRESS_PICKER_BATCH',
    block + '\nreturn { beginAdminProgressEdit, cancelAdminProgressEdit, chooseAdminProgressImages, chooseAdminProgressVideos, removeAdminProgressImage, saveAdminProgressEdit, adminDraftDescription, adminDraftMedia, adminEditingProgressId }')(
      ref, ref(true), ref({ statusCode: 'PENDING_REVIEW' }), ref('99'), records, uploadApi, orderApi, uni,
      helpers.progressMediaRemaining, helpers.progressMediaLimitMessage, helpers.acceptProgressMedia, helpers.PROGRESS_PICKER_BATCH)
  const target = records.value[1]
  editor.beginAdminProgressEdit(target)
  editor.adminDraftDescription.value = '管理员覆盖第二次'
  editor.removeAdminProgressImage(target, target.media[0])
  editor.chooseAdminProgressImages(target)
  await pickers.at(-1).success({ tempFiles: [{ tempFilePath: 'new.png', size: 100 }] })
  editor.chooseAdminProgressVideos(target)
  await pickers.at(-1).success({ tempFiles: [{ tempFilePath: 'new.mp4', size: 100, fileType: 'video' }] })
  await editor.saveAdminProgressEdit(target)
  assert.deepEqual(writes, [{ orderId: '99', id: 2,
    body: { description: '管理员覆盖第二次', fileIds: [40, 41], contentVersion: 'b'.repeat(64) } }])
  assert.equal(records.value[0].description, '第一次')
  assert.equal(records.value[1].description, '管理员覆盖第二次')
  assert.equal(records.value[2].description, '第三次')
  assert.equal(editor.adminEditingProgressId.value, null)
  assert.deepEqual(deleted, [])
  editor.beginAdminProgressEdit(records.value[2])
  editor.chooseAdminProgressImages(records.value[2])
  await pickers.at(-1).success({ tempFiles: [{ tempFilePath: 'cancel.png', size: 100 }] })
  editor.cancelAdminProgressEdit()
  assert.deepEqual(deleted, [42])
  assert.equal(writes.length, 1)
})
