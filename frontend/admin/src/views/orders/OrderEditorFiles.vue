<template>
  <div class="editor-files">
    <p class="upload-tip">jpg/png/gif/webp，每张最多 10 MB，最多 9 个附件；图片变更将在确认提交后保存。</p>
    <div class="file-grid">
      <div v-for="(file, index) in modelValue" :key="file.uid || file.id" class="file-tile">
        <video v-if="isVideo(file)" :src="file.url || file.originalUrl" controls preload="metadata" />
        <el-image v-else :src="file.previewUrl || file.thumbnailUrl || file.url" fit="cover"
          :preview-src-list="previewUrls" :initial-index="imageIndex(index)" preview-teleported />
        <div v-if="file.uploading" class="file-state">上传中 {{ file.percent || 0 }}%</div>
        <div v-else-if="file.error" class="file-state is-error">
          上传失败
          <el-button link type="danger" :disabled="disabled" @click="upload(file)">重试</el-button>
          <el-button v-if="file.previous" link :disabled="disabled" @click="cancelReplacement(file)">取消替换</el-button>
        </div>
        <div v-if="!disabled && !file.uploading" class="file-actions">
          <template v-if="!isVideo(file)">
            <el-button link type="primary" @click="chooseReplacement(index)">替换</el-button>
            <el-button link type="danger" @click="remove(index)">删除</el-button>
          </template>
          <el-button link :disabled="index === 0" @click="move(index, -1)">前移</el-button>
          <el-button link :disabled="index === modelValue.length - 1" @click="move(index, 1)">后移</el-button>
        </div>
        <span v-if="isVideo(file)" class="muted">已有视频</span>
      </div>
      <el-button v-if="!disabled && modelValue.length < 9" class="upload-add" @click="chooseFiles">添加图片</el-button>
    </div>
    <input ref="input" type="file" hidden :multiple="replacementIndex === null"
      accept="image/jpeg,image/png,image/gif,image/webp" @change="selectFiles" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadFileApi } from '@/api/files'

const props = defineProps({ modelValue: { type: Array, default: () => [] }, disabled: Boolean })
const emit = defineEmits(['update:modelValue'])
const input = ref(null)
const replacementIndex = ref(null)
const localUrls = new Set()
let sequence = 0
let disposed = false
const isVideo = file => String(file.mimeType || '').startsWith('video/')
const previewUrls = computed(() => props.modelValue.filter(file => !isVideo(file)).map(file => file.url || file.originalUrl || file.previewUrl))
const imageIndex = index => props.modelValue.slice(0, index).filter(file => !isVideo(file)).length
const chooseFiles = () => { replacementIndex.value = null; input.value?.click() }
const chooseReplacement = index => { replacementIndex.value = index; input.value?.click() }

function release(file) {
  if (file?.previewUrl?.startsWith('blob:')) {
    URL.revokeObjectURL(file.previewUrl)
    localUrls.delete(file.previewUrl)
  }
}
function remove(index) {
  release(props.modelValue[index])
  emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))
}
function move(index, direction) {
  const next = [...props.modelValue]
  const target = index + direction
  if (target < 0 || target >= next.length) return
  ;[next[index], next[target]] = [next[target], next[index]]
  emit('update:modelValue', next)
}
function cancelReplacement(file) {
  const previous = file.previous
  release(file)
  Object.keys(file).forEach(key => delete file[key])
  Object.assign(file, previous)
}
async function selectFiles(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (props.disabled) return
  const replacing = replacementIndex.value
  const remaining = replacing === null ? 9 - props.modelValue.length : 1
  const valid = []
  for (const file of files) {
    if (!['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)) {
      ElMessage.warning(`${file.name} 仅支持 jpg/png/gif/webp 图片`)
    } else if (file.size > 10 * 1024 * 1024) {
      ElMessage.warning(`${file.name} 超过 10 MB`)
    } else if (valid.length < remaining) valid.push(file)
    else ElMessage.warning('每组附件最多 9 个')
  }
  if (!valid.length) return
  const drafts = valid.map(file => {
    const previewUrl = URL.createObjectURL(file)
    localUrls.add(previewUrl)
    return reactive({ uid: `draft-${++sequence}`, file, previewUrl, url: '', uploading: true, percent: 0, error: false, mimeType: file.type })
  })
  if (replacing !== null) {
    const previous = props.modelValue[replacing]
    if (!previous) return
    drafts[0].previous = { ...previous }
    emit('update:modelValue', props.modelValue.map((file, index) => index === replacing ? drafts[0] : file))
  } else emit('update:modelValue', [...props.modelValue, ...drafts])
  await Promise.all(drafts.map(upload))
}
async function upload(item) {
  item.uploading = true
  item.error = false
  item.percent = 0
  try {
    const body = new FormData()
    body.append('file', item.file)
    const result = await uploadFileApi(body, { onUploadProgress: event => {
      if (event.total) item.percent = Math.round(event.loaded * 100 / event.total)
    } })
    if (disposed) return
    if (!result?.id) throw new Error('上传响应缺少文件标识')
    release(item)
    Object.assign(item, { id: result.id, url: result.originalUrl || result.url,
      previewUrl: result.thumbnailUrl || result.displayUrl || result.url, file: null, previous: null, bound: false, percent: 100 })
  } catch {
    item.error = true
    ElMessage.error('图片上传失败，请重试、取消替换或删除失败图片后再提交')
  } finally {
    item.uploading = false
  }
}
onBeforeUnmount(() => {
  disposed = true
  for (const url of localUrls) URL.revokeObjectURL(url)
  localUrls.clear()
})
</script>

<style scoped>
.upload-tip, .muted { color: #64748b; font-size: 13px; line-height: 1.6; }
.file-grid { display: flex; flex-wrap: wrap; gap: 12px; }
.file-tile { width: 150px; border: 1px solid #dce3ec; border-radius: 8px; padding: 8px; }
.el-image, video { display: block; width: 100%; height: 116px; border-radius: 4px; }
.file-actions { display: flex; flex-wrap: wrap; gap: 6px; padding-top: 6px; }
.file-actions .el-button + .el-button { margin-left: 0; }
.file-state { padding-top: 8px; font-size: 13px; color: #2563eb; }
.is-error { color: #dc2626; }
.upload-add { align-self: flex-start; height: 80px; }
</style>
