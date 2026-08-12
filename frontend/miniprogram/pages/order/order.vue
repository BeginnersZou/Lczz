<template>
	<view class="page">
		<view class="order-banner">
			<view class="banner-icon"><up-icon name="order" size="28" color="#ffffff"></up-icon></view>
			<view class="banner-copy">
				<text class="banner-title">服务进度，随时掌握</text>
				<text class="banner-desc">上门时间、处理状态与完工记录清晰可查</text>
			</view>
			<view class="live-dot"><view class="dot"></view><text>实时更新</text></view>
		</view>
		<!-- 标签切换 -->
		<view class="tabs-wrap">
		<view class="tabs-row">
			<view class="tab-item" v-for="(tab, index) in tabs" :key="index" :class="{ active: currentTab === index }"
				@click="switchTab(index)">
				<text>{{ tab.name }}</text>
				<view class="tab-line" v-if="currentTab === index"></view>
			</view>
		</view>
		</view>


		<!-- ═══ 订单列表 ═══ -->
		<view class="order-list">
			<!-- 骨架屏 -->
			<template v-if="listLoading && allOrders.length === 0">
				<view class="order-card skeleton-card" v-for="i in 4" :key="'sk' + i">
					<view class="skeleton-img"></view>
					<view class="skeleton-body">
						<view class="skeleton-line long"></view>
						<view class="skeleton-line medium"></view>
						<view class="skeleton-line short"></view>
						<view class="skeleton-line short"></view>
					</view>
				</view>
			</template>

			<!-- 订单卡片 -->
			<template v-else>
				<view class="order-card" hover-class="hover-card" :hover-stay-time="80" v-for="(order, index) in displayOrders" :key="order.id || index"
					@click="goDetail(order)">
					<!-- 订单头部 -->
					<view class="order-top">
						<view class="order-service">
							<view class="order-img">
								<up-icon :name="order.status === '已完成' ? 'checkmark-circle-fill' : 'home-fill'" size="31" color="#0b63ce"></up-icon>
							</view>
							<view class="order-info">
								<text class="order-name">{{ order.serviceName }}</text>
								<text class="order-product">{{ order.productName }}</text>
								<text class="order-spec">{{ order.productSpec }}</text>
								<text class="order-qty">× {{ order.quantity }}</text>
							</view>
						</view>
						<view class="order-status" :class="statusClass(order.status)">
							<text>{{ order.status }}</text>
						</view>
					</view>

					<view class="info-divider"></view>

					<!-- 订单详情信息 -->
					<view class="info-row">
						<text class="info-label">订单编号</text>
						<text class="info-value">{{ order.orderNo }}</text>
						<view class="copy-btn" @click.stop="copyOrderNo(order.orderNo)">
							<up-icon name="file-text" size="20" color="#0b63ce"></up-icon>
						</view>
					</view>
					<view class="info-row">
						<text class="info-label">收货人</text>
						<text class="info-value">{{ order.name }} {{ order.phone }}</text>
					</view>
					<view class="info-row">
						<text class="info-label">地址</text>
						<text class="info-value">{{ order.address }}</text>
					</view>
					<view class="info-row">
				<text class="info-label">上门时间</text>
				<text class="info-value highlight">{{ order.visitTime }}</text>
			</view>

			<!-- 普通用户/经销商：已完成订单显示评价入口 -->
			<view class="action-row" v-if="canEvaluate && order.status === '已完成'" @click.stop>
				<view class="evaluate-btn" hover-class="hover-press" :hover-stay-time="80"
					@click="handleEvaluate(order)">
					<text>{{ evaluatedOrderIds.includes(String(order.id)) ? '查看评价' : '评价' }}</text>
				</view>
			</view>
		</view>

				<!-- 空状态 -->
				<view class="empty-state" v-if="displayOrders.length === 0 && !listLoading">
					<view class="empty-icon"><up-icon name="empty-order" size="42" color="#7e91a4"></up-icon></view>
					<text class="empty-title">暂无相关订单</text>
					<text class="empty-text">预约服务后，可在这里查看处理进度</text>
					<view class="empty-action" @click="goHome">去首页看看</view>
				</view>

				<!-- 触底加载状态 -->
				<view class="load-status" v-if="displayOrders.length > 0">
					<view class="loading-more" v-if="loadStatus === 'loading'">
						<view class="loading-dot"></view>
						<view class="loading-dot"></view>
						<view class="loading-dot"></view>
						<text class="load-text">加载中...</text>
					</view>
					<view class="load-end" v-else-if="loadStatus === 'noMore'">
						<view class="end-line"></view>
						<text class="load-text">已经到底啦</text>
						<view class="end-line"></view>
					</view>
				</view>
			</template>
		</view>


	</view>
