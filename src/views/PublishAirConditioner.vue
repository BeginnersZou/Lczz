<template>
  <div class="publish-page">
    <!-- 顶部标题栏：左返回｜中标题｜右空白占位 -->
    <div class="page-header">
      <el-button dashed plain type="default" :icon="ArrowLeft" @click="goBack" class="back-btn">
        返回
      </el-button>
      <h2 class="page-title">{{ isEdit ? '编辑空调信息' : '发布空调信息' }}</h2>
      <div class="header-empty"></div>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" class="publish-form">
      <!-- 1.基本信息卡片 -->
      <el-card class="form-card" shadow="light">
        <template #header>
          <span class="card-title"><el-icon>
              <Monitor />
            </el-icon>基本信息</span>
        </template>
        <div class="form-row">
          <el-form-item label="空调名称" prop="name">
            <el-input v-model="form.name" placeholder="请输入空调名称" class="input-base" />
          </el-form-item>
          <el-form-item label="品牌" prop="brand">
            <el-select v-model="form.brand" placeholder="请选择品牌" class="select-base">
              <el-option label="格力" value="格力" />
              <el-option label="美的" value="美的" />
              <el-option label="海尔" value="海尔" />
              <el-option label="海信" value="海信" />
              <el-option label="奥克斯" value="奥克斯" />
              <el-option label="TCL" value="TCL" />
              <el-option label="其他" value="其他" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="价格" prop="price">
            <el-input v-model="form.price" placeholder="请输入价格" class="input-base">
              <template #suffix>¥</template>
            </el-input>
          </el-form-item>
          <el-form-item label="匹数" prop="horsepower">
            <el-select v-model="form.horsepower" placeholder="请选择匹数" class="select-base">
              <el-option label="1匹" value="1" />
              <el-option label="1.5匹" value="1.5" />
              <el-option label="2匹" value="2" />
              <el-option label="3匹" value="3" />
              <el-option label="5匹" value="5" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="适用面积" prop="roomSize">
            <el-input v-model="form.roomSize" placeholder="请输入适用面积" class="input-base">
              <template #suffix>㎡</template>
            </el-input>
          </el-form-item>
          <el-form-item label="能效等级" prop="energyEfficiency">
            <el-select v-model="form.energyEfficiency" placeholder="请选择能效等级" class="select-base">
              <el-option label="一级能效" value="一级能效" />
              <el-option label="二级能效" value="二级能效" />
              <el-option label="三级能效" value="三级能效" />
            </el-select>
          </el-form-item>
        </div>
      </el-card>

      <!-- 2.技术参数卡片 -->
      <el-card class="form-card" shadow="light">
        <template #header>
          <span class="card-title"><el-icon>
              <Setting />
            </el-icon>技术参数</span>
        </template>
        <div class="form-row">
          <el-form-item label="制冷量">
            <el-input v-model="form.coolingCapacity" placeholder="请输入制冷量" class="input-base">
              <template #suffix>W</template>
            </el-input>
          </el-form-item>
          <el-form-item label="制热量">
            <el-input v-model="form.heatingCapacity" placeholder="请输入制热量" class="input-base">
              <template #suffix>W</template>
            </el-input>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="噪音范围">
            <el-input v-model="form.noise" placeholder="请输入噪音范围" class="input-base">
              <template #suffix>dB</template>
            </el-input>
          </el-form-item>
          <el-form-item label="循环风量">
            <el-input v-model="form.airFlow" placeholder="请输入循环风量" class="input-base">
              <template #suffix>m³/h</template>
            </el-input>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="电源电压">
            <el-input v-model="form.voltage" placeholder="请输入电源电压" class="input-base">
              <template #suffix>V</template>
            </el-input>
          </el-form-item>
          <el-form-item label="额定功率">
            <el-input v-model="form.power" placeholder="请输入额定功率" class="input-base">
              <template #suffix>W</template>
            </el-input>
          </el-form-item>
        </div>
      </el-card>

      <!-- 3.媒体上传卡片 -->
      <el-card class="form-card" shadow="light">
        <template #header>
          <span class="card-title"><el-icon>
              <PictureFilled />
            </el-icon>媒体上传</span>
        </template>
        <div class="form-row upload-row">
          <el-form-item label="主图">
            <div class="upload-box">
              <el-upload class="image-upload" action="#" :show-file-list="false" :before-upload="beforeImageUpload">
                <div v-if="!form.image" class="upload-placeholder">
                  <el-icon size="40" class="upload-icon">
                    <Plus />
                  </el-icon>
                  <p class="upload-text">点击上传主图</p>
                  <p class="upload-tip">支持 JPG、PNG，建议 800×800</p>
                </div>
                <img v-else :src="form.image" class="upload-img" />
              </el-upload>
            </div>
          </el-form-item>
          <el-form-item label="视频">
            <div class="upload-box">
              <el-upload class="video-upload" action="#" :show-file-list="false" :before-upload="beforeVideoUpload">
                <div v-if="!form.video" class="upload-placeholder">
                  <el-icon size="40" class="upload-icon">
                    <VideoCamera />
                  </el-icon>
                  <p class="upload-text">点击上传视频</p>
                  <p class="upload-tip">支持 MP4、MOV，≤50MB</p>
                </div>
                <div v-else class="upload-video-preview">
                  <el-icon size="40" class="video-icon">
                    <VideoPlay />
                  </el-icon>
                  <p class="video-name">{{ form.video.split('/').pop() }}</p>
                </div>
              </el-upload>
            </div>
          </el-form-item>
        </div>
      </el-card>

      <!-- 4.描述信息卡片 -->
      <el-card class="form-card" shadow="light">
        <template #header>
          <span class="card-title"><el-icon>
              <Edit />
            </el-icon>描述信息</span>
        </template>
        <el-form-item label="简短描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入简短描述，用于列表展示"
            class="textarea-base" />
        </el-form-item>
        <el-form-item label="详细介绍">
          <div ref="editorRef" class="rich-editor"></div>
        </el-form-item>
      </el-card>

      <!-- 底部固定操作按钮 -->
      <div class="form-footer">
        <el-button dashed plain type="default" @click="saveDraft" :icon="Document"
          :loading="submitLoading">保存草稿</el-button>
        <el-button dashed plain type="primary" @click="publish" :icon="Check" :loading="submitLoading">发布</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
