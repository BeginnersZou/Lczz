<template>
	<view class="mine-page">
		<!-- ═══ 顶部渐变区域 ═══ -->
		<view class="header-section" :style="{ paddingTop: `${headerTopPadding}px` }">
			<view class="header-content">
				<!-- 用户信息行 -->
				<view class="user-row">
					<view class="avatar-box">
						<image v-if="userInfo.avatar" class="avatar-img" :src="userInfo.avatar" mode="aspectFill">
						</image>
						<up-icon v-else name="account-fill" size="36" color="#fff"></up-icon>
					</view>
					<view class="user-meta">
						<text class="username">{{ displayName }}</text>
						<view class="user-sub">
							<view class="role-tag"><text>{{ roleLabel }}</text></view>
							<text class="user-phone" v-if="userInfo.phone">{{ formatPhone(userInfo.phone) }}</text>
						</view>
					</view>
					<view class="setting-btn" hover-class="hover-mask" :hover-stay-time="80" @click="goSettings">
						<up-icon name="setting" size="22" color="#fff"></up-icon>
					</view>
				</view>
			</view>
		</view>

		<!-- ═══ 工作概览卡片（浮于头部下方） ═══ -->
		<view class="stats-card" v-if="!statsError" hover-class="hover-press" :hover-stay-time="80" @click="goToOrder()">
			<view class="stat-item" @click.stop="goToOrder(1)">
				<text class="stat-num">{{ statsLoading ? '--' : stats.pending }}</text>
				<text class="stat-label">待上门</text>
			</view>
			<view class="stat-divider"></view>
			<view class="stat-item" @click.stop="goToOrder(2)">
				<text class="stat-num">{{ statsLoading ? '--' : stats.processing }}</text>
				<text class="stat-label">处理中</text>
			</view>
			<view class="stat-divider"></view>
			<view class="stat-item" @click.stop="goToOrder(3)">
				<text class="stat-num">{{ statsLoading ? '--' : stats.done }}</text>
				<text class="stat-label">已完成</text>
			</view>
		</view>
		<view class="stats-card stats-error-card" v-else @click="fetchStats">
			<up-icon name="reload" size="22" color="#0b63ce"></up-icon>
			<view class="stats-error-copy">
				<text class="stats-error-title">订单概览暂不可用</text>
				<text class="stats-error-desc">点击重新加载真实数据</text>
			</view>
		</view>

		<!-- ═══ 快捷功能 ═══ -->
		<view class="func-card">
			<view class="func-item" hover-class="hover-press" :hover-stay-time="80" @click="goToOrder()">
				<view class="func-icon icon-blue">
					<up-icon name="order" size="24" color="#fff"></up-icon>
				</view>
				<text class="func-text">我的订单</text>
			</view>
			<view class="func-item" hover-class="hover-press" :hover-stay-time="80" @click="goToOfficial">
				<view class="func-icon icon-orange">
					<up-icon name="server-fill" size="24" color="#fff"></up-icon>
				</view>
				<text class="func-text">品牌服务</text>
			</view>
			<view class="func-item" hover-class="hover-press" :hover-stay-time="80" @click="callService">
				<view class="func-icon icon-purple">
					<up-icon name="phone-fill" size="24" color="#fff"></up-icon>
				</view>
				<text class="func-text">联系客服</text>
			</view>
		</view>

		<!-- ═══ 更多功能列表 ═══ -->
		<view class="list-card">
			<view class="list-item" hover-class="hover-bg" :hover-stay-time="80" @click="callService">
				<view class="list-left">
					<view class="list-icon icon-phone">
						<up-icon name="phone" size="18" color="#fff"></up-icon>
					</view>
					<text class="list-label">联系客服</text>
				</view>
				<up-icon name="arrow-right" size="14" color="#c0c4cc"></up-icon>
			</view>
			<view class="list-item" hover-class="hover-bg" :hover-stay-time="80" @click="goToOfficial">
				<view class="list-left">
					<view class="list-icon icon-info">
						<up-icon name="info-circle" size="18" color="#fff"></up-icon>
					</view>
					<text class="list-label">关于我们</text>
				</view>
				<up-icon name="arrow-right" size="14" color="#c0c4cc"></up-icon>
			</view>
			<view class="list-item" hover-class="hover-bg" :hover-stay-time="80" @click="clearCache">
				<view class="list-left">
					<view class="list-icon icon-cache">
						<up-icon name="trash" size="18" color="#fff"></up-icon>
					</view>
					<text class="list-label">清除缓存</text>
				</view>
				<view class="list-right">
					<text class="list-value">{{ cacheSize }}</text>
					<up-icon name="arrow-right" size="14" color="#c0c4cc"></up-icon>
				</view>
			</view>
			<!-- 			<view class="list-item" hover-class="hover-bg" :hover-stay-time="80" @click="goSettings">
				<view class="list-left">
					<view class="list-icon icon-setting">
						<up-icon name="setting" size="18" color="#fff"></up-icon>
					</view>
					<text class="list-label">设置中心</text>
				</view>
				<up-icon name="arrow-right" size="14" color="#c0c4cc"></up-icon>
			</view> -->
		</view>

		<!-- ═══ 底部版本信息 ═══ -->
		<view class="footer">
			<text class="version">v{{ version }}</text>
			<text class="copyright">© 2026 鑫立创 版权所有</text>
		</view>
	</view>
