<template>
  <div class="service-page">
    <div class="page-heading">
      <div>
        <h1>服务管理</h1>
        <p>编辑小程序“服务”页面内容，保存后立即发布。</p>
      </div>
      <el-button type="primary" size="large" :icon="Promotion" :loading="saving" @click="save">
        保存并发布
      </el-button>
    </div>

    <div v-if="loadError" class="load-error">
      <span>{{ loadError }}</span>
      <el-button link type="primary" @click="load">重新加载</el-button>
    </div>

    <el-form v-else ref="formRef" v-loading="loading" :model="form" :rules="rules" label-position="top">
      <el-card shadow="never" class="section-card">
        <template #header><SectionHeader title="品牌基础信息" v-model="form.brandVisible" /></template>
        <div class="form-grid three">
          <el-form-item label="公司简称" prop="companyName"><el-input v-model="form.companyName" maxlength="100" /></el-form-item>
          <el-form-item label="公司副标题" prop="companySubtitle"><el-input v-model="form.companySubtitle" maxlength="100" /></el-form-item>
          <el-form-item label="品牌标语" prop="slogan"><el-input v-model="form.slogan" maxlength="200" /></el-form-item>
        </div>
        <div class="sub-heading"><span>头部数据</span><small>用于服务页顶部品牌数据展示</small></div>
        <EditableList v-model="form.heroStats" title-placeholder="例如：一站式" description-placeholder="例如：服务体验" :min="1" :max="6" />
      </el-card>

      <el-card shadow="never" class="section-card">
        <template #header><SectionHeader title="服务项目" v-model="form.servicesVisible" /></template>
        <EditableList v-model="form.services" title-placeholder="服务名称" description-placeholder="服务说明" :min="1" :max="12" multiline />
      </el-card>

      <el-card shadow="never" class="section-card">
        <template #header><SectionHeader title="公司简介" v-model="form.profileVisible" /></template>
        <el-form-item label="简介内容" prop="profileText">
          <el-input v-model="form.profileText" type="textarea" :rows="5" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="特色标签">
          <el-select v-model="form.profileTags" multiple filterable allow-create default-first-option
                     :multiple-limit="12" placeholder="输入标签后按回车添加" style="width:100%" />
        </el-form-item>
      </el-card>

      <el-card shadow="never" class="section-card">
        <template #header><SectionHeader title="公司展示" v-model="form.galleryVisible" /></template>
        <div class="upload-tip">
          支持 jpg、png、gif、webp；单张不超过 10MB，最多 20 张。拖动图片可调整小程序展示顺序。
        </div>
        <div class="image-grid">
          <div v-for="(image, index) in form.galleryImages" :key="image.uid || image.id"
               class="image-card" draggable="true"
               @dragstart="dragIndex = index" @dragover.prevent @drop="dropImage(index)">
            <el-image :src="image.url" fit="cover" :preview-src-list="previewImages" :initial-index="index" preview-teleported />
            <div class="image-order">{{ index + 1 }}</div>
            <div v-if="image.uploading" class="upload-mask"><el-icon class="is-loading"><Loading /></el-icon></div>
            <button v-else type="button" class="delete-image" aria-label="删除图片" @click="removeImage(index)">
              <el-icon><Delete /></el-icon>
            </button>
          </div>
          <button v-if="form.galleryImages.length < 20" type="button" class="upload-button" @click="fileInput?.click()">
            <el-icon :size="28"><Plus /></el-icon><span>上传图片</span>
            <input ref="fileInput" type="file" multiple accept="image/jpeg,image/png,image/gif,image/webp" @change="uploadImages" />
          </button>
        </div>
        <el-empty v-if="!form.galleryImages.length" :image-size="70" description="暂未上传公司展示图片" />
      </el-card>

      <el-card shadow="never" class="section-card">
        <template #header><SectionHeader title="公司优势" v-model="form.advantagesVisible" /></template>
        <EditableList v-model="form.advantages" title-placeholder="优势名称" description-placeholder="优势说明" :min="1" :max="12" multiline />
      </el-card>

      <el-card shadow="never" class="section-card">
        <template #header><SectionHeader title="联系我们" v-model="form.contactVisible" /></template>
        <div class="form-grid two">
          <el-form-item label="服务热线一" prop="phonePrimary"><el-input v-model="form.phonePrimary" maxlength="32" /></el-form-item>
          <el-form-item label="服务热线二"><el-input v-model="form.phoneSecondary" maxlength="32" placeholder="选填" /></el-form-item>
          <el-form-item label="公司地址" prop="address" class="wide"><el-input v-model="form.address" maxlength="500" /></el-form-item>
          <el-form-item label="经度" prop="longitude"><el-input-number v-model="form.longitude" :min="-180" :max="180" :precision="6" controls-position="right" /></el-form-item>
          <el-form-item label="纬度" prop="latitude"><el-input-number v-model="form.latitude" :min="-90" :max="90" :precision="6" controls-position="right" /></el-form-item>
          <el-form-item label="营业时间" prop="businessHours" class="wide"><el-input v-model="form.businessHours" maxlength="255" /></el-form-item>
        </div>
      </el-card>

      <div class="bottom-actions">
        <span v-if="lastSavedAt">上次保存：{{ lastSavedAt }}</span>
        <el-button type="primary" size="large" :icon="Promotion" :loading="saving" @click="save">保存并发布</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElButton, ElInput, ElMessage, ElSwitch } from 'element-plus'
