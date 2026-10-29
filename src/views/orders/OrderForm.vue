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
    <el-form v-else v-loading="pageLoading" ref="orderFormRef" :disabled="submitLoading || !editable" :model="form" :rules="rules" label-width="120px" class="form-content">
      <el-alert v-if="isEdit && !editable" title="已作废订单的其它信息只读；如需更换师傅，请在订单详情操作。" type="warning" :closable="false" show-icon />
      <el-alert v-else-if="isEdit && ['PENDING_REVIEW', 'REVIEWED'].includes(orderOrigin.statusCode)"
        title="此订单已完成。管理员更正会保留客户确认及完成状态；修改耗材清单将重新备货。" type="info" :closable="false" show-icon />
      <p v-if="lastEdit" class="edit-audit">上次管理员修改：{{ lastEdit.operatorName }} · {{ formatDateTime(lastEdit.editedAt, { seconds: true }) }}</p>
      <el-card class="form-card" shadow="never">
        <template #header>
          <span class="card-title"><el-icon>
              <Document />
            </el-icon>订单基础信息</span>
        </template>

        <el-form-item v-if="isEdit" label="订单来源"><OrderOrigin :order="orderOrigin" /></el-form-item>
        <el-alert v-if="orderOrigin.statusCode === 'PENDING_ASSIGNMENT'" title="请核对经销商提交的信息，补充订单时间并指派师傅后提交。" type="info" :closable="false" show-icon style="margin-bottom:24px" />
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
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="请填写空调型号、故障或施工要求" class="textarea-base" />
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
        <el-form-item label="订单地址" prop="addressDetail" required>
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

        <el-form-item label="项目地址">
          <el-input v-model="form.projectAddress" maxlength="500" show-word-limit
            placeholder="请输入项目名称、园区或施工地点" class="input-base" />
        </el-form-item>

        <el-form-item label="销售人员">
          <div class="salesperson-field">
            <el-select v-model="form.salesUserId" class="input-base" clearable filterable remote
              :remote-method="searchSalespersons" :loading="salespersonLoading" @end-reached="loadMoreSalespersons"
              placeholder="选填，搜索销售人员姓名或手机号">
              <el-option v-for="person in salespersons" :key="person.id" :value="person.id"
                :label="`${person.name}${person.maskedPhone ? `（${person.maskedPhone}）` : ''}`" />
            </el-select>
            <div v-if="salespersonError" class="field-feedback is-error">
              {{ salespersonError }} <el-button link type="primary" @click="retrySalespersons">重试</el-button>
            </div>
          </div>
        </el-form-item>

        <!-- 订单时间 双日期框 -->
        <el-form-item label="订单时间" prop="orderEndTime" required>
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
            <el-button plain :disabled="!canChangeInstaller || submitLoading || pageLoading" @click="openMasterDialog">添加</el-button>
          </div>
          <p v-if="isEdit && !canChangeInstaller" class="edit-tip">此页只读，请到订单详情更换师傅。</p>
          <!-- 已选师傅表格 -->
          <el-table v-if="selectedMasterList.length > 0" :data="selectedMasterList" border size="small"
            style="width: 100%;margin-top:10px">
            <el-table-column label="师傅姓名" prop="masterName" align="center" />
            <el-table-column label="性别" prop="gender" align="center" />
            <el-table-column label="年龄" prop="age" align="center" />
            <el-table-column label="手机号" prop="masterPhone" align="center" />
            <el-table-column label="操作" align="center" width="100">
              <template #default="scope">
                <el-button v-if="canChangeInstaller" text type="danger" @click="removeSelectedMaster(scope.$index)">移除</el-button>
                <span v-else>不可修改</span>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>

        <el-form-item label="订单附件">
          <OrderEditorFiles v-model="form.fileList" :disabled="submitLoading || !editable" />
        </el-form-item>
        <el-form-item label="管理员备注">
          <el-input v-model="form.adminRemark" type="textarea" :rows="2" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-card>

      <el-card v-if="isEdit" class="form-card" shadow="never">
        <template #header><span class="card-title">施工进度</span></template>
        <p class="edit-tip">保留原施工师傅和提交时间，修改内容与图片将在确认提交后一起保存。已确认内容发生变化后需客户重新确认，订单状态不受影响。</p>
        <el-empty v-if="!progressEntries.length" description="师傅尚未提交施工记录" :image-size="60" />
        <section v-for="entry in progressEntries" :key="entry.id" class="progress-editor">
          <div class="progress-meta">
            <el-tag>{{ entry.type === 'COMPLETION' ? '历史完工记录' : '施工记录' }}</el-tag>
            <span>提交时间：{{ formatDateTime(entry.submittedAt, { seconds: true }) }}</span>
            <span>原师傅编号：{{ entry.installerUserId }}</span>
            <span>{{ entry.confirmationStatus === 'ADMIN_EDITED' ? '管理员已修订' : entry.customerConfirmed ? '客户已确认' : entry.confirmationStatus === 'CHANGED' ? '待重新确认' : '客户待确认' }}</span>
            <span v-if="entry.customerConfirmedAt">{{ entry.customerConfirmed ? '确认时间' : '上次确认' }}：{{ formatDateTime(entry.customerConfirmedAt, { seconds: true }) }}</span>
            <span v-if="entry.adminEditedAt">管理员修订：{{ formatDateTime(entry.adminEditedAt, { seconds: true }) }}</span>
          </div>
          <el-input v-model="entry.description" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="施工说明" />
          <OrderEditorFiles v-model="entry.files" progress :disabled="submitLoading || !editable" />
        </section>
      </el-card>

      <el-card v-if="isEdit" class="form-card" shadow="never">
        <template #header><span class="card-title">耗材清单</span></template>
        <el-alert v-if="materialsChanged" :title="materialDraft.items.length
          ? '耗材清单已变更。保存后将回到待备货，并清除原备货数量与勾选结果。'
          : '耗材清单已清空。保存后将作废当前申请并释放预占库存。'" type="warning" :closable="false" show-icon />
        <OrderEditorMaterials v-model="materialDraft" :request="materialRequest" :disabled="submitLoading || !editable" />
      </el-card>
      <el-alert v-if="submitError" :title="submitError" type="error" :closable="false" show-icon>
        <el-button v-if="saveConflict" link type="primary" @click="reloadAfterConflict">重新加载订单</el-button>
      </el-alert>

      <!-- 底部操作区 -->
      <div class="form-footer">
        <el-button v-if="isEdit && editable && orderOrigin.statusCode !== 'REVIEWED'" text type="danger" @click="handleCancelOrder">作废订单</el-button>
        <div class="footer-right">
          <el-button dashed plain type="default" :icon="ArrowLeft" @click="handleCancel">取消</el-button>
          <el-button dashed plain type="primary" @click="handleSubmit" :icon="Check"
            :loading="submitLoading" :disabled="pageLoading || !editable || uploadingCount > 0 || hasFailedUpload">确认提交</el-button>
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
        <el-table-column prop="unfinishedOrders" label="任务" align="left">
          <template #default="scope">
            <div v-if="scope.row.unfinishedOrderCount > 0" class="task-info">
              <div v-for="task in scope.row.unfinishedOrders" :key="task.orderId" class="task-item"
                :title="`订单号：${task.orderNo}；状态：${task.status}`">
                <span class="task-name">{{ task.taskType }}</span>
                <span class="task-time">{{ formatMasterTaskTime(task.orderStartTime) }}</span>
              </div>
            </div>
            <div v-else class="task-info task-info--empty">暂无任务</div>
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
          <el-button type="primary" :disabled="!canChangeInstaller || submitLoading || pageLoading" @click="confirmSelectMaster">确认选择</el-button>
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
  ArrowLeft, Document, Check, Search
} from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import {
  getAdminOrderEditorApi, saveAdminOrderEditorApi, addOrderApi,
  bindOrderFileApi, getMasterListApi, getSalespersonOptionsApi, cancelOrderApi
} from '@/api/orders'
import { getRegionTreeApi } from '@/api/regions'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'
import { formatDateTime, formatDateTimeInput, parseDateTime } from '@/utils/format'
import OrderOrigin from './OrderOrigin.vue'
import OrderEditorFiles from './OrderEditorFiles.vue'
import OrderEditorMaterials from './OrderEditorMaterials.vue'