</template>

<script setup>
	import {
		ref,
		computed
	} from 'vue'
	import {
		onShow
	} from '@dcloudio/uni-app'
	import {
		authApi,
		dashboardApi
	} from '@/api/api.js'
	import {
		clearAuthSession,
		getAuthToken,
		getAuthUserInfo,
		getRoleLabel,
		maskPhone,
		saveAuthUserInfo
	} from '@/utils/auth-session.js'

	const userInfo = ref({})
	const headerTopPadding = ref(64)
	try {
		const windowInfo = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
		const capsule = typeof wx !== 'undefined' && typeof wx.getMenuButtonBoundingClientRect === 'function'
			? wx.getMenuButtonBoundingClientRect()
			: null
		headerTopPadding.value = Math.max(
			Number(windowInfo.statusBarHeight || 20) + 44,
			Number(capsule?.bottom || 0) + 8
		)
	} catch (error) {
		headerTopPadding.value = 64
	}
	const version = ref('1.0.0')
	const cacheSize = ref('0KB')

	const stats = ref({
		pending: 0,
		processing: 0,
		done: 0
	})
	const statsLoading = ref(true)
	const statsError = ref(false)

	const fetchUserInfo = async () => {
		try {
			const res = await authApi.getUserInfo()
			if (res.code === 200) {
				if (!saveAuthUserInfo(res.data)) {
					clearAuthSession()
					uni.reLaunch({ url: '/pages/login/login' })
					return
				}
				userInfo.value = res.data
			}
		} catch (err) {
			// request.js 已统一处理错误提示
		}
	}

	const fetchStats = async () => {
		statsLoading.value = true
		statsError.value = false
		try {
			const res = await dashboardApi.getTodo({ page: 1, pageSize: 1 }, { silent: true })
			if (res.code !== 200 || !res.data) {
				statsError.value = true
				return
			}
			stats.value = {
				pending: Number(res.data.pending || 0),
				processing: Number(res.data.processing || 0),
				done: Number(res.data.done || 0)
			}
		} catch (err) {
			statsError.value = true
		} finally {
			statsLoading.value = false
		}
	}

	// 显示昵称：优先取后端返回的昵称/姓名，兜底"微信用户"
	const displayName = computed(() => {
		const u = userInfo.value || {}
		return u.nickname || u.name || u.username || '微信用户'
	})

	const roleLabel = computed(() => {
		const role = (userInfo.value && userInfo.value.role) || ''
		return getRoleLabel(role)
	})

	// 手机号脱敏
	const formatPhone = (phone) => {
		return maskPhone(phone)
	}

	// 计算本地缓存占用
	const computeCacheSize = () => {
		try {
			const info = uni.getStorageInfoSync()
			const kb = info.currentSize || 0
			cacheSize.value = kb < 1024 ? `${kb}KB` : `${(kb / 1024).toFixed(2)}MB`
		} catch (e) {
			cacheSize.value = '0KB'
		}
	}

	// 加载本地存储的用户信息
	const loadUserInfo = () => {
		userInfo.value = getAuthUserInfo()
		computeCacheSize()
	}

	// 页面再次显示时刷新（退出登录后返回会重新读取）
	onShow(() => {
		if (!getAuthToken()) {
			userInfo.value = {}
			uni.reLaunch({ url: '/pages/login/login' })
			return
		}
		loadUserInfo()
		fetchUserInfo()
		fetchStats()
	})

	// 进入设置页
	const goSettings = () => {
		uni.navigateTo({
			url: '/packageA/settings/settings'
		})
	}

	// 跳转订单页（可指定 tab：1=待上门 2=处理中 3=已完成，不传=保持当前）
	const goToOrder = (tabIndex) => {
		uni.$pendingOrderTab = (tabIndex !== undefined && tabIndex !== null) ? tabIndex : null
		uni.switchTab({
			url: '/pages/order/order'
		})
	}

	const goToOfficial = () => {
		uni.switchTab({ url: '/pages/official/official' })
	}

	// 联系客服
	const callService = () => {
		uni.showActionSheet({
			itemList: ['027-82710326', '027-82710380'],
			success: (res) => {
				const phones = ['02782710326', '02782710380']
				uni.makePhoneCall({ phoneNumber: phones[res.tapIndex] })
			}
		})
	}

	// 清除缓存（保留 token 与 userInfo 登录态）
	const clearCache = () => {
		uni.showModal({
			title: '提示',
			content: '确定清除本地缓存吗？登录状态不会被清除。',
			success: (res) => {
				if (!res.confirm) return
				const token = uni.getStorageSync('token')
				const uInfo = uni.getStorageSync('userInfo')
				uni.clearStorageSync()
				if (token) uni.setStorageSync('token', token)
				if (uInfo) uni.setStorageSync('userInfo', uInfo)
				computeCacheSize()
				uni.showToast({
					title: '缓存已清除',
					icon: 'success'
				})
			}
		})
	}

