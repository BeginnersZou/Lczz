<template>
  <div class="detail-edit-page">
    <!-- 顶部标题栏：左返回｜中标题｜右空白占位 -->
    <div class="page-header">
      <el-button dashed plain type="default" :icon="ArrowLeft" @click="goBack" class="back-btn">
        返回
      </el-button>
      <h2 class="page-title">{{ isViewMode ? '查看动态信息' : '编辑动态信息' }}</h2>
      <div class="header-empty"></div>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" class="publish-form">
      <!-- 基础信息卡片 -->
      <el-card class="form-card" shadow="light">
        <template #header>
          <span class="card-title"><el-icon>
              <Document />
            </el-icon>基础内容</span>
        </template>
        <!-- 标题输入框 -->
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入动态标题" class="input-base" :disabled="isViewMode" />
        </el-form-item>
        <!-- 封面图片上传 -->
        <el-form-item label="封面图片">
          <div class="upload-box">
            <el-upload class="image-upload" action="#" :show-file-list="false" :before-upload="beforeImageUpload"
              :disabled="isViewMode">
              <div v-if="!form.coverImage" class="upload-placeholder">
                <el-icon size="40" class="upload-icon">
                  <Plus />
                </el-icon>
                <p class="upload-text">上传图片</p>
                <p class="upload-tip">仅支持JPG/PNG格式</p>
              </div>
              <img v-else :src="form.coverImage" class="upload-img" />
            </el-upload>
          </div>
        </el-form-item>
      </el-card>

      <!-- 内容富文本卡片 -->
      <el-card class="form-card" shadow="light">
        <template #header>
          <span class="card-title"><el-icon>
              <Edit />
            </el-icon>信息内容</span>
        </template>
        <el-form-item label="内容">
          <div ref="editorRef" class="rich-editor"></div>
        </el-form-item>
      </el-card>

      <!-- 底部操作按钮：区分查看/编辑 -->
      <div class="form-footer">
        <el-button dashed plain type="default" :icon="ArrowLeft" @click="goBack">返回</el-button>
        <template v-if="!isViewMode">
          <el-button dashed plain type="info" @click="saveDraft" :icon="Document"
            :loading="submitLoading">保存草稿</el-button>
          <el-button dashed plain type="primary" @click="submitPublish" :icon="Check"
            :loading="submitLoading">发布</el-button>
        </template>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Document, Edit, Plus, Check } from '@element-plus/icons-vue'
import wangEditor from 'wangeditor'
import { getDynamicDetailApi, updateDynamicApi, uploadDynamicImageApi } from '@/api/dynamic'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const editorRef = ref(null)
let editorInstance = null

// 模式区分：true=仅查看，false=可编辑
const isViewMode = ref(false)
const submitLoading = ref(false)
const formIsDirty = ref(false)
// 表单数据
const form = reactive({
  title: '',
  coverImage: '',
  contentHtml: ''
})

watch(form, () => { formIsDirty.value = true }, { deep: true })
useUnsavedChanges(formIsDirty, '动态内容尚未保存，确定要放弃并离开吗？')

// 表单校验规则
const rules = {
  title: [
    { required: true, message: '请输入动态标题', trigger: 'blur' },
    { min: 4, max: 60, message: '标题长度4-60字符', trigger: 'blur' }
  ]
}

// 监听路由id变化（切换不同动态时重新加载数据）
watch(() => route.params.id, (newId) => {
  if (newId) loadDetailData()
}, { immediate: true })

onMounted(() => {
  // 先判断打开来源：列表点击查看按钮=只读模式，编辑按钮=可编辑
  // 必须在 initRichEditor 之前赋值，确保编辑器初始化时 readOnly 配置正确
  const openType = route.query.type
  isViewMode.value = openType === 'view'
  initRichEditor()
})

// 销毁编辑器实例
onUnmounted(() => {
  if (editorInstance) {
    editorInstance.destroy()
    editorInstance = null
  }
})

/**
 * 初始化富文本编辑器
 */
function initRichEditor() {
  editorInstance = new wangEditor(editorRef.value)
  editorInstance.config.height = 420
  editorInstance.config.onchange = () => {
    if (!isViewMode.value) formIsDirty.value = true
  }
  // 富文本内图片上传：拦截默认上传，改走统一上传接口（带 token）
  editorInstance.config.customUploadImg = async (resultFiles, insertImgFn) => {
    try {
      for (const file of resultFiles) {
        const fd = new FormData()
        fd.append('file', file)
        const res = await uploadDynamicImageApi(fd)
        insertImgFn(res.url)
      }
    } catch {
      ElMessage.error('图片上传失败')
    }
  }
  editorInstance.config.uploadImgMaxSize = 5 * 1024 * 1024
  editorInstance.config.uploadImgMaxLength = 10
  // 工具栏配置，和截图完全对齐
  editorInstance.config.toolbarKeys = [
    'bold', 'italic', 'underline', 'strikeThrough', '|',
    'fontSize', 'fontFamily', 'color', 'bgColor', '|',
    'justifyLeft', 'justifyCenter', 'justifyRight', 'justifyJustify', '|',
    'indent', 'lineHeight', '|',
    'insertLink', 'insertImage', 'insertVideo', 'insertTable', '|',
    'undo', 'redo', 'fullScreen'
  ]
  editorInstance.create()
  // 查看模式禁用编辑器
  editorInstance.config.readOnly = isViewMode.value
}

