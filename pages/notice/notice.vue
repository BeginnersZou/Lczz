<template>
	<view class="page">
		<view class="knowledge-banner">
			<view class="banner-copy">
				<text class="banner-kicker">鑫立创 · 暖通知识库</text>
				<text class="banner-title">懂空调，才能用得更舒适</text>
				<text class="banner-desc">安装避坑、节能技巧与日常保养指南</text>
			</view>
			<view class="banner-symbol"><up-icon name="file-text-fill" size="42" color="#ffffff"></up-icon></view>
		</view>

		<view class="list-heading">
			<view><text class="heading-title">精选资讯</text><text class="heading-count">持续更新专业内容</text></view>
			<view class="heading-badge"><view class="badge-dot"></view><text>专业建议</text></view>
		</view>

		<!-- 动态列表 -->
		<view class="news-list">
			<view class="news-card" hover-class="hover-card" :hover-stay-time="80" v-for="(item, index) in newsList" :key="item.id || index" @click="goDetail(item)">
				<view class="news-content">
					<text class="news-category">{{ articleTag(item) }}</text>
					<text class="news-title">{{ item.title }}</text>
					<text class="news-desc">{{ item.desc }}</text>
					<view class="news-meta">
						<text class="news-date">{{ item.date }}</text>
						<text class="news-views">阅读 {{ item.views }}</text>
					</view>
				</view>
				<view class="news-img" :class="`visual-${index % 4}`">
					<view class="visual-ring"></view>
					<up-icon :name="articleIcon(item)" size="34" color="#ffffff"></up-icon>
					<text>{{ articleShort(item) }}</text>
				</view>
			</view>
		</view>

		<!-- 骨架屏 -->
		<view class="news-list" v-if="listLoading && newsList.length === 0">
			<view class="news-card" hover-class="hover-card" :hover-stay-time="80" v-for="i in 4" :key="i">
				<view class="news-content">
					<view class="skeleton-line long"></view>
					<view class="skeleton-line short"></view>
					<view class="skeleton-line mini"></view>
				</view>
				<view class="skeleton-img"></view>
			</view>
		</view>

		<!-- 触底加载状态 -->
		<view class="load-status" v-if="newsList.length > 0">
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

		<!-- 空状态 -->
		<view class="empty-state" v-if="newsList.length === 0 && !listLoading">
			<up-icon name="empty-news" size="70" color="#9aa8b6"></up-icon>
			<text class="empty-text">暂无相关资讯</text>
		</view>


	</view>
</template>

<script setup>
import {
	ref,
	onMounted
} from 'vue'
import {
	onReachBottom,
	onPullDownRefresh,
	onShareAppMessage
} from '@dcloudio/uni-app'
import { dynamicApi } from '@/api/api.js'

// 分享给好友
onShareAppMessage(() => ({
	title: '鑫立创 — 平台公告与资讯',
	path: '/pages/notice/notice'
}))

const newsList = ref([])
const page = ref(1)
const pageSize = 8
const total = ref(0)
const listLoading = ref(false)
const loadStatus = ref('')

const fetchList = async (isRefresh = false) => {
	if (listLoading.value) return
	if (isRefresh) {
		page.value = 1
		newsList.value = []
		loadStatus.value = ''
	} else if (total.value > 0 && newsList.value.length >= total.value) {
		loadStatus.value = 'noMore'
		return
	}

	listLoading.value = true
	loadStatus.value = 'loading'

	try {
		const res = await dynamicApi.getList({ page: page.value, pageSize })
		if (res.code !== 200) {
			loadStatus.value = ''
			return
		}
		const list = (res.data && res.data.list) || []
		total.value = (res.data && res.data.total) || 0
		newsList.value = isRefresh ? list : [...newsList.value, ...list]
		page.value++
		loadStatus.value = newsList.value.length >= total.value ? 'noMore' : ''
	} catch (err) {
		// request.js 已统一处理错误提示
		loadStatus.value = ''
	} finally {
		listLoading.value = false
	}
}

onMounted(() => {
	fetchList(true)
})

onReachBottom(() => {
	if (loadStatus.value === 'noMore' || listLoading.value) return
	fetchList()
})

onPullDownRefresh(async () => {
	await fetchList(true)
	uni.stopPullDownRefresh()
	uni.showToast({ title: '刷新成功', icon: 'none', duration: 1000 })
})

const goDetail = (item) => {
	uni.navigateTo({
		url: `/packageA/notices-detail/notices-detail?id=${item.id}`
	})
}

const articleTag = (item) => {
	const title = item.title || ''
	if (title.includes('鑫立创') || title.includes('授权')) return '企业动态'
	if (title.includes('安装') || title.includes('选择') || title.includes('匹数')) return '选购安装'
	if (title.includes('故障') || title.includes('漏水') || title.includes('噪音')) return '故障排查'
	return '使用保养'
}

const articleIcon = (item) => {
	const tag = articleTag(item)
	if (tag === '企业动态') return 'server-fill'
	if (tag === '选购安装') return 'home-fill'
	if (tag === '故障排查') return 'setting-fill'
	return 'checkmark-circle-fill'
}

const articleShort = (item) => articleTag(item).slice(0, 2)
</script>