import { ArrowDown, ArrowUp, Delete, Loading, Plus, Promotion } from '@element-plus/icons-vue'
import { getServicePageApi, updateServicePageApi, uploadServiceImageApi } from '@/api/servicePage'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'

const MAX_IMAGE_BYTES = 10 * 1024 * 1024
const ALLOWED_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const formRef = ref()
const fileInput = ref()
const dirty = ref(false)
const lastSavedAt = ref('')
const dragIndex = ref(-1)
let uid = 1
let hydrating = false

const emptyForm = () => ({
  companyName: '', companySubtitle: '', slogan: '', brandVisible: true,
  heroStats: [], servicesVisible: true, services: [], profileText: '', profileVisible: true,
  profileTags: [], galleryVisible: true, galleryImages: [], advantagesVisible: true,
  advantages: [], contactVisible: true, phonePrimary: '', phoneSecondary: '', address: '',
  longitude: 114.306997, latitude: 30.665673, businessHours: ''
})
const form = reactive(emptyForm())
const rules = {
  companyName: [{ required: true, message: '请输入公司简称', trigger: 'blur' }],
  companySubtitle: [{ required: true, message: '请输入公司副标题', trigger: 'blur' }],
  slogan: [{ required: true, message: '请输入品牌标语', trigger: 'blur' }],
  profileText: [{ required: true, message: '请输入公司简介', trigger: 'blur' }],
  phonePrimary: [{ required: true, message: '请输入服务热线', trigger: 'blur' }],
  address: [{ required: true, message: '请输入公司地址', trigger: 'blur' }],
  longitude: [{ required: true, message: '请输入经度', trigger: 'change' }],
  latitude: [{ required: true, message: '请输入纬度', trigger: 'change' }],
  businessHours: [{ required: true, message: '请输入营业时间', trigger: 'blur' }]
}
const previewImages = computed(() => form.galleryImages.map(item => item.url))

const SectionHeader = defineComponent({
  props: { title: String, modelValue: Boolean }, emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h('div', { class: 'section-header' }, [
      h('span', props.title),
      h('div', { class: 'visibility-control' }, [
        h('small', props.modelValue ? '小程序显示' : '小程序隐藏'),
        h(ElSwitch, { modelValue: props.modelValue, 'onUpdate:modelValue': value => emit('update:modelValue', value) })
      ])
    ])
  }
})

