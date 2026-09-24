<template>
	<view class="page">
		<!-- ═══ 顶部品牌区 ═══ -->
		<view v-if="pageConfig.brandVisible" class="hero-section">
			<view class="status-bar"></view>
			<view class="hero-navbar"></view>
			<view class="hero-content">
				<view class="logo-area">
					<view class="logo-circle">
						<text class="logo-text">力</text>
					</view>
					<view class="company-name-area">
						<text class="company-name">{{ pageConfig.companyName }}</text>
						<text class="company-subname">{{ pageConfig.companySubtitle }}</text>
					</view>
				</view>
				<view class="slogan-area">
					<text class="slogan">{{ pageConfig.slogan }}</text>
				</view>
				<view v-if="pageConfig.heroStats.length" class="hero-stats">
					<template v-for="(item, index) in pageConfig.heroStats" :key="index">
						<view><text class="stat-value">{{ item.title }}</text><text class="stat-name">{{ item.description }}</text></view>
						<view v-if="index < pageConfig.heroStats.length - 1" class="stat-divider"></view>
					</template>
				</view>
			</view>
		</view>
		<view v-else class="plain-top-spacer"></view>

		<view v-if="loadError" class="load-error">
			<text>服务信息更新失败，当前显示默认内容</text>
			<text class="retry-link" @click="loadServicePage">重新加载</text>
		</view>

		<!-- ═══ 服务项目区 ═══ -->
		<view v-if="pageConfig.servicesVisible" class="service-section">
			<view class="section-header">
				<view class="header-line"></view>
				<text class="section-title">服务项目</text>
				<view class="header-line"></view>
			</view>
			<view class="service-list">
				<view class="service-item" v-for="(item, index) in pageConfig.services" :key="index"
					@click="handleServiceClick(item)">
					<view class="service-num">{{ String(index + 1).padStart(2, '0') }}</view>
					<view class="service-content">
						<text class="service-name">{{ item.title }}</text>
						<text class="service-desc">{{ item.description }}</text>
					</view>
					<up-icon name="arrow-right" size="14" color="#0b63ce"></up-icon>
				</view>
			</view>
		</view>

		<!-- ═══ 公司简介区 ═══ -->
		<view v-if="pageConfig.profileVisible" class="profile-section">
			<view class="section-header">
				<view class="header-line"></view>
				<text class="section-title">公司简介</text>
				<view class="header-line"></view>
			</view>
			<view class="profile-card">
				<text class="profile-text">{{ pageConfig.profileText }}</text>
				<view class="profile-tags">
					<view class="profile-tag" v-for="(tag, i) in pageConfig.profileTags" :key="i">{{ tag }}</view>
				</view>
			</view>
		</view>

		<!-- ═══ 公司展示区 ═══ -->
		<view v-if="pageConfig.galleryVisible && pageConfig.galleryImages.length" class="gallery-section">
			<view class="section-header">
				<view class="header-line"></view>
				<text class="section-title">公司展示</text>
				<view class="header-line"></view>
			</view>
			<view class="gallery-grid">
				<image v-for="(image, index) in pageConfig.galleryImages" :key="image.id || index"
					class="gallery-image" :src="image.url" mode="aspectFill" lazy-load @click="previewGallery(index)" />
			</view>
		</view>

		<!-- ═══ 公司优势区 ═══ -->
		<view v-if="pageConfig.advantagesVisible" class="advantage-section">
			<view class="section-header">
				<view class="header-line"></view>
				<text class="section-title">公司优势</text>
				<view class="header-line"></view>
			</view>
			<view class="advantage-list">
				<view class="advantage-item" v-for="(item, index) in pageConfig.advantages" :key="index">
					<view class="advantage-num">{{ String(index + 1).padStart(2, '0') }}</view>
					<view class="advantage-content">
						<text class="advantage-name">{{ item.title }}</text>
						<text class="advantage-desc">{{ item.description }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- ═══ 联系我们区 ═══ -->
		<view v-if="pageConfig.contactVisible" class="contact-section">
			<view class="section-header">
				<view class="header-line"></view>
				<text class="section-title">联系我们</text>
				<view class="header-line"></view>
			</view>
			<view class="contact-card">
				<view class="contact-item" @click="callPhone">
					<view class="contact-icon phone">
						<up-icon name="phone" size="18" color="#fff"></up-icon>
					</view>
					<view class="contact-info">
						<text class="contact-label">服务热线</text>
						<text class="contact-value">{{ phoneDisplay }}</text>
					</view>
				</view>
				<view class="contact-item address-item" hover-class="contact-item-press" @click="openAddressLocation">
					<view class="contact-icon location">
						<up-icon name="map" size="18" color="#fff"></up-icon>
					</view>
					<view class="contact-info">
						<text class="contact-label">公司地址</text>
						<text class="contact-value">{{ pageConfig.address }}</text>
						<text class="address-action">点击查看地图并导航</text>
					</view>
					<up-icon name="arrow-right" size="15" color="#0b63ce"></up-icon>
				</view>
				<view class="contact-item" @click="openHours">
					<view class="contact-icon clock">
						<up-icon name="clock" size="18" color="#fff"></up-icon>
					</view>
					<view class="contact-info">
						<text class="contact-label">营业时间</text>
						<text class="contact-value">{{ pageConfig.businessHours }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- ═══ 底部信息 ═══ -->
		<view class="footer">
			<text class="footer-slogan">{{ pageConfig.slogan }}</text>
			<text class="footer-copyright">© {{ currentYear }} {{ pageConfig.companyName }}</text>
		</view>

		<view class="bottom-placeholder"></view>


	</view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import {
	onLoad,
	onPullDownRefresh,
	onShareAppMessage,
	onShareTimeline
} from '@dcloudio/uni-app'
import { openCompanyLocation } from '@/utils/company-location.js'
import { servicePageApi } from '@/api/api.js'

const defaults = {
	companyName: '武汉力创之尊',
	companySubtitle: '制冷技术服务有限公司',
	slogan: '以诚信之心，立潮流之品',
	brandVisible: true,
	heroStats: [
		{ title: '一站式', description: '暖通服务' },
		{ title: '全流程', description: '服务跟进' },
		{ title: '双热线', description: '快速响应' }
	],
	servicesVisible: true,
	services: [
		{ title: '水系统中央空调配件材料销售', description: '提供各种高品质水系统中央空调配件及二联供材料，满足家庭和商业需求。品类齐全、价格优惠，品质可靠、送货快捷。' },
		{ title: '水系统中央空调安装', description: '专业安装团队按规范完成勘察、施工与调试，并依据具体项目约定提供相应质保服务。' },
		{ title: '水系统中央空调售后', description: '提供中央空调暖通系统故障排查、维修与保养服务，服务过程可沟通、可跟进。' }
	],
	profileText: '武汉力创之尊机电设备有限公司（力创之尊）专注于制冷技术、水系统配件、二联供材料销售及水系统中央空调安装与售后服务。我们始终秉持“以诚信之心，立潮流之品”的理念，为家庭与商业客户提供清晰、可靠的暖通服务方案。',
	profileVisible: true,
	profileTags: ['品牌授权', '持证上岗', '正品保证', '售后无忧'],
	galleryVisible: true,
	galleryImages: [],
	advantagesVisible: true,
	advantages: [
		{ title: '诚信为本', description: '我们始终坚持诚信经营，赢得了广大客户的信赖与支持。' },
		{ title: '专业服务', description: '专业的技术团队和售后服务团队，确保每一位客户都能享受到高质量的服务体验。' },
		{ title: '品质保障', description: '严格的质量控制体系，确保每一件产品都符合甚至超越客户的期望。' },
		{ title: '快速响应', description: '我们承诺快速响应客户的需求，无论是产品咨询还是售后服务，都将在最短时间内给予答复和处理。' }
	],
	contactVisible: true,
	phonePrimary: '027-82710326', phoneSecondary: '027-82710380',
	address: '湖北省武汉市江岸区不锈钢路S17-49-51号力创之尊',
	longitude: 114.306997, latitude: 30.665673, businessHours: '周一至周日 8:00-20:00'
}
const pageConfig = reactive(JSON.parse(JSON.stringify(defaults)))
const loadError = ref(false)
const currentYear = new Date().getFullYear()
const phoneDisplay = computed(() => [pageConfig.phonePrimary, pageConfig.phoneSecondary].filter(Boolean).join(' / '))

// 分享官网给好友
onShareAppMessage(() => ({
	title: pageConfig.companyName,
	path: '/pages/official/official'
}))

// 分享到朋友圈
onShareTimeline(() => ({
	title: `${pageConfig.slogan} — ${pageConfig.companyName}`
}))

async function loadServicePage({ pullDown = false } = {}) {
	const res = await servicePageApi.get({ loading: !pullDown, silent: true })
	if (res.code === 200 && res.data) {
		Object.assign(pageConfig, res.data)
		loadError.value = false
	} else {
		loadError.value = true
	}
	if (pullDown) uni.stopPullDownRefresh()
}

onLoad(() => loadServicePage())
onPullDownRefresh(() => loadServicePage({ pullDown: true }))

const handleServiceClick = (item) => {
	uni.showActionSheet({
		itemList: ['电话咨询', '复制服务名称'],
		success: (res) => {
			if (res.tapIndex === 0) {
				callPhone()
			} else {
				uni.setClipboardData({ data: item.title, success: () => uni.showToast({ title: '已复制', icon: 'none' }) })
			}
		},
	})
}

const callPhone = () => {
	uni.showActionSheet({
		itemList: [pageConfig.phonePrimary, pageConfig.phoneSecondary].filter(Boolean),
		success: (res) => {
			const phones = [pageConfig.phonePrimary, pageConfig.phoneSecondary].filter(Boolean)
			uni.makePhoneCall({
				phoneNumber: phones[res.tapIndex].replace(/[^0-9+]/g, ''),
				fail: () => uni.showToast({ title: '取消拨打', icon: 'none' })
			})
		}
	})
}

const openAddressLocation = () => openCompanyLocation(uni, {
	name: pageConfig.companyName,
	address: pageConfig.address,
	longitude: pageConfig.longitude,
	latitude: pageConfig.latitude
})

const previewGallery = (index) => uni.previewImage({
	current: pageConfig.galleryImages[index].url,
	urls: pageConfig.galleryImages.map(image => image.url)
})

const openHours = () => {
	uni.showModal({
		title: '营业时间',
		content: pageConfig.businessHours,
		showCancel: false,
		confirmText: '知道了',
	})
}
</script>

<style scoped lang="scss">
$primary: #0b63ce;
$primary-dark: #084b9b;
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

.load-error {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin: 24rpx 24rpx 0;
	padding: 20rpx 24rpx;
	color: #9a6700;
	background: #fff8db;
	border: 1rpx solid #f4d477;
	border-radius: 16rpx;
	font-size: 23rpx;
}

.retry-link { color: $primary; font-weight: 600; }
.plain-top-spacer { height: calc(var(--status-bar-height, 44rpx) + 28rpx); }

/* ═══ 顶部品牌区 ═══ */
.hero-section {
	background: linear-gradient(180deg, #0b63ce 0%, #126fda 78%, #dbeafe 100%);
	padding: 0 32rpx 52rpx;
	overflow: hidden;
	border-radius: 0 0 44rpx 44rpx;
}

.status-bar {
	height: var(--status-bar-height, 44rpx);
}

.hero-navbar { height: 88rpx; display: flex; align-items: center; color: rgba(255,255,255,.92); font-size: 30rpx; font-weight: 650; }

.hero-content {
	display: flex;
	flex-direction: column;
	align-items: stretch;
}

.logo-area {
	display: flex;
	align-items: center;
	margin-top: 18rpx;
}

.logo-circle {
	width: 96rpx;
	height: 96rpx;
	background: rgba(255, 255, 255, 0.2);
	backdrop-filter: blur(10rpx);
	border: 3rpx solid rgba(255, 255, 255, 0.4);
	border-radius: 28rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-right: 24rpx;
}

.logo-text {
	font-size: 44rpx;
	font-weight: 700;
	color: #fff;
}

.company-name-area {
	display: flex;
	flex-direction: column;
}

.company-name {
	font-size: 36rpx;
	font-weight: 700;
	color: #fff;
	line-height: 1.3;
}

.company-subname {
	font-size: 24rpx;
	color: rgba(255, 255, 255, 0.8);
	margin-top: 4rpx;
}

.slogan-area {
	align-self: flex-start;
	margin-top: 28rpx;
	padding: 13rpx 26rpx;
	background: rgba(255, 255, 255, 0.15);
	backdrop-filter: blur(10rpx);
	border-radius: 40rpx;
	border: 1rpx solid rgba(255, 255, 255, 0.2);
}

.slogan {
	font-size: 24rpx;
	color: #fff;
	font-weight: 500;
	letter-spacing: 4rpx;
}

.hero-stats { margin-top: 36rpx; display: flex; align-items: center; padding-top: 28rpx; border-top: 1rpx solid rgba(255,255,255,.16); }
.hero-stats > view:not(.stat-divider) { flex: 1; display: flex; flex-direction: column; align-items: center; }
.stat-value { color: #fff; font-size: 27rpx; font-weight: 700; }.stat-name { color: rgba(255,255,255,.6); font-size: 19rpx; margin-top: 6rpx; }.stat-divider { width: 1rpx; height: 46rpx; background: rgba(255,255,255,.18); }

/* ═══ 通用标题 ═══ */
.section-header {
	display: flex;
	align-items: center;
	justify-content: flex-start;
	gap: 20rpx;
	padding: 40rpx 0 28rpx;
}

.header-line { width: 7rpx; height: 30rpx; border-radius: 5rpx; background: $primary; }
.header-line:last-child { display: none; }

.section-title {
	font-size: 32rpx;
	font-weight: 700;
	color: $text-main;
}

/* ═══ 服务项目 ═══ */
.service-section {
	background: #fff;
	margin: 24rpx 24rpx 0;
	border-radius: 28rpx;
	padding: 0 28rpx 28rpx;
	box-shadow: 0 8rpx 28rpx rgba(20, 54, 84, 0.07);
}

.service-list {
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.service-item {
	display: flex;
	align-items: center;
	padding: 24rpx;
	background: #f8f9fb;
	border-radius: 16rpx;

	&:active {
		background: #f0f4ff;
	}
}

.service-num {
	width: 56rpx;
	height: 56rpx;
	background: #eaf3ff;
	border-radius: 14rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 26rpx;
	font-weight: 700;
	color: $primary;
	margin-right: 20rpx;
	flex-shrink: 0;
}

.service-content {
	flex: 1;
	display: flex;
	flex-direction: column;
}

.service-name {
	font-size: 28rpx;
	font-weight: 600;
	color: $text-main;
}

.service-desc {
	font-size: 22rpx;
	color: $text-sub;
	margin-top: 6rpx;
	line-height: 1.4;
}

/* ═══ 公司简介 ═══ */
.profile-section {
	background: #fff;
	margin: 24rpx 24rpx 0;
	border-radius: 28rpx;
	padding: 0 28rpx 32rpx;
	box-shadow: 0 8rpx 28rpx rgba(20, 54, 84, 0.07);
}

.profile-card {
	display: flex;
	flex-direction: column;
	gap: 20rpx;
}

.profile-text {
	font-size: 28rpx;
	color: $text-sub;
	line-height: 1.8;
	text-align: justify;
}

.profile-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-top: 8rpx;
}

.profile-tag {
	font-size: 22rpx;
	color: $primary;
	background: rgba(60, 156, 255, 0.08);
	padding: 8rpx 20rpx;
	border-radius: 8rpx;
	font-weight: 500;
}

/* ═══ 公司展示 ═══ */
.gallery-section {
	background: #fff;
	margin: 24rpx 24rpx 0;
	border-radius: 28rpx;
	padding: 0 28rpx 32rpx;
	box-shadow: 0 8rpx 28rpx rgba(20, 54, 84, 0.07);
}

.gallery-grid {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 14rpx;
}

.gallery-image {
	width: 100%;
	height: 230rpx;
	border-radius: 16rpx;
	background: #edf2f7;
}

/* ═══ 公司优势 ═══ */
.advantage-section {
	background: #fff;
	margin: 24rpx 24rpx 0;
	border-radius: 28rpx;
	padding: 0 28rpx 28rpx;
	box-shadow: 0 8rpx 28rpx rgba(20, 54, 84, 0.07);
}

.advantage-list {
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.advantage-item {
	display: flex;
	align-items: center;
	padding: 24rpx;
	background: #f8f9fb;
	border-radius: 16rpx;
}

.advantage-num {
	width: 56rpx;
	height: 56rpx;
	background: linear-gradient(135deg, $primary, $primary-dark);
	border-radius: 14rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 26rpx;
	font-weight: 700;
	color: #fff;
	margin-right: 20rpx;
	flex-shrink: 0;
}

.advantage-content {
	flex: 1;
	display: flex;
	flex-direction: column;
}

.advantage-name {
	font-size: 28rpx;
	font-weight: 600;
	color: $text-main;
}

.advantage-desc {
	font-size: 22rpx;
	color: $text-sub;
	margin-top: 6rpx;
	line-height: 1.4;
}

/* ═══ 联系我们 ═══ */
.contact-section {
	background: #fff;
	margin: 24rpx 24rpx 0;
	border-radius: 28rpx;
	padding: 0 28rpx 28rpx;
	box-shadow: 0 8rpx 28rpx rgba(20, 54, 84, 0.07);
}

.contact-card {
	display: flex;
	flex-direction: column;
	gap: 8rpx;
}

.contact-item {
	display: flex;
	align-items: center;
	padding: 24rpx;
	background: #f8f9fb;
	border-radius: 16rpx;

	&:active {
		background: #f0f4ff;
	}
}

.contact-item-press {
	background: #eaf3ff;
}

.address-item {
	align-items: flex-start;
}

.contact-icon {
	width: 64rpx;
	height: 64rpx;
	border-radius: 16rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-right: 20rpx;
	flex-shrink: 0;

	&.phone {
		background: linear-gradient(135deg, #21b487, #0c938b);
	}

	&.location {
		background: linear-gradient(135deg, #e39b3e, #cd7025);
	}

	&.clock {
		background: linear-gradient(135deg, #5d78a4, #425d89);
	}
}

.contact-info {
	flex: 1;
	display: flex;
	flex-direction: column;
}

.contact-label {
	font-size: 24rpx;
	color: $text-light;
}

.contact-value {
	font-size: 28rpx;
	font-weight: 600;
	color: $text-main;
	margin-top: 4rpx;
	line-height: 1.45;
}

.address-action {
	margin-top: 8rpx;
	color: $primary;
	font-size: 22rpx;
}

/* ═══ 底部 ═══ */
.footer {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 48rpx 32rpx 32rpx;
	gap: 12rpx;
}

.footer-slogan {
	font-size: 26rpx;
	color: $primary;
	font-weight: 600;
	letter-spacing: 4rpx;
}

.footer-copyright {
	font-size: 22rpx;
	color: $text-light;
}

.bottom-placeholder {
	height: 24rpx;
}
</style>
