<template>
  <div class="order-page">
    <!-- 顶部标题操作栏 左右分栏 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">订单管理</h2>
        <span class="page-desc">统一管理空调安装订单，支持新增、编辑、检索、导出</span>
      </div>
      <div class="header-right">
        <el-button type="info" plain :icon="Download" :loading="exportLoading" @click="handleBatchExport">
          导出筛选结果
        </el-button>
        <el-button type="primary" :icon="Plus" @click="handleAddOrder">
          新增订单
        </el-button>
      </div>
    </div>

    <!-- 搜索卡片 -->
    <el-card class="search-card" shadow="never">
      <div class="search-bar">
        <div class="search-item">
          <el-input v-model="searchKeyword" placeholder="输入订单编号、客户姓名检索订单" clearable class="search-input"
            @keyup.enter="handleSearch">
            <template #prefix>
              <el-icon>
                <Search />
              </el-icon>
            </template>
          </el-input>
          <el-select v-model="searchStatus" placeholder="全部状态" clearable class="status-select" @change="handleSearch">
            <el-option label="待上门" value="PENDING_VISIT" />
            <el-option label="处理中" value="IN_PROGRESS" />
            <el-option label="待评价" value="PENDING_REVIEW" />
            <el-option label="已评价" value="REVIEWED" />
            <el-option label="已作废" value="CANCELLED" />
          </el-select>
          <el-button type="primary" @click="handleSearch">
            <el-icon>
              <Search />
            </el-icon> 搜索
          </el-button>
          <el-button text @click="handleReset">
            <el-icon>
              <Refresh />
            </el-icon> 重置
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 订单表格主体（替换原卡片列表） -->
    <el-card shadow="never" class="table-card">
      <div v-if="loadError" class="error-state">
        <span>{{ loadError }}</span>
        <el-button type="primary" link @click="loadList">重新加载</el-button>
      </div>
      <el-table v-else v-loading="tableLoading" :data="orders" border stripe table-layout="fixed" class="business-table"
        empty-text="暂无订单，点击上方按钮新增第一条订单">
        <!-- 序号 -->
        <el-table-column label="序号" align="center" width="72">
          <template #default="scope">
            {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <!-- 订单名称 -->
        <el-table-column label="任务信息" align="left" min-width="240">
          <template #default="scope">
            <div class="primary-cell">{{ scope.row.productName || scope.row.taskType || '-' }}</div>
            <div class="secondary-cell">{{ scope.row.description || '暂无描述' }}</div>
          </template>
        </el-table-column>
        <!-- 订单编号 支持点击复制 -->
        <el-table-column prop="orderNo" label="订单编号" align="left" min-width="240" show-overflow-tooltip>
          <template #default="scope">
            <span class="table-order-id code-cell" role="button" tabindex="0" @click="copyOrderId(scope.row.orderNo || scope.row.id)" @keyup.enter="copyOrderId(scope.row.orderNo || scope.row.id)">
              {{ scope.row.orderNo || scope.row.id }}
              <el-icon size="14" class="copy-icon">
                <DocumentCopy />
              </el-icon>
            </span>
          </template>
        </el-table-column>
        <!-- 指派师傅 -->
        <el-table-column label="客户" align="left" min-width="160">
          <template #default="scope">
            <div class="primary-cell">{{ scope.row.customerName || '-' }}</div>
            <div class="secondary-cell">{{ formatPhone(scope.row.customerPhone) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="assignMaster" label="指派师傅" align="left" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.assignMaster || scope.row.masterName || '待指派' }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="104">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" size="small">{{ getStatusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <!-- 创建时间 -->
        <el-table-column prop="createTime" label="创建时间" align="left" width="180">
          <template #default="scope">
            <span class="datetime-cell">{{ formatDateTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <!-- 操作：修改+删除 -->
        <el-table-column label="操作" align="center" width="330" fixed="right">
          <template #default="scope">
            <div class="table-actions">
              <el-button text type="primary" @click="router.push({ name: 'OrderDetail', params: { id: scope.row.id }, query: route.query })">查看详情</el-button>
              <el-button v-if="isReviewedStatus(scope.row.status)" text type="success" @click="handleViewReview(scope.row)">查看评价</el-button>
              <el-button text type="primary" @click="handleEditOrder(scope.row)">修改</el-button>
              <el-button v-if="!isCancelled(scope.row.status)" text type="danger" @click="handleDeleteOrder(scope.row)">作废</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :background="background"
          layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]" :total="total" @size-change="handleSizeChange"
          @current-change="handleCurrentChange" />
      </div>
    </el-card>

    <!-- 删除确认弹窗 -->
    <el-dialog v-model="deleteDialogVisible" title="作废订单" width="460px" @closed="cancelReason = ''">
      <p class="dialog-tip">作废后订单将保留在系统中用于业务追溯，请填写作废原因。</p>
      <el-input v-model="cancelReason" type="textarea" :rows="3" maxlength="100" show-word-limit placeholder="请输入作废原因" />
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="cancelLoading" @click="confirmDelete">确认作废</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewDialogVisible" title="客户评价" width="640px" destroy-on-close>
      <div v-loading="reviewLoading" class="review-dialog-body">
        <template v-if="reviewData">
          <div class="review-order-summary">
            <div>
              <p class="review-order-title">{{ reviewOrder?.productName || reviewOrder?.taskType || '服务订单' }}</p>
              <p class="review-order-no">订单编号：{{ reviewOrder?.orderNo || reviewOrder?.id }}</p>
            </div>
            <span class="review-private-badge">仅管理员可见</span>
          </div>
          <div class="review-score-row">
            <el-rate :model-value="Number(reviewData.score || 0)" disabled />
            <strong>{{ Number(reviewData.score || 0) }}.0</strong>
            <span>{{ formatDateTime(reviewData.createTime) }}</span>
          </div>
          <div v-if="reviewData.labels?.length" class="review-label-list">
            <el-tag v-for="label in reviewData.labels" :key="label" type="primary" effect="light">{{ label }}</el-tag>
          </div>
          <div class="review-content-box">{{ reviewData.content || '用户未填写文字评价' }}</div>
          <div v-if="reviewData.images?.length" class="review-image-list">
            <el-image v-for="(image, index) in reviewData.images" :key="image" :src="image" fit="cover"
              :preview-src-list="reviewData.images" :initial-index="index" preview-teleported />
          </div>
        </template>
        <el-empty v-else-if="!reviewLoading" description="该订单暂无评价内容" :image-size="80" />
      </div>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Plus, Search, Refresh, Download, DocumentCopy
} from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { getOrderListApi, cancelOrderApi, exportOrdersApi, getOrderEvaluationApi } from '@/api/orders'
import { formatDateTime, formatPhone } from '@/utils/format'

const router = useRouter()
const route = useRoute()

// 搜索条件
const searchKeyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const searchStatus = ref(typeof route.query.status === 'string' ? route.query.status : '')
// 分页基础数据
const currentPage = ref(Number(route.query.page) || 1)
const pageSize = ref(Number(route.query.pageSize) || 10)
const total = ref(0)
const background = ref(true)
// 表格loading
const tableLoading = ref(false)
const exportLoading = ref(false)
const loadError = ref('')

// 删除相关
const deleteDialogVisible = ref(false)
const currentDeleteRow = ref(null)
const cancelReason = ref('')
const cancelLoading = ref(false)

// 管理员查看客户评价
const reviewDialogVisible = ref(false)
const reviewLoading = ref(false)
const reviewData = ref(null)
const reviewOrder = ref(null)

// 订单列表（数据全部来自接口）
const orders = ref([])

/**
 * 加载订单列表
 */
async function loadList() {
  syncRouteState()
  tableLoading.value = true
  loadError.value = ''
  try {
    const res = await getOrderListApi({
      page: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value,
      status: searchStatus.value || undefined
    })
    orders.value = res.list || []
    total.value = res.total || 0
  } catch {
    loadError.value = '订单数据加载失败，请检查网络后重试。'
  } finally {
    tableLoading.value = false
  }
}

function syncRouteState() {
  router.replace({ query: {
    ...(searchKeyword.value.trim() ? { keyword: searchKeyword.value.trim() } : {}),
    ...(searchStatus.value ? { status: searchStatus.value } : {}),
    ...(currentPage.value > 1 ? { page: currentPage.value } : {}),
    ...(pageSize.value !== 10 ? { pageSize: pageSize.value } : {})
  } })
}

/**
 * 1. 搜索订单
 */
function handleSearch() {
  currentPage.value = 1
  loadList()
}

/**
 * 2. 重置搜索条件，返回第一页刷新列表
 */
function handleReset() {
  searchKeyword.value = ''
  searchStatus.value = ''
  currentPage.value = 1
  loadList()
}

/**
 * 3. 批量导出全部订单（调用导出接口，下载文件流）
 */
async function handleBatchExport() {
  if (exportLoading.value) return
  exportLoading.value = true
  try {
    const blob = await exportOrdersApi({ keyword: searchKeyword.value, status: searchStatus.value || undefined })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `订单列表_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '')}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    // 错误已在响应拦截器提示
  } finally {
    exportLoading.value = false
  }
}

/**
 * 4. 新增订单跳转
 */
function handleAddOrder() {
  router.push({ name: 'OrderForm' })
}

/**
 * 5. 编辑订单跳转
 * @param {Object} order 当前订单对象
 */
function handleEditOrder(order) {
  // 统一使用name路由，规避路径硬编码
  router.push({ name: 'OrderEdit', params: { id: order.id } })
}

async function handleViewReview(order) {
  reviewOrder.value = order
  reviewData.value = null
  reviewDialogVisible.value = true
  reviewLoading.value = true
  try {
    reviewData.value = await getOrderEvaluationApi(order.id)
  } catch {
    reviewDialogVisible.value = false
  } finally {
    reviewLoading.value = false
  }
}

/**
 * 打开删除弹窗
 */
function handleDeleteOrder(row) {
  currentDeleteRow.value = row
  deleteDialogVisible.value = true
}

/**
 * 确认删除
 */
async function confirmDelete() {
  const row = currentDeleteRow.value
  if (!row) return
  if (!cancelReason.value.trim()) {
    ElMessage.warning('请填写作废原因')
    return
  }
  cancelLoading.value = true
  try {
    await cancelOrderApi(row.id, { reason: cancelReason.value.trim() })
    ElMessage.success('订单已作废')
    deleteDialogVisible.value = false
    await loadList()
  } catch {
    // 拦截器已提示，弹窗保持打开以便重试
  } finally {
    cancelLoading.value = false
  }
}

/**
 * 6. 分页每页条数切换
 * @param {Number} val 每页条数
 */
function handleSizeChange(val) {
  pageSize.value = val
  currentPage.value = 1 // 切换条数回到第一页
  loadList()
}

/**
 * 7. 分页页码切换
 * @param {Number} val 当前页码
 */
function handleCurrentChange(val) {
  currentPage.value = val
  loadList()
}

/**
 * 8. 复制订单编号到剪贴板
 * @param {String} id 订单号
 */
async function copyOrderId(id) {
  let success = false
  try {
    await navigator.clipboard.writeText(id)
    success = true
  } catch (err) {
    try {
      const input = document.createElement('input')
      input.value = id
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
      success = true
    } catch {
      success = false
    }
  }
  success ? ElMessage.success('订单编号复制成功') : ElMessage.error('复制失败，请手动复制')
}

function getStatusText(status) {
  const map = { pending: '待上门', assigned: '待上门', processing: '处理中', completed: '已完成', cancelled: '已作废', canceled: '已作废', PENDING_VISIT: '待上门', IN_PROGRESS: '处理中', PENDING_REVIEW: '待评价', REVIEWED: '已评价', CANCELLED: '已作废' }
  return map[status] || status || '待处理'
}

function getStatusType(status) {
  const map = { pending: 'warning', assigned: 'warning', processing: 'primary', completed: 'success', cancelled: 'info', canceled: 'info', '待上门': 'warning', '处理中': 'primary', '已完成': 'success', '待评价': 'warning', '已评价': 'success', '已作废': 'info', PENDING_VISIT: 'warning', IN_PROGRESS: 'primary', PENDING_REVIEW: 'warning', REVIEWED: 'success', CANCELLED: 'info' }
  return map[status] || 'warning'
}

function isCancelled(status) {
  return status === 'cancelled' || status === 'canceled' || status === '已作废' || status === 'CANCELLED'
}

function isReviewedStatus(status) {
  return status === 'REVIEWED' || status === '已评价'
}
// 页面初始化自动请求
onMounted(loadList)
</script>

<style lang="scss" scoped>
.order-page {
  padding: 20px;
  background-color: #f8fafc;
  min-height: 100vh;

  // 顶部头部
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding: 14px 18px;
    background: #fff;
    border-radius: 8px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);

    .header-left {
      display: flex;
      flex-direction: column;
      gap: 6px;

      .page-title {
        font-size: 20px;
        font-weight: 600;
        color: #1f2937;
        margin: 0;
      }

      .page-desc {
        font-size: 14px;
        color: #6b7280;
      }
    }

    .header-right {
      display: flex;
      gap: 12px;

      :deep(.el-button) {
        border-radius: 6px;
        padding: 7px 16px;
        display: flex;
        align-items: center;
        gap: 5px;
      }
    }
  }

  // 搜索卡片
  .search-card {
    margin-bottom: 16px;
    border-radius: 8px;
    border: none;

    :deep(.el-card__header) {
      padding: 12px 18px;
      background: #f8fafc;
      border-bottom: 1px solid #e5e7eb;
    }

    :deep(.el-card__body) {
      padding: 18px;
    }

    .search-bar {
      display: flex;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;

      .search-item {
        display: flex;
        align-items: center;
        gap: 10px;

        .search-input {
          width: 440px;

          :deep(.el-input__wrapper) {
            border: 1px solid #d1d5db;
            box-shadow: none;
            border-radius: 6px;
          }
        }
      }
    }
  }

  // 表格卡片
  .table-card {
    border-radius: 8px;
    border: none;
    margin-bottom: 16px;

    :deep(.el-card__body) {
      padding: 12px;
      overflow-x: auto;
    }

    .business-table { width: 100%; min-width: 1396px; }

    .table-actions { display: flex; align-items: center; justify-content: center; gap: 8px; white-space: nowrap; }
    .table-actions :deep(.el-button + .el-button) { margin-left: 0; }

    // 表格订单号复制样式
    .table-order-id {
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      max-width: 100%;

      .copy-icon {
        color: #94a3b8;
      }

      &:hover {
        color: #409eff;

        .copy-icon {
          color: #409eff;
        }
      }
    }

    .secondary-cell {
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    // 表格全局样式微调
    :deep(.el-table) {
      border-radius: 6px;
    }

    :deep(.el-table th) {
      background-color: #f8fafc;
      color: #1f2937;
    }
  }

  .review-dialog-body {
    min-height: 160px;
  }

  .review-order-summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
    padding: 16px;
    border-radius: 10px;
    background: #f8fafc;
    border: 1px solid #e5eaf1;
  }

  .review-order-title {
    margin: 0;
    color: #172033;
    font-size: 16px;
    font-weight: 600;
  }

  .review-order-no {
    margin: 6px 0 0;
    color: #8492a6;
    font-size: 13px;
  }

  .review-private-badge {
    flex-shrink: 0;
    padding: 6px 12px;
    border-radius: 16px;
    color: #2563eb;
    background: #eaf3ff;
    font-size: 12px;
  }

  .review-score-row {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 22px;

    strong {
      color: #d97706;
      font-size: 18px;
    }

    span {
      margin-left: auto;
      color: #94a3b8;
      font-size: 13px;
    }
  }

  .review-label-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 18px;
  }

  .review-content-box {
    min-height: 96px;
    margin-top: 18px;
    padding: 16px;
    border-radius: 10px;
    background: #f8fafc;
    color: #334155;
    font-size: 14px;
    line-height: 1.75;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .review-image-list {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 12px;
    margin-top: 18px;

    :deep(.el-image) {
      width: 100%;
      aspect-ratio: 1;
      border-radius: 8px;
      background: #f1f5f9;
    }
  }

  // 分页
  .el-pagination-style {
    margin-top: 24px;
    display: flex;
    justify-content: flex-end;

    :deep(.el-pagination) {

      .btn-prev,
      .btn-next,
      .number {
        border-radius: 4px;
      }
    }
  }
}

// 移动端适配
@media (max-width: 768px) {
  .order-page {
    padding: 12px;

    .page-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 14px;
      width: 100%;

      .header-right {
        width: 100%;
        flex-wrap: wrap;
      }
    }

    .search-bar {
      width: 100%;

      .search-item {
        width: 100%;
        flex-wrap: wrap;

        .search-input {
          width: 100%;
        }
      }
    }
  }
}
</style>
