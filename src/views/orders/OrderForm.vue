<template>
  <div class="order-form-page">
    <!-- 统一顶部标题返回栏（完全保留原有头部样式） -->
    <div class="page-header">
      <el-button dashed plain type="default" :icon="ArrowLeft" @click="handleCancel" class="back-btn">
        返回
      </el-button>
      <h2 class="page-title">{{ isEdit ? '编辑订单' : '新增订单' }}</h2>
      <div class="header-empty"></div>
    </div>

    <div v-if="loadError" class="error-state form-load-error">
      <span>{{ loadError }}</span>
      <el-button type="primary" link @click="loadEditData">重新加载</el-button>
    </div>
    <el-form v-else v-loading="pageLoading" ref="orderFormRef" :model="form" :rules="rules" label-width="120px" class="form-content">
      <el-card class="form-card" shadow="light">
        <template #header>
          <span class="card-title"><el-icon>
              <Document />
            </el-icon>订单基础信息</span>
        </template>

        <!-- 任务类型下拉 -->
        <el-form-item label="任务类型" prop="taskType">
          <el-select v-model="form.taskType" placeholder="请选择任务类型" class="input-base" style="width: 100%">
            <el-option label="空调安装" value="空调安装"></el-option>
            <el-option label="空调维修" value="空调维修"></el-option>
            <el-option label="空调清洗" value="空调清洗"></el-option>
            <el-option label="空调移机" value="空调移机"></el-option>
          </el-select>
        </el-form-item>

        <!-- 描述 -->
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="请填写空调型号、故障或施工要求" class="textarea-base" />
        </el-form-item>

        <!-- 客户姓名 -->
        <el-form-item label="客户姓名" prop="customerName">
          <el-input v-model="form.customerName" placeholder="请输入客户姓名" class="input-base" />
        </el-form-item>

        <!-- 客户手机号 -->
        <el-form-item label="客户手机号" prop="customerPhone">
          <el-input v-model="form.customerPhone" placeholder="请输入11位手机号" class="input-base" />
        </el-form-item>

        <!-- 省市区三级联动地址 -->
        <el-form-item label="订单地址" prop="addressDetail">
          <div class="city-row">
            <div class="region-control">
              <el-cascader v-model="form.addressArea" :options="cityOptions"
                :placeholder="regionLoading ? '正在加载地区数据…' : '选择省 / 市 / 区'" class="city-cascader"
                :disabled="regionLoading || cityOptions.length === 0" clearable filterable />
              <div v-if="regionError" class="field-feedback is-error">
                <span>{{ regionError }}</span>
                <el-button type="primary" link :loading="regionLoading" @click="loadRegions">重新加载</el-button>
              </div>
            </div>
            <el-input v-model="form.addressDetail" placeholder="详细街道门牌号" class="city-detail" />
          </div>
        </el-form-item>

        <!-- 订单时间 双日期框 -->
        <el-form-item label="订单时间" prop="orderEndTime">
          <div class="time-row">
            <el-date-picker v-model="form.orderStartTime" type="datetime" placeholder="上门开始时间" class="time-input"
              format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" />
            <el-date-picker v-model="form.orderEndTime" type="datetime" placeholder="预计结束时间" class="time-input"
              format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" />
          </div>
        </el-form-item>

        <!-- 指派师傅区域 -->
        <el-form-item label="指派师傅" required>
          <div class="master-select-wrap">
            <el-button plain @click="openMasterDialog">添加</el-button>
          </div>
          <!-- 已选师傅表格 -->
          <el-table v-if="selectedMasterList.length > 0" :data="selectedMasterList" border size="small"
            style="width: 100%;margin-top:10px">
            <el-table-column label="师傅姓名" prop="masterName" align="center" />
            <el-table-column label="性别" prop="gender" align="center" />
            <el-table-column label="年龄" prop="age" align="center" />
            <el-table-column label="手机号" prop="masterPhone" align="center" />
            <el-table-column label="操作" align="center" width="100">
              <template #default="scope">
                <el-button text type="danger" @click="removeSelectedMaster(scope.$index)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>

        <!-- 附件上传 -->
        <el-form-item label="附件">
          <div class="upload-tip">
            <el-icon>
              <InfoFilled />
            </el-icon>
            <span>仅支持上传图片（jpg/png/gif/webp），最多9张，已选 {{ form.fileList.length }}/9 张</span>
          </div>
          <div class="upload-wrap">
            <!-- 已上传图片卡片：hover 显示删除遮罩 -->
            <div class="img-card" v-for="(fileItem, idx) in form.fileList" :key="fileItem.uid">
              <img :src="fileItem.previewUrl" alt="附件" class="preview-img" />
              <div v-if="fileItem.uploading" class="uploading-mask"><el-icon class="is-loading"><Loading /></el-icon><span>上传中</span></div>
              <div v-else class="img-mask" @click="deleteFileImg(idx)">
                <el-icon class="mask-icon">
                  <Delete />
                </el-icon>
                <span class="mask-text">删除</span>
              </div>
            </div>
            <!-- 上传按钮：达到9张自动隐藏 -->
            <div class="upload-add file-upload-box" v-if="form.fileList.length < 9" role="button" tabindex="0" @click="triggerFileUpload" @keyup.enter="triggerFileUpload">
              <el-icon :size="26" class="upload-icon">
                <Plus />
              </el-icon>
              <span>上传附件</span>
              <input ref="fileUploadRef" type="file" multiple class="upload-input"
                accept="image/jpeg,image/jpg,image/png,image/gif,image/webp" @change="handleFileUpload" />
            </div>
          </div>
        </el-form-item>
      </el-card>

      <!-- 底部操作区 -->
      <div class="form-footer">
        <el-button v-if="isEdit" text type="danger" @click="handleCancelOrder">作废订单</el-button>
        <div class="footer-right">
          <el-button dashed plain type="default" :icon="ArrowLeft" @click="handleCancel">取消</el-button>
          <el-button dashed plain type="primary" @click="handleSubmit" :icon="Check"
            :loading="submitLoading">确认提交</el-button>
        </div>
      </div>
    </el-form>

    <!-- 指派师傅弹窗（对应第二张原型表格） -->
    <el-dialog v-model="masterDialogVisible" title="指派师傅" width="70%" top="20vh">
      <div class="master-toolbar">
        <el-input v-model="masterSearch" clearable placeholder="搜索师傅姓名或手机号" :prefix-icon="Search" @input="handleMasterSearch" />
        <span>每个订单仅允许选择 1 位师傅</span>
      </div>
      <el-table v-loading="masterLoading" :data="pagedMasterList" :row-key="getMasterKey" border stripe ref="masterTableRef" @selection-change="handleMasterSelection" empty-text="暂无可指派师傅">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="序号" align="center" width="70">
          <template #default="scope">
            {{ (masterPage.currentPage - 1) * masterPage.pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="masterName" label="师傅姓名" align="center" />
        <el-table-column prop="gender" width="80" label="性别" align="center" />
        <el-table-column prop="age" width="80" label="年龄" align="center" />
        <el-table-column prop="masterPhone" width="140" label="手机号" align="center" />
        <el-table-column prop="taskInfo" label="任务" align="left">
          <template #default="scope">
            <div class="task-info">{{ scope.row.taskInfo || '暂无任务' }}</div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 师傅分页 -->
      <div class="master-pagination">
        <el-pagination v-model:current-page="masterPage.currentPage" v-model:page-size="masterPage.pageSize"
          layout="total, prev, pager, next" :total="filteredMasterList.length" @current-change="refreshMasterTable" />
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="masterDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmSelectMaster">确认选择</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 离开表单确认弹窗 -->
    <el-dialog v-model="leaveDialogVisible" title="提示" width="360px">
      <p>表单内容已修改，确定要放弃填写返回吗？</p>
      <template #footer>
        <el-button @click="leaveDialogVisible = false">继续填写</el-button>
        <el-button type="danger" @click="confirmLeave">确认放弃</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Plus, Document, Check, Delete, InfoFilled, Loading, Search
} from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import {
  getOrderDetailApi, addOrderApi, updateOrderApi,
  uploadOrderImageApi, bindOrderFileApi, getMasterListApi, cancelOrderApi
} from '@/api/orders'
import { getRegionTreeApi } from '@/api/regions'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'

const router = useRouter()
const route = useRoute()
const orderFormRef = ref(null)
const masterTableRef = ref(null)

// 基础状态【全部原样保留，无修改】
const isEdit = ref(false)
const orderId = ref('')
const submitLoading = ref(false)
const pageLoading = ref(false)
const loadError = ref('')
const fileUploadRef = ref(null)

const masterDialogVisible = ref(false)
const leaveDialogVisible = ref(false)
const formIsDirty = ref(false)
const masterPage = reactive({
  currentPage: 1,
  pageSize: 5
})
const tempSelectMaster = ref([])
const selectedMasterList = ref([])
const masterSearch = ref('')
const masterLoading = ref(false)
// 附件上传中计数，提交时据此拦截，避免丢图
const uploadingCount = ref(0)

const cityOptions = ref([])
const regionLoading = ref(false)
const regionError = ref('')

// 表单结构完全不变，fileList 存储 {uid, file, previewUrl, url, uploading}
const form = reactive({
  taskType: '',
  description: '',
  customerName: '',
  customerPhone: '',
  addressArea: [],
  addressDetail: '',
  orderStartTime: '',
  orderEndTime: '',
  images: [],
  videos: [],
  fileList: [],
  dealer: '',
  contact: '',
  dealerAddress: '',
  adminRemark: ''
})

const rules = {
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  customerName: [
    { required: true, message: '请输入客户姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '姓名长度2~20字符', trigger: 'blur' }
  ],
  customerPhone: [
    { required: true, message: '请输入客户手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  addressDetail: [{
    validator: (rule, value, callback) => {
      if (!Array.isArray(form.addressArea) || form.addressArea.length < 3) callback(new Error('请选择完整的省市区'))
      else if (!value?.trim()) callback(new Error('请填写详细街道门牌号'))
      else callback()
    },
    trigger: ['blur', 'change']
  }],
  orderEndTime: [{
    validator: (rule, value, callback) => {
      if (!form.orderStartTime || !value) callback(new Error('请选择完整的上门时间范围'))
      else if (new Date(value).getTime() <= new Date(form.orderStartTime).getTime()) callback(new Error('结束时间必须晚于开始时间'))
      else callback()
    },
    trigger: 'change'
  }]
}

// 可指派师傅列表（来自接口）
const allMasterList = ref([])
const filteredMasterList = computed(() => {
  const keyword = masterSearch.value.trim().toLowerCase()
  if (!keyword) return allMasterList.value
  return allMasterList.value.filter(item => `${item.masterName || ''} ${item.masterPhone || ''}`.toLowerCase().includes(keyword))
})
const pagedMasterList = computed(() => {
  const start = (masterPage.currentPage - 1) * masterPage.pageSize
  return filteredMasterList.value.slice(start, start + masterPage.pageSize)
})

useUnsavedChanges(formIsDirty, '订单内容尚未保存，确定要放弃并离开吗？')

watch(form, () => {
  formIsDirty.value = true
}, { deep: true })

onMounted(async () => {
  const tasks = [loadRegions()]
  if (route.params.id) {
    isEdit.value = true
    orderId.value = route.params.id
    tasks.push(loadEditData())
  }
  await Promise.allSettled(tasks)
})

async function loadRegions() {
  regionLoading.value = true
  regionError.value = ''
  try {
    const rows = await getRegionTreeApi()
    if (!rows.length) throw new Error('EMPTY_REGION_TREE')
    cityOptions.value = rows
  } catch {
    cityOptions.value = []
    regionError.value = '后端尚未提供省市区数据，暂时无法选择订单地区。'
  } finally {
    regionLoading.value = false
  }
}

/**
 * 编辑态：拉取订单详情回显
 */
async function loadEditData() {
  pageLoading.value = true
  loadError.value = ''
  try {
    const res = await getOrderDetailApi(orderId.value)
    // 回显 fileList：远程图片 url 直接作为 previewUrl，file 为 null
    const remoteFiles = res.fileList || res.images || []
    const fileList = remoteFiles.map(item => {
      const url = typeof item === 'string' ? item : (item.url || item.previewUrl)
      return {
        uid: fileUid++,
        id: typeof item === 'string' ? null : item.id,
        file: null,
        previewUrl: url,
        url,
        bound: true,
        uploading: false
      }
    }).filter(f => f.url)

    Object.assign(form, {
      taskType: res.taskType || '',
      description: res.description || '',
      addressArea: res.addressArea || [],
      addressDetail: res.addressDetail || '',
      orderStartTime: res.orderStartTime || '',
      orderEndTime: res.orderEndTime || '',
      customerName: res.customerName || '',
      customerPhone: res.customerPhone || '',
      images: res.images || [],
      videos: res.videos || [],
      fileList,
      dealer: res.dealer || '',
      contact: res.contact || '',
      dealerAddress: res.dealerAddress || '',
      adminRemark: res.adminRemark || ''
    })
    selectedMasterList.value = res.selectedMasterList || res.masterList || []
    formIsDirty.value = false
  } catch {
    loadError.value = '订单详情加载失败，请重试后再编辑。'
  } finally {
    pageLoading.value = false
  }
}

// ====================== 师傅全套逻辑 ======================
// 用 id 作为师傅唯一标识，避免姓名/手机号重复导致回显错乱
const isRestoring = ref(false)

function getMasterKey(master) {
  return master && master.id != null ? master.id : master.masterName + '_' + master.masterPhone
}

// 打开指派师傅弹窗，拉取师傅列表并回显已选
async function openMasterDialog() {
  masterDialogVisible.value = true
  masterPage.currentPage = 1
  masterSearch.value = ''
  tempSelectMaster.value = [...selectedMasterList.value]
  masterLoading.value = true
  try {
    const res = await getMasterListApi()
    allMasterList.value = Array.isArray(res) ? res : (res.list || [])
  } catch {
    allMasterList.value = []
  } finally {
    masterLoading.value = false
  }
  nextTick(() => {
    restoreMasterSelection()
  })
}

// 回显已选师傅到弹窗表格的勾选状态
function restoreMasterSelection() {
  if (!masterTableRef.value) return
  isRestoring.value = true
  // 先清空勾选，再按已选列表逐行勾选，保证状态干净
  masterTableRef.value.clearSelection()
  pagedMasterList.value.forEach(row => {
    const isSelected = tempSelectMaster.value.some(m => getMasterKey(m) === getMasterKey(row))
    if (isSelected) {
      masterTableRef.value.toggleRowSelection(row, true)
    }
  })
  isRestoring.value = false
}

// 弹窗中勾选变化：仅更新临时选择，不直接改 selectedMasterList
function handleMasterSelection(selection) {
  // 回显期间不处理，避免 clearSelection 把临时选择清空
  if (isRestoring.value) return
  if (selection.length > 1) {
    const selected = selection[selection.length - 1]
    tempSelectMaster.value = [selected]
    ElMessage.warning('一个订单只能指派一位师傅')
    nextTick(() => {
      isRestoring.value = true
      masterTableRef.value?.clearSelection()
      masterTableRef.value?.toggleRowSelection(selected, true)
      isRestoring.value = false
    })
    return
  }
  tempSelectMaster.value = selection.slice(0, 1)
}

// 确认选择：临时选择覆盖已选（去重保序），关闭弹窗；编辑态立即调指派接口持久化
async function confirmSelectMaster() {
  const result = tempSelectMaster.value.slice(0, 1)
  selectedMasterList.value = result
  masterDialogVisible.value = false
  formIsDirty.value = true
  ElMessage.success(result.length > 0 ? '已选择安装师傅，提交订单后生效' : '已清空指派师傅')
}

// 弹窗翻页后重新回显已选勾选
function refreshMasterTable() {
  nextTick(() => {
    restoreMasterSelection()
  })
}

function handleMasterSearch() {
  masterPage.currentPage = 1
  nextTick(restoreMasterSelection)
}

// 从已选师傅表格移除某行
function removeSelectedMaster(index) {
  selectedMasterList.value.splice(index, 1)
  formIsDirty.value = true
  ElMessage.success('已移除该师傅')
}

// ====================== 附件上传逻辑：仅图片、去重、本地预览、选择后立即上传真实 url ======================
const MAX_IMAGE_COUNT = 9
let fileUid = 1
function triggerFileUpload() { fileUploadRef.value.click() }
async function handleFileUpload(event) {
  const files = Array.from(event.target.files)
  // 允许的图片格式
  const allowTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp']
  const maxSize = 5 * 1024 * 1024
  // 剩余可上传数量
  const remaining = MAX_IMAGE_COUNT - form.fileList.length
  if (remaining <= 0) {
    ElMessage.warning(`最多只能上传${MAX_IMAGE_COUNT}张图片`)
    event.target.value = ''
    return
  }
  // 过滤非图片、去重、按剩余数量截取
  const toUpload = []
  for (const file of files) {
    if (!allowTypes.includes(file.type)) {
      ElMessage.warning(`文件${file.name}不是图片，禁止上传`)
      continue
    }
    if (file.size > maxSize) {
      ElMessage.warning(`图片${file.name}超过5MB，禁止上传`)
      continue
    }
    // 文件名+大小双条件去重（已上传项 file 为 null，需 guard）
    const repeat = form.fileList.some(item => item.file && item.file.name === file.name && item.file.size === file.size)
    if (repeat) {
      ElMessage.info(`图片${file.name}已存在，无需重复上传`)
      continue
    }
    if (toUpload.length >= remaining) {
      ElMessage.warning(`最多只能上传${MAX_IMAGE_COUNT}张图片，超出部分已忽略`)
      break
    }
    toUpload.push(file)
  }
  event.target.value = ''
  if (toUpload.length === 0) return

  formIsDirty.value = true
  // 逐个：先生成本地 blob 预览即时呈现，再立即上传，成功替换为真实 url 并释放 blob，失败移除并提示
  await Promise.all(toUpload.map(async file => {
    const uid = fileUid++
    const previewUrl = URL.createObjectURL(file)
    const item = reactive({
      uid,
      file,
      previewUrl,
      url: '',
      bound: false,
      uploading: true
    })
    form.fileList.push(item)
    uploadingCount.value++
    try {
      const formData = new FormData()
      formData.append('file', file)
      const res = await uploadOrderImageApi(formData)
      // 上传成功：用真实 url 替换预览，释放临时 blob
      URL.revokeObjectURL(item.previewUrl)
      item.id = res.id
      item.url = res.url
      item.previewUrl = res.url
      item.file = null
      item.uploading = false
    } catch {
      // 上传失败：从列表移除该项并提示
      const idx = form.fileList.findIndex(f => f.uid === uid)
      if (idx > -1) {
        URL.revokeObjectURL(form.fileList[idx].previewUrl)
        form.fileList.splice(idx, 1)
      }
      ElMessage.error(`图片${file.name}上传失败，已移除`)
    } finally {
      uploadingCount.value--
    }
  }))
}
// 删除附件图片，仅本地 blob 预览需释放内存，远程回显 url 不处理
function deleteFileImg(idx) {
  const target = form.fileList[idx]
  if (target && target.previewUrl && target.previewUrl.startsWith('blob:')) {
    URL.revokeObjectURL(target.previewUrl)
  }
  form.fileList.splice(idx, 1)
  formIsDirty.value = true
}

// ====================== 提交、取消、作废 ======================
async function handleSubmit() {
  if (submitLoading.value) return
  // 附件仍在上传中时拦截提交，避免丢图
  if (uploadingCount.value > 0) {
    ElMessage.warning('附件正在上传中，请稍候再提交')
    return
  }
  try {
    await orderFormRef.value.validate()
  } catch {
    return
  }
  if (selectedMasterList.value.length !== 1) {
    ElMessage.warning('请选择一位安装师傅')
    return
  }
  submitLoading.value = true
  try {
    const submitData = {
      taskType: form.taskType,
      description: form.description,
      customerName: form.customerName,
      customerPhone: form.customerPhone,
      addressArea: form.addressArea,
      addressDetail: form.addressDetail,
      orderStartTime: form.orderStartTime,
      orderEndTime: form.orderEndTime,
      masterIds: selectedMasterList.value.map(m => m.id).filter(id => id != null),
      adminRemark: form.adminRemark || ''
    }
    let savedOrder
    if (isEdit.value) {
      savedOrder = await updateOrderApi(orderId.value, submitData)
      ElMessage.success('更新成功')
    } else {
      savedOrder = await addOrderApi(submitData)
      ElMessage.success('新增成功')
    }
    const savedOrderId = savedOrder?.id || orderId.value
    const unboundFiles = form.fileList.filter(file => file.id && !file.bound)
    await Promise.all(unboundFiles.map((file, index) => bindOrderFileApi(file.id, savedOrderId, index)))
    formIsDirty.value = false
    returnToOrderList()
  } catch {
    // 拦截器已提示
  } finally {
    submitLoading.value = false
  }
}

function handleCancel() {
  if (formIsDirty.value) {
    leaveDialogVisible.value = true
  } else {
    returnToOrderList()
  }
}
function confirmLeave() {
  leaveDialogVisible.value = false
  formIsDirty.value = false
  returnToOrderList()
}

async function handleCancelOrder() {
  // 新增态无订单 ID，直接返回列表
  if (!orderId.value) {
    returnToOrderList()
    return
  }
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('作废后订单仍会保留用于业务追溯，请填写作废原因。', '作废订单', {
      confirmButtonText: '确认作废',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入作废原因',
      inputPattern: /\S+/,
      inputErrorMessage: '请填写作废原因',
      type: 'warning'
    })
    reason = result.value.trim()
  } catch {
    return
  }
  try {
    await cancelOrderApi(orderId.value, { reason })
    ElMessage.success('订单已作废')
    formIsDirty.value = false
    returnToOrderList()
  } catch {
    // 错误已在响应拦截器提示
  }
}

function returnToOrderList() {
  const backPath = window.history.state?.back
  if (typeof backPath === 'string' && /^\/orders(?:\?|$)/.test(backPath)) {
    router.back()
  } else {
    router.push({ name: 'Orders' })
  }
}
</script>

<style lang="scss" scoped>
.order-form-page {
  padding: 20px;
  background-color: #f8fafc;
  min-height: calc(100vh - 60px);

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

  .form-content {
    width: 100%;
  }

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

  .input-base {
    width: 100%;

    :deep(.el-input__wrapper) {
      border: 1px solid #d1d5db;
      border-radius: 6px;
      box-shadow: none;
      transition: border 0.2s;

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

    &:hover {
      border-color: #94a3b8;
    }

    &:focus {
      border-color: #409eff;
    }
  }

  .city-row {
    display: flex;
    align-items: flex-start;
    gap: 14px;
    width: 100%;

    .region-control {
      flex: 0 0 360px;
      min-width: 0;
    }

    .city-cascader {
      width: 100%;
    }

    .city-detail {
      flex: 1;
    }
  }

  .field-feedback {
    display: flex;
    align-items: center;
    gap: 4px;
    min-height: 24px;
    margin-top: 4px;
    color: var(--text-tertiary);
    font-size: 12px;

    &.is-error {
      color: var(--brand-danger);
    }
  }

  .time-row {
    display: flex;
    gap: 12px;

    .time-input {
      flex: 1;
    }
  }

  .master-select-wrap {
    display: flex;
    align-items: center;
  }

  // 附件上传提示条
  .upload-tip {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 100%;
    font-size: 12px;
    color: #64748b;
    margin-bottom: 12px;
    padding: 7px 12px;
    background: #f1f5f9;
    border-radius: 6px;
    border-left: 3px solid #3b82f6;
  }

  // 附件容器：图片卡片与上传框同行排列
  .upload-wrap {
    display: flex;
    flex-wrap: wrap;
    gap: 14px;
  }

  // 图片卡片
  .img-card {
    width: 120px;
    height: 120px;
    border-radius: 10px;
    overflow: hidden;
    position: relative;
    box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
    transition: transform 0.25s ease, box-shadow 0.25s ease;

    &:hover {
      transform: translateY(-3px);
      box-shadow: 0 8px 20px rgba(15, 23, 42, 0.18);

      .img-mask {
        opacity: 1;
      }
    }

    .preview-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }

    .img-mask {
      position: absolute;
      inset: 0;
      background: linear-gradient(180deg, rgba(15, 23, 42, 0) 0%, rgba(15, 23, 42, 0.6) 100%);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 4px;
      opacity: 0;
      transition: opacity 0.25s ease;
      cursor: pointer;

      .mask-icon {
        font-size: 20px;
        color: #fff;
        transition: color 0.2s;
      }

      .mask-text {
        font-size: 12px;
        color: #fff;
      }

      &:hover .mask-icon {
        color: #f87171;
      }
    }
  }

  // 上传框
  .file-upload-box {
    width: 120px;
    height: 120px;
    border: 1px dashed #cbd5e1;
    border-radius: 10px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    cursor: pointer;
    background: #f8fafc;
    transition: all 0.25s ease;

    &:hover {
      border-color: #3b82f6;
      background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);

      .upload-icon {
        transform: scale(1.15);
        color: #3b82f6;
      }

      span {
        color: #3b82f6;
      }
    }

    .upload-icon {
      color: #94a3b8;
      transition: all 0.25s ease;
    }

    span {
      font-size: 12px;
      color: #94a3b8;
      margin-top: 8px;
      transition: color 0.25s ease;
    }
  }

  .upload-list {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;

    .upload-item {
      position: relative;
      width: 120px;
      height: 120px;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      background: #fff;

      .upload-preview {
        flex: 1;
        display: flex;
        justify-content: center;
        align-items: center;
        background: #f8fafc;

        img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }

        &.video-preview {
          background: #1e293b;
        }
      }

      .video-name {
        padding: 4px 8px;
        font-size: 10px;
        color: #64748b;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        background: #f1f5f9;
      }

      :deep(.el-button) {
        position: absolute;
        top: 4px;
        right: 4px;
        z-index: 10;
        padding: 2px 4px;
        background: rgba(0, 0, 0, 0.5);
        color: #fff;
        font-size: 12px;
      }
    }

    .upload-add {
      width: 120px;
      height: 120px;
      border: 1px dashed #cbd5e1;
      border-radius: 8px;
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      cursor: pointer;
      transition: all 0.24s ease;
      background: #f8fafc;

      &:hover {
        border-color: #409eff;
        background: #eff6ff;
      }

      span {
        font-size: 12px;
        color: #94a3b8;
        margin-top: 6px;
      }
    }
  }

  .upload-input {
    display: none;
  }

  .form-footer {
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px solid #e5e7eb;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 30px;

    .footer-right {
      display: flex;
      gap: 14px;
    }

    :deep(.el-button) {
      padding: 8px 26px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }

  .master-pagination {
    margin-top: 16px;
    display: flex;
    justify-content: center;
  }

  .dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }

  // 师傅弹窗任务列换行样式
  .task-info {
    line-height: 1.8;
    font-size: 13px;
    color: #4b5563;
  }

  :deep(.el-form-item__label) {
    width: 120px;
    color: #374151;
  }

  :deep(.el-form-item__error) {
    font-size: 12px;
  }

  .master-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 16px;
    color: #64748b;

    .el-input {
      width: 320px;
    }
  }
}

.uploading-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #fff;
  background: rgba(15, 23, 42, 0.62);
  border-radius: 8px;
}

.form-load-error {
  margin: 24px auto;
  max-width: 960px;
}

@media (max-width: 1024px) {
  .order-form-page .city-row {
    flex-direction: column;
  }

  .order-form-page .city-cascader {
    width: 100% !important;
    flex: none;
  }

  .order-form-page .region-control {
    width: 100%;
    flex: none;
  }

  .order-form-page .time-row {
    flex-direction: column;
  }
}

@media (max-width: 768px) {
  .order-form-page {
    padding: 12px;
  }

  .order-form-page .page-header {
    grid-template-columns: 1fr;
    gap: 12px;

    .page-title {
      text-align: left;
    }
  }

  .upload-list .upload-item,
  .upload-list .upload-add,
  .file-upload-box {
    width: 100px;
    height: 100px;
  }

  .form-footer {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }

  .footer-right {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
