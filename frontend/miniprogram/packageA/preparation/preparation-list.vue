<template>
	<view class="page">
		<view class="hero">
			<text class="hero-title">订单备货</text>
			<text class="hero-subtitle">随时查看师傅提交的耗材清单并完成备货</text>
		</view>

		<view class="search-card">
			<view class="search-box">
				<up-icon name="search" size="19" color="#94a3b8"></up-icon>
				<input v-model="keyword" confirm-type="search" placeholder="搜索订单、师傅、客户或耗材" @confirm="search" />
				<text v-if="keyword" class="clear" @click="clearKeyword">清除</text>
			</view>
			<scroll-view scroll-x class="status-scroll" :show-scrollbar="false">
				<view class="status-row">
					<view v-for="item in statuses" :key="item.value || 'all'" class="status-chip"
						:class="{ active: status === item.value }" @click="changeStatus(item.value)">{{ item.label }}</view>
				</view>
			</scroll-view>
		</view>

		<view v-if="loading" class="state-card"><up-loading-icon color="#0b63ce"></up-loading-icon><text>正在加载备货订单</text></view>
		<view v-else-if="error" class="state-card"><text class="state-title">{{ error }}</text><button class="outline-button" @click="loadList(true)">重新加载</button></view>
		<view v-else-if="!records.length" class="state-card"><up-icon name="order" size="48" color="#cbd5e1"></up-icon><text class="state-title">暂无备货订单</text><text>当前筛选条件下没有师傅提交的耗材申请</text></view>
		<view v-else class="record-list">
			<view v-for="record in records" :key="record.id" class="record-card" @click="openDetail(record)">
				<view class="card-head">
					<view class="title-wrap"><text class="order-title">{{ record.productName || '工程订单耗材' }}</text><text class="order-no">{{ record.orderNo }}</text></view>
					<text class="status" :class="statusClass(record.statusCode)">{{ record.statusLabel }}</text>
				</view>
				<view class="info-row"><text>安装师傅</text><text>{{ record.submitterName || '-' }}</text></view>
				<view class="info-row"><text>客户</text><text>{{ record.customerName || '-' }}</text></view>
				<view class="card-foot">
					<text>{{ record.itemCount || 0 }} 项耗材</text>
					<text>{{ formatDateTime(record.createTime) }}</text>
				</view>
				<button v-if="canPrepare(record)" class="primary-button" @click.stop="openDetail(record)">处理备货</button>
			</view>
			<view class="page-tip">{{ loadingMore ? '正在加载更多…' : (records.length >= total ? '没有更多了' : '上拉加载更多') }}</view>
		</view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onReachBottom, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { preparationApi } from '@/api/api.js'
import { getAuthUserInfo } from '@/utils/auth-session.js'
import { formatDateTime } from '@/utils/time.js'
import { PREPARATION_STATUSES, canPrepare, hasAdminRole } from '@/utils/admin-preparation.js'

const records = ref([])
const keyword = ref('')
const status = ref('')
const page = ref(1)
const total = ref(0)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const statuses = PREPARATION_STATUSES

function requireAdmin() {
	if (hasAdminRole(getAuthUserInfo())) return true
	uni.showToast({ title: '仅管理员可使用订单备货', icon: 'none' })
	setTimeout(() => uni.navigateBack(), 300)
	return false
}

async function loadList(reset = true) {
	if (!requireAdmin() || loadingMore.value) return
	if (reset) {
		page.value = 1
		loading.value = true
		error.value = ''
	} else {
		loadingMore.value = true
	}
	try {
		const res = await preparationApi.getList({
			page: page.value,
			pageSize: 10,
			source: 'W',
			keyword: keyword.value.trim(),
			status: status.value
		}, { loading: reset })
		if (res.code !== 200) {
			if (!reset) page.value = Math.max(1, page.value - 1)
			if (reset) error.value = res.msg || '备货订单加载失败'
			return
		}
		const rows = res.data?.list || []
		records.value = reset ? rows : [...records.value, ...rows]
		total.value = Number(res.data?.total || 0)
	} catch (e) {
		if (!reset) page.value = Math.max(1, page.value - 1)
		if (reset) error.value = '网络异常，请稍后重试'
		else uni.showToast({ title: '加载更多失败', icon: 'none' })
	} finally {
		loading.value = false
		loadingMore.value = false
		uni.stopPullDownRefresh()
	}
}

