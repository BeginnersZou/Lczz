<template>
  <div class="preparation-page">
    <div class="page-header">
      <div>
        <h2>订单备货</h2>
        <p>统一查看工程订单耗材（W）与师傅自助下单（A），两类单据独立管理</p>
      </div>
      <el-button type="info" plain :icon="Download" :loading="exportLoading" :disabled="tableLoading" @click="handleBatchExport">
        导出筛选结果
      </el-button>
    </div>

    <el-card class="filter-card" shadow="never">
      <div class="filters">
        <el-input v-model="searchKeyword" class="keyword-input" clearable :disabled="tableLoading"
          placeholder="搜索订单编号、名称、师傅或耗材" @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="searchSource" clearable :disabled="tableLoading" placeholder="全部来源" @change="handleSearch">
          <el-option label="W · 工程订单耗材" value="W" />
          <el-option label="A · 师傅自助下单" value="A" />
        </el-select>
        <el-select v-model="searchStatus" clearable :disabled="tableLoading" placeholder="全部状态" @change="handleSearch">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" :icon="Search" :loading="tableLoading" @click="handleSearch">搜索</el-button>
        <el-button plain :icon="Refresh" :disabled="tableLoading" @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div v-if="loadError" class="feedback-state">
        <el-icon size="28"><Warning /></el-icon>
        <p>{{ loadError }}</p>
        <el-button type="primary" @click="loadList">重新加载</el-button>
      </div>
      <el-table v-else v-loading="tableLoading" :data="pagedList" border stripe
        table-layout="fixed" class="business-table" empty-text="当前筛选条件下暂无备货记录">
        <el-table-column label="序号" width="72" align="center">
          <template #default="scope">{{ (currentPage - 1) * pageSize + scope.$index + 1 }}</template>
        </el-table-column>
        <el-table-column label="来源" width="150" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.source === 'A' ? 'success' : 'primary'" effect="light">
              {{ scope.row.source }} · {{ scope.row.source === 'A' ? '自助下单' : '工程耗材' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="订单名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="订单编号" min-width="220" show-overflow-tooltip>
          <template #default="scope">
            <button class="copy-button" type="button" @click="copyOrderId(scope.row.orderNo)">
              <span>{{ scope.row.orderNo }}</span><el-icon><DocumentCopy /></el-icon>
            </button>
          </template>
        </el-table-column>
        <el-table-column label="下单师傅 / 客户" min-width="180">
          <template #default="scope">
            <div class="person-cell">
              <strong>{{ scope.row.submitterName || '-' }}</strong>
              <span v-if="scope.row.customerName">客户：{{ scope.row.customerName }}</span>
              <span v-else>师傅自助下单</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="耗材明细" min-width="320">
          <template #default="scope">
            <div class="material-summary">
              <strong>{{ scope.row.itemCount || 0 }} 种耗材</strong>
              <span>规格、单位及数量请查看明细</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.statusCode)" effect="light">{{ scope.row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="scope">
            <div class="row-actions">
              <el-button type="primary" link :loading="openingAction === `${rowKey(scope.row)}:view`" :disabled="openingId !== ''" @click="handleView(scope.row)">查看</el-button>
              <el-button type="primary" link :loading="exportingId === rowKey(scope.row)" :disabled="exportingId !== ''" @click="handleDetailExport(scope.row)">导出</el-button>
              <el-button v-if="canPrepare(scope.row)" type="primary" size="small" :loading="openingAction === `${rowKey(scope.row)}:prepare`" :disabled="openingId !== ''" @click="handlePrepare(scope.row)">备货</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="total > 0" class="pagination-wrap">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" background
          layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="total"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" :title="dialogEditable ? '备货处理' : '备货详情'" width="760px" destroy-on-close>
      <div v-loading="dialogLoading" class="detail-content">
        <template v-if="currentRow">
          <div class="summary-grid">
            <div><span>来源</span><el-tag :type="currentRow.source === 'A' ? 'success' : 'primary'">{{ currentRow.sourceLabel }}</el-tag></div>
            <div><span>状态</span><el-tag :type="getStatusType(currentRow.statusCode)">{{ currentRow.statusLabel }}</el-tag></div>
            <div><span>订单编号</span><strong>{{ currentRow.orderNo }}</strong></div>
            <div><span>订单名称</span><strong>{{ currentRow.productName }}</strong></div>
            <div><span>下单人/师傅</span><strong>{{ currentRow.submitterName || '-' }}</strong></div>
            <div><span>客户</span><strong>{{ currentRow.customerName || '不关联工程客户' }}</strong></div>
            <div><span>创建时间</span><strong>{{ formatDateTime(currentRow.createTime) }}</strong></div>
            <div v-if="currentRow.remark"><span>申请备注</span><strong>{{ currentRow.remark }}</strong></div>
          </div>
          <div class="section-title">
            <span>耗材清单</span><small>共 {{ currentRow.materials.length }} 项</small>
          </div>
          <el-table :data="currentRow.materials" border size="small" empty-text="暂无耗材明细">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="name" label="耗材名称" min-width="130" />
            <el-table-column prop="skuCode" label="SKU编码" min-width="120">
              <template #default="scope">{{ scope.row.skuCode || '-' }}</template>
            </el-table-column>
            <el-table-column prop="spec" label="规格" min-width="150">
              <template #default="scope">{{ scope.row.spec || '通用规格' }}</template>
            </el-table-column>
            <el-table-column label="申请数量" width="110" align="center">
              <template #default="scope">{{ formatQuantity(scope.row.count) }} {{ scope.row.unit }}</template>
            </el-table-column>
            <el-table-column v-if="currentRow.source === 'W'" label="备货状态" width="110" align="center">
              <template #default="scope">
                <el-checkbox v-model="scope.row.checked" :disabled="!dialogEditable">{{ scope.row.checked ? '已备' : '待备' }}</el-checkbox>
              </template>
            </el-table-column>
          </el-table>
          <el-alert v-if="currentRow.source === 'A'" class="readonly-tip" type="info" :closable="false"
            title="A 类为师傅自助取货订单，仅供核对和导出，不关联工程订单，也不在此执行工程备货操作。" />
        </template>
      </div>
      <template #footer>
        <el-button plain @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" plain :icon="Download" :loading="exportingId === rowKey(currentRow)" :disabled="preparing || dialogLoading" @click="handleDetailExport(currentRow)">导出明细</el-button>
        <el-button v-if="dialogEditable" type="primary" :loading="preparing" :disabled="dialogLoading" @click="savePreparation">
          {{ allChecked ? '保存并完成备货' : '保存备货进度' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentCopy, Download, Refresh, Search, Warning } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  exportPreparationApi,
  exportPreparationDetailApi,
  finishPreparationApi,
  getPreparationDetailApi,
  getPreparationListApi,
  submitPreparationApi
} from '@/api/preparation'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const searchKeyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const searchSource = ref(typeof route.query.source === 'string' ? route.query.source : '')
const searchStatus = ref(typeof route.query.status === 'string' ? route.query.status : '')
const currentPage = ref(Number(route.query.page) || 1)
const pageSize = ref(Number(route.query.pageSize) || 10)
const total = ref(0)
const pagedList = ref([])
const tableLoading = ref(false)
const loadError = ref('')
const exportLoading = ref(false)
const exportingId = ref('')
const openingId = ref('')
const openingAction = ref('')
const detailVisible = ref(false)
const dialogLoading = ref(false)
const dialogEditable = ref(false)
const currentRow = ref(null)
const preparing = ref(false)

const statusOptions = [
  { label: '待备货', value: 'PENDING' },
  { label: '备货中', value: 'PREPARING' },
  { label: '已备货', value: 'DONE' },
  { label: '已作废', value: 'VOIDED' },
  { label: '已下单（A类）', value: 'ORDERED' }
]
const allChecked = computed(() => currentRow.value?.materials?.length > 0
  && currentRow.value.materials.every(item => item.checked))

function queryParams(withPage = true) {
  return {
    ...(withPage ? { page: currentPage.value, pageSize: pageSize.value } : {}),
    ...(searchKeyword.value.trim() ? { keyword: searchKeyword.value.trim() } : {}),
    ...(searchSource.value ? { source: searchSource.value } : {}),
    ...(searchStatus.value ? { status: searchStatus.value } : {})
  }
}

async function loadList() {
  syncRoute()
  tableLoading.value = true
  loadError.value = ''
  try {
    const data = await getPreparationListApi(queryParams())
    pagedList.value = data?.list || []
    total.value = Number(data?.total || 0)
  } catch {
    pagedList.value = []
    total.value = 0
    loadError.value = '备货数据加载失败，请检查网络后重试。'
  } finally {
    tableLoading.value = false
  }
}

function syncRoute() {
  router.replace({ query: {
    ...queryParams(false),
    ...(currentPage.value > 1 ? { page: currentPage.value } : {}),
    ...(pageSize.value !== 10 ? { pageSize: pageSize.value } : {})
  } })
}
function handleSearch() { currentPage.value = 1; loadList() }
function handleReset() {
  searchKeyword.value = ''
  searchSource.value = ''
  searchStatus.value = ''
  currentPage.value = 1
  loadList()
}
function handleSizeChange(value) { pageSize.value = value; currentPage.value = 1; loadList() }
function handleCurrentChange(value) { currentPage.value = value; loadList() }
function canPrepare(row) { return row.source === 'W' && ['PENDING', 'PREPARING'].includes(row.statusCode) }
function rowKey(row) { return row ? `${row.source}:${row.id}` : '' }
function formatQuantity(value) {
  const number = Number(value || 0)
  return Number.isInteger(number) ? String(number) : String(number).replace(/0+$/, '').replace(/\.$/, '')
}
function getStatusType(code) {
  return { PENDING: 'warning', PREPARING: 'primary', DONE: 'success', VOIDED: 'info', ORDERED: 'success' }[code] || 'info'
}

async function openDetail(row, editable) {
  if (openingId.value) return
  openingId.value = rowKey(row)
  openingAction.value = `${openingId.value}:${editable ? 'prepare' : 'view'}`
  currentRow.value = { ...row, materials: [] }
  dialogEditable.value = editable
  detailVisible.value = true
  dialogLoading.value = true
  try {
    const data = await getPreparationDetailApi(row.id, row.source)
    currentRow.value = JSON.parse(JSON.stringify(data))
  } catch {
    detailVisible.value = false
  } finally {
    dialogLoading.value = false
    openingId.value = ''
    openingAction.value = ''
  }
}
function handleView(row) { return openDetail(row, false) }
function handlePrepare(row) { return openDetail(row, true) }

async function savePreparation() {
  if (!currentRow.value || preparing.value) return
  const shouldFinish = allChecked.value
  if (shouldFinish) {
    try {
      await ElMessageBox.confirm('确认全部耗材已经备齐并完成本单备货？', '完成备货', { type: 'warning' })
    } catch { return }
  }
  preparing.value = true
  try {
    await submitPreparationApi(currentRow.value.id, {
      materials: currentRow.value.materials.map(item => ({ id: item.id, checked: Boolean(item.checked) }))
    })
    if (shouldFinish) await finishPreparationApi(currentRow.value.id)
    ElMessage.success(shouldFinish ? '备货已完成' : '备货进度已保存')
    detailVisible.value = false
    await loadList()
  } finally {
    preparing.value = false
  }
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
async function handleBatchExport() {
  if (exportLoading.value) return
  if (total.value === 0) { ElMessage.warning('当前筛选条件下没有可导出的备货记录'); return }
  exportLoading.value = true
  try {
    const blob = await exportPreparationApi(queryParams(false))
    downloadBlob(blob, `备货总表_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.csv`)
    ElMessage.success('筛选结果导出成功')
  } finally { exportLoading.value = false }
}
async function handleDetailExport(row) {
  if (!row || exportingId.value) return
  exportingId.value = rowKey(row)
  try {
    const blob = await exportPreparationDetailApi(row.id, row.source)
    downloadBlob(blob, `备货明细_${row.orderNo}.csv`)
    ElMessage.success('备货明细导出成功')
  } finally { exportingId.value = '' }
}

async function copyOrderId(value) {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success('订单编号已复制')
  } catch { ElMessage.error('复制失败，请手动复制') }
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
.preparation-page { min-height: 100vh; padding: 24px; background: #f6f8fb; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 18px; }
.page-header h2 { margin: 0 0 8px; color: #172033; font-size: 26px; }
.page-header p { margin: 0; color: #667085; font-size: 14px; }
.filter-card, .table-card { border: 0; border-radius: 10px; }
.filter-card { margin-bottom: 16px; }
.filters { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.filters .keyword-input { width: 360px; }
.filters :deep(.el-select) { width: 190px; }
.table-card :deep(.el-card__body) { padding: 0; overflow-x: auto; }
.business-table { width: 100%; min-width: 1540px; }
.business-table :deep(th.el-table__cell) { height: 52px; color: #344054; background: #f8fafc; }
.copy-button { display: inline-flex; align-items: center; gap: 6px; max-width: 100%; padding: 0; color: #175cd3; background: transparent; border: 0; cursor: pointer; }
.copy-button span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.person-cell { display: flex; flex-direction: column; gap: 5px; }
.person-cell strong { color: #344054; font-weight: 600; }
.person-cell span { color: #98a2b3; font-size: 12px; }
.material-summary { display: flex; flex-direction: column; gap: 5px; }
.material-summary strong { color: #344054; font-size: 13px; }
.material-summary span { color: #98a2b3; font-size: 12px; }
.row-actions { display: flex; align-items: center; justify-content: center; gap: 8px; white-space: nowrap; }
.row-actions :deep(.el-button + .el-button) { margin-left: 0; }
.pagination-wrap { display: flex; justify-content: flex-end; padding: 18px 20px; }
.feedback-state { display: flex; min-height: 280px; flex-direction: column; align-items: center; justify-content: center; color: #667085; }
.feedback-state p { margin: 12px 0 18px; }
.detail-content { min-height: 220px; }
.summary-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 26px; padding: 18px; background: #f8fafc; border-radius: 8px; }
.summary-grid > div { display: grid; grid-template-columns: 100px minmax(0, 1fr); align-items: center; min-height: 28px; }
.summary-grid span { color: #667085; font-size: 13px; }
.summary-grid strong { overflow-wrap: anywhere; color: #344054; font-size: 14px; font-weight: 600; }
.section-title { display: flex; align-items: baseline; justify-content: space-between; margin: 24px 0 12px; color: #1d2939; font-size: 16px; font-weight: 600; }
.section-title small { color: #98a2b3; font-size: 12px; font-weight: 400; }
.readonly-tip { margin-top: 16px; }
@media (max-width: 900px) {
  .preparation-page { padding: 14px; }
  .page-header { align-items: flex-start; flex-direction: column; }
  .filters .keyword-input, .filters :deep(.el-select) { width: 100%; }
  .summary-grid { grid-template-columns: 1fr; }
}
</style>
