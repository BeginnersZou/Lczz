<template>
  <div class="order-page">
    <!-- 顶部标题操作栏 左右分栏（与订单管理完全统一） -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">耗材管理</h2>
        <span class="page-desc">统一管理空调安装耗材，支持发布、修改、检索、删除</span>
      </div>
      <div class="header-right">
        <el-button type="info" plain :icon="Download" :loading="exportLoading" @click="handleBatchExport">
          导出筛选结果
        </el-button>
        <el-button type="primary" :icon="Plus" @click="handleAdd">
          发布耗材
        </el-button>
      </div>
    </div>

    <!-- 搜索卡片 -->
    <el-card class="search-card" shadow="never">
      <div class="search-bar">
        <div class="search-item">
          <el-input v-model="searchKeyword" placeholder="输入耗材名称、规格检索耗材" clearable class="search-input"
            @keyup.enter="handleSearch">
            <template #prefix>
              <el-icon>
                <Search />
              </el-icon>
            </template>
          </el-input>
          <el-select v-model="searchCategory" :placeholder="categoryLoading ? '分类加载中…' : '全部分类'" clearable
            :disabled="categoryLoading || categoryFilterOptions.length === 0" class="category-select" @change="handleSearch">
            <el-option v-for="item in categoryFilterOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="stockStatus" placeholder="库存状态" clearable class="category-select" @change="handleSearch">
            <el-option label="库存充足" value="normal" />
            <el-option label="库存不足" value="low" />
            <el-option label="已无库存" value="empty" />
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

    <!-- 耗材表格主体 -->
    <el-card shadow="never" class="table-card">
      <div v-if="loadError" class="error-state">
        <span>{{ loadError }}</span>
        <el-button type="primary" link @click="loadList">重新加载</el-button>
      </div>
      <el-table v-else v-loading="tableLoading" :data="pagedList" border stripe table-layout="fixed" class="business-table"
        empty-text="暂无耗材，点击上方按钮发布第一条耗材">
        <!-- 序号 -->
        <el-table-column label="序号" align="center" width="72">
          <template #default="scope">
            {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <!-- 耗材图片 -->
        <el-table-column label="图片" align="center" width="88">
          <template #default="scope">
            <el-image :src="scope.row.image" fit="cover" class="row-img" :preview-src-list="[scope.row.image]"
              preview-teleported />
          </template>
        </el-table-column>
        <!-- 耗材名称 -->
        <el-table-column prop="name" label="耗材名称" align="left" min-width="170" show-overflow-tooltip />
        <!-- 耗材分类（二级） -->
        <el-table-column label="耗材分类" align="left" min-width="220">
          <template #default="scope">
            <el-tag size="small" effect="light" type="info">{{ getCategory(scope.row, 0) }}</el-tag>
            <span class="category-arrow">/</span>
            <el-tag size="small" effect="light">{{ getCategory(scope.row, 1) }}</el-tag>
          </template>
        </el-table-column>
        <!-- 规格 -->
        <el-table-column label="规格" align="left" min-width="140" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.skuCount > 1 ? (scope.row.skuCount + ' 个SKU') : (scope.row.spec || '通用规格') }}</template>
        </el-table-column>
        <!-- 单位 -->
        <el-table-column prop="unit" label="单位" align="center" width="76" />
        <!-- 库存 -->
        <el-table-column prop="stock" label="库存" align="center" min-width="180">
          <template #default="scope">
            <span :class="['stock-num', isLowStock(scope.row) ? 'low' : '']">{{ scope.row.stockSummary || ((scope.row.stock ?? 0) + ' ' + (scope.row.unit || '')) }}</span>
            <div v-if="scope.row.safetyStock != null" class="secondary-cell">安全库存 {{ scope.row.safetyStock }}</div>
          </template>
        </el-table-column>
        <!-- 创建时间 -->
        <el-table-column prop="createTime" label="创建时间" align="left" width="180">
          <template #default="scope"><span class="datetime-cell">{{ formatDateTime(scope.row.createTime) }}</span></template>
        </el-table-column>
        <!-- 操作 -->
        <el-table-column label="操作" align="center" width="280" fixed="right">
          <template #default="scope">
            <div class="table-actions">
              <el-button text type="primary" @click="handleEdit(scope.row)">修改</el-button>
              <el-button text :type="scope.row.enabled ? 'warning' : 'success'" @click="toggleEnabled(scope.row)">
                {{ scope.row.enabled ? '下架' : '上架' }}
              </el-button>
              <el-button text type="success" @click="openStockDialog(scope.row)">调整库存</el-button>
              <el-button text type="danger" @click="handleDelete(scope.row)">删除</el-button>
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
    <el-dialog v-model="deleteDialogVisible" title="提示" width="380px">
      <p>确定要删除该耗材吗？删除后数据不可恢复</p>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="deleteLoading" @click="confirmDelete">确认删除</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stockDialogVisible" title="调整库存" width="480px" @closed="resetStockForm">
      <el-form label-width="90px">
        <el-form-item label="耗材"><strong>{{ currentStockRow?.name }}</strong></el-form-item>
        <el-form-item label="具体规格" required>
          <el-select v-model="stockForm.skuId" placeholder="请选择要调整的SKU" style="width: 100%">
            <el-option v-for="sku in currentStockSkus" :key="sku.id"
              :label="`${sku.specLabel || '通用规格'} · ${sku.code}`" :value="sku.id"
              :disabled="!sku.enabled">
              <span>{{ sku.specLabel || '通用规格' }}</span><span class="sku-stock-option">库存 {{ sku.stock }} {{ sku.unit }}</span>
            </el-option>
          </el-select>
          <div v-if="currentStockSkus.length > 1" class="stock-help">多规格耗材必须按具体SKU调整，避免库存错账。</div>
        </el-form-item>
        <el-form-item label="当前库存">{{ selectedStockSku?.stock ?? '-' }} {{ selectedStockSku?.unit || '' }}</el-form-item>
        <el-form-item label="调整方式">
          <el-radio-group v-model="stockForm.type"><el-radio-button value="IN">入库</el-radio-button><el-radio-button value="OUT">出库</el-radio-button></el-radio-group>
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="stockForm.quantity" :min="1" :max="99999" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="stockForm.reason" maxlength="500" show-word-limit placeholder="例如：采购入库、施工领用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="stockLoading" @click="confirmStockChange">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Plus, Search, Refresh, Download
} from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { getConsumablesListApi, getConsumableCategoriesApi, deleteConsumablesApi, exportConsumablesApi, adjustConsumableStockApi, setConsumableEnabledApi } from '@/api/consumables'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const route = useRoute()