// 全部官方合法图标
import {
  ArrowLeft,
  Monitor,
  Setting,
  PictureFilled,
  Edit,
  Plus,
  VideoCamera,
  VideoPlay,
  Document,
  Check
} from '@element-plus/icons-vue'
import wangEditor from 'wangeditor'
import {
  getAirConditionerDetailApi,
  addAirConditionerApi,
  updateAirConditionerApi,
  uploadAirConditionerFileApi
} from '@/api/airConditioner'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const editorRef = ref(null)
let editorInstance = null

const isEdit = ref(false)
const submitLoading = ref(false)
const formIsDirty = ref(false)

const form = reactive({
  name: '',
  brand: '',
  price: '',
  horsepower: '',
  roomSize: '',
  energyEfficiency: '',
  coolingCapacity: '',
  heatingCapacity: '',
  noise: '',
  airFlow: '',
  voltage: '',
  power: '',
  image: '',
  video: '',
  description: '',
  detail: ''
})

watch(form, () => { formIsDirty.value = true }, { deep: true })
useUnsavedChanges(formIsDirty, '发布内容尚未保存，确定要放弃并离开吗？')

const rules = {
  name: [
    { required: true, message: '请输入空调名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度2-50字符', trigger: 'blur' }
  ],
  brand: [{ required: true, message: '请选择品牌', trigger: 'change' }],
  price: [
    { required: true, message: '请输入价格', trigger: 'blur' }
  ],
  horsepower: [{ required: true, message: '请选择匹数', trigger: 'change' }],
  roomSize: [{ required: true, message: '请输入适用面积', trigger: 'blur' }],
  energyEfficiency: [{ required: true, message: '请选择能效等级', trigger: 'change' }]
}

onMounted(() => {
  initEditor()
  if (route.params.id) {
    isEdit.value = true
    loadEditData()
  }
})

onUnmounted(() => {
  if (editorInstance) {
    editorInstance.destroy()
    editorInstance = null
  }
})

function initEditor() {
  editorInstance = new wangEditor(editorRef.value)
  editorInstance.config.height = 400
  editorInstance.config.onchange = () => { formIsDirty.value = true }
  // 富文本内图片上传：拦截默认上传，改走统一上传接口（带 token）
  editorInstance.config.customUploadImg = async (resultFiles, insertImgFn) => {
    try {
      for (const file of resultFiles) {
        const fd = new FormData()
        fd.append('file', file)
        const res = await uploadAirConditionerFileApi(fd)
        insertImgFn(res.url)
      }
    } catch {
      ElMessage.error('图片上传失败')
    }
  }
  editorInstance.config.uploadImgMaxSize = 5 * 1024 * 1024
  editorInstance.config.uploadImgMaxLength = 10
  editorInstance.create()
}