<style scoped lang="scss">
// 页面里直接用
@import '@/uni.scss';
$primary: #0b63ce;
$bg: #f4f7fb;
$text-main: #142434;
$text-sub: #64748b;
$text-light: #94a3b8;

.page {
	min-height: 100vh;
	background: $bg;
	padding-bottom: calc(100rpx + env(safe-area-inset-bottom));
}

.knowledge-banner {
	margin: 22rpx 24rpx 0;
	padding: 32rpx;
	height: 190rpx;
	box-sizing: border-box;
	border-radius: 28rpx;
	background: linear-gradient(135deg, #0a355f 0%, #0b63ce 68%, #3199e6 100%);
	display: flex;
	align-items: center;
	position: relative;
	overflow: hidden;
	box-shadow: 0 12rpx 30rpx rgba(11,74,145,.16);
}
.knowledge-banner::after { content: ''; position: absolute; width: 230rpx; height: 230rpx; border-radius: 50%; border: 1rpx solid rgba(255,255,255,.13); right: -80rpx; top: -120rpx; }
.banner-copy { display: flex; flex-direction: column; position: relative; z-index: 2; }.banner-kicker { color: rgba(255,255,255,.68); font-size: 19rpx; letter-spacing: 2rpx; }
.banner-title { color: #fff; font-size: 31rpx; font-weight: 750; margin-top: 10rpx; }.banner-desc { color: rgba(255,255,255,.72); font-size: 21rpx; margin-top: 10rpx; }
.banner-symbol { margin-left: auto; width: 92rpx; height: 92rpx; border-radius: 28rpx; background: rgba(255,255,255,.14); border: 1rpx solid rgba(255,255,255,.2); display: flex; align-items: center; justify-content: center; transform: rotate(4deg); }
.list-heading { display: flex; align-items: center; justify-content: space-between; padding: 34rpx 28rpx 4rpx; }
.list-heading > view:first-child { display: flex; flex-direction: column; }.heading-title { color: $text-main; font-size: 32rpx; font-weight: 750; }.heading-count { color: $text-light; font-size: 21rpx; margin-top: 6rpx; }
.heading-badge { display: flex; align-items: center; padding: 9rpx 15rpx; border-radius: 22rpx; background: #e7f8f6; color: #0f8f85; font-size: 19rpx; }.badge-dot { width: 9rpx; height: 9rpx; border-radius: 50%; background: #17ae9e; margin-right: 7rpx; }

.news-list {
	padding: 18rpx 24rpx 0;
}

.news-card {
	display: flex;
	background: #fff;
	border-radius: 24rpx;
	padding: 24rpx;
	margin-bottom: 20rpx;
	box-shadow: 0 8rpx 26rpx rgba(20, 54, 84, 0.06);

	&:active {
		background: #f8f9fb;
	}
}

.news-content {
	flex: 1;
	display: flex;
	flex-direction: column;
	justify-content: space-between;
	margin-right: 20rpx;
	min-height: 168rpx;
}

.news-category { align-self: flex-start; padding: 5rpx 12rpx; border-radius: 8rpx; background: #eaf3ff; color: $primary; font-size: 18rpx; margin-bottom: 8rpx; }

.news-title {
	font-size: 30rpx;
	font-weight: 600;
	color: $text-main;
	line-height: 1.4;
	display: -webkit-box;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 2;
	overflow: hidden;
}

.news-desc {
	font-size: 24rpx;
	color: $text-sub;
	margin-top: 8rpx;
	line-height: 1.5;
	display: -webkit-box;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 2;
	overflow: hidden;
}

.news-meta {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 12rpx;
}

.news-date {
	font-size: 22rpx;
	color: $text-light;
}

.news-views {
	font-size: 22rpx;
	color: $text-light;
}

.news-img {
	width: 164rpx;
	height: 164rpx;
	border-radius: 22rpx;
	flex-shrink: 0;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	position: relative;
	overflow: hidden;
	color: rgba(255,255,255,.78);
	font-size: 19rpx;
	gap: 7rpx;
}
.visual-0 { background: linear-gradient(145deg, #0b63ce, #49a3e7); }.visual-1 { background: linear-gradient(145deg, #0b756f, #38b9a8); }
.visual-2 { background: linear-gradient(145deg, #52657a, #8ba0b4); }.visual-3 { background: linear-gradient(145deg, #805d33, #d69543); }
.visual-ring { position: absolute; width: 140rpx; height: 140rpx; border-radius: 50%; border: 1rpx solid rgba(255,255,255,.17); right: -65rpx; top: -62rpx; }

/* 骨架屏 */
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

	&.short {
		width: 60%;
	}

	&.mini {
		width: 40%;
	}
}

.skeleton-img {
	width: 180rpx;
	height: 140rpx;
	border-radius: 12rpx;
	flex-shrink: 0;
	background: linear-gradient(90deg, #f0f1f3 25%, #e6e7eb 50%, #f0f1f3 75%);
	background-size: 200% 100%;
	animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
	0% {
		background-position: 200% 0;
	}

	100% {
		background-position: -200% 0;
	}
}

/* 加载状态 */
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

/* 空状态 */
.empty-state {
	display: flex;
	flex-direction: column;
	align-items: center;
	padding: 120rpx 0;
}

.empty-text {
	font-size: 28rpx;
	color: $text-light;
	margin-top: 20rpx;
}
</style>