</script>

<style lang="scss" scoped>
	@import '@/uni.scss';

	.mine-page {
		min-height: 100vh;
		background: #f4f7fb;
		padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
	}

	/* ═══ 顶部渐变区域 ═══ */
	.header-section {
		background: linear-gradient(180deg, #0b63ce 0%, #126fda 78%, #dbeafe 100%);
		padding-bottom: 90rpx;
		border-radius: 0 0 44rpx 44rpx;
	}

	.header-content {
		padding: 0 32rpx;
	}

	/* 用户信息行 */
	.user-row {
		display: flex;
		align-items: center;
		position: relative;
	}

	.avatar-box {
		width: 108rpx;
		height: 108rpx;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.16);
		border: 4rpx solid rgba(255, 255, 255, 0.5);
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		overflow: hidden;
	}

	.avatar-img {
		width: 100%;
		height: 100%;
		border-radius: 50%;
	}

	.user-meta {
		margin-left: 24rpx;
		flex: 1;
		display: flex;
		flex-direction: column;
	}

	.username {
		font-size: 38rpx;
		font-weight: 700;
		color: #fff;
		margin-bottom: 12rpx;
	}

	.user-sub {
		display: flex;
		align-items: center;
		gap: 16rpx;
	}

	.role-tag {
		padding: 4rpx 18rpx;
		border-radius: 20rpx;
		background: rgba(255, 255, 255, 0.25);
		border: 1rpx solid rgba(255, 255, 255, 0.4);
	}

	.role-tag text {
		font-size: 20rpx;
		color: #fff;
	}

	.user-phone {
		font-size: 24rpx;
		color: rgba(255, 255, 255, 0.85);
	}

	.setting-btn {
		width: 64rpx;
		height: 64rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;

		&:active {
			opacity: 0.6;
		}
	}

	/* ═══ 工作概览卡片 ═══ */
	.stats-card {
		margin: -60rpx 24rpx 0;
		background: #fff;
		border-radius: 24rpx;
		padding: 36rpx 0;
		display: flex;
		align-items: center;
		box-shadow: 0 12rpx 36rpx rgba(20, 54, 84, 0.12);
		position: relative;
		z-index: 10;

		&:active {
			transform: scale(0.98);
		}
	}

	.stats-error-card {
		min-height: 128rpx;
		box-sizing: border-box;
		padding: 28rpx 36rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 20rpx;
	}
	.stats-error-copy { display: flex; flex-direction: column; }
	.stats-error-title { font-size: 26rpx; font-weight: 650; color: #142434; }
	.stats-error-desc { margin-top: 6rpx; font-size: 21rpx; color: #8b9aaa; }

	.stat-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
	}

	.stat-num {
		font-size: 44rpx;
		font-weight: 700;
		color: #142434;
		line-height: 1.2;
	}

	.stat-label {
		font-size: 24rpx;
		color: #94a3b8;
		margin-top: 8rpx;
	}

	.stat-divider {
		width: 1rpx;
		height: 56rpx;
		background: #f0f1f3;
	}

	/* ═══ 快捷功能 ═══ */
	.func-card {
		margin: 24rpx 24rpx 0;
		background: #fff;
		border-radius: 24rpx;
		padding: 32rpx 0;
		display: flex;
		box-shadow: 0 2rpx 12rpx rgba(30, 41, 59, 0.04);
	}

	.func-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;

		&:active {
			opacity: 0.6;
		}
	}

	.func-icon {
		width: 88rpx;
		height: 88rpx;
		border-radius: 22rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-bottom: 14rpx;
	}

	.icon-blue {
		background: linear-gradient(135deg, #1479e8 0%, #0b63ce 100%);
	}

	.icon-green {
		background: linear-gradient(135deg, #3bc5ae 0%, #0f958a 100%);
	}

	.icon-orange {
		background: linear-gradient(135deg, #f3a449 0%, #d97c18 100%);
	}

	.icon-purple {
		background: linear-gradient(135deg, #7f73cf 0%, #6554b6 100%);
	}

	.func-text {
		font-size: 24rpx;
		color: #64748b;
	}

	/* ═══ 更多功能列表 ═══ */
	.list-card {
		margin: 24rpx 24rpx 0;
		background: #fff;
		border-radius: 24rpx;
		overflow: hidden;
		box-shadow: 0 2rpx 12rpx rgba(30, 41, 59, 0.04);
	}

	.list-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 28rpx 28rpx;
		border-bottom: 1rpx solid #f5f6f8;

		&:last-child {
			border-bottom: none;
		}

		&:active {
			background: #f8f9fb;
		}
	}

	.list-left {
		display: flex;
		align-items: center;
	}

	.list-icon {
		width: 56rpx;
		height: 56rpx;
		border-radius: 14rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 20rpx;
		flex-shrink: 0;
	}

	.icon-phone {
		background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
	}

	.icon-info {
		background: linear-gradient(135deg, #3b8eea 0%, #0b63ce 100%);
	}

	.icon-cache {
		background: linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%);
	}

	.icon-setting {
		background: linear-gradient(135deg, #a18cd1 0%, #7c92f0 100%);
	}

	.list-label {
		font-size: 28rpx;
		color: #142434;
	}

	.list-right {
		display: flex;
		align-items: center;
	}

	.list-value {
		font-size: 24rpx;
		color: #94a3b8;
		margin-right: 8rpx;
	}

	/* ═══ 底部版本信息 ═══ */
	.footer {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 56rpx 0 0;
		gap: 8rpx;
	}

	.version {
		font-size: 24rpx;
		color: #cbd5e1;
	}

	.copyright {
		font-size: 22rpx;
		color: #cbd5e1;
	}
</style>