</template>

<script setup>
import {
	ref,
	computed
} from 'vue'
import {
	onShow,
	onReachBottom,
	onPullDownRefresh,
	onShareAppMessage
} from '@dcloudio/uni-app'
import { orderApi, evaluationApi, authApi } from '@/api/api.js'
import { getAuthToken } from '@/utils/auth-session.js'

// onShow 确保从详情页返回时刷新列表（完工提交后状态会变化）
// 同时处理从"我的"页统计项点击跳转时切换到对应 tab
onShow(() => {
	if (!getAuthToken()) {
		uni.reLaunch({ url: '/pages/login/login' })
		return
	}
	if (uni.$pendingOrderTab != null) {
		currentTab.value = uni.$pendingOrderTab
		uni.$pendingOrderTab = null
	}
	loadUserRole()
	fetchOrders(true)
})

// 分享给好友
onShareAppMessage(() => ({
	title: '鑫立创 — 我的订单',
	path: '/pages/index/index'
}))

// 标签（type 直接存中文，与订单 status 字段一致，mock/真实接口均按此过滤）
const tabs = [{
	name: '全部',
	type: 'all'
},
{
	name: '待上门',
	type: '待上门'
},
{
	name: '处理中',
	type: '处理中'
},
{
	name: '已完成',
	type: '已完成'
}
]
const currentTab = ref(0)

const allOrders = ref([])
const page = ref(1)
const pageSize = 6
const total = ref(0)
const listLoading = ref(false)
const loadStatus = ref('')
const userRole = ref('')
const canEvaluate = computed(() => ['customer', 'dealer'].includes(userRole.value))
const evaluatedOrderIds = ref([])

// 当前登录用户角色
const loadUserRole = async () => {
	try {
		const res = await authApi.getUserInfo()
		if (res.code === 200 && res.data) {
			userRole.value = res.data.role || ''
		}
	} catch (err) {
		// 忽略错误，按默认处理
	}
}

// 分类过滤由接口按 status 完成，直接展示已加载列表
const displayOrders = computed(() => allOrders.value)

// 切换标签：重置分页并重新请求
const switchTab = (index) => {
	if (currentTab.value === index) return
	currentTab.value = index
	fetchOrders(true)
}

// 获取订单列表
const fetchOrders = async (isRefresh = false) => {
	if (listLoading.value) return
	if (isRefresh) {
		page.value = 1
		allOrders.value = []
		loadStatus.value = ''
	} else if (total.value > 0 && allOrders.value.length >= total.value) {
		loadStatus.value = 'noMore'
		return
	}
	listLoading.value = true
	loadStatus.value = 'loading'
	try {
			const tab = tabs[currentTab.value]
			const params = { page: page.value, pageSize }
			if (tab.type !== 'all') params.status = tab.type
			const idsPromise = isRefresh
				? evaluationApi.getEvaluatedOrderIds()
				: Promise.resolve({ data: evaluatedOrderIds.value })
			const [orderRes, idsRes] = await Promise.all([
				orderApi.getList(params),
				idsPromise
			])
			if (orderRes.code !== 200) {
				loadStatus.value = ''
				return
			}
			const list = (orderRes.data && orderRes.data.list) || []
			total.value = (orderRes.data && orderRes.data.total) || 0
			allOrders.value = isRefresh ? list : [...allOrders.value, ...list]
			page.value++
			loadStatus.value = allOrders.value.length >= total.value ? 'noMore' : ''
			evaluatedOrderIds.value = (idsRes.data || []).map(String)
		} catch (err) {
		// request.js 已统一处理错误提示
		loadStatus.value = ''
	} finally {
		listLoading.value = false
	}
}

onReachBottom(() => {
	if (loadStatus.value === 'noMore' || listLoading.value) return
	fetchOrders()
})

onPullDownRefresh(async () => {
	await fetchOrders(true)
	uni.stopPullDownRefresh()
	uni.showToast({ title: '刷新成功', icon: 'none', duration: 1000 })
})

// 状态样式
const statusClass = (status) => {
	if (status === '已完成') return 'status-done'
	if (status === '待上门') return 'status-waiting'
	if (status === '处理中') return 'status-processing'
	return ''
}

