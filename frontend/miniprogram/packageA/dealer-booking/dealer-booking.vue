<template>
  <view class="booking-page">
    <view v-if="checking || accessError" class="card state">
      <text class="title">{{ checking ? '正在确认账号权限' : accessError }}</text>
      <template v-if="!checking">
        <button class="secondary" @click="checkAccess">重新检查</button>
        <button class="secondary" @click="goHome">返回首页</button>
      </template>
    </view>
    <view v-else-if="result" class="card state">
      <up-icon name="checkmark-circle-fill" color="#16a370" size="52" />
      <text class="title">预约申请已提交</text>
      <text class="hint">订单编号：{{ result.orderNo }}</text>
      <text class="hint">管理员将核对需求、安排上门时间并指派师傅。</text>
      <button class="primary" @click="goHome">返回首页</button>
    </view>
    <template v-else-if="allowed">
      <view class="intro"><text class="title">预约安装</text><text class="hint">填写客户与安装需求，由管理员统一安排施工</text></view>
      <view v-if="locked" class="notice">正在核实上次提交结果，表单已保留。请点击“核实提交结果”，不要重复新建申请。</view>
      <view class="card">
        <text class="section-title">安装需求</text>
        <text class="label">任务类型 <text class="required">*</text></text>
        <picker :range="BOOKING_TASKS" :disabled="locked" @change="form.taskType = BOOKING_TASKS[Number($event.detail.value)]">
          <view class="field picker">{{ form.taskType || '请选择任务类型' }}<text>›</text></view>
        </picker>
        <text class="label">需求描述 <text class="required">*</text></text>
        <textarea v-model="form.description" class="field textarea" :disabled="locked" :maxlength="1000" :placeholder="BOOKING_DESCRIPTION" />
        <text class="counter">{{ form.description.length }}/1000</text>
      </view>
      <view class="card">
        <text class="section-title">客户与上门地址</text>
        <text class="label">客户姓名 <text class="required">*</text></text>
        <input v-model="form.customerName" class="field" :disabled="locked" :maxlength="64" placeholder="请输入客户姓名" />
        <text class="label">客户手机号 <text class="required">*</text></text>
        <input v-model="form.customerPhone" class="field" :disabled="locked" type="number" :maxlength="11" placeholder="请输入11位客户手机号" />
        <text class="label">订单地址 <text class="required">*</text></text>
        <picker mode="region" :value="form.addressArea" :disabled="locked" @change="form.addressArea = $event.detail.value">
          <view class="field picker">{{ form.addressArea.length ? form.addressArea.join(' / ') : '请选择省 / 市 / 区' }}<text>›</text></view>
        </picker>
        <textarea v-model="form.addressDetail" class="field address" :disabled="locked" :maxlength="500" placeholder="请输入街道、楼栋、门牌等详细地址" />
        <text class="hint privacy">客户信息仅用于此次服务联系与订单处理，请确保已获得客户提供信息的授权。</text>
      </view>
      <view class="card">
        <view class="section-row"><text class="section-title">现场附件</text><text class="hint">选填 · {{ files.length }}/9 张</text></view>
        <text class="hint">图片每张不超过10MB，可上传设备、安装位置等照片</text>
        <view class="photos">
          <view v-for="file in files" :key="file.key" class="photo">
            <image :src="file.path" mode="aspectFill" @click="preview(file)" />
            <text v-if="file.status === 'uploading'" class="upload-state">上传中 {{ file.progress }}%</text>
            <button v-if="file.status === 'failed'" class="retry" :disabled="locked || choosing" @click="upload(file)">重试上传</button>
            <button class="remove" :disabled="locked || file.status === 'uploading'" @click="remove(file)">移除</button>
          </view>
          <button v-if="files.length < 9" class="add-photo" :disabled="locked || choosing || uploading" @click="chooseImages"><text class="plus">＋</text><text>添加图片</text></button>
        </view>
      </view>
      <view class="card actions">
        <text v-if="error" class="error">{{ error }}</text>
        <button class="primary" :loading="submitting" :disabled="submitting || uploading || choosing" @click="submit">{{ locked && !submitting ? '核实提交结果' : '提交预约申请' }}</button>
        <button class="secondary" :disabled="submitting || uploading || choosing" @click="cancel">暂不提交，返回首页</button>
        <text class="hint">提交后由管理员安排上门时间，无需在线支付</text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { authApi, dealerBookingApi, uploadApi } from '@/api/api.js'
