<template>
  <div class="audit-page">
    <!-- 顶部标题操作栏 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">用户审核</h2>
        <span class="page-desc">管理平台用户实名/企业认证申请，支持检索、审核、查看详情</span>
      </div>
      <div class="header-right">
        <el-button dashed plain type="default" :icon="ArrowLeft" @click="handleBack">
          返回用户管理
        </el-button>
      </div>
    </div>

    <!-- 搜索卡片 -->
    <el-card class="search-card" shadow="light">
      <div class="search-bar">
        <div class="search-item">
          <el-input v-model="searchKeyword" placeholder="输入昵称、真实姓名、手机号检索" clearable class="search-input"
            @keyup.enter="handleSearch">
            <template #prefix>
              <el-icon>
                <Search />
              </el-icon>
            </template>
          </el-input>
          <el-select v-model="searchStatus" placeholder="审核状态" clearable class="status-select" @change="handleSearch">
            <el-option label="待审核" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已驳回" value="rejected" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="handleSearch">
            搜索
          </el-button>
          <el-button text :icon="Refresh" @click="handleReset">
            重置
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 审核表格 -->
    <el-card class="table-card" shadow="light">
      <div v-if="loadError" class="error-state">
        <span>{{ loadError }}</span>
        <el-button type="primary" link @click="loadList">重新加载</el-button>
      </div>
      <el-table v-else v-loading="tableLoading" :data="pagedList" border stripe style="width: 100%" empty-text="暂无符合条件的审核记录">
        <el-table-column label="序号" align="center" width="70">
          <template #default="scope">
            {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        <el-table-column label="申请人" align="left" min-width="180">
          <template #default="scope">
            <div class="applicant-cell">
              <span class="applicant-name">{{ scope.row.nickname }}</span>
              <span class="applicant-phone">{{ formatPhone(scope.row.phone) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="身份类型" align="center" width="120">
          <template #default="scope">
            <el-tag :type="scope.row.auditType === 'enterprise' ? 'primary' : 'success'" size="small" effect="light">
              {{ scope.row.auditType === 'enterprise' ? '企业认证' : '个人认证' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="realName" label="真实姓名/企业名称" align="left" min-width="200">
          <template #default="scope">
            {{ scope.row.auditType === 'enterprise' ? scope.row.enterpriseName : scope.row.realName }}
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="提交时间" align="left" width="180" />
        <el-table-column label="审核状态" align="center" width="110">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" size="small" effect="light">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="140" fixed="right">
          <template #default="scope">
            <el-button text type="primary" @click="handleAudit(scope.row)">
              {{ scope.row.status === 'pending' ? '审核' : '查看' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :background="true"
          layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]" :total="total" @size-change="handleSizeChange"
          @current-change="handleCurrentChange" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, ArrowLeft } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { getAuditListApi } from '@/api/audit'

const router = useRouter()
const route = useRoute()

// 搜索条件
const searchKeyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const searchStatus = ref(typeof route.query.status === 'string' ? route.query.status : '')

// 分页
const currentPage = ref(Number(route.query.page) || 1)
const pageSize = ref(Number(route.query.pageSize) || 10)
const total = ref(0)
const tableLoading = ref(false)
const loadError = ref('')

// 列表数据
const pagedList = ref([])

/**
 * 加载审核列表
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
    const res = await getAuditListApi(params)
    pagedList.value = res?.list || []
    total.value = res?.total || 0
  } catch (e) {
    pagedList.value = []
    total.value = 0
    loadError.value = '审核记录加载失败，请检查网络后重试。'
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

const getStatusType = (status) => {
  const map = { pending: 'warning', approved: 'success', rejected: 'danger' }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = { pending: '待审核', approved: '已通过', rejected: '已驳回' }
  return map[status] || status
}

const formatPhone = (phone) => {
  if (!phone) return ''
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

const handleSearch = () => {
  currentPage.value = 1
  loadList()
}

const handleReset = () => {
  searchKeyword.value = ''
  searchStatus.value = ''
  currentPage.value = 1
  loadList()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  loadList()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadList()
}

const handleAudit = (row) => {
  router.push({ name: 'UserAuditDetail', params: { id: row.id } })
}

const handleBack = () => {
  router.push({ name: 'Users' })
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
.audit-page {
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
      .search-item {
        display: flex;
        align-items: center;
        gap: 12px;
        flex-wrap: wrap;

        .search-input {
          width: 320px;

          :deep(.el-input__wrapper) {
            border: 1px solid #d1d5db;
            box-shadow: none;
            border-radius: 6px;
          }
        }

        .status-select {
          width: 160px;
        }

        :deep(.el-button) {
          border-radius: 6px;
          padding: 7px 16px;
          display: flex;
          align-items: center;
          gap: 5px;
        }
      }
    }
  }

  .table-card {
    border-radius: 8px;
    border: none;

    :deep(.el-card__body) {
      padding: 18px;
    }

    .applicant-cell {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .applicant-name {
        font-size: 14px;
        font-weight: 500;
        color: #1f2937;
      }

      .applicant-phone {
        font-size: 12px;
        color: #6b7280;
      }
    }

    :deep(.el-table th) {
      background-color: #f8fafc;
      color: #1f2937;
    }
  }

  .pagination-wrap {
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

@media (max-width: 768px) {
  .audit-page {
    padding: 12px;

    .page-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 14px;
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
