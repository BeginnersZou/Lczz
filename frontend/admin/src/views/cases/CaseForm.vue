<template>
  <div class="case-form-page">
    <div class="page-header">
      <el-button plain :icon="ArrowLeft" @click="backToList">返回</el-button>
      <h2>{{ isEdit ? '修改项目案例' : '新增项目案例' }}</h2>
      <span></span>
    </div>

    <div v-if="loadError" class="error-state">
      <span>{{ loadError }}</span><el-button link type="primary" @click="loadDetail">重新加载</el-button>
    </div>
    <el-form v-else ref="formRef" v-loading="pageLoading" :model="form" :rules="rules" label-width="108px" class="case-form">
      <el-card shadow="never">
        <template #header><span class="card-title"><el-icon><Picture /></el-icon>案例信息</span></template>
        <el-form-item label="工地名称" prop="siteName">
          <el-input v-model="form.siteName" maxlength="255" show-word-limit placeholder="请输入工地名称" />
        </el-form-item>
        <el-form-item label="案例图片" prop="images" required>
          <div class="upload-tip">
            <el-icon><InfoFilled /></el-icon>
            <span>至少上传 1 张，可上传多张。支持 jpg、png、gif、webp，单张图片不超过 10MB。</span>
          </div>
          <div class="image-grid">
            <div v-for="(image, index) in form.images" :key="image.uid" class="image-card">
              <img :src="image.previewUrl" alt="案例图片" @error="image.failed = true" />
              <div v-if="image.failed" class="preview-error">图片加载失败</div>
              <div v-if="image.uploading" class="uploading-mask"><el-icon class="is-loading"><Loading /></el-icon><span>上传中</span></div>
              <button v-else type="button" class="remove-button" @click="removeImage(index)"><el-icon><Delete /></el-icon>删除</button>
            </div>
            <button type="button" class="upload-button" @click="openFilePicker">
              <el-icon :size="26"><Plus /></el-icon><span>上传图片</span>
              <input ref="fileInputRef" type="file" multiple accept="image/jpeg,image/png,image/gif,image/webp" @change="uploadImages" />
            </button>
          </div>
        </el-form-item>
      </el-card>

      <div class="form-actions">
        <el-button @click="backToList">取消</el-button>
        <el-button type="primary" :icon="Check" :loading="submitting" @click="submit">确认提交</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, Delete, InfoFilled, Loading, Picture, Plus } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createProjectCaseApi,
  getProjectCaseDetailApi,
  updateProjectCaseApi,
  uploadProjectCaseImageApi
} from '@/api/cases'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'

const MAX_IMAGE_BYTES = 10 * 1024 * 1024
const ALLOWED_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])

const route = useRoute()
const router = useRouter()
const formRef = ref()
const fileInputRef = ref()
const isEdit = ref(Boolean(route.params.id))
const pageLoading = ref(false)
const submitting = ref(false)
const loadError = ref('')
const dirty = ref(false)
let nextUid = 1

const form = reactive({ siteName: '', images: [] })
const rules = {
  siteName: [
    { required: true, message: '请输入工地名称', trigger: 'blur' },
    { max: 255, message: '工地名称不能超过255个字符', trigger: 'blur' }
  ],
  images: [{
    validator: (_rule, value, callback) => value.length ? callback() : callback(new Error('请至少上传1张案例图片')),
    trigger: 'change'
  }]
}

watch(form, () => { dirty.value = true }, { deep: true })
useUnsavedChanges(dirty, '项目案例尚未保存，确定要离开吗？')

function openFilePicker() {
  fileInputRef.value?.click()
}

async function uploadImages(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  for (const file of files) {
    if (!ALLOWED_TYPES.has(file.type)) {
      ElMessage.warning(`${file.name} 不是支持的图片格式`)
      continue
    }
    if (file.size > MAX_IMAGE_BYTES) {
      ElMessage.warning(`${file.name} 超过10MB，无法上传`)
      continue
    }
    const previewUrl = URL.createObjectURL(file)
    const image = { uid: nextUid++, id: null, previewUrl, uploading: true, failed: false }
    form.images.push(image)
    try {
      const data = new FormData()
      data.append('file', file)
      const uploaded = await uploadProjectCaseImageApi(data)
      Object.assign(image, { id: uploaded.id, previewUrl: uploaded.url, uploading: false })
      URL.revokeObjectURL(previewUrl)
    } catch {
      const index = form.images.indexOf(image)
      if (index !== -1) form.images.splice(index, 1)
      URL.revokeObjectURL(previewUrl)
      ElMessage.error(`${file.name} 上传失败，请重试`)
    } finally {
      formRef.value?.validateField('images').catch(() => {})
    }
  }
}