import { getAuthToken } from '@/utils/auth-session.js'
import { BOOKING_TASKS, BOOKING_DESCRIPTION, isDealer, bookingPayload, validateBooking } from '@/utils/dealer-booking.js'

const form = reactive({ taskType: '', description: '', customerName: '', customerPhone: '', addressArea: [], addressDetail: '' })
const checking = ref(true)
const accessError = ref('')
const allowed = ref(false)
const files = ref([])
const choosing = ref(false)
const submitting = ref(false)
const locked = ref(false)
const result = ref(null)
const error = ref('')
const uploading = computed(() => files.value.some(file => file.status === 'uploading'))
let pendingPayload = null
let disposed = false
let attachmentSequence = 0

async function checkAccess() {
  checking.value = true
  accessError.value = ''
  try {
    if (!getAuthToken()) { allowed.value = false; accessError.value = '请先使用经销商账号登录'; return }
    const res = await authApi.getUserInfo({ redirectOnUnauthorized: false })
    if (res.code !== 200) { allowed.value = false; accessError.value = res.msg || '账号权限检查失败'; return }
    allowed.value = isDealer(res.data)
    if (!allowed.value) accessError.value = '预约安装仅对经销商开放'
  } catch { allowed.value = false; accessError.value = '网络异常，请重新检查账号权限' }
  finally { checking.value = false }
}
onShow(checkAccess)
onUnload(() => { disposed = true })

async function upload(file) {
  if (locked.value || file.status === 'uploading' || file.status === 'done' || !allowed.value || !files.value.some(item => item.key === file.key)) return
  file.status = 'uploading'
  file.progress = 0
  try {
    const res = await uploadApi.uploadImage(file.path, {}, {
      showError: false,
      onProgress: event => { file.progress = Math.min(99, Number(event.progress) || 0) }
    })
    if (res.code !== 200 || !Number.isSafeInteger(Number(res.data?.id)) || Number(res.data.id) < 1) throw new Error(res.msg || '上传失败')
    file.id = Number(res.data.id)
    file.status = 'done'
    file.progress = 100
  } catch { file.status = 'failed' }
}

function chooseImages() {
  if (choosing.value || uploading.value || locked.value || !allowed.value) return
  choosing.value = true
  uni.chooseImage({
    count: 9 - files.value.length,
    sizeType: ['compressed'],
    success: async selection => {
      if (disposed) return
      const selected = (selection.tempFiles || []).slice(0, 9 - files.value.length)
      let invalid = false
      const added = []
      for (const item of selected) {
        if (!item.path || !Number.isFinite(item.size) || item.size <= 0 || item.size > 10 * 1024 * 1024) { invalid = true; continue }
        const file = reactive({ key: ++attachmentSequence, path: item.path, status: 'waiting', progress: 0, id: null })
        files.value.push(file)
        added.push(file)
      }
      if (invalid) uni.showToast({ title: '已跳过超10MB或无效图片', icon: 'none' })
      for (const file of added) { if (disposed) break; await upload(file) }
    },
    complete: () => { choosing.value = false }
  })
}

function preview(file) { uni.previewImage({ current: file.path, urls: files.value.map(item => item.path) }) }
async function remove(file) {
  if (locked.value || file.status === 'uploading') return
  // Delete only unbound files explicitly removed by the user.
  if (file.id) {
    try { const res = await uploadApi.deleteTemporary(file.id); if (res.code !== 200) return }
    catch { error.value = '附件移除失败，请重试'; return }
  }
  files.value = files.value.filter(item => item.key !== file.key)
}

