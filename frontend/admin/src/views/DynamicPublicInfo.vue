<template>
    <div class="publish-page">
        <!-- 顶部标题栏：左返回｜中标题｜右空白占位 -->
        <div class="page-header">
            <el-button dashed plain type="default" :icon="ArrowLeft" @click="goBack" class="back-btn">
                返回
            </el-button>
            <h2 class="page-title">{{ isEdit ? '编辑动态信息' : '发布信息' }}</h2>
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
                    <el-input v-model="form.title" placeholder="请输入动态标题" class="input-base" />
                </el-form-item>
                <!-- 封面图片上传 -->
                <el-form-item label="封面图片">
                    <div class="upload-box">
                        <el-upload class="image-upload" action="#" :show-file-list="false"
                            :before-upload="beforeImageUpload">
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

            <!-- 底部操作按钮 -->
            <div class="form-footer">
                <el-button dashed plain type="default" :icon="ArrowLeft" @click="goBack">取消</el-button>
                <el-button dashed plain type="primary" @click="submitForm" :icon="Check">确认</el-button>
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
import { getDynamicDetailApi, addDynamicApi, updateDynamicApi, uploadDynamicImageApi } from '@/api/dynamic'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const editorRef = ref(null)
let editorInstance = null

// 区分新增/编辑模式
const isEdit = ref(false)
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

// 页面加载：初始化富文本 + 判断编辑回填数据
onMounted(() => {
    initRichEditor()
    if (route.params.id) {
        isEdit.value = true
        loadEditData()
    }
})

// 销毁编辑器实例
onUnmounted(() => {
    if (editorInstance) {
        editorInstance.destroy()
        editorInstance = null
    }
})

/**
 * 初始化富文本编辑器（匹配截图工具栏）
 */
function initRichEditor() {
    editorInstance = new wangEditor(editorRef.value)
    // 编辑器高度
    editorInstance.config.height = 420
    editorInstance.config.onchange = () => { formIsDirty.value = true }
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
}

/**
 * 编辑模式加载详情回填
 */
async function loadEditData() {
    try {
        const data = await getDynamicDetailApi(route.params.id)
        form.title = data.title || ''
        form.coverImage = data.coverImage || ''
        // 富文本赋值，延时等待编辑器实例创建完成
        setTimeout(() => {
            if (editorInstance) editorInstance.setHtml(data.contentHtml || '')
            formIsDirty.value = false
        }, 100)
    } catch {
        // 拦截器已提示
    }
}

/**
 * 封面图上传前校验 + 手动上传（走统一接口带 token）
 */
async function beforeImageUpload(file) {
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
    // 本地即时预览
    const localUrl = URL.createObjectURL(file)
    form.coverImage = localUrl
    try {
        const fd = new FormData()
        fd.append('file', file)
        const res = await uploadDynamicImageApi(fd)
        // 释放本地 blob，使用远程 url
        if (form.coverImage === localUrl) URL.revokeObjectURL(localUrl)
        form.coverImage = res.url
    } catch {
        if (form.coverImage === localUrl) URL.revokeObjectURL(localUrl)
        form.coverImage = ''
        ElMessage.error('封面图上传失败')
    }
    // 返回 false 阻止 el-upload 默认上传
    return false
}

/**
 * 返回动态列表页面
 */
function goBack() {
    router.push('/dynamic')
}

/**
 * 提交表单：新增/编辑动态
 */
async function submitForm() {
    try {
        await formRef.value.validate()
    } catch {
        return
    }
    // 获取富文本内容
    form.contentHtml = editorInstance ? editorInstance.getHtml() : ''
    const textContent = form.contentHtml.replace(/<[^>]+>/g, '').trim()
    if (!textContent) {
        ElMessage.warning('请填写动态内容')
        return
    }
    submitLoading.value = true
    try {
        const submitData = {
            title: form.title,
            coverImage: form.coverImage,
            contentHtml: form.contentHtml
        }
        if (isEdit.value) {
            await updateDynamicApi(route.params.id, submitData)
            ElMessage.success('动态编辑成功')
        } else {
            await addDynamicApi(submitData)
            ElMessage.success('动态发布成功')
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

    // 富文本编辑器，匹配截图简约边框
    .rich-editor {
        // border: 1px solid #e5e7eb;
        // border-radius: 8px;
        overflow: hidden;

        :deep(.w-e-toolbar) {
            background: #f9fafb;
            // border-bottom: 1px solid #e5e7eb;
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

    .upload-box .upload-placeholder,
    .upload-box .upload-img {
        width: 100%;
    }
}
</style>