// 复制订单编号
const copyOrderNo = (no) => {
	uni.setClipboardData({
		data: no,
		success: () => {
			uni.showToast({
				title: '复制成功',
				icon: 'success'
			})
		}
	})
}

// 进入详情页
const goDetail = (order) => {
	uni.navigateTo({
		url: `/packageA/order-detail/order-detail?id=${order.id}`
	})
}

// 评价按钮：未评价去评价页，已评价去详情页查看
const handleEvaluate = (order) => {
	if (evaluatedOrderIds.value.includes(String(order.id))) {
		uni.navigateTo({
			url: `/packageA/order-detail/order-detail?id=${order.id}`
		})
	} else {
		uni.navigateTo({
			url: `/packageA/order-evaluate/order-evaluate?id=${order.id}`
		})
	}
}

const goHome = () => uni.switchTab({ url: '/pages/index/index' })
</script>

<style scoped lang="scss">
$primary: #0b63ce;
$bg: #f4f7fb;
$text-main: #142434;
$text-sub: #64748b;
$text-light: #94a3b8;
// 页面里直接用
@import '@/uni.scss';

.page {
	min-height: 100vh;
	background: $bg;
	padding-bottom: calc(100rpx + env(safe-area-inset-bottom));
}

.order-banner {
	margin: 22rpx 24rpx 6rpx;
	padding: 28rpx;
	border-radius: 26rpx;
	background: linear-gradient(135deg, #0a355f 0%, #0b63ce 72%, #2f91e4 100%);
	display: flex;
	align-items: center;
	box-shadow: 0 12rpx 30rpx rgba(11, 74, 145, .16);
}
.banner-icon { width: 74rpx; height: 74rpx; border-radius: 22rpx; background: rgba(255,255,255,.14); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.banner-copy { display: flex; flex-direction: column; margin-left: 18rpx; min-width: 0; }
.banner-title { color: #fff; font-size: 29rpx; font-weight: 700; }.banner-desc { color: rgba(255,255,255,.7); font-size: 20rpx; margin-top: 6rpx; white-space: nowrap; }
.live-dot { margin-left: auto; display: flex; align-items: center; align-self: flex-start; color: rgba(255,255,255,.76); font-size: 18rpx; }
.live-dot .dot { width: 10rpx; height: 10rpx; border-radius: 50%; background: #48dfb7; margin-right: 6rpx; box-shadow: 0 0 0 5rpx rgba(72,223,183,.14); }

/* 标签切换 */
.tabs-wrap { position: sticky; top: 0; z-index: 20; background: rgba(244,247,251,.96); padding: 16rpx 24rpx 6rpx; }
.tabs-row {
	display: flex;
	background: #e9eff6;
	border-radius: 18rpx;
	padding: 6rpx;
}

.tab-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	flex: 1;
	min-height: 76rpx;
	padding: 0;
	position: relative;
	border-radius: 14rpx;

	text {
		font-size: 28rpx;
		color: $text-sub;
		font-size: 25rpx;
	}

	&.active {
		background: #fff;
		box-shadow: 0 3rpx 12rpx rgba(20,54,84,.08);
	}

	&.active text {
		color: $primary;
		font-weight: 600;
	}
}

.tab-line {
	display: none;
}

/* ═══ 订单列表 ═══ */
.order-list {
	padding: 12rpx 24rpx 20rpx;
}

.order-card {
	background: #fff;
	border-radius: 24rpx;
	padding: 24rpx;
	margin-bottom: 16rpx;
	box-shadow: 0 8rpx 26rpx rgba(20, 54, 84, 0.06);

	&:active {
		background: #f8f9fb;
	}
}

/* 订单头部 */
.order-top {
	display: flex;
	justify-content: space-between;
	align-items: flex-start;
}

.order-service {
	display: flex;
	flex: 1;
}

.order-img {
	width: 132rpx;
	height: 132rpx;
	border-radius: 22rpx;
	background: linear-gradient(145deg, #eaf3ff, #dcecff);
	flex-shrink: 0;
	display: flex;
	align-items: center;
	justify-content: center;
}

.order-info {
	margin-left: 20rpx;
	flex: 1;
	display: flex;
	flex-direction: column;
}

.order-name {
	font-size: 30rpx;
	font-weight: 600;
	color: $text-main;
	margin-bottom: 10rpx;
}

.order-product {
	font-size: 26rpx;
	color: $text-sub;
	margin-bottom: 8rpx;
	line-height: 1.4;
}

.order-spec {
	font-size: 24rpx;
	color: $text-light;
	margin-bottom: 8rpx;
}

.order-qty {
	font-size: 24rpx;
	color: $text-light;
}

/* 状态标签 */
.order-status {
	padding: 6rpx 16rpx;
	border-radius: 20rpx;
	flex-shrink: 0;

	text {
		font-size: 24rpx;
	}

	&.status-processing {
		background-color: rgba(255, 152, 0, 0.1);

		text {
			color: #ff9800;
		}
	}

	&.status-done {
		background-color: rgba(76, 175, 80, 0.1);

		text {
			color: #4caf50;
		}
	}

	&.status-waiting {
		background-color: rgba(60, 156, 255, 0.1);

		text {
			color: $primary;
		}
	}
}

/* 分割线 */
.info-divider {
	height: 1rpx;
	background-color: #f0f0f0;
	margin: 24rpx 0;
}

/* 订单详情行 */
.info-row {
	display: flex;
	align-items: center;
	padding: 8rpx 0;
}

.info-label {
	font-size: 24rpx;
	color: $text-light;
	width: 140rpx;
	flex-shrink: 0;
}

.info-value {
	font-size: 24rpx;
	color: $text-main;
	flex: 1;

	&.highlight {
		color: $primary;
		font-weight: 500;
	}
}

.copy-btn {
	margin-left: 10rpx;
}

/* 操作按钮 */
.action-row {
	display: flex;
	justify-content: flex-end;
	padding-top: 20rpx;
	margin-top: 16rpx;
	border-top: 1rpx solid #f0f0f0;
}

.evaluate-btn {
	padding: 10rpx 32rpx;
	border-radius: 28rpx;
	border: 2rpx solid $primary;
	background: #fff;

	text {
		font-size: 26rpx;
		color: $primary;
	}

	&:active {
		background: rgba(60, 156, 255, 0.08);
	}
}

/* ═══ 骨架屏 ═══ */
.skeleton-card {
	display: flex;
}

.skeleton-img {
	width: 160rpx;
	height: 160rpx;
	border-radius: 12rpx;
	flex-shrink: 0;
	background: linear-gradient(90deg, #f0f1f3 25%, #e6e7eb 50%, #f0f1f3 75%);
	background-size: 200% 100%;
	animation: shimmer 1.5s infinite;
}

.skeleton-body {
	flex: 1;
	margin-left: 20rpx;
	display: flex;
	flex-direction: column;
	justify-content: center;
}

.skeleton-line {
	height: 24rpx;
	border-radius: 6rpx;
	background: linear-gradient(90deg, #f0f1f3 25%, #e6e7eb 50%, #f0f1f3 75%);
	background-size: 200% 100%;
	animation: shimmer 1.5s infinite;
	margin-bottom: 16rpx;

	&.long {
		width: 90%;
	}

	&.medium {
		width: 65%;
	}

	&.short {
		width: 45%;
	}
}

@keyframes shimmer {
	0% {
		background-position: 200% 0;
	}

	100% {
		background-position: -200% 0;
	}
}

/* ═══ 空状态 ═══ */
.empty-state {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 120rpx 0;
}

.empty-icon { width: 92rpx; height: 92rpx; border-radius: 50%; background: #e9eff6; display: flex; align-items: center; justify-content: center; }
.empty-title { font-size: 29rpx; font-weight: 650; color: $text-main; margin-top: 20rpx; }

.empty-text {
	font-size: 23rpx;
	color: $text-light;
	margin-top: 8rpx;
}
.empty-action { margin-top: 25rpx; padding: 13rpx 30rpx; border-radius: 28rpx; background: #eaf3ff; color: $primary; font-size: 23rpx; }

/* ═══ 触底加载状态 ═══ */
.load-status {
	padding: 32rpx 0 16rpx;
	display: flex;
	justify-content: center;
	align-items: center;
}

.loading-more {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.loading-dot {
	width: 12rpx;
	height: 12rpx;
	border-radius: 50%;
	background: $primary;
	animation: bounce 1.4s infinite ease-in-out both;

	&:nth-child(1) {
		animation-delay: -0.32s;
	}

	&:nth-child(2) {
		animation-delay: -0.16s;
	}
}

@keyframes bounce {

	0%,
	80%,
	100% {
		transform: scale(0);
	}

	40% {
		transform: scale(1);
	}
}

.load-end {
	display: flex;
	align-items: center;
	gap: 16rpx;
}

.end-line {
	width: 60rpx;
	height: 2rpx;
	background: #e0e2e8;
}

.load-text {
	font-size: 24rpx;
	color: $text-light;
}
</style>
