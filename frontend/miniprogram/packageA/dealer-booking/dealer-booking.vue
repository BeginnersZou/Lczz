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
      <button class="primary" @click="goOrders">查看我的预约</button>
      <button class="secondary" @click="goHome">返回首页</button>
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
      <view class="card sales-card">
        <view class="sales-heading"><text class="section-title">销售人员</text><text class="optional-tag">选填</text></view>
        <button class="sales-trigger" :class="{ 'has-selection': form.salesUserId }" :disabled="locked" @click="openSalesPicker">
          <view class="sales-trigger-icon"><up-icon name="account" size="21" color="#0b63ce" /></view>
          <view class="sales-trigger-copy">
            <text :class="['sales-trigger-name', { placeholder: !form.salesUserId }]">{{ selectedSalespersonName || '请选择销售人员' }}</text>
            <text v-if="form.salesUserId" class="sales-trigger-detail">{{ selectedSalespersonPhone || '已指定此订单的销售人员' }}</text>
          </view>
          <text v-if="form.salesUserId" class="sales-change">更换</text><up-icon name="arrow-right" size="14" color="#94a3b8" />
        </button>
        <text class="sales-help">可不选择；指定后，该销售人员可查看此订单</text>
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
    <up-popup :show="salesPickerVisible" mode="bottom" :round="24" @close="closeSalesPicker">
      <view class="sales-sheet">
        <view class="sheet-handle"></view>
        <view class="sheet-header">
          <view><text class="sheet-title">选择销售人员</text><text class="sheet-subtitle">选择一位销售人员，或保持不指定</text></view>
          <button class="sheet-close" aria-label="关闭销售人员选择" @click="closeSalesPicker"><up-icon name="close" size="18" color="#64748b" /></button>
        </view>
        <view class="sheet-search">
          <up-icon name="search" size="20" color="#94a3b8" />
          <input v-model="salesKeyword" class="sheet-search-input" placeholder="搜索姓名或手机号" :maxlength="100" confirm-type="search" @input="scheduleSalesSearch" @confirm="loadSalespersons" />
          <button v-if="salesKeyword" class="search-clear" aria-label="清空搜索" @click="clearSalesSearch"><up-icon name="close-circle-fill" size="17" color="#94a3b8" /></button>
          <button class="search-action" @click="loadSalespersons">搜索</button>
        </view>
        <scroll-view class="sales-options" scroll-y :lower-threshold="80" @scrolltolower="loadMoreSalespersons">
          <button class="sales-option no-assignment" :class="{ selected: !form.salesUserId }" @click="clearSalesperson">
            <view class="option-avatar neutral"><up-icon name="minus" size="20" color="#64748b" /></view>
            <view class="option-copy"><text class="option-name">不指定销售人员</text><text class="option-detail">不影响预约提交，后续可由管理员指派</text></view>
            <view :class="['option-radio', { checked: !form.salesUserId }]"><up-icon v-if="!form.salesUserId" name="checkmark" size="12" color="#fff" /></view>
          </button>
          <text class="options-caption">{{ activeSalesKeyword ? '搜索结果' : '平台销售人员' }}</text>
          <button v-for="person in salespersons" :key="person.id" class="sales-option" :class="{ selected: form.salesUserId === person.id }" @click="selectSalesperson(person)">
            <view class="option-avatar">{{ person.name.slice(0, 1) }}</view>
            <view class="option-copy"><text class="option-name">{{ person.name }}</text><text class="option-detail">{{ person.maskedPhone || '平台销售人员' }}</text></view>
            <view :class="['option-radio', { checked: form.salesUserId === person.id }]"><up-icon v-if="form.salesUserId === person.id" name="checkmark" size="12" color="#fff" /></view>
          </button>
          <view v-if="salespersonError" class="sales-list-state">
            <up-icon name="info-circle" size="30" color="#94a3b8" />
            <text class="list-state-title">销售人员暂不可用</text><text class="list-state-description">{{ salespersonError }}</text>
            <button class="list-retry" :disabled="salespersonLoading" @click="retrySalespersons">重新加载</button>
          </view>
          <view v-else-if="salespersonLoading" class="sales-list-state compact"><up-loading-icon size="20" color="#0b63ce" /><text class="list-state-description">正在加载销售人员…</text></view>
          <view v-else-if="!salespersons.length" class="sales-list-state">
            <up-icon name="account" size="32" color="#b8c5d5" />
            <text class="list-state-title">{{ activeSalesKeyword ? '未找到匹配的销售人员' : '暂无可选销售人员' }}</text>
            <text class="list-state-description">{{ activeSalesKeyword ? '试试其他姓名或手机号，也可暂不指定' : '可选择“不指定销售人员”继续预约' }}</text>
          </view>
          <button v-else-if="salespersons.length < salespersonTotal" class="load-more-sales" @click="loadMoreSalespersons">加载更多销售人员</button>
          <text v-else class="sales-list-end">已显示全部 {{ salespersons.length }} 位销售人员</text>
        </scroll-view>
      </view>
    </up-popup>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { authApi, dealerBookingApi, salespersonApi, uploadApi } from '@/api/api.js'
