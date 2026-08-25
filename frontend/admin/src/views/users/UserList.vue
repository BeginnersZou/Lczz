<template>
  <div class="user-page">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">用户管理</h2>
        <span class="page-desc">管理后台与小程序用户，支持角色、账号状态及黑名单管控</span>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增用户</el-button>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form :model="searchForm" class="search-form" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            class="keyword-input"
            clearable
            placeholder="昵称、姓名或手机号"
            :prefix-icon="Search"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" class="filter-select" clearable placeholder="全部角色" @change="handleSearch">
            <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="账号状态">
          <el-select v-model="searchForm.accountStatus" class="filter-select" clearable placeholder="全部状态" @change="handleSearch">
            <el-option label="已启用" value="ENABLED" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="黑名单">
          <el-select v-model="searchForm.blacklist" class="filter-select" clearable placeholder="全部" @change="handleSearch">
            <el-option label="正常用户" :value="false" />
            <el-option label="黑名单用户" :value="true" />
          </el-select>
        </el-form-item>
        <el-form-item class="search-actions">
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-alert v-if="loadError" class="load-alert" :title="loadError" type="error" show-icon :closable="false">
        <template #default>
          <el-button type="primary" link @click="loadList">重新加载</el-button>
        </template>
      </el-alert>

      <el-table v-else v-loading="loading" :data="tableData" border stripe table-layout="fixed">
        <el-table-column label="用户" min-width="152">
          <template #default="{ row }">
            <div class="user-cell">
              <span class="primary-text">{{ row.nickname || row.username || '-' }}</span>
              <span class="secondary-text">{{ row.realName || row.username || '未填写真实姓名' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="手机号" width="128">
          <template #default="{ row }">{{ formatPhone(row.phone) }}</template>
        </el-table-column>
        <el-table-column label="性别" width="64" align="center">
          <template #default="{ row }">{{ getGenderText(row.gender) }}</template>
        </el-table-column>
        <el-table-column label="角色" width="92" align="center">
          <template #default="{ row }">
            <el-tag :type="getRoleTagType(row.role)" size="small" effect="light">{{ getRoleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="92" align="center">
          <template #default="{ row }">
            <el-tag :type="row.accountStatus === 'ENABLED' ? 'success' : 'info'" size="small" effect="light">
              {{ getAccountStatusText(row.accountStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="黑名单" width="84" align="center">
          <template #default="{ row }">
            <el-tag :type="row.blacklist ? 'danger' : 'success'" size="small" effect="plain">
              {{ row.blacklist ? '已拉黑' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间信息" min-width="168">
          <template #default="{ row }">
            <div class="time-stack">
              <span><em>登录</em>{{ formatDateTime(row.lastLoginAt) }}</span>
              <span><em>注册</em>{{ formatDateTime(row.createdAt || row.registerTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="208" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openDialog('view', row)">详情</el-button>
              <el-button link type="primary" @click="openDialog('edit', row)">编辑</el-button>
              <el-button
                link
                :type="row.accountStatus === 'ENABLED' ? 'warning' : 'success'"
                :loading="statusLoadingId === row.id"
                @click="handleStatusChange(row)"
              >
                {{ row.accountStatus === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
              <el-button link :type="row.blacklist ? 'primary' : 'danger'" @click="openBlacklistDialog(row)">
                {{ row.blacklist ? '移出黑名单' : '加入黑名单' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的用户" :image-size="92" />
        </template>
      </el-table>

      <div v-if="!loadError && total > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          background
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          @size-change="handleSizeChange"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" destroy-on-close @closed="resetForm">
      <div v-loading="dialogLoading" class="dialog-content">
        <el-descriptions v-if="dialogType === 'view'" :column="2" border>
          <el-descriptions-item label="登录账号">{{ form.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ form.nickname || '-' }}</el-descriptions-item>
          <el-descriptions-item label="真实姓名">{{ form.realName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ getGenderText(form.gender) }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ formatPhone(form.phone) }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ getRoleText(form.role) }}</el-descriptions-item>
          <el-descriptions-item label="账号状态">{{ getAccountStatusText(form.accountStatus) }}</el-descriptions-item>
          <el-descriptions-item label="黑名单">{{ form.blacklist ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="师傅状态">{{ getInstallerStatusText(form.installerStatus) }}</el-descriptions-item>
          <el-descriptions-item label="最近登录">{{ formatDateTime(form.lastLoginAt) }}</el-descriptions-item>
          <el-descriptions-item label="注册时间" :span="2">{{ formatDateTime(form.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-form v-else ref="userFormRef" :model="form" :rules="formRules" label-width="96px">
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="form.nickname" maxlength="64" show-word-limit placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="form.realName" maxlength="64" show-word-limit placeholder="未填写可留空" />
          </el-form-item>
          <el-form-item label="性别" prop="gender">
            <el-select v-model="form.gender" clearable placeholder="未设置">
              <el-option label="男" value="MALE" />
              <el-option label="女" value="FEMALE" />
              <el-option label="未知" value="UNKNOWN" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="dialogType === 'create'" label="手机号" prop="phone">
            <el-input v-model="form.phone" maxlength="11" placeholder="请输入11位手机号" inputmode="numeric" />
          </el-form-item>
          <el-form-item label="角色" prop="role">
            <el-select v-model="form.role" placeholder="请选择角色">
              <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-alert
            v-if="dialogType === 'edit'"
            title="手机号、账号状态及认证材料不在资料编辑契约内，请使用对应操作入口。"
            type="info"
            show-icon
            :closable="false"
          />
          <el-alert
            v-else
            title="用户首次在小程序授权相同手机号后，将自动绑定此账号并保留当前角色。"
            type="info"
            show-icon
            :closable="false"
          />
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ dialogType === 'view' ? '关闭' : '取消' }}</el-button>
        <el-button v-if="dialogType !== 'view'" type="primary" :loading="submitting" @click="submitForm">
          {{ dialogType === 'create' ? '创建用户' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="blacklistDialogVisible"
      :title="blacklistTarget?.blacklist ? '移出黑名单' : '加入黑名单'"
      width="480px"
      @closed="resetBlacklistDialog"
    >
      <el-alert
        :title="blacklistTarget?.blacklist ? '移出后该用户将恢复正常状态。' : '加入黑名单属于高风险操作，请填写原因后确认。'"
        :type="blacklistTarget?.blacklist ? 'info' : 'warning'"
        show-icon
        :closable="false"
      />
      <el-input
        v-model="blacklistReason"
        class="reason-input"
        type="textarea"
        :rows="4"
        minlength="2"
        maxlength="500"
        show-word-limit
        placeholder="请输入 2-500 字操作原因"
      />
      <template #footer>
        <el-button @click="blacklistDialogVisible = false">取消</el-button>
        <el-button :type="blacklistTarget?.blacklist ? 'primary' : 'danger'" :loading="blacklistLoading" @click="handleBlacklist">
          确认{{ blacklistTarget?.blacklist ? '移出' : '加入' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  changeUserStatusApi,
  createUserApi,
  getUserDetailApi,
  getUserListApi,
  toggleBlacklistApi,
  updateUserApi
} from '@/api/users'
import { formatDateTime, formatPhone } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const roleOptions = [
  { label: '管理员', value: 'ADMIN' },
  { label: '安装师傅', value: 'INSTALLER' },
  { label: '普通用户', value: 'CUSTOMER' },
  { label: '经销商', value: 'DEALER' }
]

const routeBlacklist = route.query.blacklist
const searchForm = reactive({
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  role: typeof route.query.role === 'string' ? route.query.role.toUpperCase() : '',
  accountStatus: typeof route.query.accountStatus === 'string' ? route.query.accountStatus.toUpperCase() : '',
  blacklist: routeBlacklist === 'true' ? true : routeBlacklist === 'false' ? false : ''
})

const currentPage = ref(Math.max(1, Number(route.query.page) || 1))
const pageSize = ref([10, 20, 50].includes(Number(route.query.pageSize)) ? Number(route.query.pageSize) : 10)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const tableData = ref([])

const dialogVisible = ref(false)
const dialogLoading = ref(false)
const dialogType = ref('view')
const dialogTitle = ref('用户详情')
const userFormRef = ref(null)
const submitting = ref(false)
const originalRole = ref('')
const statusLoadingId = ref(null)

const blacklistDialogVisible = ref(false)
const blacklistTarget = ref(null)
const blacklistReason = ref('')
const blacklistLoading = ref(false)

const form = reactive({
  id: '',
  username: '',
  nickname: '',
  realName: '',
  gender: '',
  phone: '',
  role: '',
  accountStatus: '',
  blacklist: false,
  installerStatus: '',
  lastLoginAt: '',
  createdAt: ''
})

const formRules = {
  nickname: [{ required: true, whitespace: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请输入正确的11位手机号', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

async function loadList() {
  syncRouteState()
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: currentPage.value, pageSize: pageSize.value }
    const keyword = searchForm.keyword.trim()
    if (keyword) params.keyword = keyword
    if (searchForm.role) params.role = searchForm.role
    if (searchForm.accountStatus) params.accountStatus = searchForm.accountStatus
    if (typeof searchForm.blacklist === 'boolean') params.blacklist = searchForm.blacklist
    const data = await getUserListApi(params, { silent: true })
    tableData.value = Array.isArray(data?.list) ? data.list : []
    total.value = Number(data?.total || 0)
  } catch (error) {
    tableData.value = []
    total.value = 0
    const status = error?.response?.status
    loadError.value = status === 404
      ? '联调服务尚未部署最新用户管理接口，请后端更新 dev 服务后重试。'
      : status === 403
        ? '当前账号没有用户管理权限，请使用管理员账号登录。'
        : '用户数据加载失败，请检查联调服务后重试。'
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadList()
}

function handleReset() {
  Object.assign(searchForm, { keyword: '', role: '', accountStatus: '', blacklist: '' })
  currentPage.value = 1
  loadList()
}

function handleSizeChange() {
  currentPage.value = 1
  loadList()
}

function syncRouteState() {
  router.replace({
    query: {
      ...(searchForm.keyword.trim() ? { keyword: searchForm.keyword.trim() } : {}),
      ...(searchForm.role ? { role: searchForm.role } : {}),
      ...(searchForm.accountStatus ? { accountStatus: searchForm.accountStatus } : {}),
      ...(typeof searchForm.blacklist === 'boolean' ? { blacklist: String(searchForm.blacklist) } : {}),
      ...(currentPage.value > 1 ? { page: currentPage.value } : {}),
      ...(pageSize.value !== 10 ? { pageSize: pageSize.value } : {})
    }
  })
}

async function openDialog(type, row) {
  dialogType.value = type
  dialogTitle.value = type === 'view' ? '用户详情' : '编辑用户资料'
  dialogVisible.value = true
  dialogLoading.value = true
  fillForm(row)
  try {
    const detail = await getUserDetailApi(row.id)
    fillForm(detail || row)
  } catch {
    ElMessage.warning('用户详情获取失败，当前展示列表中的最新数据')
  } finally {
    dialogLoading.value = false
  }
}

function openCreateDialog() {
  resetForm()
  dialogType.value = 'create'
  dialogTitle.value = '新增用户'
  form.gender = 'UNKNOWN'
  dialogVisible.value = true
}

function fillForm(user = {}) {
  Object.assign(form, {
    id: user.id ?? '',
    username: user.username ?? '',
    nickname: user.nickname ?? '',
    realName: user.realName ?? '',
    gender: user.gender ?? '',
    phone: user.phone ?? '',
    role: String(user.role || user.roles?.[0] || '').toUpperCase(),
    accountStatus: user.accountStatus ?? '',
    blacklist: Boolean(user.blacklist),
    installerStatus: user.installerStatus ?? '',
    lastLoginAt: user.lastLoginAt ?? '',
    createdAt: user.createdAt || user.registerTime || ''
  })
  originalRole.value = form.role
}

function resetForm() {
  userFormRef.value?.clearValidate()
  Object.assign(form, {
    id: '', username: '', nickname: '', realName: '', gender: '', phone: '', role: '',
    accountStatus: '', blacklist: false, installerStatus: '', lastLoginAt: '', createdAt: ''
  })
  originalRole.value = ''
}

async function submitForm() {
  if (submitting.value) return
  try {
    await userFormRef.value?.validate()
    if (dialogType.value === 'edit' && form.role !== originalRole.value) {
      await ElMessageBox.confirm(
        `确认将该用户角色调整为“${getRoleText(form.role)}”吗？角色变更会影响其可访问功能。`,
        '确认角色变更',
        { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' }
      )
    }
    submitting.value = true
    const payload = {
      nickname: form.nickname.trim(),
      realName: form.realName.trim() || null,
      gender: form.gender || null,
      role: form.role
    }
    if (dialogType.value === 'create') {
      await createUserApi({ ...payload, phone: form.phone.trim() })
    } else {
      await updateUserApi(form.id, payload)
    }
    dialogVisible.value = false
    ElMessage.success(dialogType.value === 'create' ? '用户创建成功，可立即用于订单指派' : '用户资料已更新')
    await loadList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && !error?.fields) {
      // 接口错误由请求拦截器统一反馈。
    }
  } finally {
    submitting.value = false
  }
}

async function handleStatusChange(row) {
  const nextStatus = row.accountStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const action = nextStatus === 'ENABLED' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      `确认${action}用户“${row.nickname || row.username || row.id}”吗？`,
      `确认${action}账号`,
      { type: nextStatus === 'DISABLED' ? 'warning' : 'info', confirmButtonText: `确认${action}`, cancelButtonText: '取消' }
    )
    statusLoadingId.value = row.id
    const updated = await changeUserStatusApi(row.id, nextStatus)
    Object.assign(row, updated || { accountStatus: nextStatus })
    ElMessage.success(`账号已${action}`)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      // 接口错误由请求拦截器统一反馈。
    }
  } finally {
    statusLoadingId.value = null
  }
}

function openBlacklistDialog(row) {
  blacklistTarget.value = row
  blacklistReason.value = ''
  blacklistDialogVisible.value = true
}

function resetBlacklistDialog() {
  blacklistTarget.value = null
  blacklistReason.value = ''
}

async function handleBlacklist() {
  const row = blacklistTarget.value
  const reason = blacklistReason.value.trim()
  if (!row) return
  if (reason.length < 2) {
    ElMessage.warning('操作原因至少填写 2 个字符')
    return
  }
  blacklistLoading.value = true
  try {
    const updated = await toggleBlacklistApi(row.id, { blacklist: !row.blacklist, reason })
    Object.assign(row, updated || { blacklist: !row.blacklist })
    ElMessage.success(row.blacklist ? '用户已加入黑名单' : '用户已移出黑名单')
    blacklistDialogVisible.value = false
  } finally {
    blacklistLoading.value = false
  }
}

function getRoleTagType(role) {
  return { ADMIN: 'danger', INSTALLER: 'warning', CUSTOMER: 'success', DEALER: 'primary' }[String(role || '').toUpperCase()] || 'info'
}

function getRoleText(role) {
  return { ADMIN: '管理员', INSTALLER: '安装师傅', CUSTOMER: '普通用户', DEALER: '经销商' }[String(role || '').toUpperCase()] || role || '-'
}

function getGenderText(gender) {
  return { MALE: '男', FEMALE: '女', UNKNOWN: '未知', '男': '男', '女': '女' }[gender] || '-'
}

function getAccountStatusText(status) {
  return { ENABLED: '已启用', DISABLED: '已停用' }[status] || status || '-'
}

function getInstallerStatusText(status) {
  return { PENDING: '待认证', APPROVED: '已认证', REJECTED: '已驳回', DISABLED: '已停用' }[status] || status || '不适用'
}

onMounted(loadList)
</script>

<style lang="scss" scoped>
.user-page {
  min-height: 100%;
  padding: 20px;
  background: #f8fafc;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;

  .page-title {
    margin: 0 0 6px;
    color: #0f172a;
    font-size: 22px;
    font-weight: 700;
  }

  .page-desc {
    color: #64748b;
    font-size: 14px;
  }
}

.search-card,
.table-card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.search-card {
  margin-bottom: 16px;

  :deep(.el-card__body) {
    padding: 18px 20px 2px;
  }
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0 14px;

  .keyword-input {
    width: 260px;
  }

  .filter-select {
    width: 148px;
  }

  .search-actions {
    margin-left: auto;
  }
}

.table-card {
  :deep(.el-card__body) {
    padding: 0;
  }

  :deep(.el-table th.el-table__cell) {
    height: 50px;
    color: #334155;
    background: #f8fafc;
  }

  :deep(.el-table td.el-table__cell) {
    height: 62px;
  }
}

.load-alert {
  margin: 20px;
}

.user-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;

  .primary-text,
  .secondary-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .primary-text {
    color: #0f172a;
    font-weight: 600;
  }

  .secondary-text {
    color: #94a3b8;
    font-size: 12px;
  }
}

.datetime-cell {
  color: #475569;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.time-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #475569;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;

  span {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  em {
    color: #94a3b8;
    font-style: normal;
  }
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 16px 20px;
  border-top: 1px solid #e2e8f0;
}

.dialog-content {
  min-height: 120px;

  :deep(.el-select) {
    width: 100%;
  }
}

.reason-input {
  margin-top: 18px;
}

@media (max-width: 1100px) {
  .search-form .search-actions {
    margin-left: 0;
  }
}

@media (max-width: 768px) {
  .user-page {
    padding: 14px;
  }

  .search-form {
    display: block;

    .keyword-input,
    .filter-select {
      width: 100%;
    }
  }
}
</style>
