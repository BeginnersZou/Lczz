<template>
  <div class="user-page">
    <!-- 顶部标题操作栏 和订单/动态页面统一 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">用户管理</h2>
        <span class="page-desc">统一管理平台所有微信注册用户，支持检索、查看、修改、黑名单管控</span>
      </div>
      <div class="header-right">
        <el-badge :value="auditCount" :hidden="auditCount === 0">
          <el-button type="primary" plain aria-label="进入用户审核" @click="handleAudit">
            <el-icon>
              <Bell />
            </el-icon>
          </el-button>
        </el-badge>
      </div>
    </div>

    <!-- 搜索卡片 单行不换行 -->
    <el-card class="search-card" shadow="light">
      <div class="search-bar">
        <el-form :model="searchForm" style="display: flex; align-items: center;">
          <el-form-item label="昵称">
            <el-input v-model="searchForm.nickname" placeholder="请输入用户昵称检索" clearable class="search-input"
              @keyup.enter="handleSearch">
              <template #prefix>
                <el-icon>
                  <Search />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="身份" style="margin-left: 12px;">
            <el-select v-model="searchForm.role" placeholder="全部身份" clearable class="role-select" @change="handleSearch">
              <el-option label="管理员" value="admin" />
              <el-option label="安装师傅" value="installer" />
              <el-option label="普通用户" value="customer" />
            </el-select>
          </el-form-item>
          <el-form-item class="search-btn-group" style="margin-left: 12px;">
            <el-button type="primary" :icon="Search" @click="handleSearch">
              搜索
            </el-button>
            <el-button text :icon="Refresh" @click="handleReset">
              重置
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <!-- 用户表格 -->
    <el-card class="table-card" shadow="light">
      <div v-if="loadError" class="error-state">
        <span>{{ loadError }}</span>
        <el-button type="primary" link @click="loadList">重新加载</el-button>
      </div>
      <el-table v-else v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="nickname" label="昵称" min-width="160" />
        <el-table-column prop="gender" label="性别" width="80" />
        <el-table-column prop="phone" label="手机号" width="160">
          <template #default="{ row }">
            {{ formatPhone(row.phone) }}
          </template>
        </el-table-column>
        <el-table-column prop="role" label="身份" width="120">
          <template #default="{ row }">
            <el-tag :type="getRoleTagType(row.role)" size="small" effect="light">
              {{ getRoleText(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="registerTime" label="注册时间" width="200" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDialog('view', row)">详情</el-button>
            <el-button text type="primary" @click="openDialog('edit', row)">修改</el-button>
            <el-button text :type="row.blacklist ? 'danger' : 'warning'" @click="openBlacklistDialog(row)">
              {{ row.blacklist ? '移出黑名单' : '加入黑名单' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页区域 全站统一居右 -->
      <div class="pagination-wrap" v-if="tableData.length">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" :background="true"
          layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]" @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </el-card>

    <!-- 用户详情/编辑共用弹窗 匹配UI表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680" @close="resetForm">
      <el-descriptions v-if="dialogType === 'view'" :column="2" border>
        <el-descriptions-item label="昵称">{{ form.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ form.gender || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份">{{ getRoleText(form.role) }}</el-descriptions-item>
        <el-descriptions-item label="地区">{{ form.area || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证" :span="2">{{ maskIdCard(form.idCard) }}</el-descriptions-item>
      </el-descriptions>
      <el-form v-else ref="userFormRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" :disabled="dialogType === 'view'" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="form.gender" :disabled="dialogType === 'view'" placeholder="请选择性别">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="身份" prop="role">
          <el-select v-model="form.role" :disabled="dialogType === 'view'" placeholder="请选择身份">
            <el-option label="管理员" value="admin" />
            <el-option label="安装师傅" value="installer" />
            <el-option label="普通用户" value="customer" />
          </el-select>
        </el-form-item>
        <el-form-item label="地区" prop="area">
          <el-input v-model="form.area" :disabled="dialogType === 'view'" placeholder="请输入地区" />
        </el-form-item>
        <el-form-item label="身份证" prop="idCard">
          <el-input v-model="form.idCard" :disabled="dialogType === 'view'" placeholder="请输入身份证号" />
        </el-form-item>
      </el-form>
      <!-- 弹窗底部按钮 -->
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button v-if="dialogType !== 'view'" type="primary" :loading="submitting" @click="submitForm">保存修改</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="blacklistDialogVisible" :title="blacklistTarget?.blacklist ? '移出黑名单' : '加入黑名单'" width="460px" @closed="blacklistReason = ''">
      <p class="dialog-tip">{{ blacklistTarget?.blacklist ? '移出后该用户可重新登录。' : '加入后该用户将无法登录，请说明操作原因。' }}</p>
      <el-input v-model="blacklistReason" type="textarea" :rows="3" maxlength="100" show-word-limit placeholder="请输入操作原因" />
      <template #footer>
        <el-button @click="blacklistDialogVisible = false">取消</el-button>
        <el-button :type="blacklistTarget?.blacklist ? 'primary' : 'danger'" :loading="blacklistLoading" @click="handleBlacklist">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Bell } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { getUserListApi, toggleBlacklistApi, updateUserApi } from '@/api/users'
import { getAuditListApi } from '@/api/audit'

const router = useRouter()
const route = useRoute()

// 搜索表单
const searchForm = reactive({
  nickname: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  role: typeof route.query.role === 'string' ? route.query.role : ''
})

// 分页配置
const currentPage = ref(Number(route.query.page) || 1)
const pageSize = ref(Number(route.query.pageSize) || 10)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const auditCount = ref(0)

// 列表数据
const tableData = ref([])

// 弹窗控制
const dialogVisible = ref(false)
const dialogType = ref('') // view 查看 / edit 编辑
const dialogTitle = ref('')
const userFormRef = ref(null)
const submitting = ref(false)
const blacklistDialogVisible = ref(false)
const blacklistTarget = ref(null)
const blacklistReason = ref('')
const blacklistLoading = ref(false)
// 表单数据 匹配UI字段：昵称、性别、身份、地区、身份证
const form = reactive({
  id: '',
  nickname: '',
  gender: '',
  role: '',
  area: '',
  idCard: ''
})

const formRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  role: [{ required: true, message: '请选择身份', trigger: 'change' }],
  idCard: [{ pattern: /(^\d{15}$)|(^\d{17}[\dXx]$)/, message: '身份证号格式不正确', trigger: 'blur' }]
}

/**
 * 加载用户列表
 */
async function loadList() {
  syncRouteState()
  loading.value = true
  loadError.value = ''
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    const kw = searchForm.nickname.trim()
    if (kw) params.keyword = kw
    if (searchForm.role) params.role = searchForm.role
    const res = await getUserListApi(params)
    tableData.value = res?.list || []
    total.value = res?.total || 0
  } catch (e) {
    tableData.value = []
    total.value = 0
    loadError.value = '用户数据加载失败，请检查网络后重试。'
  } finally {
    loading.value = false
  }
}

// 角色标签颜色
const getRoleTagType = (role) => {
  const map = { admin: 'danger', installer: 'warning', customer: 'success' }
  return map[role] || 'info'
}
// 角色文字转换
const getRoleText = (role) => {
  const map = { admin: '管理员', installer: '安装师傅', customer: '普通用户' }
  return map[role] || role
}
// 手机号脱敏
const formatPhone = (phone) => {
  if (!phone) return ''
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  loadList()
}
// 重置搜索
const handleReset = () => {
  searchForm.nickname = ''
  searchForm.role = ''
  currentPage.value = 1
  loadList()
}

// 分页切换
const handleSizeChange = () => {
  currentPage.value = 1
  loadList()
}
const handleCurrentChange = () => {
  loadList()
}

// 打开弹窗：view查看 / edit编辑
const openDialog = (type, row) => {
  dialogType.value = type
  // 回填表单
  form.id = row.id
  form.nickname = row.nickname
  form.gender = row.gender
  form.role = row.role
  form.area = row.area
  form.idCard = row.idCard
  // 弹窗标题
  dialogTitle.value = type === 'view' ? '用户详情' : '修改用户信息'
  dialogVisible.value = true
}

// 重置表单
const resetForm = () => {
  userFormRef.value?.clearValidate()
  form.id = ''
  form.nickname = ''
  form.gender = ''
  form.role = ''
  form.area = ''
  form.idCard = ''
}

// 提交编辑表单
const submitForm = async () => {
  if (submitting.value) return
  try {
    await userFormRef.value.validate()
    submitting.value = true
    await updateUserApi(form.id, {
      nickname: form.nickname,
      gender: form.gender,
      role: form.role,
      area: form.area,
      idCard: form.idCard
    })
    dialogVisible.value = false
    ElMessage.success('用户信息修改成功')
    loadList()
  } catch (e) {
    // 校验失败或接口失败，错误已在拦截器/表单内提示
  } finally {
    submitting.value = false
  }
}

// 黑名单气泡确认逻辑：调用接口切换，成功后本地更新状态
const openBlacklistDialog = (row) => {
  blacklistTarget.value = row
  blacklistDialogVisible.value = true
}

function syncRouteState() {
  router.replace({ query: {
    ...(searchForm.nickname.trim() ? { keyword: searchForm.nickname.trim() } : {}),
    ...(searchForm.role ? { role: searchForm.role } : {}),
    ...(currentPage.value > 1 ? { page: currentPage.value } : {}),
    ...(pageSize.value !== 10 ? { pageSize: pageSize.value } : {})
  } })
}

const handleBlacklist = async () => {
  const row = blacklistTarget.value
  if (!row) return
  if (!blacklistReason.value.trim()) {
    ElMessage.warning('请填写操作原因')
    return
  }
  blacklistLoading.value = true
  try {
    await toggleBlacklistApi(row.id, { blacklist: !row.blacklist, reason: blacklistReason.value.trim() })
    row.blacklist = !row.blacklist
    ElMessage.success(row.blacklist ? '已加入黑名单' : '已移出黑名单')
    blacklistDialogVisible.value = false
  } catch (e) {
    // 错误已在响应拦截器提示，状态保持不变
  } finally {
    blacklistLoading.value = false
  }
}

// 跳转用户审核列表页
const handleAudit = () => {
  router.push({ name: 'UserAudit' })
}

const maskIdCard = (value) => {
  if (!value || value.length < 8) return value || '-'
  return `${value.slice(0, 4)}**********${value.slice(-4)}`
}

async function loadAuditCount() {
  try {
    const res = await getAuditListApi({ page: 1, pageSize: 1, status: 'pending' }, { silent: true })
    auditCount.value = Number(res?.total || 0)
  } catch {
    auditCount.value = 0
  }
}

onMounted(() => {
  loadList()
  loadAuditCount()
})
</script>

<style lang="scss" scoped>
.user-page {
  padding: 20px;
  background-color: #f8fafc;
  min-height: 100vh;

  // 顶部头部 全站统一
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
  }

  // 搜索卡片 单行横向不换行
  .search-card {
    margin-bottom: 16px;
    border-radius: 8px;
    border: none;

    :deep(.el-card__body) {
      padding: 18px;
    }

    .search-bar {
      .search-input {
        width: 320px;

        :deep(.el-input__wrapper) {
          border: 1px solid #d1d5db;
          box-shadow: none;
          border-radius: 6px;
        }
      }

      .role-select {
        width: 160px;
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

  // 表格卡片
  .table-card {
    border-radius: 8px;
    border: none;

    :deep(.el-card__body) {
      padding: 18px;
    }
  }

  // 分页居右
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

  // 弹窗底部按钮靠右
  .dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
}

// 移动端适配
@media (max-width: 768px) {
  .user-page {
    padding: 12px;

    .page-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 14px;
    }

    .search-bar {
      flex-wrap: wrap;

      .search-input {
        width: 100%;
      }
    }
  }
}
</style>