/**
 * 编辑模式加载详情回填
 */
async function loadEditData() {
  try {
    const data = await getAirConditionerDetailApi(route.params.id)
    Object.assign(form, {
      name: data.name || '',
      brand: data.brand || '',
      price: data.price ?? '',
      horsepower: data.horsepower || '',
      roomSize: data.roomSize || '',
      energyEfficiency: data.energyEfficiency || '',
      coolingCapacity: data.coolingCapacity || '',
      heatingCapacity: data.heatingCapacity || '',
      noise: data.noise || '',
      airFlow: data.airFlow || '',
      voltage: data.voltage || '',
      power: data.power || '',
      image: data.image || '',
      video: data.video || '',
      description: data.description || '',
      detail: data.detail || ''
    })
    setTimeout(() => {
      if (editorInstance) editorInstance.setHtml(form.detail || '')
      formIsDirty.value = false
    }, 100)
  } catch {
    // 拦截器已提示
  }
}

/**
 * 主图上传前校验 + 手动上传
 */
async function beforeImageUpload(file) {
  const isImg = file.type.startsWith('image/')
  const sizeOk = file.size / 1024 / 1024 < 5
  if (!isImg) {
    ElMessage.error('仅支持图片')
    return false
  }
  if (!sizeOk) {
    ElMessage.error('图片不超过5MB')
    return false
  }
  const localUrl = URL.createObjectURL(file)
  form.image = localUrl
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await uploadAirConditionerFileApi(fd)
    if (form.image === localUrl) URL.revokeObjectURL(localUrl)
    form.image = res.url
  } catch {
    if (form.image === localUrl) URL.revokeObjectURL(localUrl)
    form.image = ''
    ElMessage.error('主图上传失败')
  }
  return false
}

/**
 * 视频上传前校验 + 手动上传
 */
async function beforeVideoUpload(file) {
  const isVid = file.type.startsWith('video/')
  const sizeOk = file.size / 1024 / 1024 < 50
  if (!isVid) {
    ElMessage.error('仅支持视频文件')
    return false
  }
  if (!sizeOk) {
    ElMessage.error('视频不超过50MB')
    return false
  }
  const localUrl = URL.createObjectURL(file)
  form.video = localUrl
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await uploadAirConditionerFileApi(fd)
    if (form.video === localUrl) URL.revokeObjectURL(localUrl)
    form.video = res.url
  } catch {
    if (form.video === localUrl) URL.revokeObjectURL(localUrl)
    form.video = ''
    ElMessage.error('视频上传失败')
  }
  return false
}

function goBack() {
  router.push('/dynamic')
}

/**
 * 保存草稿（status: draft）
 */
async function saveDraft() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  form.detail = editorInstance ? editorInstance.getHtml() : ''
  submitLoading.value = true
  try {
    const submitData = { ...form, status: 'draft' }
    if (isEdit.value) {
      await updateAirConditionerApi(route.params.id, submitData)
    } else {
      await addAirConditionerApi(submitData)
    }
    ElMessage.success('草稿保存成功')
    formIsDirty.value = false
    router.push('/dynamic')
  } catch {
    // 拦截器已提示
  } finally {
    submitLoading.value = false
  }
}

/**
 * 发布（status: published）
 */