function removeImage(index) {
  const image = form.images[index]
  if (image?.previewUrl?.startsWith('blob:')) URL.revokeObjectURL(image.previewUrl)
  form.images.splice(index, 1)
  formRef.value?.validateField('images').catch(() => {})
}

async function loadDetail() {
  if (!isEdit.value) return
  pageLoading.value = true
  loadError.value = ''
  try {
    const data = await getProjectCaseDetailApi(route.params.id)
    form.siteName = data.siteName || ''
    form.images = (data.images || []).map(image => ({
      uid: nextUid++,
      id: image.id,
      previewUrl: image.url,
      uploading: false,
      failed: false
    }))
    dirty.value = false
  } catch {
    loadError.value = '项目案例详情加载失败，请重试。'
  } finally {
    pageLoading.value = false
  }
}

async function submit() {
  if (submitting.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.images.some(image => image.uploading || !image.id)) {
    ElMessage.warning('图片正在上传，请稍候')
    return
  }
  submitting.value = true
  try {
    const payload = { siteName: form.siteName, imageFileIds: form.images.map(image => image.id) }
    if (isEdit.value) {
      await updateProjectCaseApi(route.params.id, payload)
      ElMessage.success('项目案例已修改')
    } else {
      await createProjectCaseApi(payload)
      ElMessage.success('项目案例已创建')
    }
    dirty.value = false
    router.replace({ name: 'ProjectCases' })
  } catch {
    // 全局请求拦截器已提示失败原因。
  } finally {
    submitting.value = false
  }
}

function backToList() {
  router.push({ name: 'ProjectCases' })
}

onMounted(loadDetail)
</script>

<style lang="scss" scoped>
.case-form-page { min-height: calc(100vh - 112px); }
.page-header { display: grid; grid-template-columns: 150px 1fr 150px; align-items: center; margin-bottom: 18px; }
.page-header h2 { margin: 0; text-align: center; color: #172033; font-size: 22px; }
.case-form { max-width: 980px; margin: 0 auto; }
.card-title { display: inline-flex; align-items: center; gap: 8px; font-weight: 600; }
.upload-tip { display: flex; gap: 7px; align-items: center; width: 100%; margin-bottom: 12px; color: #64748b; font-size: 13px; }
.image-grid { display: flex; flex-wrap: wrap; gap: 12px; }
.image-card, .upload-button { position: relative; display: flex; width: 136px; height: 136px; box-sizing: border-box; overflow: hidden; border-radius: 8px; }
.image-card { background: #f1f5f9; }
.image-card img { width: 100%; height: 100%; object-fit: cover; }
.preview-error { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #64748b; background: #f8fafc; font-size: 13px; }
.uploading-mask { position: absolute; inset: 0; display: flex; flex-direction: column; gap: 8px; align-items: center; justify-content: center; color: #fff; background: rgba(15, 23, 42, .64); }
.remove-button { position: absolute; right: 0; bottom: 0; display: inline-flex; align-items: center; gap: 3px; padding: 7px 9px; color: #fff; border: 0; background: rgba(185, 28, 28, .88); cursor: pointer; }
.upload-button { flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #2563eb; border: 1px dashed #93c5fd; background: #eff6ff; cursor: pointer; }
.upload-button input { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; padding: 20px 0; }
.error-state { display: flex; gap: 8px; align-items: center; padding: 28px; color: #b45309; background: #fff; border: 1px solid #fde68a; border-radius: 8px; }
@media (max-width: 640px) { .page-header { grid-template-columns: 100px 1fr 100px; } .case-form :deep(.el-form-item__label) { width: 88px !important; } .case-form :deep(.el-form-item__content) { margin-left: 88px !important; } }
</style>