const EditableList = defineComponent({
  props: {
    modelValue: { type: Array, required: true }, titlePlaceholder: String, descriptionPlaceholder: String,
    min: { type: Number, default: 0 }, max: { type: Number, default: 12 }, multiline: Boolean
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    const change = list => emit('update:modelValue', list)
    const add = () => change([...props.modelValue, { title: '', description: '' }])
    const remove = index => change(props.modelValue.filter((_, i) => i !== index))
    const move = (index, offset) => {
      const list = [...props.modelValue]
      const target = index + offset
      if (target < 0 || target >= list.length) return
      ;[list[index], list[target]] = [list[target], list[index]]
      change(list)
    }
    return () => h('div', { class: 'editable-list' }, [
      ...props.modelValue.map((item, index) => h('div', { class: 'editable-row', key: index }, [
        h('div', { class: 'row-number' }, String(index + 1)),
        h(ElInput, { modelValue: item.title, maxlength: 255, placeholder: props.titlePlaceholder,
          'onUpdate:modelValue': value => { item.title = value } }),
        h(ElInput, { modelValue: item.description, maxlength: 1000, placeholder: props.descriptionPlaceholder,
          type: props.multiline ? 'textarea' : 'text', rows: 2,
          'onUpdate:modelValue': value => { item.description = value } }),
        h('div', { class: 'row-actions' }, [
          h(ElButton, { icon: ArrowUp, circle: true, disabled: index === 0, onClick: () => move(index, -1) }),
          h(ElButton, { icon: ArrowDown, circle: true, disabled: index === props.modelValue.length - 1, onClick: () => move(index, 1) }),
          h(ElButton, { icon: Delete, circle: true, type: 'danger', plain: true,
            disabled: props.modelValue.length <= props.min, onClick: () => remove(index) })
        ])
      ])),
      h(ElButton, { icon: Plus, plain: true, type: 'primary', disabled: props.modelValue.length >= props.max, onClick: add }, () => '新增一项')
    ])
  }
})

watch(form, () => { if (!hydrating) dirty.value = true }, { deep: true })
useUnsavedChanges(dirty, '服务页内容尚未保存，确定要离开吗？')

async function assignForm(data) {
  hydrating = true
  Object.assign(form, emptyForm(), data, {
    galleryImages: (data.galleryImages || []).map(image => ({ ...image, uid: uid++, uploading: false }))
  })
  lastSavedAt.value = data.updatedAt ? new Date(data.updatedAt).toLocaleString('zh-CN', { hour12: false }) : ''
  await nextTick()
  dirty.value = false
  hydrating = false
}

async function load() {
  loading.value = true
  loadError.value = ''
  try { await assignForm(await getServicePageApi()) }
  catch { loadError.value = '服务页配置加载失败，请重试。' }
  finally { loading.value = false }
}

function validateLists() {
  const groups = [['头部数据', form.heroStats], ['服务项目', form.services], ['公司优势', form.advantages]]
  for (const [name, items] of groups) {
    if (!items.length || items.some(item => !item.title.trim())) {
      ElMessage.warning(`${name}至少保留一项，并填写完整名称`)
      return false
    }
  }
  if (form.profileTags.some(tag => !String(tag).trim())) {
    ElMessage.warning('公司简介标签不能为空')
    return false
  }
  return true
}

async function save() {
  if (saving.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !validateLists()) return
  if (form.galleryImages.some(image => image.uploading || !image.id)) {
    ElMessage.warning('图片仍在上传，请稍候')
    return
  }
  saving.value = true
  try {
    await assignForm(await updateServicePageApi(form))
    ElMessage.success('服务页已保存并发布')
  } catch {
    // 请求层已展示具体原因。
  } finally { saving.value = false }
}

async function uploadImages(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  const remaining = 20 - form.galleryImages.length
  if (files.length > remaining) ElMessage.warning(`最多还能上传 ${remaining} 张图片`)
  for (const file of files.slice(0, remaining)) {
    if (!ALLOWED_TYPES.has(file.type)) { ElMessage.warning(`${file.name} 不是支持的图片格式`); continue }
    if (file.size > MAX_IMAGE_BYTES) { ElMessage.warning(`${file.name} 超过 10MB`); continue }
    const blobUrl = URL.createObjectURL(file)
    const image = { id: null, uid: uid++, url: blobUrl, uploading: true }
    form.galleryImages.push(image)
    try {
      const data = new FormData()
      data.append('file', file)
      const uploaded = await uploadServiceImageApi(data)
      image.id = uploaded.id
      image.url = uploaded.url
    } catch {
      form.galleryImages.splice(form.galleryImages.indexOf(image), 1)
      ElMessage.error(`${file.name} 上传失败，请重试`)
    } finally {
      image.uploading = false
      URL.revokeObjectURL(blobUrl)
    }
  }
}