/**
 * 加载动态详情数据（回显）
 */
async function loadDetailData() {
  try {
    const data = await getDynamicDetailApi(route.params.id)
    form.title = data.title || ''
    form.coverImage = data.coverImage || ''
    // 富文本赋值，延时等待编辑器实例创建完成，并做判空保护避免 watch immediate 在 onMounted 前触发时报错
    const html = data.contentHtml || ''
    setTimeout(() => {
      if (!editorInstance) return
      editorInstance.setHtml(html)
      editorInstance.config.readOnly = isViewMode.value
      formIsDirty.value = false
    }, 100)
  } catch {
    // 拦截器已提示
  }
}

/**
 * 封面图上传前校验 + 手动上传（走统一接口带 token）；查看模式禁止上传
 */
async function beforeImageUpload(file) {
  if (isViewMode.value) return false
  const isImage = file.type.startsWith('image/')
  const sizeLimit = file.size / 1024 / 1024 < 5
  if (!isImage) {
    ElMessage.error('仅支持JPG、PNG图片文件')
    return false
  }
  if (!sizeLimit) {
    ElMessage.error('图片大小不能超过5MB')
    return false
  }
  const localUrl = URL.createObjectURL(file)
  form.coverImage = localUrl
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await uploadDynamicImageApi(fd)
    if (form.coverImage === localUrl) URL.revokeObjectURL(localUrl)
    form.coverImage = res.url
  } catch {
    if (form.coverImage === localUrl) URL.revokeObjectURL(localUrl)
    form.coverImage = ''
    ElMessage.error('封面图上传失败')
  }
  return false
}

/**
 * 返回动态列表页面
 */
function goBack() {
  router.push('/dynamic')
}

/**
 * 保存草稿（调用更新接口，状态标记为草稿）
 */
async function saveDraft() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  form.contentHtml = editorInstance ? editorInstance.getHtml() : ''
  const textContent = form.contentHtml.replace(/<[^>]+>/g, '').trim()
  if (!textContent) {
    ElMessage.warning('请填写动态内容')
    return
  }
  submitLoading.value = true
  try {
    await updateDynamicApi(route.params.id, { ...form, status: 'draft' })
    ElMessage.success('草稿保存成功')
    formIsDirty.value = false
  } catch {
    // 拦截器已提示
  } finally {
    submitLoading.value = false
  }
}

/**
 * 发布提交（调用更新接口，状态标记为已发布）
 */
async function submitPublish() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  form.contentHtml = editorInstance ? editorInstance.getHtml() : ''
  const textContent = form.contentHtml.replace(/<[^>]+>/g, '').trim()
  if (!textContent) {
    ElMessage.warning('请填写动态内容')
    return
  }
  submitLoading.value = true
  try {
    await updateDynamicApi(route.params.id, { ...form, status: 'published' })
    ElMessage.success('动态编辑发布成功')
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
.detail-edit-page {
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

  // 表单卡片轻量化，全站统一
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

  // 输入框统一样式
  .input-base {
    width: 100%;

    :deep(.el-input__wrapper) {
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

  // 封面图片上传区域
  .upload-box {
    .upload-placeholder {
      width: 180px;
      height: 180px;
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
      width: 180px;
      height: 180px;
      object-fit: cover;
      border-radius: 8px;
    }
  }

  // 富文本编辑器
  .rich-editor {
    // border: 1px solid #e5e7eb;
    // border-radius: 8px;
    overflow: hidden;

    :deep(.w-e-toolbar) {
      background: #f9fafb;
      //   border-bottom: 1px solid #e5e7eb;
    }

    :deep(.w-e-text-container) {
      min-height: 420px;
      background: #fff;
    }
  }

  // 底部操作按钮区
  .form-footer {
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px solid #e5e7eb;
    display: flex;
    justify-content: flex-end;
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

// 手机端适配
@media (max-width: 768px) {
  .detail-edit-page {
    padding: 12px;
  }

  .detail-edit-page .page-header {
    grid-template-columns: 1fr;
    gap: 12px;

    .page-title {
      text-align: left;
    }
  }

  .upload-box .upload-placeholder,
  .upload-box .upload-img {
    width: 100%;
  }
}
</style>
