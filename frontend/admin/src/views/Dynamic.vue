<template>
  <div class="dynamic-page">
    <!-- 顶部标题操作栏 左右分栏（和订单页面完全统一） -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">动态信息管理</h2>
        <span class="page-desc">统一管理空调宣传动态资讯，支持发布、编辑、检索、删除</span>
      </div>
      <div class="header-right">
        <el-button type="primary" :icon="Plus" @click="goToPublish">
          发布信息
        </el-button>
      </div>
    </div>

    <!-- 搜索筛选卡片 -->
    <el-card class="search-card" shadow="light">
      <div class="search-bar">
        <div class="search-item">
          <el-input v-model="searchKeyword" placeholder="输入动态标题检索资讯" clearable class="search-input"
            @keyup.enter="handleSearch">
            <template #prefix>
              <el-icon>
                <Search />
              </el-icon>
            </template>
          </el-input>
        </div>
        <div class="search-btn-group">
          <el-button type="primary" @click="handleSearch">
            <el-icon>
              <Search />
            </el-icon> 搜索
          </el-button>
          <el-button text @click="resetSearch">
            <el-icon>
              <Refresh />
            </el-icon> 重置
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 动态卡片列表 -->
    <div v-if="tableLoading && !filteredList.length" v-loading="true" class="loading-state"></div>
    <div v-else-if="loadError" class="error-state">
      <span>{{ loadError }}</span>
      <el-button type="primary" link @click="loadList">重新加载</el-button>
    </div>
    <div class="card-list" v-else-if="filteredList.length" v-loading="tableLoading">
      <el-card v-for="item in filteredList" :key="item.id" class="dynamic-card" shadow="light">
        <div class="card-body">
          <div class="card-img">
            <img v-if="item.image" :src="item.image" alt="动态封面" />
            <div v-else class="img-empty">
              <el-icon size="40" color="#909399">
                <Picture />
              </el-icon>
            </div>
          </div>
          <div class="card-text">
            <div class="text-top">
              <h3 class="dynamic-title">{{ item.name }}</h3>
              <el-tag :type="getTagType(item.status)" size="small" effect="light">
                {{ item.status }}
              </el-tag>
            </div>
            <div class="info-row">
              <span class="info-item">
                <el-icon size="16">
                  <Clock />
                </el-icon>
                {{ item.publishTime }}
              </span>
              <span class="info-item">
                <el-icon size="16">
                  <View />
                </el-icon>
                浏览量：{{ item.viewCount }}
              </span>
            </div>
            <div class="text-bottom">
              <span class="desc-text">{{ item.description }}</span>
            </div>
          </div>
        </div>
        <!-- 右侧操作按钮：编辑、查看、删除 对照UI补充 -->
        <div class="card-operate">
          <el-button size="small" dashed plain @click.stop="goToDetail(item.id)">
            <el-icon>
              <Document />
            </el-icon> 查看
          </el-button>
          <el-button type="primary" size="small" dashed plain @click.stop="goToEdit(item.id)">
            <el-icon>
              <Edit />
            </el-icon> 编辑
          </el-button>
          <el-button type="danger" size="small" dashed plain @click.stop="handleDelete(item)">
            <el-icon>
              <Delete />
            </el-icon> 删除
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty-wrap">
      <el-empty description="暂无动态资讯，点击上方按钮发布第一条动态">
        <template #image>
          <el-icon size="80" color="#dcdcdc">
            <Document />
          </el-icon>
        </template>
        <template #footer>
          <el-button type="primary" dashed plain @click="goToPublish">立即发布信息</el-button>
        </template>
      </el-empty>
    </div>

    <!-- 分页区域（和订单页面统一居右） -->
    <div class="el-pagination-style" v-if="filteredList.length">
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :background="background"
        layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]" :total="total" @size-change="handleSizeChange"
        @current-change="handleCurrentChange" />
    </div>

    <!-- 删除确认弹窗 -->
    <el-dialog v-model="delDialogVisible" title="提示" width="420px">
      <p>确定要删除这条动态资讯吗？删除后数据无法恢复！</p>
      <template #footer>
        <el-button @click="delDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="deleteLoading" @click="confirmDelete">确认删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Refresh, Picture, Clock, View, Document, Edit, Delete
} from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { getDynamicListApi, deleteDynamicApi } from '@/api/dynamic'

const router = useRouter()
const route = useRoute()

// 搜索条件
const searchKeyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
// 分页配置
const currentPage = ref(Number(route.query.page) || 1)
const pageSize = ref(Number(route.query.pageSize) || 10)
const total = ref(0)
const background = ref(true)
// 表格loading
const tableLoading = ref(false)
const loadError = ref('')
const deleteLoading = ref(false)

// 删除弹窗
const delDialogVisible = ref(false)
const deleteTargetId = ref('')

// 动态资讯列表（数据来自接口）
const dynamicList = ref([])
// 列表分页数据
const filteredList = ref([])