function removeImage(index) { form.galleryImages.splice(index, 1) }
function dropImage(index) {
  if (dragIndex.value < 0 || dragIndex.value === index) return
  const list = [...form.galleryImages]
  const [moved] = list.splice(dragIndex.value, 1)
  list.splice(index, 0, moved)
  form.galleryImages = list
  dragIndex.value = -1
}

onMounted(load)
</script>

<style lang="scss" scoped>
.service-page { max-width: 1180px; margin: 0 auto; padding-bottom: 36px; }
.page-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.page-heading h1 { margin: 0 0 6px; color: #172033; font-size: 26px; }
.page-heading p { margin: 0; color: #718096; }
.section-card { margin-bottom: 16px; border-color: #e5eaf2; }
.section-card :deep(.section-header) { display: flex; align-items: center; justify-content: space-between; font-size: 17px; font-weight: 650; }
.section-card :deep(.visibility-control) { display: flex; align-items: center; gap: 10px; color: #718096; font-weight: 400; }
.form-grid { display: grid; gap: 0 18px; }.form-grid.three { grid-template-columns: repeat(3, 1fr); }.form-grid.two { grid-template-columns: repeat(2, 1fr); }
.form-grid .wide { grid-column: 1 / -1; }.form-grid :deep(.el-input-number) { width: 100%; }
.sub-heading { display: flex; gap: 10px; align-items: baseline; margin: 4px 0 12px; color: #25324b; font-weight: 600; }.sub-heading small { color: #94a3b8; font-weight: 400; }
.section-card :deep(.editable-list) { display: grid; gap: 10px; }
.section-card :deep(.editable-row) { display: grid; grid-template-columns: 34px minmax(180px, .7fr) minmax(280px, 1.5fr) auto; gap: 10px; align-items: center; padding: 12px; background: #f8fafc; border: 1px solid #edf1f7; border-radius: 8px; }
.section-card :deep(.row-number) { display: grid; place-items: center; width: 28px; height: 28px; color: #2563eb; background: #e8f1ff; border-radius: 50%; font-weight: 700; }
.section-card :deep(.row-actions) { display: flex; white-space: nowrap; }
.upload-tip { margin-bottom: 14px; color: #64748b; font-size: 13px; }
.image-grid { display: flex; flex-wrap: wrap; gap: 12px; }
.image-card, .upload-button { position: relative; width: 148px; height: 116px; overflow: hidden; border-radius: 8px; box-sizing: border-box; }
.image-card { background: #f1f5f9; cursor: grab; }.image-card :deep(.el-image) { width: 100%; height: 100%; }
.image-order { position: absolute; left: 7px; top: 7px; display: grid; place-items: center; min-width: 24px; height: 24px; color: #fff; background: rgba(15, 23, 42, .72); border-radius: 12px; font-size: 12px; }
.delete-image { position: absolute; right: 7px; top: 7px; display: grid; place-items: center; width: 28px; height: 28px; color: #fff; border: 0; background: rgba(185, 28, 28, .9); border-radius: 50%; cursor: pointer; }
.upload-mask { position: absolute; inset: 0; display: grid; place-items: center; color: #fff; background: rgba(15, 23, 42, .55); }
.upload-button { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #2563eb; border: 1px dashed #93c5fd; background: #eff6ff; cursor: pointer; }.upload-button input { display: none; }
.load-error { display: flex; justify-content: center; gap: 8px; padding: 36px; color: #b45309; background: #fff; border: 1px solid #fde68a; border-radius: 8px; }
.bottom-actions { position: sticky; bottom: 0; display: flex; align-items: center; justify-content: flex-end; gap: 18px; padding: 14px 18px; color: #64748b; background: rgba(255,255,255,.96); border: 1px solid #e5eaf2; border-radius: 8px; box-shadow: 0 -6px 18px rgba(15,23,42,.05); z-index: 3; }
@media (max-width: 850px) { .form-grid.three, .form-grid.two { grid-template-columns: 1fr; }.form-grid .wide { grid-column: auto; }.section-card :deep(.editable-row) { grid-template-columns: 30px 1fr; }.section-card :deep(.editable-row > :nth-child(3)), .section-card :deep(.row-actions) { grid-column: 2; }.page-heading { align-items: flex-start; gap: 16px; } }
</style>
