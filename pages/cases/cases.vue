<template>
	<view class="page">
		<view class="page-head">
			<text class="page-title">项目案例</text>
			<text class="page-desc">记录每一处认真完成的施工现场</text>
		</view>

		<view v-if="loading && !cases.length" class="case-grid">
			<view v-for="index in 4" :key="index" class="skeleton-card">
				<view class="skeleton-image"></view><view class="skeleton-line"></view>
			</view>
		</view>

		<view v-else-if="loadError && !cases.length" class="state-card">
			<up-icon name="warning" size="38" color="#d97706"></up-icon>
			<text class="state-title">案例加载失败</text>
			<text class="state-desc">{{ loadError }}</text>
			<view class="retry-button" @click="loadCases(true)">重新加载</view>
		</view>

		<view v-else-if="!cases.length" class="state-card">
			<up-icon name="photo" size="42" color="#94a3b8"></up-icon>
			<text class="state-title">暂未发布项目案例</text>
			<text class="state-desc">后续将展示更多施工现场</text>
		</view>

		<view v-else class="case-grid">
			<view v-for="item in cases" :key="item.id" class="case-card" hover-class="case-card-press" @click="openDetail(item)">
				<view class="cover-wrap">
					<image v-if="item.coverImage && !failedImages[item.id]" class="cover-image" :src="item.coverImage"
						mode="aspectFill" lazy-load @error="markImageFailed(item.id)"></image>
					<view v-else class="cover-fallback">
						<up-icon name="photo" size="40" color="#8da1b7"></up-icon>
						<text>{{ item.coverImage ? '图片加载失败' : '暂无图片' }}</text>
					</view>
					<view class="image-count"><up-icon name="photo" size="12" color="#fff"></up-icon><text>{{ item.images.length }} 图</text></view>
				</view>
				<view class="case-info">
					<text class="case-name">{{ item.siteName }}</text>
					<text class="case-link">查看现场 <up-icon name="arrow-right" size="13" color="#0b63ce"></up-icon></text>
				</view>
			</view>
		</view>

		<view v-if="cases.length" class="load-state">
			<text v-if="loading">加载中…</text>
			<text v-else-if="loadError" class="retry-text" @click="loadCases(false)">{{ loadError }}，点击重试</text>
			<text v-else-if="noMore">已展示全部案例</text>
		</view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { projectCaseApi } from '@/api/api.js'

const cases = ref([])
const failedImages = ref({})
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const total = ref(0)
const pageSize = 12
const noMore = ref(false)

function markImageFailed(id) {
	failedImages.value[id] = true
}

function setLoadError(response) {
	if (response?.code === -1) return '网络连接失败，请检查网络后重试'
	return response?.msg || '服务暂时不可用，请稍后重试'
}

async function loadCases(refresh = false) {
	if (loading.value) return
	if (refresh) {
		page.value = 1
		total.value = 0
		cases.value = []
		noMore.value = false
		failedImages.value = {}
	}
	if (noMore.value) return
	loading.value = true
	loadError.value = ''
	try {
		const response = await projectCaseApi.getList({ page: page.value, pageSize })
		if (response.code !== 200) {
			loadError.value = setLoadError(response)
			return
		}
		const list = response.data?.list || []
		total.value = Number(response.data?.total || 0)
		cases.value = refresh ? list : [...cases.value, ...list]
		page.value += 1
		noMore.value = cases.value.length >= total.value
	} catch {
		loadError.value = '网络连接失败，请检查网络后重试'
	} finally {
		loading.value = false
	}
}

function openDetail(item) {
	uni.navigateTo({ url: `/pages/cases/case-detail?id=${item.id}` })
}

onLoad(() => loadCases(true))
onReachBottom(() => {
	if (!loading.value && !noMore.value) loadCases(false)
})
onPullDownRefresh(async () => {
	await loadCases(true)
	uni.stopPullDownRefresh()
})
</script>

<style scoped lang="scss">
@import '@/uni.scss';
.page { min-height: 100vh; padding: 28rpx 24rpx 48rpx; box-sizing: border-box; background: $bg-page; }
.page-head { margin: 8rpx 4rpx 26rpx; display: flex; flex-direction: column; }
.page-title { color: $text-main; font-size: 40rpx; font-weight: 700; }
.page-desc { margin-top: 10rpx; color: $text-sub; font-size: 25rpx; }
.case-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20rpx; }
.case-card { overflow: hidden; border-radius: 20rpx; background: #fff; box-shadow: 0 6rpx 20rpx rgba(28, 57, 91, .08); }
.case-card-press { transform: scale(.98); opacity: .88; }
.cover-wrap { position: relative; height: 230rpx; background: #e9eff5; }
.cover-image { width: 100%; height: 100%; }
.cover-fallback { width: 100%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12rpx; color: #8296aa; font-size: 22rpx; }
.image-count { position: absolute; right: 12rpx; bottom: 12rpx; display: flex; align-items: center; gap: 5rpx; padding: 6rpx 10rpx; border-radius: 18rpx; color: #fff; font-size: 20rpx; background: rgba(15, 23, 42, .65); }
.case-info { padding: 18rpx 18rpx 20rpx; }
.case-name { display: block; overflow: hidden; color: $text-main; font-size: 27rpx; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.case-link { display: flex; align-items: center; gap: 2rpx; margin-top: 12rpx; color: #0b63ce; font-size: 22rpx; }
.state-card { min-height: 430rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16rpx; padding: 32rpx; background: #fff; border-radius: 20rpx; }
.state-title { color: $text-main; font-size: 30rpx; font-weight: 600; }
.state-desc { color: $text-sub; font-size: 24rpx; text-align: center; }
.retry-button { margin-top: 12rpx; padding: 14rpx 32rpx; color: #fff; border-radius: 30rpx; background: #0b63ce; font-size: 25rpx; }
.load-state { padding: 30rpx 0 10rpx; color: $text-sub; font-size: 23rpx; text-align: center; }
.retry-text { color: #0b63ce; }
.skeleton-card { overflow: hidden; padding-bottom: 18rpx; border-radius: 20rpx; background: #fff; }
.skeleton-image, .skeleton-line { background: linear-gradient(90deg, #edf2f7 25%, #f6f8fb 37%, #edf2f7 63%); background-size: 400% 100%; animation: shimmer 1.3s infinite; }
.skeleton-image { height: 230rpx; }.skeleton-line { height: 25rpx; margin: 18rpx; border-radius: 8rpx; }
@keyframes shimmer { 0% { background-position: 100% 0; } 100% { background-position: -100% 0; } }
</style>
