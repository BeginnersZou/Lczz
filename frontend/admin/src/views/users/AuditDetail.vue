<template>
  <div class="audit-detail-page">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <el-button dashed plain type="default" :icon="ArrowLeft" @click="handleBack" class="back-btn">
        返回
      </el-button>
      <h2 class="page-title">用户审核</h2>
      <div class="header-empty"></div>
    </div>

    <div v-if="loadError" class="error-state">
      <span>{{ loadError }}</span><el-button type="primary" link @click="loadDetail">重新加载</el-button>
    </div>
    <div v-else class="detail-content" v-loading="pageLoading">
      <!-- 身份信息卡片 -->
      <el-card class="info-card" shadow="light">
        <template #header>
          <span class="card-title">
            <el-icon>
              <User />
            </el-icon>
            身份信息
          </span>
        </template>

        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">认证类型</span>
            <span class="info-value">{{ isEnterprise ? '企业认证' : '个人认证' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">联系电话</span>
            <span class="info-value">{{ formatPhone(detail.phone) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">真实姓名</span>
            <span class="info-value">{{ detail.realName || '-' }}</span>
          </div>
          <div class="info-item" v-if="isEnterprise">
            <span class="info-label">企业名称</span>
            <span class="info-value">{{ detail.enterpriseName || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">身份证号</span>
            <span class="info-value">{{ maskIdCard(detail.idCard) }}</span>
          </div>
          <div class="info-item" v-if="isEnterprise">
            <span class="info-label">统一社会信用代码</span>
            <span class="info-value">{{ detail.creditCode || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">提交时间</span>
            <span class="info-value datetime-cell">{{ formatDateTime(detail.submitTime || detail.createdAt) }}</span>
          </div>
          <div class="info-item" v-if="detail.rejectReason">
            <span class="info-label">驳回原因</span>
            <span class="info-value danger-text">{{ detail.rejectReason }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">详细地址</span>
            <span class="info-value">{{ detail.address || '-' }}</span>
          </div>
          <div class="info-item" v-if="isEnterprise">
            <span class="info-label">公司地址</span>
            <span class="info-value">{{ detail.companyAddress || '-' }}</span>
          </div>
        </div>
      </el-card>

      <!-- 相关材料卡片 -->
      <el-card class="material-card" shadow="light">
        <template #header>
          <span class="card-title">
            <el-icon>
              <Picture />
            </el-icon>
            相关材料
          </span>
        </template>

        <div class="material-list" v-if="detail.materials && detail.materials.length">
          <div class="material-item" v-for="(url, index) in detail.materials" :key="index" @click="handlePreview(url)">
            <el-image :src="url" fit="cover" class="material-img" :preview-src-list="detail.materials"
              :initial-index="index" preview-teleported />
            <div class="material-mask">
              <el-icon>
                <ZoomIn />
              </el-icon>
              <span>查看大图</span>
            </div>
            <div class="material-name">{{ getMaterialName(index) }}</div>
          </div>
        </div>
        <el-empty v-else description="未上传相关材料" />
      </el-card>

      <!-- 底部操作栏 -->
      <div class="detail-footer">
        <div class="audit-status" v-if="detail.status !== 'pending'">
          <el-tag :type="getStatusType(detail.status)" size="large" effect="light">
            {{ getStatusText(detail.status) }}
          </el-tag>
          <span class="audit-time" v-if="detail.auditTime">审核时间：{{ formatDateTime(detail.auditTime) }}</span>
        </div>
        <div class="footer-btns">
          <el-button dashed plain type="default" :icon="ArrowLeft" @click="handleBack">
            返回
          </el-button>
          <template v-if="detail.status === 'pending'">
            <el-button dashed plain type="danger" :icon="Close" @click="handleReject">
              审核不通过
            </el-button>
            <el-button dashed plain type="primary" :icon="Check" @click="handleApprove">
              审核通过
            </el-button>
          </template>
        </div>
      </div>
    </div>

    <!-- 审核不通过弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="审核不通过" width="520px" @close="resetRejectForm">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="100px">
        <el-form-item label="申请人">
          <span>{{ detail.realName || detail.enterpriseName || detail.nickname }}</span>
        </el-form-item>
        <el-form-item label="驳回原因" prop="reason">
          <el-input v-model="rejectForm.reason" type="textarea" :rows="4" placeholder="请输入审核不通过的原因，便于申请人修改后重新提交"
            maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="rejectDialogVisible = false">取消</el-button>
          <el-button type="danger" :loading="auditLoading" @click="confirmReject">确认驳回</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 审核通过确认弹窗 -->
    <el-dialog v-model="approveDialogVisible" title="提示" width="400px">
      <p>确定通过 <strong>{{ detail.realName || detail.enterpriseName || detail.nickname }}</strong> 的审核申请吗？</p>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="approveDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="auditLoading" @click="confirmApprove">确认通过</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, Close, User, Picture, ZoomIn } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { getAuditDetailApi, approveAuditApi, rejectAuditApi } from '@/api/audit'
import { formatDateTime, formatPhone } from '@/utils/format'

const router = useRouter()
const route = useRoute()

const pageLoading = ref(false)
const loadError = ref('')
const auditLoading = ref(false)
const approveDialogVisible = ref(false)
const rejectDialogVisible = ref(false)
const rejectFormRef = ref(null)

const rejectForm = reactive({
  reason: ''
})

const rejectRules = {
  reason: [
    { required: true, message: '请输入驳回原因', trigger: 'blur' },
    { min: 2, max: 200, message: '原因长度2~200字符', trigger: 'blur' }
  ]
}

// 默认空详情
const detail = ref({
  id: '',
  nickname: '',
  phone: '',
  auditType: 'personal',
  realName: '',
  idCard: '',
  address: '',
  enterpriseName: '',
  creditCode: '',
  companyAddress: '',
  submitTime: '',
  status: 'pending',
  materials: []
})

const isEnterprise = computed(() => detail.value.auditType === 'enterprise')

const getStatusType = (status) => {
  const map = { pending: 'warning', approved: 'success', rejected: 'danger' }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = { pending: '待审核', approved: '已通过', rejected: '已驳回' }
  return map[status] || status
}

/**
 * 加载审核详情：onMounted 调 getAuditDetailApi(id) 回显
 */
async function loadDetail() {
  const id = route.params.id
  if (id === undefined || id === null || id === '') {
    ElMessage.error('缺少审核记录 ID')
    handleBack()
    return
  }
  pageLoading.value = true
  loadError.value = ''
  try {
    const data = await getAuditDetailApi(id)
    if (data) {
      detail.value = {
        ...detail.value,
        ...data,
        materials: Array.isArray(data.materials) ? data.materials : []
      }
    } else {
      ElMessage.error('未找到该审核记录')
      handleBack()
    }
  } catch (e) {
    loadError.value = '审核详情加载失败，请检查网络后重试。'
  } finally {
    pageLoading.value = false
  }
}

const handleBack = () => {
  const backPath = window.history.state?.back
  if (typeof backPath === 'string' && /^\/users\/audit(?:\?|$)/.test(backPath)) {
    router.back()
  } else {
    router.push({ name: 'UserAudit' })
  }
}

const handlePreview = (url) => {
  // el-image preview 通过 click 触发，此方法仅作兜底
}

const maskIdCard = (value) => {
  if (!value || value.length < 8) return value || '-'
  return `${value.slice(0, 4)}**********${value.slice(-4)}`
}

const getMaterialName = (index) => {
  if (isEnterprise.value) return ['营业执照', '法人身份证正面', '法人身份证反面'][index] || `补充材料 ${index + 1}`
  return ['身份证正面', '身份证反面', '资格证明'][index] || `补充材料 ${index + 1}`
}

const handleApprove = () => {
  approveDialogVisible.value = true
}

const handleReject = () => {
  rejectDialogVisible.value = true
}

const resetRejectForm = () => {
  rejectForm.reason = ''
  rejectFormRef.value?.clearValidate()
}

/**
 * 确认审核通过
 */
const confirmApprove = async () => {
  if (auditLoading.value) return
  auditLoading.value = true
  try {
    await approveAuditApi(detail.value.id)
    detail.value.status = 'approved'
    detail.value.auditTime = new Date().toISOString()
    approveDialogVisible.value = false
    ElMessage.success('审核已通过')
    router.replace({ name: 'UserAudit', query: { status: 'pending' } })
  } catch (e) {
    // 错误已在响应拦截器提示
  } finally {
    auditLoading.value = false
  }
}

/**
 * 确认审核驳回
 */
const confirmReject = async () => {
  if (auditLoading.value) return
  try {
    await rejectFormRef.value.validate()
    auditLoading.value = true
    await rejectAuditApi(detail.value.id, { reason: rejectForm.reason })
    detail.value.status = 'rejected'
    detail.value.auditTime = new Date().toISOString()
    detail.value.rejectReason = rejectForm.reason
    rejectDialogVisible.value = false
    ElMessage.success('审核已驳回')
    router.replace({ name: 'UserAudit', query: { status: 'pending' } })
  } catch (e) {
    // 校验失败或接口失败，错误已在拦截器/表单内提示
  } finally {
    auditLoading.value = false
  }
}

onMounted(loadDetail)
</script>

<style lang="scss" scoped>
.audit-detail-page {
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

  .detail-content {

    .info-card,
    .material-card {
      margin-bottom: 16px;
      border-radius: 8px;
      border: none;

      :deep(.el-card__header) {
        padding: 12px 18px;
        background: #f8fafc;
        border-bottom: 1px solid #e5e7eb;
      }

      :deep(.el-card__body) {
        padding: 24px;
      }

      .card-title {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 15px;
        font-weight: 500;
        color: #1f2937;
      }
    }

    .info-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 24px 40px;

      .info-item {
        display: flex;
        align-items: baseline;
        gap: 16px;

        .info-label {
          flex: 0 0 130px;
          font-size: 14px;
          color: #6b7280;
          text-align: right;
        }

        .info-value {
          flex: 1;
          font-size: 14px;
          color: #1f2937;
          font-weight: 500;
          word-break: break-all;
        }
      }
    }

    .material-list {
      display: flex;
      flex-wrap: wrap;
      gap: 20px;

      .material-item {
        width: 200px;
        height: 178px;
        border-radius: 10px;
        overflow: hidden;
        position: relative;
        box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
        cursor: pointer;
        transition: transform 0.25s ease, box-shadow 0.25s ease;

        &:hover {
          transform: translateY(-3px);
          box-shadow: 0 8px 20px rgba(15, 23, 42, 0.18);

          .material-mask {
            opacity: 1;
          }
        }

        .material-img {
          width: 100%;
          height: 145px;
          display: block;
        }

        .material-mask {
          position: absolute;
          inset: 0;
          background: linear-gradient(180deg, rgba(15, 23, 42, 0) 0%, rgba(15, 23, 42, 0.6) 100%);
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          gap: 6px;
          opacity: 0;
          transition: opacity 0.25s ease;
          color: #fff;
          font-size: 13px;

          .el-icon {
            font-size: 22px;
          }
        }

        .material-name {
          position: absolute;
          left: 0;
          right: 0;
          bottom: 0;
          height: 33px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #475569;
          background: #fff;
          font-size: 13px;
        }
      }
    }
  }

  .danger-text {
    color: #dc2626 !important;
  }

  .detail-footer {
    margin-top: 24px;
    padding: 18px;
    background: #ffffff;
    border-radius: 8px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
    display: flex;
    justify-content: space-between;
    align-items: center;

    .audit-status {
      display: flex;
      align-items: center;
      gap: 12px;

      .audit-time {
        font-size: 13px;
        color: #6b7280;
      }
    }

    .footer-btns {
      display: flex;
      gap: 14px;

      :deep(.el-button) {
        padding: 8px 26px;
        border-radius: 6px;
        display: flex;
        align-items: center;
        gap: 6px;
      }
    }
  }

  .dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
}

@media (max-width: 1024px) {
  .audit-detail-page {
    .info-grid {
      grid-template-columns: 1fr !important;
    }
  }
}

@media (max-width: 768px) {
  .audit-detail-page {
    padding: 12px;

    .page-header {
      grid-template-columns: 1fr;
      gap: 12px;

      .page-title {
        text-align: left;
      }
    }

    .detail-footer {
      flex-direction: column;
      gap: 16px;
      align-items: flex-start;

      .footer-btns {
        width: 100%;
        justify-content: flex-end;
        flex-wrap: wrap;
      }
    }

    .material-list .material-item {
      width: 100%;
      height: 200px;
    }
  }
}
</style>