const orderOrigin = ref({})
const editable = ref(true)
const editorRevision = ref('')
const lastEdit = ref(null)
const progressEntries = ref([])
const materialRequest = ref(null)
const materialDraft = ref({ items: [], remark: '' })
const initialDraft = ref(null)
const submitError = ref('')
const saveConflict = ref(false)
let pendingSave = null

const router = useRouter()
const route = useRoute()
const orderFormRef = ref(null)
const masterTableRef = ref(null)

// 基础信息与本次编辑草稿。
const isEdit = ref(false)
const orderId = ref('')
const submitLoading = ref(false)
const pageLoading = ref(false)
const loadError = ref('')
const canChangeInstaller = computed(() => editable.value)

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
// 所有板块的图片上传状态共同决定是否可提交。
const allDraftFiles = computed(() => [...form.fileList, ...progressEntries.value.flatMap(entry => entry.files)])
const uploadingCount = computed(() => allDraftFiles.value.filter(file => file.uploading).length)
const hasFailedUpload = computed(() => allDraftFiles.value.some(file => file.error || !file.id))
const materialsChanged = computed(() => !!initialDraft.value && materialSignature(materialDraft.value.items)
  !== materialSignature(initialDraft.value.materials.items))

const cityOptions = ref([])
const regionLoading = ref(false)
const regionError = ref('')
const salespersons = ref([])
const salespersonLoading = ref(false)
const salespersonError = ref('')
const salespersonPage = ref(0)
const salespersonTotal = ref(0)
const salespersonKeyword = ref('')
let salespersonRequestVersion = 0
const salespersonPageSize = 30
async function fetchSalespersons(page, keyword, replace) {
  const version = replace ? ++salespersonRequestVersion : salespersonRequestVersion
  salespersonLoading.value = true
  salespersonError.value = ''
  try {
    const result = await getSalespersonOptionsApi({ page, pageSize: salespersonPageSize, keyword })
    if (version !== salespersonRequestVersion) return
    const found = result.list || []
    const selected = salespersons.value.find(person => person.id === form.salesUserId)
    const merged = replace ? found : [...salespersons.value, ...found]
    salespersons.value = [...new Map([
      ...(selected ? [[selected.id, selected]] : []),
      ...merged.map(person => [person.id, person])
    ]).values()]
    salespersonPage.value = page
    salespersonTotal.value = Number(result.total) || 0
  } catch {
    if (version === salespersonRequestVersion) {
      salespersonError.value = '销售人员加载失败'
      if (replace) salespersons.value = []
    }
  } finally {
    if (version === salespersonRequestVersion) salespersonLoading.value = false
  }
}
function searchSalespersons(keyword = '') {
  salespersonKeyword.value = keyword
  salespersonPage.value = 0
  return fetchSalespersons(1, keyword, true)
}
function loadMoreSalespersons(direction) {
  if (direction !== 'bottom' || salespersonLoading.value
    || salespersonPage.value * salespersonPageSize >= salespersonTotal.value) return
  return fetchSalespersons(salespersonPage.value + 1, salespersonKeyword.value, false)
}
function retrySalespersons() {
  return salespersonPage.value ? fetchSalespersons(salespersonPage.value + 1, salespersonKeyword.value, false)
    : searchSalespersons(salespersonKeyword.value)
}