async function submit() {
  if (!allowed.value || checking.value || submitting.value || uploading.value || choosing.value || result.value) return
  error.value = validateBooking(form, files.value)
  if (error.value) return
  if (!pendingPayload) pendingPayload = bookingPayload(form, files.value, `${Date.now()}-${Math.random().toString(36).slice(2, 14)}`)
  submitting.value = true
  locked.value = true
  try {
    const res = await dealerBookingApi.create(pendingPayload)
    if (res.code === 200 && res.data?.id && res.data?.orderNo) {
      result.value = res.data
      pendingPayload = null
      return
    }
    if ([400, 401, 403, 404, 422].includes(res.code)) {
      locked.value = false
      pendingPayload = null
    }
    if ([401, 403].includes(res.code)) { allowed.value = false; accessError.value = res.msg || '账号暂无预约权限' }
    error.value = res.code === 404 ? '预约服务暂未开放，请稍后再试' : (res.msg || '未能确认提交结果，请重试核实')
  } catch { error.value = '网络异常，未能确认提交结果，请重试核实' }
  finally { submitting.value = false }
}

function goHome() { uni.switchTab({ url: '/pages/index/index' }) }
function cancel() {
  if (submitting.value || uploading.value) return
  uni.showModal({
    title: locked.value ? '提交结果尚未确认' : '放弃本次填写？',
    content: locked.value ? '申请可能已被受理，建议先核实提交结果。返回后请联系管理员确认，避免重复预约。' : '返回后不会保留本次填写的客户信息。',
    confirmText: '返回首页', cancelText: '继续填写',
    success: answer => { if (answer.confirm) goHome() }
  })
}
</script>

<style scoped>
.booking-page { min-height:100vh; box-sizing:border-box; padding:28rpx 24rpx calc(40rpx + env(safe-area-inset-bottom)); background:#f4f7fb; color:#172b42; }
.intro { padding:8rpx 8rpx 28rpx; }.title { display:block; font-size:36rpx; font-weight:700; }.hint { display:block; font-size:24rpx; line-height:1.65; color:#738398; }.intro .hint { margin-top:10rpx; }
.card { padding:30rpx; margin-bottom:24rpx; background:#fff; border-radius:24rpx; }.section-title { font-size:30rpx; font-weight:650; }.section-row { display:flex; justify-content:space-between; align-items:center; margin-bottom:12rpx; }.label { display:block; font-size:27rpx; margin:28rpx 0 14rpx; }.required { color:#df4747; }
.field { box-sizing:border-box; width:100%; min-height:88rpx; border:1rpx solid #e3eaf2; border-radius:14rpx; padding:20rpx; font-size:27rpx; background:#f8fafc; }.picker { display:flex; justify-content:space-between; align-items:center; }.textarea { height:230rpx; line-height:1.6; }.address { margin-top:16rpx; height:150rpx; }.counter { display:block; text-align:right; font-size:22rpx; color:#8897aa; margin-top:8rpx; }.privacy { margin-top:18rpx; }
.photos { display:flex; flex-wrap:wrap; gap:18rpx; margin-top:24rpx; }.photo,.add-photo { position:relative; width:190rpx; height:230rpx; margin:0; overflow:hidden; border-radius:14rpx; }.photo image { width:190rpx; height:180rpx; }.remove { margin:0; height:50rpx; padding:0; line-height:50rpx; font-size:23rpx; color:#b42332; background:#fff1f2; }.upload-state,.retry { position:absolute; top:64rpx; left:0; width:100%; background:rgba(0,0,0,.65); color:#fff; text-align:center; font-size:23rpx; line-height:60rpx; padding:0; }.add-photo { height:180rpx; border:1rpx dashed #b9cce0; display:flex; flex-direction:column; align-items:center; justify-content:center; background:#f6faff; color:#0b63ce; font-size:24rpx; }.plus { font-size:48rpx; line-height:1.3; }
button::after { border:0; }.primary,.secondary { width:100%; margin:20rpx 0 0; min-height:88rpx; line-height:88rpx; border-radius:16rpx; font-size:28rpx; }.primary { background:#0b63ce; color:#fff; }.secondary { background:#fff; color:#45617f; border:1rpx solid #d7e1ed; }.primary[disabled] { background:#aac7e9; color:#fff; }.actions .hint { margin-top:20rpx; text-align:center; }.error,.notice { display:block; font-size:25rpx; line-height:1.6; color:#a54a12; }.notice { padding:24rpx; margin-bottom:24rpx; background:#fff5e6; border-radius:16rpx; }.state { text-align:center; padding:60rpx 30rpx; }.state .title,.state .hint { margin:22rpx 0; }
</style>
