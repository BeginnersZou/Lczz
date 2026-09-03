
<script>
import { authApi } from '@/api/api.js'
import { clearAuthSession, getAuthToken, saveAuthUserInfo } from '@/utils/auth-session.js'

async function restoreAuthSession() {
	if (!getAuthToken()) return
	const res = await authApi.getUserInfo({ loading: false, silent: true, redirectOnUnauthorized: false })
	if (res.code === 200) {
		if (!saveAuthUserInfo(res.data)) {
			clearAuthSession()
		}
		return
	}
	if (res.code === 401 || res.code === 403) {
		clearAuthSession()
	}
}

export default {
	onLaunch() {
		// #ifdef MP-WEIXIN
		// 微信已废弃 getSystemInfoSync，按官方建议改用细分接口。
		const windowInfo = wx.getWindowInfo()
		uni.$statusBarHeight = windowInfo.statusBarHeight || 20
		// 导航栏高度 = 状态栏到胶囊的间距 * 2 + 胶囊高度
		const menuBtn = uni.getMenuButtonBoundingClientRect()
		uni.$navbarHeight = (menuBtn.top - uni.$statusBarHeight) * 2 + menuBtn.height
		uni.$menuBtn = menuBtn
		// #endif
		// #ifndef MP-WEIXIN
		const sysInfo = uni.getSystemInfoSync()
		uni.$statusBarHeight = sysInfo.statusBarHeight || 20
		uni.$navbarHeight = 44
		// #endif

		// 全局路由跳转提示：拦截 navigateTo/switchTab/reLaunch
		// 100ms 后显示"跳转中"，跳转成功立即隐藏；兜底 1000ms 自动隐藏防止卡死
		// 用 success 而非 complete（complete 触发过早导致 loading 从不显示）
		let navLoadingShown = false
		let navShowTimer = null
		let navHideTimer = null
		const navInterceptor = {
			invoke() {
				clearTimeout(navShowTimer)
				clearTimeout(navHideTimer)
				navLoadingShown = false
				navShowTimer = setTimeout(() => {
					navLoadingShown = true
					uni.showLoading({ title: '跳转中...', mask: true })
					// 兜底：1000ms 后自动隐藏，防止 success 未触发导致 loading 卡死
					navHideTimer = setTimeout(() => {
						if (navLoadingShown) {
							uni.hideLoading()
							navLoadingShown = false
						}
					}, 1000)
				}, 100)
			},
			success() {
				clearTimeout(navShowTimer)
				if (navLoadingShown) {
					uni.hideLoading()
					navLoadingShown = false
				}
			},
			fail() {
				clearTimeout(navShowTimer)
				if (navLoadingShown) {
					uni.hideLoading()
					navLoadingShown = false
				}
			}
		}
		uni.addInterceptor('navigateTo', navInterceptor)
		uni.addInterceptor('switchTab', navInterceptor)
		uni.addInterceptor('reLaunch', navInterceptor)

		// 冷启动时用真实接口恢复登录态并刷新角色。
		restoreAuthSession()
	},
	onShow() {},
	onHide() {}
}
</script>

<style lang="scss">
/* ═══════════════════════════════════════════════
 * App.vue 全局样式 — iOS/Android 统一适配
 * ═══════════════════════════════════════════════ */

/* 全局重置 */
page {
	background-color: #f4f7fb !important;
	font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', sans-serif;
	font-size: 28rpx;
	color: #142434;
	-webkit-font-smoothing: antialiased;
	-moz-osx-font-smoothing: grayscale;
}

/* 解决 iOS 弹性滚动穿透 */
::-webkit-scrollbar {
	width: 0;
	height: 0;
	color: transparent;
}

/* ═══ 状态栏占位（自定义导航栏页面使用） ═══ */
.status-bar {
	width: 100%;
	/* 动态高度：通过 JS 获取实际状态栏高度 */
	height: var(--status-bar-height, 44rpx);
}

/* ═══ 自定义导航栏（统一高度） ═══ */
.custom-navbar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	height: 88rpx;
	padding: 0 30rpx;
}

/* ═══ 安全区底部留白 ═══ */
.safe-bottom {
	height: env(safe-area-inset-bottom);
}

/* ═══ 卡片通用样式 ═══ */
.u-card {
	background: #fff;
	border-radius: 24rpx;
	padding: 24rpx;
	margin: 16rpx 24rpx 0;
	box-shadow: 0 8rpx 28rpx rgba(20, 54, 84, 0.07);
}

/* 原生 button 默认边框会破坏统一视觉 */
button::after {
	border: none;
}

/* ═══ 文本溢出省略 ═══ */
.u-ellipsis-1 {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.u-ellipsis-2 {
	display: -webkit-box;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 2;
	overflow: hidden;
}

.u-ellipsis-3 {
	display: -webkit-box;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 3;
	overflow: hidden;
}

/* ═══ 触底加载状态（统一动画） ═══ */
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
	background: #0b63ce;
	animation: loading-bounce 1.4s infinite ease-in-out both;

	&:nth-child(1) { animation-delay: -0.32s; }
	&:nth-child(2) { animation-delay: -0.16s; }
}

@keyframes loading-bounce {
	0%, 80%, 100% { transform: scale(0); }
	40% { transform: scale(1); }
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
	color: #94a3b8;
}

/* ═══ 骨架屏（统一闪烁动画） ═══ */
.skeleton-line {
	height: 24rpx;
	border-radius: 6rpx;
	background: linear-gradient(90deg, #f0f1f3 25%, #e6e7eb 50%, #f0f1f3 75%);
	background-size: 200% 100%;
	animation: shimmer 1.5s infinite;
	margin-bottom: 16rpx;

	&.long { width: 90%; }
	&.medium { width: 65%; }
	&.short { width: 45%; }
	&.mini { width: 30%; }
}

.skeleton-img {
	border-radius: 12rpx;
	background: linear-gradient(90deg, #f0f1f3 25%, #e6e7eb 50%, #f0f1f3 75%);
	background-size: 200% 100%;
	animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
	0% { background-position: 200% 0; }
	100% { background-position: -200% 0; }
}

/* ═══ 底部导航栏（统一样式） ═══ */
.tabbar {
	position: fixed;
	bottom: 0;
	left: 0;
	right: 0;
	height: calc(100rpx + env(safe-area-inset-bottom));
	padding-bottom: env(safe-area-inset-bottom);
	background: #fff;
	display: flex;
	align-items: center;
	box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
	z-index: 100;
}

.tabbar-item {
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 4rpx;
}

.tabbar-text {
	font-size: 20rpx;
	color: #999;

	&.active {
		color: #0b63ce;
		font-weight: 600;
	}
}

/* ═══ 点击按压反馈（微信小程序 view 的 :active 伪类不生效，统一用 hover-class） ═══ */
/* 列表项/设置项：浅灰底反馈 */
.hover-bg {
	background: #f1f2f5 !important;
}

/* 深色/渐变背景上的按钮：半透明白高亮 */
.hover-mask {
	background: rgba(255, 255, 255, 0.2) !important;
}

/* 功能宫格/图标按钮：整体降透明度 */
.hover-press {
	opacity: 0.68 !important;
}

/* 卡片类点击：轻按压效果 */
.hover-card {
	background: #f8f9fb !important;
	opacity: 0.92 !important;
}
</style>
