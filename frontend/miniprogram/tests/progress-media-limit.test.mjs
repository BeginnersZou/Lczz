import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const source = readFileSync(new URL('../packageA/utils/progress-media.js', import.meta.url), 'utf8')
const helpers = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
const pageSource = readFileSync(new URL('../packageA/order-detail/order-detail.vue', import.meta.url), 'utf8')
const require = createRequire(new URL('../../admin/package.json', import.meta.url))
const { ref, computed } = require('vue')
const pageMethods = pageSource.slice(pageSource.indexOf('\tconst createProgressMediaDraft ='), pageSource.indexOf('\tconst hasUnsavedChanges ='))

function page() {
  const calls = { picks: [], toasts: [], submits: [], uploads: [], refresh: 0 }
  const progressImages = ref([]), uploadProgress = ref(''), submittingProgress = ref(false), progressDescription = ref('安装完成')
  let failNext = false
  const canOperateProgress = ref(true)
  const isUploadingProgressMedia = computed(() => progressImages.value.some(item => ['queued', 'uploading'].includes(item.status)))
  const failedProgressMediaCount = computed(() => progressImages.value.filter(item => item.status === 'failed').length)
  const uploadedProgressMediaCount = computed(() => progressImages.value.filter(item => item.status === 'success').length)
  const canSubmitProgress = computed(() => progressDescription.value.trim() && !isUploadingProgressMedia.value && !failedProgressMediaCount.value)
  const state = { ...helpers, progressImages, uploadProgress, submittingProgress, progressDescription, canOperateProgress,
    isUploadingProgressMedia, failedProgressMediaCount, uploadedProgressMediaCount, canSubmitProgress,
    hasMaterialDraft: ref(false), orderId: ref(170), resolveMediaUrl: value => value,
    refreshOrderStatus: async () => { calls.refresh++ },
    orderApi: {
      uploadMedia: async path => {
        calls.uploads.push(path)
        if (failNext) { failNext = false; return { code: 500, msg: '模拟失败' } }
        return { code: 200, data: { id: calls.uploads.length, url: 'https://files.test/' + path } }
      },
      submitProgress: async (id, body) => { calls.submits.push({ id, body }); return { code: 200 } }
    },
    uni: { chooseMedia: options => calls.picks.push(options), showToast: options => calls.toasts.push(options.title),
      showModal: options => options.success({ confirm: true }), showActionSheet: () => {} }
  }
  const methods = new Function(...Object.keys(state), 'let progressMediaUid = 0;\n' + pageMethods + '\nreturn { openProgressMediaPicker, retryProgressMedia, handleProgressSubmit };')(...Object.values(state))
  return { ...methods, progressImages, calls, failUpload: () => { failNext = true } }
}
const selected = (count, offset = 0) => Array.from({ length: count }, (_, index) => ({ fileType: 'image', tempFilePath: `site-${offset + index}.jpg`, size: 1000 }))

test('four batches retain all thirty photos and submit IDs in upload order', async () => {
  const view = page()
  for (const [index, count] of [9, 9, 9, 3].entries()) {
    view.openProgressMediaPicker('album', 'image')
    const picker = view.calls.picks.at(-1)
    assert.equal(picker.count, count)
    assert.deepEqual(picker.mediaType, ['image'])
    await picker.success({ tempFiles: selected(count, index * 9) })
  }
  assert.equal(view.progressImages.value.length, 30)
  assert.ok(view.progressImages.value.every(item => item.status === 'success'))
  view.openProgressMediaPicker('album', 'image')
  assert.equal(view.calls.picks.length, 4)
  assert.equal(view.calls.toasts.at(-1), '每条施工进度最多30张图片')
  await view.handleProgressSubmit()
  assert.deepEqual(view.calls.submits[0].body.fileIds, Array.from({ length: 30 }, (_, index) => index + 1))
  assert.equal(view.progressImages.value.length, 0)
  assert.equal(view.calls.refresh, 1)
})

test('oversized picker results are capped at thirty and a tampered submission is blocked', async () => {
  const view = page()
  view.progressImages.value = Array.from({ length: 29 }, (_, id) => ({ id: id + 1, mimeType: 'image/png', status: 'success' }))
  view.openProgressMediaPicker('album', 'image')
  await view.calls.picks.at(-1).success({ tempFiles: selected(2) })
  assert.equal(view.progressImages.value.length, 30)
  assert.equal(view.calls.uploads.length, 1)
  assert.ok(view.calls.toasts.includes('每条施工进度最多30张图片'))
  view.progressImages.value.push({ id: 99, mimeType: 'image/png', status: 'success' })
  await view.handleProgressSubmit()
  assert.equal(view.calls.submits.length, 0)
  assert.equal(view.calls.toasts.at(-1), '每条施工进度最多30张图片')
})

test('failed drafts reserve slots, block premature submit and retry without losing other images', async () => {
  const view = page()
  view.failUpload()
  view.openProgressMediaPicker('album', 'image')
  await view.calls.picks.at(-1).success({ tempFiles: selected(2) })
  const failed = view.progressImages.value[0], other = view.progressImages.value[1]
  assert.equal(failed.status, 'failed')
  assert.equal(helpers.progressMediaRemaining(view.progressImages.value), 28)
  await view.handleProgressSubmit()
  assert.equal(view.calls.submits.length, 0)
  await view.retryProgressMedia(failed)
  assert.equal(failed.status, 'success')
  assert.equal(view.progressImages.value[1].uid, other.uid)
  await view.handleProgressSubmit()
  assert.equal(view.calls.submits[0].body.fileIds.length, 2)
})

test('photo and video quotas are independent and include all draft states', () => {
  const mixed = [...selected(30).map(file => ({ ...file, status: 'queued' })), ...Array.from({ length: 9 }, () => ({ mimeType: 'video/mp4', status: 'failed' }))]
  assert.deepEqual(helpers.progressMediaCounts(mixed), { images: 30, videos: 9 })
  assert.equal(helpers.progressMediaLimitMessage(mixed), '')
  assert.equal(helpers.progressMediaRemaining(mixed, 'video'), 0)
  assert.equal(helpers.progressMediaLimitMessage([...mixed, { mimeType: 'video/mp4' }]), '每条施工进度最多9个视频')
  assert.equal(helpers.progressMediaRemaining([{ mimeType: 'video/mp4' }]), 30)
})

test('updated upload counters and administrator controls compile in the real order page', () => {
  const { parse, compileTemplate } = require('@vue/compiler-sfc')
  const parsed = parse(pageSource)
  assert.deepEqual(parsed.errors, [])
  assert.deepEqual(compileTemplate({ source: parsed.descriptor.template.content, filename: 'order-detail.vue', id: 'issue-170' }).errors, [])
})