import { getAuthToken } from '@/utils/auth-session.js'
import { BOOKING_TASKS, BOOKING_DESCRIPTION, isDealer, bookingPayload, validateBooking } from '@/utils/dealer-booking.js'

const form = reactive({ taskType: '', description: '', customerName: '', customerPhone: '', addressArea: [], addressDetail: '', salesUserId: null })
const role = ref('')
const salespersons = ref([])
const salesKeyword = ref('')
const activeSalesKeyword = ref('')
const salespersonLoading = ref(false)
const salespersonError = ref('')
const selectedSalespersonName = ref('')
const selectedSalespersonPhone = ref('')
const salesPickerVisible = ref(false)
const salespersonPage = ref(0)
const salespersonTotal = ref(0)
let salespersonRequestVersion = 0
let salesSearchTimer = null
const toSalespersonOption = person => ({ ...person, id: Number(person.id), name: String(person.name || '未命名销售人员'), displayName: person.maskedPhone ? `${person.name}（${person.maskedPhone}）` : person.name })
function selectSalesperson(person) {
  if (locked.value) return
  form.salesUserId = person?.id || null
  selectedSalespersonName.value = person?.name || ''
  selectedSalespersonPhone.value = person?.maskedPhone || ''
  closeSalesPicker()
}
function clearSalesperson() { selectSalesperson(null) }
function openSalesPicker() {
  if (locked.value) return
  salesPickerVisible.value = true
  salesKeyword.value = ''
  return loadSalespersons()
}
function closeSalesPicker() {
  salesPickerVisible.value = false
  clearTimeout(salesSearchTimer)
  salespersonRequestVersion++
  salespersonLoading.value = false
}
function scheduleSalesSearch() {
  clearTimeout(salesSearchTimer)
  // Invalidate the previous query immediately, including during the debounce window.
  salespersonRequestVersion++
  salespersonLoading.value = true
  salespersonError.value = ''
  salespersons.value = []
  salespersonPage.value = 0
  salespersonTotal.value = 0
  salesSearchTimer = setTimeout(loadSalespersons, 300)
}
function clearSalesSearch() { salesKeyword.value = ''; return loadSalespersons() }
function salesErrorMessage(response) {
  const code = Number(response?.httpStatus || response?.code)
  if (code === 404) return '销售人员服务暂未开放，可暂不指定，或稍后重试。'
  if (code === 401) return '登录状态已失效，请重新登录后选择。'
  if (code === 403) return '当前账号暂无选择权限，请联系管理员。'
  return '暂时无法加载，请检查网络后重试；也可不指定销售人员。'
}
async function loadSalespersons() {
  clearTimeout(salesSearchTimer)
  const version = ++salespersonRequestVersion
  const keyword = salesKeyword.value.trim()
  salespersonLoading.value = true
  salespersonError.value = ''
  activeSalesKeyword.value = keyword
  salespersons.value = []
  salespersonPage.value = 0
  salespersonTotal.value = 0
  try {
    const response = await salespersonApi.options({ page: 1, pageSize: 100, keyword })
    if (version !== salespersonRequestVersion || disposed) return
    if (response.code !== 200 || !Array.isArray(response.data?.list)) { salespersonError.value = salesErrorMessage(response); return }
    salespersons.value = [...new Map(response.data.list.map(toSalespersonOption).map(person => [person.id, person])).values()]
    salespersonPage.value = 1
    salespersonTotal.value = Number(response.data.total) || salespersons.value.length
  } catch (err) { if (version === salespersonRequestVersion && !disposed) salespersonError.value = salesErrorMessage(err) }
  finally { if (version === salespersonRequestVersion) salespersonLoading.value = false }
}
async function loadMoreSalespersons() {
  if (salespersonLoading.value || salespersonError.value || salespersons.value.length >= salespersonTotal.value) return
  return fetchMoreSalespersons()
}
function retrySalespersons() { return salespersonPage.value ? fetchMoreSalespersons() : loadSalespersons() }
async function fetchMoreSalespersons() {
  if (salespersonLoading.value) return
  const version = salespersonRequestVersion
  salespersonLoading.value = true
  salespersonError.value = ''
  try {
    const nextPage = salespersonPage.value + 1
    const response = await salespersonApi.options({ page: nextPage, pageSize: 100, keyword: activeSalesKeyword.value })
    if (version !== salespersonRequestVersion || disposed) return
    if (response.code !== 200 || !Array.isArray(response.data?.list)) { salespersonError.value = salesErrorMessage(response); return }
    salespersons.value = [...new Map([...salespersons.value, ...response.data.list.map(toSalespersonOption)].map(person => [person.id, person])).values()]
    salespersonPage.value = nextPage
    salespersonTotal.value = Number(response.data?.total || 0)
  } catch (err) { if (version === salespersonRequestVersion && !disposed) salespersonError.value = salesErrorMessage(err) }
  finally { if (version === salespersonRequestVersion) salespersonLoading.value = false }
}
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
    if (!getAuthToken()) { allowed.value = false; accessError.value = '请先登录经销商或销售员账号'; return }
    const res = await authApi.getUserInfo({ redirectOnUnauthorized: false })
    if (res.code !== 200) { allowed.value = false; accessError.value = res.msg || '账号权限检查失败'; return }
    allowed.value = isDealer(res.data)
    role.value = String(res.data?.role || '').toLowerCase()
    if (!allowed.value) accessError.value = '预约安装仅对经销商或销售员开放'
  } catch { allowed.value = false; accessError.value = '网络异常，请重新检查账号权限' }
  finally { checking.value = false }
}
onShow(checkAccess)
onUnload(() => { disposed = true; closeSalesPicker() })

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
    const res = await dealerBookingApi.create(pendingPayload, role.value)
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
function goOrders() { uni.switchTab({ url: '/pages/order/order' }) }
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
.sales-heading { display:flex; align-items:center; gap:14rpx; margin-bottom:24rpx; }
.optional-tag { padding:3rpx 12rpx; border-radius:8rpx; background:#f1f5f9; color:#7a8ba0; font-size:21rpx; line-height:1.5; }
.sales-trigger { display:flex; align-items:center; gap:18rpx; width:100%; min-height:100rpx; margin:0; padding:22rpx; border:1rpx solid #e3eaf2; border-radius:16rpx; background:#f8fafc; text-align:left; line-height:1.5; }
.sales-trigger.has-selection { background:#f5f9ff; border-color:#d5e5f8; }.sales-trigger[disabled] { opacity:.6; }
.sales-trigger-icon { display:flex; align-items:center; justify-content:center; width:62rpx; height:62rpx; flex-shrink:0; border-radius:16rpx; background:#e8f1fd; }
.sales-trigger-copy { flex:1; min-width:0; }.sales-trigger-name { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#172b42; font-size:28rpx; font-weight:600; }.sales-trigger-name.placeholder { color:#8493a6; font-weight:400; }
.sales-trigger-detail { display:block; margin-top:4rpx; color:#7a8ba0; font-size:23rpx; }.sales-change { flex-shrink:0; color:#0b63ce; font-size:24rpx; }.sales-help { display:block; margin-top:16rpx; color:#8291a5; font-size:23rpx; line-height:1.6; }
.sales-sheet { padding:14rpx 28rpx 20rpx; color:#172b42; }.sheet-handle { width:64rpx; height:7rpx; margin:0 auto 28rpx; border-radius:8rpx; background:#dbe3ed; }
.sheet-header { display:flex; align-items:flex-start; justify-content:space-between; gap:20rpx; margin:0 8rpx 28rpx; }.sheet-title { display:block; font-size:34rpx; font-weight:700; line-height:1.4; }.sheet-subtitle { display:block; margin-top:8rpx; font-size:23rpx; color:#8493a6; line-height:1.6; }
.sheet-close { display:flex; align-items:center; justify-content:center; flex-shrink:0; width:64rpx; height:64rpx; padding:0; margin:0; border-radius:50%; background:#f1f5f9; }
.sheet-search { display:flex; align-items:center; gap:14rpx; padding:0 18rpx; height:88rpx; background:#f4f7fb; border:1rpx solid #e7edf5; border-radius:16rpx; }.sheet-search-input { flex:1; min-width:0; font-size:26rpx; }.search-clear { display:flex; align-items:center; justify-content:center; width:48rpx; height:72rpx; padding:0; margin:0; background:transparent; }.search-action { flex-shrink:0; padding:0 6rpx 0 20rpx; margin:0; border-left:1rpx solid #dbe3ed; border-radius:0; background:transparent; color:#0b63ce; font-size:25rpx; line-height:48rpx; }
.sales-options { height:52vh; margin-top:22rpx; }.sales-option { box-sizing:border-box; display:flex; align-items:center; gap:20rpx; width:100%; min-height:120rpx; padding:22rpx 18rpx; margin:0 0 10rpx; border:1rpx solid transparent; border-radius:16rpx; background:#fff; text-align:left; line-height:1.5; }.sales-option.selected { background:#f0f6ff; border-color:#dceaff; }.sales-option:active { background:#f4f7fb; }.no-assignment { margin-bottom:26rpx; }
.option-avatar { flex-shrink:0; display:flex; align-items:center; justify-content:center; width:68rpx; height:68rpx; border-radius:18rpx; background:#e8f1fd; color:#0b63ce; font-size:28rpx; font-weight:650; }.option-avatar.neutral { background:#f1f5f9; }.option-copy { flex:1; min-width:0; }.option-name { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:27rpx; font-weight:600; }.option-detail { display:block; margin-top:5rpx; color:#8493a6; font-size:22rpx; }.option-radio { display:flex; align-items:center; justify-content:center; width:34rpx; height:34rpx; flex-shrink:0; border:2rpx solid #d6dfe9; border-radius:50%; }.option-radio.checked { background:#0b63ce; border-color:#0b63ce; }
.options-caption { display:block; margin:0 18rpx 10rpx; color:#94a3b8; font-size:22rpx; }.sales-list-state { display:flex; flex-direction:column; align-items:center; gap:14rpx; padding:52rpx 28rpx; text-align:center; }.sales-list-state.compact { padding:32rpx; }.list-state-title { color:#4d6078; font-size:27rpx; font-weight:600; }.list-state-description { color:#8998aa; font-size:23rpx; line-height:1.65; }.list-retry { margin:10rpx 0 0; padding:0 34rpx; border:1rpx solid #d8e5f5; border-radius:12rpx; color:#0b63ce; background:#fff; font-size:24rpx; line-height:68rpx; }.load-more-sales { margin:10rpx 0; background:transparent; color:#0b63ce; font-size:24rpx; line-height:76rpx; }.sales-list-end { display:block; padding:24rpx 0 32rpx; color:#a2afbf; text-align:center; font-size:21rpx; }
button::after { border:0; }.primary,.secondary { width:100%; margin:20rpx 0 0; min-height:88rpx; line-height:88rpx; border-radius:16rpx; font-size:28rpx; }.primary { background:#0b63ce; color:#fff; }.secondary { background:#fff; color:#45617f; border:1rpx solid #d7e1ed; }.primary[disabled] { background:#aac7e9; color:#fff; }.actions .hint { margin-top:20rpx; text-align:center; }.error,.notice { display:block; font-size:25rpx; line-height:1.6; color:#a54a12; }.notice { padding:24rpx; margin-bottom:24rpx; background:#fff5e6; border-radius:16rpx; }.state { text-align:center; padding:60rpx 30rpx; }.state .title,.state .hint { margin:22rpx 0; }
</style>