async function publish() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  form.detail = editorInstance ? editorInstance.getHtml() : ''
  const text = form.detail.replace(/<[^>]+>/g, '').trim()
  if (!text) {
    ElMessage.warning('请填写详细介绍')
    return
  }
  submitLoading.value = true
  try {
    const submitData = { ...form, status: 'published' }
    if (isEdit.value) {
      await updateAirConditionerApi(route.params.id, submitData)
      ElMessage.success('编辑保存成功')
    } else {
      await addAirConditionerApi(submitData)
      ElMessage.success('发布成功')
    }
    formIsDirty.value = false
    router.push('/dynamic')
  } catch {
    // 拦截器已提示
  } finally {
    submitLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.publish-page {
  padding: 20px;
  background-color: #f8fafc;
  min-height: calc(100vh - 60px);

  // 顶部标题栏
  .page-header {
    display: grid;
    grid-template-columns: 160px 1fr 160px;
    align-items: center;
    margin-bottom: 20px;
    padding: 14px 18px;
    background: #ffffff;
    border-radius: 8px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);

    .back-btn {
      display: flex;
      align-items: center;
      gap: 5px;
      color: #64748b;
    }

    .page-title {
      text-align: center;
      font-size: 20px;
      font-weight: 600;
      color: #1f2937;
      margin: 0;
    }

    .header-empty {
      text-align: right;
    }
  }

  .publish-form {
    width: 100%;
  }

  // 表单卡片轻量化
  .form-card {
    margin-bottom: 16px;
    border-radius: 8px;
    border: none;

    :deep(.el-card__header) {
      padding: 12px 18px;
      background: #f8fafc;
      border-bottom: 1px solid #e5e7eb;
    }

    .card-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 15px;
      font-weight: 500;
      color: #1f2937;
    }

    :deep(.el-card__body) {
      padding: 20px;
    }
  }

  .form-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 18px;
    margin-bottom: 16px;

    &.upload-row {
      gap: 40px;
    }
  }

  // ========== 输入框边框重点优化 ==========
  .input-base,
  .select-base {
    width: 100%;

    :deep(.el-input__wrapper) {
      border: 1px solid #d1d5db; // 标准浅灰边框
      border-radius: 6px;
      box-shadow: none;
      transition: border-color 0.2s ease;

      &:hover {
        border-color: #94a3b8; // hover浅灰加深
      }

      &.is-focus {
        border-color: #409eff; // 聚焦蓝色细边框
      }
    }

    :deep(.el-select__wrapper) {
      border: 1px solid #d1d5db;
      border-radius: 6px;
      box-shadow: none;
      transition: border-color 0.2s ease;

      &:hover {
        border-color: #94a3b8;
      }

      &.is-focus {
        border-color: #409eff;
      }
    }
  }

  .textarea-base :deep(.el-textarea__inner) {
    border: 1px solid #d1d5db;
    border-radius: 6px;
    box-shadow: none;
    transition: border-color 0.2s ease;

    &:hover {
      border-color: #94a3b8;
    }

    &:focus {
      border-color: #409eff;
    }
  }

  // ========== 富文本边框极简优化（解决丑粗边框） ==========
  .rich-editor {
    border: 1px solid #e5e7eb; // 极浅细边框，不突兀
    border-radius: 8px;
    overflow: hidden;

    :deep(.w-e-toolbar) {
      background: #f9fafb; // 弱化工具栏底色，不厚重
      border-bottom: 1px solid #e5e7eb;
    }

    :deep(.w-e-text-container) {
      min-height: 400px;
      background: #fff;
    }
  }

  // 上传区域
  .upload-box {
    .upload-placeholder {
      width: 200px;
      height: 200px;
      border: 1px dashed #cbd5e1;
      border-radius: 8px;
      background: #f8fafc;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      cursor: pointer;

      .upload-icon {
        color: #9ca3af;
        margin-bottom: 8px;
      }

      .upload-text {
        font-size: 14px;
        color: #4b5563;
        margin: 4px 0;
      }

      .upload-tip {
        font-size: 12px;
        color: #9ca3af;
      }
    }

    .upload-img {
      width: 200px;
      height: 200px;
      object-fit: cover;
      border-radius: 8px;
    }

    .upload-video-preview {
      width: 200px;
      height: 200px;
      border: 1px dashed #cbd5e1;
      border-radius: 8px;
      background: #f8fafc;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;

      .video-icon {
        color: #6b7280;
      }

      .video-name {
        max-width: 180px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 13px;
        color: #4b5563;
        margin-top: 8px;
      }
    }
  }

  // 底部操作栏
  .form-footer {
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px solid #e5e7eb;
    display: flex;
    justify-content: center;
    gap: 14px;
    padding-bottom: 30px;

    :deep(.el-button) {
      padding: 8px 26px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }

  :deep(.el-form-item__label) {
    color: #374151;
    font-weight: 400;
  }

  :deep(.el-form-item__error) {
    font-size: 12px;
  }
}

// 平板适配
@media (max-width: 1024px) {
  .publish-page .form-row {
    grid-template-columns: 1fr;
  }
}

// 手机端适配
@media (max-width: 768px) {
  .publish-page {
    padding: 12px;
  }

  .publish-page .page-header {
    grid-template-columns: 1fr;
    gap: 12px;

    .page-title {
      text-align: left;
    }
  }

  .publish-page .upload-box .upload-placeholder,
  .publish-page .upload-box .upload-img,
  .publish-page .upload-box .upload-video-preview {
    width: 100%;
  }
}
</style>