// 状态标签颜色
const getTagType = (status) => {
  const map = { '已发布': 'success', 'published': 'success', '草稿': 'warning', 'draft': 'warning', '审核中': 'info' }
  return map[status] || 'default'
}

/**
 * 加载动态资讯分页列表
 */
async function loadList() {
  router.replace({ query: {
    ...(searchKeyword.value.trim() ? { keyword: searchKeyword.value.trim() } : {}),
    ...(currentPage.value > 1 ? { page: currentPage.value } : {}),
    ...(pageSize.value !== 10 ? { pageSize: pageSize.value } : {})
  } })
  tableLoading.value = true
  loadError.value = ''
  try {
    const res = await getDynamicListApi({
      page: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value
    })
    dynamicList.value = res.list || []
    filteredList.value = dynamicList.value
    total.value = res.total || 0
  } catch {
    filteredList.value = []
    total.value = 0
    loadError.value = '内容数据加载失败，请检查网络后重试。'
  } finally {
    tableLoading.value = false
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  loadList()
}

// 重置搜索
const resetSearch = () => {
  searchKeyword.value = ''
  currentPage.value = 1
  loadList()
}

// 分页切换每页条数
const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  loadList()
}

// 切换页码
const handleCurrentChange = () => {
  loadList()
}

// 跳转发布页面
const goToPublish = () => {
  router.push('/dynamic/publicInfo')
}

// 查看详情
const goToDetail = (id) => {
  router.push({
    path: `/dynamic/publicInfo/${id}`,
    query: { type: 'view' }
  })
}

// 编辑动态
const goToEdit = (id) => {
  router.push(`/dynamic/publicInfo/${id}`)
}

// 打开删除弹窗
const handleDelete = (item) => {
  deleteTargetId.value = item.id
  delDialogVisible.value = true
}

// 确认删除
async function confirmDelete() {
  deleteLoading.value = true
  try {
    await deleteDynamicApi(deleteTargetId.value)
    delDialogVisible.value = false
    ElMessage.success('删除成功')
    // 删除后若当前页删空且非第一页，回退一页
    if (filteredList.value.length <= 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    loadList()
  } catch {
    // 拦截器已提示，弹窗保持打开以便重试
  } finally {
    deleteLoading.value = false
  }
}

// 页面初始化自动请求
onMounted(loadList)
</script>

<style lang="scss" scoped>
.dynamic-page {
  padding: 20px;
  background-color: #f8fafc;
  min-height: 100vh;

  // 顶部头部 完全对齐订单页面
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

      .search-item .search-input {
        width: 440px;

        :deep(.el-input__wrapper) {
          border: 1px solid #d1d5db;
          box-shadow: none;
          border-radius: 6px;
        }
      }

      .search-btn-group {
        display: flex;
        gap: 10px;

        :deep(.el-button) {
          border-radius: 6px;
          padding: 7px 18px;
          display: flex;
          align-items: center;
          gap: 5px;
        }
      }
    }
  }

  // 动态卡片列表（和订单卡片布局完全统一）
  .card-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .dynamic-card {
    border-radius: 8px;
    border: 1px solid #e5e7eb;
    box-shadow: 0 1px 3px rgba(16, 24, 40, 0.06);
    overflow: hidden;

    :deep(.el-card__body) {
      padding: 16px 20px;
    }

    .card-body {
      display: flex;
      gap: 16px;
    }

    .card-img {
      width: 90px;
      height: 90px;
      flex-shrink: 0;
      border-radius: 6px;
      background: #f8fafc;
      overflow: hidden;
      display: flex;
      align-items: center;
      justify-content: center;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .img-empty {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
    }

    .card-text {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 8px;

      .text-top {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;

        .dynamic-title {
          font-size: 16px;
          font-weight: 600;
          color: #1f2937;
          margin: 0;
        }
      }

      .info-row {
        display: flex;
        gap: 20px;

        .info-item {
          display: flex;
          align-items: center;
          gap: 6px;
          font-size: 13px;
          color: #4b5563;
        }
      }

      .text-bottom {
        .desc-text {
          font-size: 13px;
          color: #6b7280;
          display: -webkit-box;
          -webkit-line-clamp: 1;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }
      }
    }

    // 底部操作按钮：查看/编辑/删除 对照UI图
    .card-operate {
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px solid #e5e7eb;
      display: flex;
      justify-content: flex-end;
      gap: 10px;

      :deep(.el-button) {
        border-radius: 6px;
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }
  }

  // 空页面
  .empty-wrap {
    padding: 80px 0;
    background: #fff;
    border-radius: 8px;
  }

  // 分页居右 同订单页面
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
  .dynamic-page {
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

    .search-bar .search-item .search-input {
      width: 100%;
    }

    .dynamic-card .card-body {
      flex-direction: column;

      .card-img {
        width: 100%;
        height: 160px;
      }
    }

    .dynamic-card .card-operate {
      flex-wrap: wrap;
    }
  }
}
</style>