// 搜索条件
const searchKeyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const searchCategory = ref(typeof route.query.category === 'string' ? route.query.category : '')
const stockStatus = ref(typeof route.query.stockStatus === 'string' ? route.query.stockStatus : '')
const categoryFilterOptions = ref([])
const categoryLoading = ref(false)
// 分页基础数据
const currentPage = ref(Number(route.query.page) || 1)
const pageSize = ref(Number(route.query.pageSize) || 10)
const total = ref(0)
const background = ref(true)
// 表格loading
const tableLoading = ref(false)
const exportLoading = ref(false)
const deleteLoading = ref(false)
const loadError = ref('')

// 删除相关
const deleteDialogVisible = ref(false)
const currentDeleteRow = ref(null)
const stockDialogVisible = ref(false)
const currentStockRow = ref(null)
const stockLoading = ref(false)
const stockForm = reactive({ skuId: null, type: 'IN', quantity: 1, reason: '' })
const currentStockSkus = computed(() => currentStockRow.value?.skus || [])
const selectedStockSku = computed(() => currentStockSkus.value.find(sku => sku.id === stockForm.skuId))

// 耗材列表（数据全部来自接口）
const pagedList = ref([])

/**
 * 加载耗材列表
 */
async function loadList() {
  syncRouteState()
  tableLoading.value = true
  loadError.value = ''
  try {
    const res = await getConsumablesListApi({
      page: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value.trim(),
      category: searchCategory.value || undefined,
      stockStatus: stockStatus.value || undefined
    })
    // 响应拦截器已返回 data 层，列表接口返回 { list, total }
    pagedList.value = res.list || []
    total.value = res.total || 0
  } catch (err) {
    loadError.value = '耗材数据加载失败，请检查网络后重试。'
  } finally {
    tableLoading.value = false
  }
}

/**
 * 搜索耗材
 */
async function handleSearch() {
  currentPage.value = 1
  await loadList()
}

/**
 * 重置搜索条件
 */
async function handleReset() {
  searchKeyword.value = ''
  searchCategory.value = ''
  stockStatus.value = ''
  currentPage.value = 1
  await loadList()
}

/**
 * 批量导出（调用导出接口，下载文件流）
 */
