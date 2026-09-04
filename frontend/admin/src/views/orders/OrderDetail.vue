<template>
  <div class="order-detail-page">
    <div class="detail-header">
      <div>
        <el-button link :icon="ArrowLeft" @click="backToList">返回订单列表</el-button>
        <h2>订单详情</h2>
        <p v-if="order" class="muted">{{ order.orderNo }} · {{ order.taskType }}</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadDetail">刷新详情</el-button>
    </div>

    <el-card v-if="loading" shadow="never" aria-label="正在加载订单详情">
      <el-skeleton :rows="8" animated />
    </el-card>
    <el-result v-else-if="loadError" icon="warning" :title="errorTitle" :sub-title="loadError">
      <template #extra>
        <el-button @click="backToList">返回订单列表</el-button>
        <el-button v-if="canRetry" type="primary" @click="loadDetail">重新加载</el-button>
      </template>
    </el-result>

    <template v-else-if="order">
      <el-card shadow="never">
        <template #header><h3>基础信息</h3></template>
        <el-descriptions :column="descriptionColumns" border>
          <el-descriptions-item label="订单编号">{{ order.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="statusType">{{ statusText }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="订单类型">{{ order.taskType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(order.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="客户确认时间" :span="descriptionColumns">{{ order.customerConfirmedAt ? formatDateTime(order.customerConfirmedAt) : '暂无客户确认记录' }}</el-descriptions-item>
          <el-descriptions-item label="客户姓名">{{ order.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户手机号">{{ formatPhone(order.customerPhone) }}</el-descriptions-item>
          <el-descriptions-item label="预约时间" :span="descriptionColumns">
            {{ formatDateTime(order.orderStartTime) }} 至 {{ formatDateTime(order.orderEndTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="安装地址" :span="descriptionColumns">{{ order.address || '-' }}</el-descriptions-item>
          <el-descriptions-item label="指派师傅" :span="descriptionColumns">
            <template v-if="order.selectedMasterList?.length">
              <div v-for="master in order.selectedMasterList" :key="master.id">
                {{ master.masterName }} · {{ formatPhone(master.masterPhone) }}
              </div>
            </template>
            <span v-else>待指派</span>
          </el-descriptions-item>
          <el-descriptions-item label="任务描述" :span="descriptionColumns"><div class="text-content">{{ order.description || '暂无描述' }}</div></el-descriptions-item>
          <el-descriptions-item label="管理员备注" :span="descriptionColumns"><div class="text-content">{{ order.adminRemark || '暂无备注' }}</div></el-descriptions-item>
          <el-descriptions-item v-if="order.cancelReason" label="作废原因" :span="descriptionColumns"><div class="text-content">{{ order.cancelReason }}</div></el-descriptions-item>
        </el-descriptions>
        <h4>订单附件</h4>
        <OrderDetailMedia v-if="order.fileList?.length" :files="order.fileList" />
        <el-empty v-else description="暂无订单附件" :image-size="60" />
      </el-card>

      <el-card shadow="never">
        <template #header><h3>施工进度 <span class="muted">{{ detail.progress.length }} 条</span></h3></template>
        <el-timeline v-if="detail.progress.length">
          <el-timeline-item v-for="progress in detail.progress" :key="progress.id"
            :timestamp="formatDateTime(progress.submittedAt)" placement="top">
            <el-tag size="small" :type="progress.type === 'COMPLETION' ? 'success' : 'primary'">
              {{ progress.type === 'COMPLETION' ? '完工记录' : '施工进度' }}
            </el-tag>
            <p class="text-content">{{ progress.description || '未填写说明' }}</p>
            <OrderDetailMedia v-if="progress.images?.length" :files="progress.images" />
            <span v-else class="muted">未上传现场图片</span>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="师傅尚未提交施工进度" :image-size="70" />
      </el-card>

      <el-card shadow="never">
        <template #header><h3>耗材清单 <span class="muted">{{ detail.materialRequests.length }} 次申请</span></h3></template>
        <template v-if="detail.materialRequests.length">
          <section v-for="request in detail.materialRequests" :key="request.id" class="material-request">
            <div class="request-header">
              <strong>{{ request.requestNo }}</strong>
              <el-tag :type="request.statusCode === 'VOIDED' ? 'info' : 'primary'">{{ request.statusLabel }}</el-tag>
              <span class="muted">提交时间：{{ formatDateTime(request.submittedAt) }}</span>
            </div>
            <p v-if="request.remark" class="text-content">申请备注：{{ request.remark }}</p>
            <p v-if="request.voidReason" class="text-content muted">作废原因：{{ request.voidReason }}</p>
            <el-table :data="request.materials" border stripe empty-text="该申请暂无耗材明细">
              <el-table-column prop="name" label="耗材名称" min-width="180" />
              <el-table-column prop="spec" label="规格" min-width="140">
                <template #default="{ row }">{{ row.spec || '-' }}</template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="90" />
              <el-table-column prop="count" label="申请数量" width="110" />
            </el-table>
          </section>
        </template>
        <el-empty v-else description="师傅尚未提交耗材申请" :image-size="70" />
      </el-card>

      <el-card shadow="never">
        <template #header><h3>客户评价</h3></template>
        <template v-if="detail.review">
          <div class="review-summary">
            <el-rate :model-value="detail.review.score" disabled show-score score-template="{value} 分" />
            <el-tag v-if="detail.review.liked" type="success">客户点赞</el-tag>
            <span class="muted">{{ formatDateTime(detail.review.createTime) }}</span>
          </div>
          <div v-if="detail.review.labels?.length" class="review-labels">
            <el-tag v-for="label in detail.review.labels" :key="label">{{ label }}</el-tag>
          </div>
          <p class="text-content">{{ detail.review.content || '客户未填写文字评价' }}</p>
          <OrderDetailMedia v-if="reviewImages.length" :files="reviewImages" />
        </template>
        <el-empty v-else description="客户尚未提交评价" :image-size="70" />
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElRate, ElResult, ElSkeleton, ElTimeline, ElTimelineItem } from 'element-plus'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { getAdminOrderDetailApi } from '@/api/orders'
import { formatDateTime, formatPhone } from '@/utils/format'
import OrderDetailMedia from './OrderDetailMedia.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const errorTitle = ref('订单详情加载失败')
const canRetry = ref(true)
const detail = ref(null)
const compactScreen = window.matchMedia('(max-width: 768px)')
const descriptionColumns = ref(compactScreen.matches ? 1 : 2)
const updateColumns = event => { descriptionColumns.value = event.matches ? 1 : 2 }
compactScreen.addEventListener('change', updateColumns)
let requestVersion = 0
const order = computed(() => detail.value?.order)
const statusText = computed(() => ({
  PENDING_VISIT: '待上门', IN_PROGRESS: '处理中', PENDING_REVIEW: '待评价', REVIEWED: '已评价', CANCELLED: '已作废'
}[order.value?.statusCode] || order.value?.status || '-'))
const statusType = computed(() => ({
  PENDING_VISIT: 'warning', IN_PROGRESS: 'primary', PENDING_REVIEW: 'warning', REVIEWED: 'success', CANCELLED: 'info'
}[order.value?.statusCode] || 'info'))
const reviewImages = computed(() => (detail.value?.review?.images || []).map(url => ({ url })))

function backToList() {
  router.push({ name: 'Orders', query: route.query })
}

async function loadDetail() {
  const version = ++requestVersion
  detail.value = null
  loadError.value = ''
  errorTitle.value = '订单详情加载失败'
  canRetry.value = true
  loading.value = true
  try {
    const result = await getAdminOrderDetailApi(route.params.id)
    if (version !== requestVersion) return
    if (!result?.order) throw new Error('详情响应缺少订单信息，请重试。')
    detail.value = result
  } catch (error) {
    if (version !== requestVersion) return
    const status = error.response?.status
    canRetry.value = ![400, 401, 403, 404].includes(status)
    if (status === 404) {
      errorTitle.value = '订单不存在'
      loadError.value = '该订单不存在或已被删除，请返回订单列表。'
    } else if (status === 403) {
      errorTitle.value = '无权查看订单详情'
      loadError.value = '仅管理员可查看后台订单详情。'
    } else {
      loadError.value = error.message || '请检查网络后重试。'
    }
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

watch(() => route.params.id, loadDetail, { immediate: true })
onBeforeUnmount(() => {
  requestVersion++
  compactScreen.removeEventListener('change', updateColumns)
})
</script>

<style scoped>
.order-detail-page { padding: 20px; max-width: 1280px; margin: 0 auto; }
.detail-header, .request-header, .review-summary, .review-labels { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.detail-header { justify-content: space-between; margin-bottom: 20px; }
h2 { margin: 14px 0 8px; font-size: 22px; color: #1f2937; }
h3 { margin: 0; font-size: 16px; }
h4 { margin-bottom: 12px; }
.muted { color: #64748b; font-size: 13px; font-weight: normal; }
.el-card { margin-bottom: 20px; border-radius: 8px; }
.text-content { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.75; }
.material-request + .material-request { border-top: 1px solid #e2e8f0; padding-top: 20px; margin-top: 24px; }
.request-header { margin-bottom: 14px; overflow-wrap: anywhere; }
.review-labels { margin-top: 12px; }
.el-timeline { padding-left: 8px; }
:deep(.el-descriptions__body) { overflow-wrap: anywhere; }
@media (max-width: 768px) {
  .order-detail-page { padding: 12px; }
  :deep(.el-descriptions__label) { width: 94px; white-space: nowrap; }
}
</style>