function search() { loadList(true) }
function clearKeyword() { keyword.value = ''; loadList(true) }
function changeStatus(value) { if (status.value !== value) { status.value = value; loadList(true) } }
function openDetail(record) { uni.navigateTo({ url: `/packageA/preparation/preparation-detail?id=${record.id}&source=W` }) }
function statusClass(value) { return `status-${String(value || '').toLowerCase()}` }
onShow(() => loadList(true))
onPullDownRefresh(() => loadList(true))
onReachBottom(() => {
	if (!loading.value && !loadingMore.value && records.value.length < total.value) {
		page.value += 1
		loadList(false)
	}
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:24rpx;box-sizing:border-box;background:#f4f7fb}.hero{padding:34rpx 30rpx;border-radius:24rpx;background:linear-gradient(135deg,#0b63ce,#2785e6);color:#fff}.hero-title{display:block;font-size:34rpx;font-weight:750}.hero-subtitle{display:block;margin-top:10rpx;font-size:23rpx;opacity:.88}.search-card{margin-top:20rpx;padding:22rpx 20rpx 12rpx;border-radius:22rpx;background:#fff}.search-box{height:76rpx;padding:0 22rpx;display:flex;align-items:center;gap:14rpx;border-radius:18rpx;background:#f4f7fb}.search-box input{flex:1;font-size:24rpx;color:#142434}.clear{color:#0b63ce;font-size:22rpx}.status-scroll{margin-top:18rpx;white-space:nowrap}.status-row{display:flex;gap:14rpx;padding-bottom:10rpx}.status-chip{padding:13rpx 24rpx;border-radius:28rpx;background:#f4f7fb;color:#64748b;font-size:23rpx}.status-chip.active{background:#e7f1ff;color:#0b63ce;font-weight:650}.state-card{min-height:500rpx;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18rpx;color:#8b9aaa;font-size:23rpx}.state-title{color:#334155;font-size:27rpx;font-weight:650}.outline-button{height:70rpx;margin:8rpx 0 0;padding:0 32rpx;border:2rpx solid #0b63ce;border-radius:18rpx;background:#fff;color:#0b63ce;font-size:24rpx}.outline-button::after,.primary-button::after{border:0}.record-list{display:flex;flex-direction:column;gap:18rpx;margin-top:20rpx}.record-card{padding:28rpx;border-radius:22rpx;background:#fff;box-shadow:0 4rpx 16rpx rgba(30,41,59,.05)}.card-head,.info-row,.card-foot{display:flex;align-items:center;justify-content:space-between;gap:20rpx}.title-wrap{min-width:0;flex:1}.order-title{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#142434;font-size:29rpx;font-weight:700}.order-no{display:block;margin-top:8rpx;color:#64748b;font-size:22rpx}.status{flex-shrink:0;padding:8rpx 16rpx;border-radius:18rpx;background:#edf2f7;color:#64748b;font-size:21rpx}.status-pending{background:#fff3df;color:#d97706}.status-preparing{background:#e7f1ff;color:#0b63ce}.status-done{background:#e8f8ef;color:#159455}.status-voided{background:#edf2f7;color:#64748b}.info-row{margin-top:18rpx;color:#94a3b8;font-size:23rpx}.info-row text:last-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#334155}.card-foot{margin-top:20rpx;padding-top:18rpx;border-top:1rpx solid #edf1f5;color:#94a3b8;font-size:21rpx}.primary-button{height:72rpx;margin:22rpx 0 0;border-radius:18rpx;background:linear-gradient(135deg,#1479e8,#0b63ce);color:#fff;font-size:24rpx;font-weight:650}.page-tip{text-align:center;padding:18rpx;color:#94a3b8;font-size:22rpx}
</style>
