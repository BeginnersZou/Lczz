<template>
  <div class="case-page">
    <div class="page-header">
      <div>
        <h2>项目案例</h2>
        <p>维护小程序展示的工地案例与现场图片</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="router.push({ name: 'ProjectCaseForm' })">新增案例</el-button>
    </div>

    <el-card shadow="never" class="search-card">
      <div class="search-row">
        <el-input v-model="keyword" clearable placeholder="输入工地名称检索" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="search">搜索</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div v-if="loadError" class="error-state">
        <span>{{ loadError }}</span><el-button link type="primary" @click="loadCases">重新加载</el-button>
      </div>
      <el-table v-else v-loading="loading" :data="rows" border stripe empty-text="暂无项目案例，点击“新增案例”创建第一条案例">
        <el-table-column label="序号" width="76" align="center">
          <template #default="scope">{{ (page - 1) * pageSize + scope.$index + 1 }}</template>
        </el-table-column>
        <el-table-column label="封面" width="110" align="center">
          <template #default="{ row }">
            <el-image v-if="row.coverImage?.url" :src="row.coverImage.url" fit="cover" class="cover-image"
              :preview-src-list="row.images.map(image => image.url).filter(Boolean)" preview-teleported>
              <template #error><div class="image-error">图片加载失败</div></template>
            </el-image>
            <span v-else class="image-error">暂无图片</span>
          </template>
        </el-table-column>
        <el-table-column prop="siteName" label="工地名称" min-width="260" show-overflow-tooltip />
        <el-table-column label="案例图片" width="110" align="center">
          <template #default="{ row }">{{ row.images.length }} 张</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="最后更新" width="190">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'ProjectCaseEdit', params: { id: row.id } })">修改</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="total" class="pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" background
          :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next"
          @size-change="loadCases" @current-change="loadCases" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { deleteProjectCaseApi, getProjectCaseListApi } from '@/api/cases'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const rows = ref([])
const loading = ref(false)
const loadError = ref('')

async function loadCases() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await getProjectCaseListApi({ page: page.value, pageSize: pageSize.value, keyword: keyword.value.trim() || undefined })
    rows.value = result.list || []
    total.value = Number(result.total || 0)
  } catch {
    loadError.value = '项目案例加载失败，请检查网络后重试。'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  loadCases()
}

function reset() {
  keyword.value = ''
  page.value = 1
  loadCases()
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除“${row.siteName}”后，小程序将不再展示该案例。确定继续吗？`, '删除项目案例', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteProjectCaseApi(row.id)
    ElMessage.success('项目案例已删除')
    if (rows.value.length === 1 && page.value > 1) page.value--
    await loadCases()
  } catch {
    // 全局请求拦截器已提示失败原因。
  }
}

onMounted(loadCases)
</script>

<style lang="scss" scoped>
.case-page { min-height: calc(100vh - 112px); }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.page-header h2 { margin: 0; font-size: 22px; color: #172033; }
.page-header p { margin: 7px 0 0; color: #64748b; font-size: 14px; }
.search-card, .table-card { border-color: #e7edf5; }
.search-card { margin-bottom: 16px; }
.search-row { display: flex; gap: 10px; max-width: 560px; }
.cover-image { width: 64px; height: 64px; border-radius: 6px; background: #f1f5f9; }
.image-error { display: inline-flex; width: 64px; height: 64px; align-items: center; justify-content: center; color: #94a3b8; font-size: 12px; background: #f8fafc; text-align: center; }
.error-state { display: flex; align-items: center; gap: 8px; padding: 24px; color: #b45309; }
.pagination { display: flex; justify-content: flex-end; padding-top: 18px; }
@media (max-width: 640px) { .search-row { flex-wrap: wrap; } .search-row :deep(.el-input) { width: 100%; } }
</style>
