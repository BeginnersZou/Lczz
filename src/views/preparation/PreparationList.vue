<template>
  <div class="order-page">
    <!-- 顶部标题操作栏（与订单管理完全统一） -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">订单备货</h2>
        <span class="page-desc">统一管理订单备货进度，支持检索、备货、完成确认</span>
      </div>
      <div class="header-right">
        <el-button type="info" plain :icon="Download" :loading="exportLoading" @click="handleBatchExport">
          导出筛选结果
        </el-button>
      </div>
    </div>

    <!-- 搜索卡片 -->
    <el-card class="search-card" shadow="never">
      <div class="search-bar">
        <div class="search-item">
          <el-input v-model="searchKeyword" placeholder="输入订单编号、订单名称检索" clearable class="search-input"
            @keyup.enter="handleSearch">
            <template #prefix>
              <el-icon>
                <Search />
              </el-icon>
            </template>
          </el-input>
          <el-select v-model="searchStatus" placeholder="备货状态" clearable class="status-select" @change="handleSearch">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
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

    <!-- 备货表格主体 -->
    <el-card shadow="never" class="table-card">
      <div v-if="loadError" class="error-state">
        <span>{{ loadError }}</span>
        <el-button type="primary" link @click="loadList">重新加载</el-button>
      </div>
      <el-table v-else v-loading="tableLoading" :data="pagedList" border stripe table-layout="fixed" style="width: 100%" empty-text="暂无备货订单">
        <!-- 序号 -->
        <el-table-column label="序号" align="center" width="56">
          <template #default="scope">
            {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <!-- 订单名称 -->
        <el-table-column prop="productName" label="订单名称" align="left" min-width="150" show-overflow-tooltip />
        <!-- 订单编号 -->
        <el-table-column prop="orderNo" label="订单编号" align="left" min-width="176" show-overflow-tooltip>
          <template #default="scope">
            <span class="table-order-id code-cell" role="button" tabindex="0" @click="copyOrderId(scope.row.orderNo || scope.row.orderId)" @keyup.enter="copyOrderId(scope.row.orderNo || scope.row.orderId)">
              {{ scope.row.orderNo || scope.row.orderId }}
              <el-icon size="14" class="copy-icon">
                <DocumentCopy />
              </el-icon>
            </span>
          </template>
        </el-table-column>
        <!-- 备货耗材 -->
        <el-table-column label="备货耗材" align="left" min-width="180">
          <template #default="scope">
            <div class="materials-cell">
              <el-tag v-for="(m, idx) in scope.row.materials" :key="idx" size="small" effect="light"
                :type="isMaterialShort(m) ? 'danger' : 'info'" class="material-tag">
                {{ m.name }} ×{{ m.count }}{{ m.unit }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <!-- 备货状态 -->
        <el-table-column label="备货状态" align="center" width="96">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" size="small" effect="light">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 创建时间 -->
        <el-table-column prop="createTime" label="创建时间" align="left" width="150">
          <template #default="scope"><span class="datetime-cell">{{ formatDateTime(scope.row.createTime || scope.row.createdAt) }}</span></template>
        </el-table-column>
        <!-- 操作 -->
        <el-table-column label="操作" align="center" width="132" fixed="right">
          <template #default="scope">
            <div class="table-actions">
              <el-button text type="primary" @click="handlePrepare(scope.row)">{{ scope.row.status === '已备货' ? '查看' : '备货' }}</el-button>
              <el-button text type="success" v-if="scope.row.status !== '已备货'"
                @click="handleFinish(scope.row)">完成</el-button>
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

    <!-- 备货详情弹窗 -->
    <el-dialog v-model="prepareDialogVisible" title="备货详情" width="640px">
      <div class="prepare-dialog-body" v-if="currentRow" v-loading="dialogLoading">
        <div class="dialog-info-row">
          <span class="info-label">订单名称：</span>
          <span class="info-value">{{ currentRow.productName }}</span>
        </div>
        <div class="dialog-info-row">
          <span class="info-label">订单编号：</span>
          <span class="info-value">{{ currentRow.orderNo || currentRow.orderId }}</span>
        </div>
        <div class="dialog-info-row">
          <span class="info-label">当前状态：</span>
          <el-tag :type="getStatusType(currentRow.status)" size="small" effect="light">{{ currentRow.status }}</el-tag>
        </div>
        <div class="dialog-materials">
          <p class="materials-title">耗材清单</p>
          <el-table :data="currentRow.materials" border size="small">
            <el-table-column type="index" label="序号" align="center" width="60" />
            <el-table-column prop="name" label="耗材名称" align="center" />
            <el-table-column prop="spec" label="规格" align="center" />
            <el-table-column prop="count" label="数量" align="center" width="80" />
            <el-table-column prop="unit" label="单位" align="center" width="70" />
            <el-table-column label="备货" align="center" width="80">
              <template #default="scope">
                <el-checkbox v-model="scope.row.checked" :disabled="currentRow.status === '已备货'" />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="prepareDialogVisible = false">取消</el-button>
        <el-button v-if="currentRow?.status !== '已备货'" type="primary" :loading="preparing" @click="confirmPrepare">保存备货进度</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Refresh, Download, DocumentCopy
} from '@element-plus/icons-vue'
import {
  getPreparationListApi,
  getPreparationDetailApi,
  submitPreparationApi,
  finishPreparationApi,
  exportPreparationApi
} from '@/api/preparation'
import { useRouter, useRoute } from 'vue-router'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const route = useRoute()

// 后端状态码与中文标签互转（统一转为中文以匹配模板展示与 getStatusType）
const STATUS_LABEL_MAP = { pending: '待备货', preparing: '备货中', done: '已备货' }

// 搜索条件
const searchKeyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const searchStatus = ref(typeof route.query.status === 'string' ? route.query.status : '')
const tableLoading = ref(false)
const exportLoading = ref(false)
const loadError = ref('')

// 分页
const currentPage = ref(Number(route.query.page) || 1)
const pageSize = ref(Number(route.query.pageSize) || 10)
const total = ref(0)
const background = ref(true)

// 列表数据
const pagedList = ref([])

// 备货弹窗
const prepareDialogVisible = ref(false)
const currentRow = ref(null)
const preparing = ref(false)
const dialogLoading = ref(false)

// 状态选项（value 为后端状态码，作为筛选参数传给接口）
const statusOptions = [
  { label: '待备货', value: 'pending' },
  { label: '备货中', value: 'preparing' },
  { label: '已备货', value: 'done' }
]

// 状态颜色映射
function getStatusType(status) {
  const map = { '待备货': 'warning', '备货中': 'primary', '已备货': 'success' }
  return map[status] || 'info'
}

/**
 * 加载备货列表
 */
async function loadList() {
  syncRouteState()
  tableLoading.value = true
  loadError.value = ''
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    const kw = searchKeyword.value.trim()
    if (kw) params.keyword = kw
    if (searchStatus.value) params.status = searchStatus.value
    const res = await getPreparationListApi(params)
    const list = res?.list || []
    // 统一将状态码转为中文标签，兼容后端返回中文或英文
    pagedList.value = list.map(item => ({
      ...item,
      status: STATUS_LABEL_MAP[item.status] || item.status
    }))
    total.value = res?.total || 0
  } catch (e) {
    pagedList.value = []
    total.value = 0
    loadError.value = '备货数据加载失败，请检查网络后重试。'
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
 * 搜索
 */
function handleSearch() {
  currentPage.value = 1
  loadList()
}

/**
 * 重置
 */
function handleReset() {
  searchKeyword.value = ''
  searchStatus.value = ''
  currentPage.value = 1
  loadList()
}

/**
 * 批量导出（调用导出接口，下载文件流）
 */
async function handleBatchExport() {
  if (exportLoading.value) return
  exportLoading.value = true
  try {
    const params = {}
    const kw = searchKeyword.value.trim()
    if (kw) params.keyword = kw
    if (searchStatus.value) params.status = searchStatus.value
    const blob = await exportPreparationApi(params)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `备货清单_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '')}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    // 错误已在响应拦截器提示
  } finally {
    exportLoading.value = false
  }
}

/**
 * 打开备货弹窗：拉取详情（含勾选状态的耗材清单），深拷贝到临时编辑态
 */
async function handlePrepare(row) {
  currentRow.value = { ...row, materials: [] }
  prepareDialogVisible.value = true
  dialogLoading.value = true
  try {
    const data = await getPreparationDetailApi(row.id)
    // 深拷贝避免直接修改列表数据，状态码统一转中文
    currentRow.value = JSON.parse(JSON.stringify({
      ...data,
      status: STATUS_LABEL_MAP[data.status] || data.status
    }))
  } catch (e) {
    prepareDialogVisible.value = false
  } finally {
    dialogLoading.value = false
  }
}

/**
 * 确认备货：提交耗材勾选状态，成功后刷新列表
 */
async function confirmPrepare() {
  if (!currentRow.value || preparing.value) return
  preparing.value = true
  try {
    const materials = (currentRow.value.materials || []).map(m => ({
      id: m.id,
      checked: !!m.checked
    }))
    await submitPreparationApi(currentRow.value.id, { materials })
    prepareDialogVisible.value = false
    ElMessage.success('备货已更新')
    await loadList()
  } catch (e) {
    // 错误已在响应拦截器提示
  } finally {
    preparing.value = false
  }
}

/**
 * 一键完成备货：调用完成接口，成功后刷新列表
 */
async function handleFinish(row) {
  try {
    const detail = await getPreparationDetailApi(row.id)
    const unfinished = (detail.materials || []).some(item => !item.checked)
    const shortage = (detail.materials || []).some(isMaterialShort)
    if (shortage) {
      ElMessage.warning('存在库存不足的耗材，暂时不能完成备货')
      return
    }
    if (unfinished) {
      ElMessage.warning('请先勾选并保存全部耗材后再完成备货')
      return
    }
    await ElMessageBox.confirm('确认该订单所有耗材已备货完成？', '完成备货', { type: 'warning' })
    try {
      await finishPreparationApi(row.id)
      ElMessage.success('已标记为备货完成')
      await loadList()
    } catch (e) {
      // 错误已在响应拦截器提示
    }
  } catch (e) {
    // 用户取消确认或接口失败
  }
}

/**
 * 分页每页条数切换
 */
function handleSizeChange(val) {
  pageSize.value = val
  currentPage.value = 1
  loadList()
}

/**
 * 分页页码切换
 */
function handleCurrentChange(val) {
  currentPage.value = val
  loadList()
}

/**
 * 复制订单编号
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

function isMaterialShort(material) {
  return material?.stock != null && Number(material.count || 0) > Number(material.stock || 0)
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
.order-page {
  padding: 20px;
  background-color: #f8fafc;
  min-height: 100vh;

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

  .search-card {
    margin-bottom: 16px;
    border-radius: 8px;
    border: none;

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
        flex-wrap: wrap;

        .search-input {
          width: 360px;

          :deep(.el-input__wrapper) {
            border: 1px solid #d1d5db;
            box-shadow: none;
            border-radius: 6px;
          }
        }

        .status-select {
          width: 160px;

          :deep(.el-input__wrapper) {
            border: 1px solid #d1d5db;
            box-shadow: none;
            border-radius: 6px;
          }
        }
      }
    }
  }

  .table-card {
    border-radius: 8px;
    border: none;
    margin-bottom: 16px;

    :deep(.el-card__body) {
      padding: 12px;
    }

    .table-order-id {
      font-size: 14px;
      color: #6b7280;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;

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

    .materials-cell {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;

      .material-tag {
        margin: 0;
      }
    }

    :deep(.el-table) {
      border-radius: 6px;
    }

    :deep(.el-table th) {
      background-color: #f8fafc;
      color: #1f2937;
    }
  }

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

// 备货弹窗
.prepare-dialog-body {
  .dialog-info-row {
    display: flex;
    align-items: center;
    margin-bottom: 12px;
    font-size: 14px;

    .info-label {
      color: #6b7280;
      width: 90px;
    }

    .info-value {
      color: #1f2937;
      font-weight: 500;
    }
  }

  .dialog-materials {
    margin-top: 16px;

    .materials-title {
      font-size: 14px;
      font-weight: 600;
      color: #1f2937;
      margin: 0 0 10px 0;
    }
  }
}

@media (max-width: 768px) {
  .order-page {
    padding: 12px;

    .page-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 14px;

      .header-right {
        width: 100%;
        flex-wrap: wrap;
      }
    }

    .search-bar .search-item {
      width: 100%;

      .search-input,
      .status-select {
        width: 100%;
      }
    }
  }
}
</style>
