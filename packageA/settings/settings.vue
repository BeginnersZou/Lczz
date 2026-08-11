<template>
	<view class="page">
		<!-- 去掉 顶部用户摘要 -->
		<!-- <view class="profile-card">
			<view class="avatar">
				<up-icon name="account-fill" size="32" color="#fff"></up-icon>
			</view>
			<view class="profile-info">
				<text class="username">{{ userInfo.nickname || userInfo.name || '微信用户' }}</text>
				<text class="userphone" v-if="userInfo.phone">{{ formatPhone(userInfo.phone) }}</text>
			</view>
		</view -->

		<!-- 设置项分组 -->
		<view class="group-card">
			<view class="group-item" @click="clearCache">
				<view class="item-left">
					<view class="item-icon icon-cache">
						<up-icon name="trash" size="18" color="#fff"></up-icon>
					</view>
					<text class="item-label">清除缓存</text>
				</view>
				<view class="item-right">
					<text class="item-value">{{ cacheSize }}</text>
					<up-icon name="arrow-right" size="14" color="#c0c4cc"></up-icon>
				</view>
			</view>

			<view class="group-item" @click="showAbout">
				<view class="item-left">
					<view class="item-icon icon-about">
						<up-icon name="info-circle" size="18" color="#fff"></up-icon>
					</view>
					<text class="item-label">关于我们</text>
				</view>
				<view class="item-right">
					<text class="item-version">v{{ version }}</text>
					<up-icon name="arrow-right" size="14" color="#c0c4cc"></up-icon>
				</view>
			</view>

			<view class="group-item" @click="callHotline">
				<view class="item-left">
					<view class="item-icon icon-phone">
						<up-icon name="phone" size="18" color="#fff"></up-icon>
					</view>
					<text class="item-label">联系客服</text>
				</view>
				<view class="item-right">
					<up-icon name="arrow-right" size="14" color="#c0c4cc"></up-icon>
				</view>
			</view>
		</view>

		<!-- 退出登录 -->
		<view class="logout-btn" @click="handleLogout">
			<text>退出登录</text>
		</view>

		<view class="footer">
			<text class="footer-text">武汉力创之尊机电设备有限公司</text>
			<text class="footer-copyright">© 2026 鑫立创 版权所有</text>
		</view>
	</view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { authApi } from '@/api/api.js'

const userInfo = ref({})
const version = ref('1.0.0')
const cacheSize = ref('0KB')

// 手机号脱敏
const formatPhone = (phone) => {
	if (!phone) return ''
	const s = String(phone)
	if (s.length >= 11) return s.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
	return s
}

onMounted(() => {
	userInfo.value = uni.getStorageSync('userInfo') || {}
	computeCacheSize()
})

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
			uni.showToast({ title: '缓存已清除', icon: 'success' })
		}
	})
}

// 关于我们
const showAbout = () => {
	uni.showModal({
		title: '关于我们',
		content: '武汉力创之尊机电设备有限公司（鑫立创）\n专注于制冷技术、水系统配件及水系统中央空调安装与售后服务。\n\n以诚信之心，立潮流之品',
		showCancel: false,
		confirmText: '知道了'
	})
}

// 联系客服 — 拨打服务热线
const callHotline = () => {
	uni.showActionSheet({
		itemList: ['027-82710326', '027-82710380'],
		success: (res) => {
			const phones = ['02782710326', '02782710380']
			uni.makePhoneCall({ phoneNumber: phones[res.tapIndex] })
		}
	})
}

// 退出登录
const handleLogout = () => {
	uni.showModal({
		title: '提示',
		content: '确定退出登录吗？',
		success: async (res) => {
			if (!res.confirm) return
			uni.showLoading({ title: '退出中...', mask: true })
			try {
				await authApi.logout()
			} catch (err) {
				// 即使接口失败也继续清除本地登录态
			} finally {
				uni.removeStorageSync('token')
				uni.removeStorageSync('userInfo')
				uni.hideLoading()
				uni.showToast({ title: '已退出登录', icon: 'success' })
				setTimeout(() => {
					uni.reLaunch({ url: '/pages/login/login' })
				}, 1000)
			}
		}
	})
}
</script>

<style scoped lang="scss">
@import '@/uni.scss';

.page {
	min-height: 100vh;
	background: $bg-page;
	padding-bottom: calc(60rpx + env(safe-area-inset-bottom));
}

/* 设置项分组 */
.group-card {
	margin: 24rpx 24rpx 0;
	background: $bg-card;
	border-radius: $radius-xl;
	overflow: hidden;
	box-shadow: $shadow-card;
}

.group-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 28rpx 28rpx;
	border-bottom: 1rpx solid #f0f1f3;

	&:last-child {
		border-bottom: none;
	}

	&:active {
		background: $bg-hover;
	}
}

.item-left {
	display: flex;
	align-items: center;
}

.item-icon {
	width: 64rpx;
	height: 64rpx;
	border-radius: $radius-md;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-right: 20rpx;
	flex-shrink: 0;
}

.icon-cache {
	background: linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%);
}

.icon-about {
	background: linear-gradient(135deg, #3b8eea 0%, #0b63ce 100%);
}

.icon-phone {
	background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}

.item-label {
	font-size: $font-md;
	color: $text-main;
}

.item-right {
	display: flex;
	align-items: center;
}

.item-value,
.item-version {
	font-size: $font-sm;
	color: $text-light;
	margin-right: 8rpx;
}

/* 退出登录按钮 */
.logout-btn {
	margin: 40rpx 24rpx 0;
	height: 96rpx;
	background: $bg-card;
	border-radius: $radius-xl;
	display: flex;
	align-items: center;
	justify-content: center;
	box-shadow: $shadow-card;

	text {
		font-size: $font-lg;
		color: $danger;
		font-weight: 500;
	}

	&:active {
		background: $bg-hover;
	}
}

/* 底部信息 */
.footer {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 56rpx 32rpx 0;
	gap: 8rpx;
}

.footer-text {
	font-size: $font-sm;
	color: $text-light;
}

.footer-copyright {
	font-size: $font-xs;
	color: $text-light;
}
</style>