// 图片只在草稿中增删，确认提交后才变更业务关联。
const form = reactive({
  taskType: '',
  description: '',
  customerName: '',
  customerPhone: '',
  addressArea: [],
  addressDetail: '',
  projectAddress: '',
  salesUserId: null,
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
    { min: 1, max: 64, message: '姓名长度1~64字符', trigger: 'blur' }
  ],
  customerPhone: [
    { required: true, message: '请输入客户手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  addressDetail: [{
    required: true,
    validator: (rule, value, callback) => {
      if (!Array.isArray(form.addressArea) || form.addressArea.length < 3) callback(new Error('请选择完整的省市区'))
      else if (!value?.trim()) callback(new Error('请填写详细街道门牌号'))
      else callback()
    },
    trigger: ['blur', 'change']
  }],
  orderEndTime: [{
    required: true,
    validator: (rule, value, callback) => {
      if (isEdit.value && orderOrigin.value.statusCode === 'PENDING_ASSIGNMENT' && !selectedMasterList.value.length
        && !form.orderStartTime && !value) return callback()
      if (!form.orderStartTime || !value) callback(new Error('请选择完整的上门时间范围'))
      else if (!parseDateTime(value) || !parseDateTime(form.orderStartTime)) callback(new Error('请选择有效的上门时间'))
      else if (parseDateTime(value).getTime() <= parseDateTime(form.orderStartTime).getTime()) callback(new Error('结束时间必须晚于开始时间'))
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

watch([form, progressEntries, materialDraft, selectedMasterList], () => {
  if (pageLoading.value) return
  formIsDirty.value = initialDraft.value ? JSON.stringify(currentDraft()) !== JSON.stringify(initialDraft.value) : true
  pendingSave = null
}, { deep: true })

onMounted(async () => {
  const tasks = [loadRegions(), searchSalespersons()]
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
  masterDialogVisible.value = false
  tempSelectMaster.value = []
  loadError.value = ''
  try {
    const result = await getAdminOrderEditorApi(orderId.value)
    const res = result.order
    editorRevision.value = result.revision
    editable.value = result.editable
    lastEdit.value = result.lastEdit || null
    submitError.value = ''
    saveConflict.value = false
    pendingSave = null
    progressEntries.value = (result.progress || []).map(entry => ({ ...entry, files: (entry.images || []).map(toDraftFile) }))
    materialRequest.value = result.materialRequest || null
    materialDraft.value = {
      items: (result.materialRequest?.materials || []).map(item => ({ ...item, productId: item.productId,
        quantity: Number(item.count), name: item.name, skuId: item.skuId, stock: item.stock })),
      remark: result.materialRequest?.remark || ''
    }
    orderOrigin.value = { orderSource: res.orderSource, dealerName: res.dealerName,
      salesUserId: res.salesUserId, salesName: res.salesName,
      creatorUserId: res.creatorUserId, creatorName: res.creatorName, statusCode: res.statusCode }
    if (res.salesUserId && !salespersons.value.some(person => person.id === res.salesUserId)) {
      salespersons.value.push({ id: res.salesUserId, name: res.salesName || `销售员 #${res.salesUserId}` })
    }
    // 回显 fileList：远程图片 url 直接作为 previewUrl，file 为 null
    const remoteFiles = res.fileList || res.images || []
    const fileList = remoteFiles.map(toDraftFile)

    Object.assign(form, {
      taskType: res.taskType || '',
      description: res.description || '',
      addressArea: res.addressArea || [],
      addressDetail: res.addressDetail || '',
      projectAddress: res.projectAddress || '',
      salesUserId: res.salesUserId || null,
      orderStartTime: formatDateTimeInput(res.orderStartTime),
      orderEndTime: formatDateTimeInput(res.orderEndTime),
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
    await nextTick()
    initialDraft.value = currentDraft()
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

function normalizeMaster(master = {}) {
  const unfinishedOrders = Array.isArray(master.unfinishedOrders) ? master.unfinishedOrders : []
  return {
    ...master,
    unfinishedOrders,
    unfinishedOrderCount: Number(master.unfinishedOrderCount || unfinishedOrders.length || 0)
  }
}

function formatMasterTaskTime(value) {
  if (!value) return '未设置上门时间'
  return formatDateTime(value, { seconds: true })
}

// 打开指派师傅弹窗，拉取师傅列表并回显已选
async function openMasterDialog() {
  if (!canChangeInstaller.value || submitLoading.value || pageLoading.value) return
  masterDialogVisible.value = true
  masterPage.currentPage = 1
  masterSearch.value = ''
  tempSelectMaster.value = [...selectedMasterList.value]
  masterLoading.value = true
  try {
    const res = await getMasterListApi()
    allMasterList.value = (Array.isArray(res) ? res : (res.list || [])).map(normalizeMaster)
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
  if (isRestoring.value || !canChangeInstaller.value || submitLoading.value || pageLoading.value) return
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

// 确认选择仅修改草稿；提交订单后才持久化。
async function confirmSelectMaster() {
  if (!canChangeInstaller.value || submitLoading.value || pageLoading.value) return
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
  if (!canChangeInstaller.value || submitLoading.value || pageLoading.value) return
  selectedMasterList.value.splice(index, 1)
  formIsDirty.value = true
  ElMessage.success('已移除该师傅')
}

function toDraftFile(item) {
  const value = typeof item === 'string' ? { url: item } : item
  return { ...value, uid: value.id, id: value.id, file: null,
    url: value.originalUrl || value.url, previewUrl: value.thumbnailUrl || value.displayUrl || value.url,
    bound: true, uploading: false, error: false }
}
function orderPayload() {
  return { taskType: form.taskType, description: form.description, customerName: form.customerName,
    customerPhone: form.customerPhone, addressArea: [...form.addressArea], addressDetail: form.addressDetail,
    projectAddress: form.projectAddress, salesUserId: form.salesUserId || null,
    orderStartTime: form.orderStartTime, orderEndTime: form.orderEndTime,
    masterIds: selectedMasterList.value.map(master => master.id).filter(id => id != null), adminRemark: form.adminRemark || '' }
}
function currentDraft() {
  return { order: orderPayload(), attachmentFileIds: form.fileList.map(file => file.id),
    progress: progressEntries.value.map(entry => ({ id: entry.id, description: entry.description, fileIds: entry.files.map(file => file.id) })),
    materials: { items: materialDraft.value.items.map(item => ({ productId: item.productId, skuId: item.skuId,
      quantity: item.quantity })), remark: materialDraft.value.remark } }
}
function materialSignature(items) {
  return JSON.stringify(items.map(item => [Number(item.productId), item.skuId == null ? null : Number(item.skuId), Number(item.quantity)])
    .sort((a, b) => a[0] - b[0] || (a[1] || 0) - (b[1] || 0)))
}
function createEditRequest() {
  const draft = currentDraft()
  const request = { requestId: globalThis.crypto?.randomUUID?.() || `edit_${Date.now()}_${Math.random().toString(36).slice(2)}`,
    revision: editorRevision.value }
  if (JSON.stringify(draft.order) !== JSON.stringify(initialDraft.value.order)) request.order = draft.order
  if (JSON.stringify(draft.attachmentFileIds) !== JSON.stringify(initialDraft.value.attachmentFileIds)) request.attachmentFileIds = draft.attachmentFileIds
  const originalProgress = new Map(initialDraft.value.progress.map(entry => [entry.id, entry]))
  const changedProgress = draft.progress.filter(entry => JSON.stringify(entry) !== JSON.stringify(originalProgress.get(entry.id)))
  if (changedProgress.length) request.progress = changedProgress
  if (materialsChanged.value || draft.materials.remark !== initialDraft.value.materials.remark) request.materials = draft.materials
  return request
}
async function reloadAfterConflict() {
  try { await ElMessageBox.confirm('重新加载会放弃当前未保存的修改，是否继续？', '订单已变化', { type: 'warning' }) }
  catch { return }
  await loadEditData()
}

// ====================== 提交、取消、作废 ======================
async function handleSubmit() {
  if (submitLoading.value || !editable.value || pageLoading.value || loadError.value) return
  // 附件仍在上传中时拦截提交，避免丢图
  if (uploadingCount.value > 0) {
    ElMessage.warning('附件正在上传中，请稍候再提交')
    return
  }
  if (hasFailedUpload.value) {
    ElMessage.warning('请先重试或删除上传失败的图片')
    return
  }
  if (isEdit.value && progressEntries.value.some(entry => !entry.description?.trim())) {
    ElMessage.warning('请填写每条施工记录的说明')
    return
  }
  if (isEdit.value && materialDraft.value.items.some(item => !Number.isInteger(item.quantity) || item.quantity < 1)) {
    ElMessage.warning('耗材申请数量必须为正整数')
    return
  }
  try {
    await orderFormRef.value.validate()
  } catch {
    return
  }
  if (selectedMasterList.value.length !== 1 && !(isEdit.value && orderOrigin.value.statusCode === 'PENDING_ASSIGNMENT' && !selectedMasterList.value.length)) {
    ElMessage.warning('请选择一位安装师傅')
    return
  }
  submitLoading.value = true
  submitError.value = ''
  saveConflict.value = false
  try {
    if (isEdit.value) {
      if (materialsChanged.value) {
        await ElMessageBox.confirm(materialDraft.value.items.length
          ? '耗材清单发生变化，保存后将重新备货。订单原有完成状态和客户确认记录会保留。确认提交吗？'
          : '清空清单会作废当前耗材申请并释放预占库存。确认提交吗？', '确认耗材变更', { type: 'warning' })
      }
      pendingSave ||= createEditRequest()
      await saveAdminOrderEditorApi(orderId.value, pendingSave)
    } else {
      const savedOrder = await addOrderApi(orderPayload())
      await Promise.all(form.fileList.map((file, index) => bindOrderFileApi(file.id, savedOrder.id, index)))
    }
    ElMessage.success(isEdit.value ? '订单修改已全部保存' : '新增成功')
    pendingSave = null
    formIsDirty.value = false
    returnToOrderList()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    saveConflict.value = error.response?.data?.error === 'ORDER_EDIT_CONFLICT'
      || error.response?.status === 409 && /已被修改|重新加载/.test(error.response?.data?.message || error.message || '')
    submitError.value = error.response?.data?.message || error.message || '保存失败，请检查后重试。'
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
.progress-editor + .progress-editor { margin-top: 24px; border-top: 1px solid #e2e8f0; padding-top: 24px; }
.progress-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 12px; color: #64748b; }
.edit-tip, .edit-audit { color: #64748b; font-size: 13px; line-height: 1.7; }
.el-alert { margin-bottom: 16px; }
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

  .salesperson-field {
    width: 100%;
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

    .task-item {
      display: flex;
      align-items: baseline;
      gap: 8px;
      min-width: 250px;
    }

    .task-name {
      color: #374151;
      white-space: nowrap;
    }

    .task-time {
      color: #6b7280;
      white-space: nowrap;
    }

    &--empty {
      color: #9ca3af;
    }
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
