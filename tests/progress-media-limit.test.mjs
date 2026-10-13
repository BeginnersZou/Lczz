import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { ref, reactive, computed } = require('vue')
const source = readFileSync(new URL('../src/views/orders/OrderEditorFiles.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')

function editor(progress) {
  const props = reactive({ progress, modelValue: [], disabled: false }), messages = []
  const uploadFileApi = async () => ({ id: props.modelValue.length, url: 'https://file.test/image' })
  const state = new Function('defineProps', 'defineEmits', 'ref', 'reactive', 'computed', 'onBeforeUnmount', 'ElMessage', 'uploadFileApi',
    script + '\nreturn { selectFiles, chooseFiles, imageRemaining, input };')(
    () => props, () => (name, value) => { props.modelValue = value }, ref, reactive, computed, () => {},
    { warning: message => messages.push(message), error: message => messages.push(message) }, uploadFileApi)
  let opened = 0
  state.input.value = { click: () => opened++ }
  return { ...state, props, messages, opened: () => opened }
}
const images = count => Array.from({ length: count }, (_, index) => Object.assign(new Blob(['image'], { type: 'image/png' }), { name: `site-${index}.png` }))

test('PC progress editor accepts thirty images with existing videos and warns on the next image', async () => {
  const view = editor(true)
  view.props.modelValue = [{ id: 1, mimeType: 'video/mp4' }]
  await view.selectFiles({ target: { files: images(31), value: 'selected' } })
  assert.equal(view.props.modelValue.length, 31)
  assert.equal(view.imageRemaining.value, 0)
  assert.equal(view.messages.at(-1), '每条施工进度最多30张图片')
  view.chooseFiles()
  assert.equal(view.opened(), 0)
})

test('original order attachment editor still accepts at most nine attachments', async () => {
  const view = editor(false)
  await view.selectFiles({ target: { files: images(10), value: 'selected' } })
  assert.equal(view.props.modelValue.length, 9)
  assert.equal(view.messages.at(-1), '每组附件最多9个')
})
