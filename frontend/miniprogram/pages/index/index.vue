<template>
	<view class="page">
		<!-- 品牌头部：纯代码绘制，避免外部随机图片和域名白名单问题 -->
		<view class="header-section">
			<view class="welcome-row">
				<view class="brand-group">
					<view class="brand-mark">鑫</view>
					<view class="brand-copy">
						<text class="brand-name">鑫立创制冷</text>
						<text class="brand-subtitle">安装 · 售后 · 配件一站式服务</text>
					</view>
				</view>
				<view class="service-pill" hover-class="hover-mask" @click="callService">
					<up-icon name="phone" size="15" color="#ffffff"></up-icon>
					<text>客服</text>
				</view>
			</view>

			<view class="search-bar">
				<view class="search-input-wrap">
					<up-icon name="search" size="17" color="#6f8396"></up-icon>
					<input v-model="searchKeyword" class="search-input" placeholder="搜索铜管、冷媒、支架等配件"
						placeholder-class="search-placeholder" confirm-type="search" @confirm="performSearch" />
					<view v-if="searchKeyword" class="search-clear" @click="clearSearch">
						<up-icon name="close-circle-fill" size="16" color="#a4b0bd"></up-icon>
					</view>
				</view>
				<view class="search-btn" hover-class="hover-mask" @click="performSearch">搜索</view>
			</view>

			<swiper class="hero-swiper" indicator-dots circular autoplay :interval="5000" :duration="450"
				indicator-color="rgba(255,255,255,0.35)" indicator-active-color="#ffffff">
				<swiper-item v-for="item in heroList" :key="item.title">
					<view class="hero-card" :class="item.theme" @click="handleHeroClick(item)">
						<view class="hero-orb orb-one"></view>
						<view class="hero-orb orb-two"></view>
						<view class="hero-copy">
							<text class="hero-eyebrow">{{ item.eyebrow }}</text>
							<text class="hero-title">{{ item.title }}</text>
							<text class="hero-desc">{{ item.desc }}</text>
							<view class="hero-link">
								<text>{{ item.link }}</text>
								<up-icon name="arrow-right" size="13" color="#ffffff"></up-icon>
							</view>
						</view>
						<view class="hero-symbol">
							<up-icon :name="item.icon" size="54" color="rgba(255,255,255,0.94)"></up-icon>
						</view>
					</view>
				</swiper-item>
			</swiper>
		</view>

		<view class="trust-strip">
			<view class="trust-item" v-for="item in trustList" :key="item.text">
				<up-icon :name="item.icon" size="15" color="#0b63ce"></up-icon>
				<text>{{ item.text }}</text>
			</view>
		</view>

		<!-- 高频入口只保留能产生明确结果的功能 -->
		<view class="section-card function-card">
			<view class="section-heading">
				<view>
					<text class="section-title">常用服务</text>
					<text class="section-subtitle">从咨询到售后，一站处理</text>
				</view>
			</view>
			<view class="function-grid">
				<view class="grid-item" hover-class="hover-press" :hover-stay-time="80"
					v-for="item in functionList" :key="item.title" @click="handleItemClick(item)">
					<view class="icon-block" :class="`tone-${item.tone}`">
						<up-icon :name="item.icon" size="23" :color="item.color"></up-icon>
					</view>
					<text class="grid-label">{{ item.title }}</text>
					<text class="grid-tip">{{ item.tip }}</text>
				</view>
			</view>
		</view>

		<!-- 配件商城 -->
		<view id="product-section" class="section-card content-card">
			<view class="section-heading product-heading">
				<view>
					<text class="section-title">常用配件</text>
					<text class="section-subtitle">正品直供 · 价格透明</text>
				</view>
				<text class="result-tip" v-if="activeKeyword">“{{ activeKeyword }}”的结果</text>
				<text class="swipe-hint" v-else>‹ 左右滑动 ›</text>
			</view>

			<scroll-view scroll-x class="tab-scroll" :show-scrollbar="false" scroll-with-animation
				:scroll-into-view="activeTabId" enable-flex enhanced>
				<view class="tab-list">
					<view class="tab-item" v-for="(tab, index) in tabList" :key="tab.type"
						:id="`product-tab-${index}`" :class="{ active: currentTab === index }"
						hover-class="tab-pressed" :hover-stay-time="70" @click="switchTab(index)">
						<text>{{ tab.name }}</text>
					</view>
				</view>
			</scroll-view>

			<view class="product-grid" v-if="listLoading && displayList.length === 0">
				<view class="skeleton-card" v-for="i in 4" :key="i">
					<view class="skeleton-img"></view>
					<view class="skeleton-body">
						<view class="skeleton-line long"></view>
						<view class="skeleton-line short"></view>
					</view>
				</view>
			</view>

			<view class="product-grid" v-else>
				<view class="product-card" hover-class="hover-card" :hover-stay-time="80"
					v-for="(item, index) in displayList" :key="item.id || index" @click="handleCardClick(item)">
					<view class="product-visual" :class="`visual-${item.type || 'aux'}`">
						<view class="visual-ring ring-one"></view>
						<view class="visual-ring ring-two"></view>
						<up-icon :name="productIcon(item.type)" size="40" color="#ffffff"></up-icon>
						<text class="visual-label">{{ item.category || categoryName(item.type) }}</text>
						<view class="product-tag" :style="{ background: item.tagColor }" v-if="item.tag">{{ item.tag }}</view>
					</view>
					<view class="product-body">
						<text class="product-title">{{ item.title }}</text>
						<text class="product-spec">{{ item.spec || item.desc }}</text>
						<view class="product-meta">
							<text class="product-price">产品展示</text>
							<text class="product-sales">{{ item.stock > 0 ? '可咨询' : '库存待确认' }}</text>
						</view>
					</view>
				</view>

				<view class="empty-state" v-if="displayList.length === 0 && !listLoading">
					<view class="empty-icon"><up-icon name="search" size="30" color="#7e91a4"></up-icon></view>
					<text class="empty-title">没有找到相关配件</text>
					<text class="empty-text">换个关键词或分类试试</text>
					<view class="empty-action" @click="clearSearch">查看全部配件</view>
				</view>
			</view>

			<view class="load-status" v-if="displayList.length > 0">
				<view class="loading-more" v-if="loadStatus === 'loading'">
					<view class="loading-dot"></view><view class="loading-dot"></view><view class="loading-dot"></view>
					<text class="load-text">加载中</text>
				</view>
				<view class="load-end" v-else-if="loadStatus === 'noMore'">
					<view class="end-line"></view><text class="load-text">已展示全部</text><view class="end-line"></view>
				</view>
			</view>
		</view>

		<view class="safe-area"></view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { onReachBottom, onPullDownRefresh, onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { consumablesApi } from '@/api/api.js'

onMounted(() => fetchList(true))

onShareAppMessage(() => ({ title: '鑫立创 — 专业空调安装与配件直供', path: '/pages/index/index' }))
onShareTimeline(() => ({ title: '鑫立创 — 专业空调安装与配件直供' }))

const heroList = [
	{ eyebrow: '专业暖通服务', title: '舒适，不止于冷暖', desc: '水系统中央空调安装、维修与保养', link: '立即咨询', icon: 'home-fill', theme: 'hero-blue', action: 'service' },
	{ eyebrow: '常用耗材直供', title: '配件透明，选购放心', desc: '铜管、冷媒、支架等常用配件', link: '查看配件', icon: 'shopping-cart-fill', theme: 'hero-teal', action: 'shop' },
	{ eyebrow: '全流程服务保障', title: '安装售后，一站负责', desc: '持证上岗 · 规范施工 · 快速响应', link: '了解服务', icon: 'server-fill', theme: 'hero-navy', action: 'official' }
]

const trustList = [
	{ icon: 'checkmark-circle-fill', text: '品牌授权' },
	{ icon: 'account-fill', text: '持证上岗' },
	{ icon: 'server-fill', text: '售后保障' }
]

const functionList = [
	{ title: '预约安装', tip: '专业施工', icon: 'calendar', tone: 'blue', color: '#0b63ce', action: 'service' },
	{ title: '快速报修', tip: '及时响应', icon: 'setting-fill', tone: 'red', color: '#dc5b62', action: 'service' },
	{ title: '配件选购', tip: '正品直供', icon: 'shopping-cart', tone: 'cyan', color: '#0f9b91', action: 'shop' },
	{ title: '清洗保养', tip: '节能健康', icon: 'reload', tone: 'green', color: '#189566', action: 'service' },
	{ title: '我的订单', tip: '进度可查', icon: 'order', tone: 'blue', color: '#0b63ce', action: 'order' },
	{ title: '服务保障', tip: '售后无忧', icon: 'server-fill', tone: 'amber', color: '#d47a18', action: 'official' },
	{ title: '空调知识', tip: '专业指南', icon: 'file-text-fill', tone: 'purple', color: '#7459c7', action: 'notice' },
	{ title: '联系客服', tip: '电话咨询', icon: 'phone-fill', tone: 'cyan', color: '#0f9b91', action: 'phone' }
]

const searchKeyword = ref('')
const activeKeyword = ref('')
const tabList = [
	{ name: '全部', type: 'all' }, { name: '铜管', type: 'copper' }, { name: '支架', type: 'bracket' },
	{ name: '电缆线', type: 'cable' }, { name: '辅材', type: 'aux' }, { name: '冷媒', type: 'refrigerant' }
]
const currentTab = ref(0)
const activeTabId = computed(() => `product-tab-${currentTab.value}`)
const allList = ref([])
const page = ref(1)
const pageSize = 6
const total = ref(0)
const listLoading = ref(false)
const loadStatus = ref('')
const displayList = computed(() => allList.value)

const performSearch = () => {
	activeKeyword.value = searchKeyword.value.trim()
	fetchList(true)
	uni.pageScrollTo({ selector: '#product-section', duration: 280 })
}

const clearSearch = () => {
	const hadKeyword = Boolean(activeKeyword.value || searchKeyword.value)
	searchKeyword.value = ''
	activeKeyword.value = ''
	if (hadKeyword) fetchList(true)
}

const switchTab = (index) => {
	if (currentTab.value === index) return
	currentTab.value = index
	fetchList(true)
}

const fetchList = async (isRefresh = false) => {
	if (listLoading.value) return
	if (isRefresh) {
		page.value = 1
		allList.value = []
		loadStatus.value = ''
	} else if (total.value > 0 && allList.value.length >= total.value) {
		loadStatus.value = 'noMore'
		return
	}

	listLoading.value = true
	loadStatus.value = 'loading'
	try {
		const tab = tabList[currentTab.value]
		const params = { page: page.value, pageSize }
		if (tab.type !== 'all') params.category = tab.type
		if (activeKeyword.value) params.keyword = activeKeyword.value
		const res = await consumablesApi.getList(params)
		if (res.code !== 200) {
			loadStatus.value = ''
			return
		}
		const list = (res.data && res.data.list) || []
		total.value = (res.data && res.data.total) || 0
		allList.value = isRefresh ? list : [...allList.value, ...list]
		page.value++
		loadStatus.value = allList.value.length >= total.value ? 'noMore' : ''
	} catch (err) {
		loadStatus.value = ''
	} finally {
		listLoading.value = false
	}
}

onReachBottom(() => {
	if (loadStatus.value === 'noMore' || listLoading.value) return
	fetchList()
})

onPullDownRefresh(async () => {
	await fetchList(true)
	uni.stopPullDownRefresh()
	uni.showToast({ title: '已刷新', icon: 'none', duration: 1000 })
})

const handleHeroClick = (item) => handleAction(item.action, item.title)
const handleItemClick = (item) => handleAction(item.action, item.title)

const handleAction = (action, title) => {
	if (action === 'shop') {
		uni.pageScrollTo({ selector: '#product-section', duration: 320 })
		return
	}
	if (action === 'order') return uni.switchTab({ url: '/pages/order/order' })
	if (action === 'notice') return uni.switchTab({ url: '/pages/notice/notice' })
	if (action === 'official') return uni.switchTab({ url: '/pages/official/official' })
	if (action === 'phone') return callService()
	uni.showActionSheet({
		itemList: [`电话咨询${title || '服务'}`, '查看服务保障'],
		success: (res) => res.tapIndex === 0 ? callService() : uni.switchTab({ url: '/pages/official/official' })
	})
}

const callService = () => {
	uni.showActionSheet({
		itemList: ['027-82710326', '027-82710380'],
		success: (res) => {
			const phones = ['02782710326', '02782710380']
			uni.makePhoneCall({ phoneNumber: phones[res.tapIndex] })
		}
	})
}

const productIcon = (type) => ({
	copper: 'integral', bracket: 'grid-fill', cable: 'share-fill', refrigerant: 'hourglass-half-fill', aux: 'bag-fill'
}[type] || 'bag-fill')

const categoryName = (type) => ({
	copper: '铜管类', bracket: '支架类', cable: '电缆线类', refrigerant: '冷媒类', aux: '辅材类'
}[type] || '空调配件')

const handleCardClick = (item) => uni.navigateTo({ url: `/packageA/goos-details/goos-details?id=${item.id}` })
</script>

<style scoped lang="scss">
@import '@/uni.scss';

.page { min-height: 100vh; background: $bg-page; padding-bottom: 32rpx; }

.header-section {
	padding: 20rpx 24rpx 72rpx;
	background: linear-gradient(180deg, #0b63ce 0%, #126fda 68%, #f4f7fb 100%);
}
.welcome-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20rpx; }
.brand-group { display: flex; align-items: center; min-width: 0; }
.brand-mark {
	width: 64rpx; height: 64rpx; border-radius: 18rpx; display: flex; align-items: center; justify-content: center;
	background: rgba(255,255,255,.16); border: 1rpx solid rgba(255,255,255,.34); color: #fff; font-size: 30rpx; font-weight: 800;
}
.brand-copy { display: flex; flex-direction: column; margin-left: 16rpx; }
.brand-name { color: #fff; font-size: 30rpx; font-weight: 700; line-height: 1.2; }
.brand-subtitle { color: rgba(255,255,255,.72); font-size: 20rpx; margin-top: 5rpx; }
.service-pill {
	display: flex; align-items: center; gap: 7rpx; height: 54rpx; padding: 0 18rpx; border-radius: 27rpx;
	background: rgba(255,255,255,.16); border: 1rpx solid rgba(255,255,255,.24); color: #fff; font-size: 23rpx;
}

.search-bar { display: flex; align-items: center; margin-bottom: 22rpx; }
.search-input-wrap {
	flex: 1; height: 72rpx; padding: 0 22rpx; background: #fff; border-radius: 20rpx; display: flex; align-items: center;
	box-shadow: 0 8rpx 24rpx rgba(5,57,119,.12);
}
.search-input { flex: 1; height: 72rpx; margin-left: 12rpx; font-size: 26rpx; color: $text-main; }
.search-placeholder { color: #9aa8b6; }
.search-clear { padding: 12rpx 0 12rpx 18rpx; }
.search-btn { color: #fff; font-size: 26rpx; font-weight: 600; padding: 18rpx 4rpx 18rpx 22rpx; }

.hero-swiper { height: 292rpx; }
.hero-card {
	height: 262rpx; box-sizing: border-box; border-radius: 28rpx; padding: 32rpx; margin: 0 2rpx;
	position: relative; overflow: hidden; display: flex; box-shadow: 0 16rpx 36rpx rgba(6,44,92,.22);
}
.hero-blue { background: linear-gradient(135deg, #173b67, #0b63ce 58%, #48a5ef); }
.hero-teal { background: linear-gradient(135deg, #0b4d59, #0c8b86 58%, #3bc5ae); }
.hero-navy { background: linear-gradient(135deg, #14283d, #224e76 58%, #357ba4); }
.hero-copy { position: relative; z-index: 2; width: 76%; display: flex; flex-direction: column; }
.hero-eyebrow { font-size: 21rpx; color: rgba(255,255,255,.76); letter-spacing: 2rpx; }
.hero-title { color: #fff; font-size: 38rpx; font-weight: 800; margin-top: 12rpx; letter-spacing: 1rpx; }
.hero-desc { color: rgba(255,255,255,.78); font-size: 23rpx; margin-top: 12rpx; }
.hero-link { display: flex; align-items: center; gap: 5rpx; margin-top: auto; color: #fff; font-size: 23rpx; font-weight: 600; }
.hero-symbol {
	position: absolute; right: 34rpx; top: 62rpx; width: 116rpx; height: 116rpx; border-radius: 34rpx;
	background: rgba(255,255,255,.12); border: 1rpx solid rgba(255,255,255,.22); display: flex; align-items: center; justify-content: center;
	transform: rotate(5deg); z-index: 1;
}
.hero-orb { position: absolute; border-radius: 50%; border: 1rpx solid rgba(255,255,255,.14); }
.orb-one { width: 250rpx; height: 250rpx; right: -90rpx; top: -110rpx; }
.orb-two { width: 180rpx; height: 180rpx; right: 10rpx; bottom: -120rpx; }

.trust-strip {
	margin: -48rpx 24rpx 0; position: relative; z-index: 3; height: 88rpx; border-radius: 22rpx; background: #fff;
	display: flex; align-items: center; box-shadow: $shadow-card;
}
.trust-item { flex: 1; display: flex; align-items: center; justify-content: center; gap: 8rpx; font-size: 22rpx; color: $text-sub; }
.trust-item + .trust-item { border-left: 1rpx solid #edf1f5; }

.section-card { margin: 24rpx 24rpx 0; border-radius: 28rpx; background: #fff; box-shadow: $shadow-card; overflow: hidden; }
.section-heading { display: flex; align-items: flex-end; justify-content: space-between; padding: 30rpx 28rpx 18rpx; }
.section-heading > view:first-child { display: flex; flex-direction: column; }
.section-title { font-size: 32rpx; line-height: 1.2; font-weight: 750; color: $text-main; }
.section-subtitle { font-size: 21rpx; color: $text-light; margin-top: 8rpx; }

.function-card { padding-bottom: 16rpx; }
.function-grid { display: flex; flex-wrap: wrap; padding: 0 12rpx; }
.grid-item { width: 25%; padding: 14rpx 0 18rpx; display: flex; flex-direction: column; align-items: center; box-sizing: border-box; }
.icon-block { width: 80rpx; height: 80rpx; border-radius: 24rpx; display: flex; align-items: center; justify-content: center; margin-bottom: 13rpx; }
.tone-blue { background: #eaf3ff; }.tone-cyan { background: #e7f8f6; }.tone-red { background: #fff0f1; }
.tone-green { background: #ebf8f1; }.tone-amber { background: #fff5e6; }.tone-purple { background: #f1edfc; }
.grid-label { font-size: 24rpx; color: $text-main; font-weight: 600; }
.grid-tip { font-size: 19rpx; color: $text-light; margin-top: 4rpx; }

.content-card { padding-bottom: 24rpx; }
.product-heading { padding-bottom: 20rpx; }
.result-tip { max-width: 260rpx; font-size: 21rpx; color: $primary; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.swipe-hint { font-size: 20rpx; color: #8b9aaa; letter-spacing: 1rpx; }
.tab-scroll { width: 100%; border-bottom: 1rpx solid #edf1f5; touch-action: pan-x; }
.tab-list { display: inline-flex; min-width: 100%; padding: 0 16rpx; white-space: nowrap; box-sizing: border-box; }
.tab-item {
	flex-shrink: 0; min-width: 128rpx; height: 88rpx; padding: 0 20rpx; box-sizing: border-box;
	display: flex; align-items: center; justify-content: center; font-size: 25rpx; color: $text-sub; position: relative;
}
.tab-pressed { background: #f1f5f9; }
.tab-item.active { color: $primary; font-weight: 700; }
.tab-item.active::after { content: ''; position: absolute; width: 32rpx; height: 5rpx; border-radius: 4rpx; bottom: 5rpx; left: 50%; transform: translateX(-50%); background: $primary; }

.product-grid { display: flex; flex-wrap: wrap; gap: 20rpx; padding: 24rpx 24rpx 0; }
.product-card { width: calc(50% - 10rpx); border: 1rpx solid #edf1f5; border-radius: 22rpx; overflow: hidden; box-sizing: border-box; background: #fff; }
.product-visual { height: 184rpx; position: relative; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.visual-copper { background: linear-gradient(135deg, #b66d3d, #e3a163); }.visual-bracket { background: linear-gradient(135deg, #53697d, #8da3b6); }
.visual-cable { background: linear-gradient(135deg, #35485e, #5e7691); }.visual-refrigerant { background: linear-gradient(135deg, #087a84, #23b6ae); }
.visual-aux { background: linear-gradient(135deg, #426aa3, #75a0d8); }
.visual-ring { position: absolute; border: 1rpx solid rgba(255,255,255,.18); border-radius: 50%; }
.ring-one { width: 180rpx; height: 180rpx; right: -70rpx; top: -80rpx; }.ring-two { width: 120rpx; height: 120rpx; left: -50rpx; bottom: -65rpx; }
.visual-label { position: absolute; left: 16rpx; bottom: 13rpx; font-size: 20rpx; color: rgba(255,255,255,.82); }
.product-tag { position: absolute; top: 12rpx; left: 12rpx; padding: 4rpx 13rpx; border-radius: 8rpx; color: #fff; font-size: 19rpx; font-weight: 600; }
.product-body { padding: 17rpx 17rpx 19rpx; }
.product-title { height: 72rpx; font-size: 26rpx; color: $text-main; font-weight: 650; line-height: 1.4; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
.product-spec { display: block; height: 32rpx; font-size: 20rpx; color: $text-light; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 4rpx; }
.product-meta { display: flex; align-items: flex-end; justify-content: space-between; margin-top: 12rpx; }
.product-price { color: #e25151; font-size: 34rpx; font-weight: 800; line-height: 1; }.price-unit { font-size: 21rpx; margin-right: 2rpx; }
.product-sales { color: $text-light; font-size: 19rpx; }

.skeleton-card { width: calc(50% - 10rpx); border-radius: 22rpx; overflow: hidden; border: 1rpx solid #edf1f5; box-sizing: border-box; }
.skeleton-img { width: 100%; height: 184rpx; }.skeleton-body { padding: 18rpx; }
.skeleton-line { height: 22rpx; margin-bottom: 12rpx; }.skeleton-line.long { width: 100%; }.skeleton-line.short { width: 55%; }

.empty-state { width: 100%; padding: 90rpx 0 70rpx; display: flex; flex-direction: column; align-items: center; }
.empty-icon { width: 88rpx; height: 88rpx; border-radius: 50%; background: #edf3f8; display: flex; align-items: center; justify-content: center; }
.empty-title { font-size: 28rpx; color: $text-main; font-weight: 650; margin-top: 20rpx; }.empty-text { font-size: 23rpx; color: $text-light; margin-top: 8rpx; }
.empty-action { margin-top: 24rpx; padding: 13rpx 30rpx; color: $primary; background: #eaf3ff; border-radius: 28rpx; font-size: 23rpx; }

.load-status { width: 100%; padding: 30rpx 0 8rpx; display: flex; justify-content: center; align-items: center; }
.loading-more, .load-end { display: flex; align-items: center; gap: 9rpx; }
.loading-dot { width: 10rpx; height: 10rpx; border-radius: 50%; background: $primary; animation: bounce 1.4s infinite ease-in-out both; }
.loading-dot:nth-child(1) { animation-delay: -.32s; }.loading-dot:nth-child(2) { animation-delay: -.16s; }
.end-line { width: 48rpx; height: 1rpx; background: #dbe3eb; }.load-text { font-size: 21rpx; color: $text-light; }
@keyframes bounce { 0%,80%,100% { transform: scale(0); } 40% { transform: scale(1); } }
.safe-area { height: calc(20rpx + env(safe-area-inset-bottom)); }
</style>