async function handleBatchExport() {
  if (exportLoading.value) return
  exportLoading.value = true
  try {
    const blob = await exportConsumablesApi({ keyword: searchKeyword.value.trim(), category: searchCategory.value || undefined })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `耗材列表_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '')}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (err) {
    // 错误已由响应拦截器统一提示
  } finally {
    exportLoading.value = false
  }
}

/**
 * 发布耗材跳转
 */
function handleAdd() {
  router.push({ name: 'ConsumablesForm' })
}

/**
 * 修改耗材跳转
 * @param {Object} row 当前耗材对象
 */
function handleEdit(row) {
  router.push({ name: 'ConsumablesEdit', params: { id: row.id } })
}

async function toggleEnabled(row) {
  await setConsumableEnabledApi(row.id, !row.enabled)
  ElMessage.success(row.enabled ? '已下架' : '已上架')
  await loadList()
}

/**
 * 打开删除弹窗
 */
function handleDelete(row) {
  currentDeleteRow.value = row
  deleteDialogVisible.value = true
}

/**
 * 确认删除
 */
async function confirmDelete() {
  const targetId = currentDeleteRow.value?.id
  if (targetId == null) return
  deleteLoading.value = true
  try {
    await deleteConsumablesApi(targetId)
    ElMessage.success('删除成功')
    deleteDialogVisible.value = false
    currentDeleteRow.value = null
    // 删除后空页回退：当前页仅剩这一条且不是第一页，回退到上一页避免空白
    if (pagedList.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    await loadList()
  } catch (err) {
    // 错误已由响应拦截器统一提示，弹窗保留以便重试
  } finally {
    deleteLoading.value = false
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

function syncRouteState() {
  router.replace({ query: {
    ...(searchKeyword.value.trim() ? { keyword: searchKeyword.value.trim() } : {}),
    ...(searchCategory.value ? { category: searchCategory.value } : {}),
    ...(stockStatus.value ? { stockStatus: stockStatus.value } : {}),
    ...(currentPage.value > 1 ? { page: currentPage.value } : {}),
    ...(pageSize.value !== 10 ? { pageSize: pageSize.value } : {})
  } })
}

function getCategory(row, index) {
  if (Array.isArray(row.category)) return row.category[index] || '-'
  return index === 0 ? (row.category || '-') : '-'
}

function isLowStock(row) {
  const enabledSkus = (row.skus || []).filter(sku => sku.enabled)
  if (enabledSkus.length) return enabledSkus.some(sku => Number(sku.stock) <= 5)
  const stock = Number(row.stock)
  return Number.isFinite(stock) && stock <= 5
}

async function loadCategoryFilters() {
  categoryLoading.value = true
  try {
    const rows = await getConsumableCategoriesApi()
    categoryFilterOptions.value = (rows || [])
      .filter(item => item.level === 1 || item.parentId == null)
      .map(item => ({ label: item.name, value: item.name }))
      .filter((item, index, list) => item.value && list.findIndex(other => other.value === item.value) === index)
  } catch {
    categoryFilterOptions.value = []
  } finally {
    categoryLoading.value = false
  }
}

function openStockDialog(row) {
  currentStockRow.value = row
  const skus = row.skus || []
  stockForm.skuId = skus.length === 1 ? skus[0].id : null
  stockDialogVisible.value = true
}

function resetStockForm() {
  currentStockRow.value = null
  stockForm.skuId = null
  stockForm.type = 'IN'
  stockForm.quantity = 1
  stockForm.reason = ''
}

async function confirmStockChange() {
  const row = currentStockRow.value
  if (!row) return
  if (!stockForm.skuId || !selectedStockSku.value) {
    ElMessage.warning('请选择需要调整库存的具体SKU')
    return
  }
  const reason = stockForm.reason.trim()
  if (reason.length < 2) {
    ElMessage.warning('库存调整原因至少填写2个字符')
    return
  }
  const delta = stockForm.type === 'IN' ? stockForm.quantity : -stockForm.quantity
  const nextStock = Number(selectedStockSku.value.stock || 0) + delta
  if (nextStock < 0) {
    ElMessage.warning('出库数量不能大于当前库存')
    return
  }
  stockLoading.value = true
  try {
    await adjustConsumableStockApi(row.id, {
      skuId: stockForm.skuId,
      type: stockForm.type,
      quantity: stockForm.quantity,
      reason
    })
    ElMessage.success('库存调整成功')
    stockDialogVisible.value = false
    await loadList()
  } finally {
    stockLoading.value = false
  }
}

// 页面初始化
onMounted(() => {
  loadCategoryFilters()
  loadList()
})
</script>

<style lang="scss" scoped>
.sku-stock-option { float: right; margin-left: 24px; color: #64748b; }
.stock-help { margin-top: 6px; color: #94a3b8; font-size: 12px; line-height: 1.5; }
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

    .business-table { width: 100%; min-width: 1322px; }
    .table-actions { display: flex; align-items: center; justify-content: center; gap: 8px; white-space: nowrap; }
    .table-actions :deep(.el-button + .el-button) { margin-left: 0; }

    // 行内耗材图片
    .row-img {
      width: 48px;
      height: 48px;
      border-radius: 6px;
      display: block;
      margin: 0 auto;
    }

    // 分类标签间距
    .category-arrow {
      margin: 0 4px;
      color: #94a3b8;
    }

    // 库存数量样式
    .stock-num {
      font-weight: 600;
      color: #16a34a;

      &.low {
        color: #ef4444;
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
