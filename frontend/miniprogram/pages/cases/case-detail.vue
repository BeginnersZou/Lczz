<template>
	<view class="page">
		<view v-if="loading" class="state-card">
			<up-loading-icon mode="circle" color="#0b63ce"></up-loading-icon><text>正在加载案例…</text>
		</view>
		<view v-else-if="loadError" class="state-card">
			<up-icon name="warning" size="40" color="#d97706"></up-icon>
			<text class="state-title">案例详情加载失败</text>
			<text class="state-desc">{{ loadError }}</text>
			<view class="retry-button" @click="loadDetail">重新加载</view>
		</view>
		<view v-else-if="caseData" class="content">
			<view class="title-card">
				<text class="eyebrow">项目案例</text>
				<text class="site-name">{{ caseData.siteName }}</text>
				<text class="image-summary">现场图片 {{ caseData.images.length }} 张</text>
			</view>
			<view v-if="caseData.images.length" class="photo-list">
				<view v-for="(image, index) in caseData.images" :key="image.id || image.url" class="photo-card" @click="preview(index)">
					<image v-if="!failedImages[image.url]" class="photo" :src="image.url" mode="widthFix" lazy-load @error="markImageFailed(image.url)"></image>
					<view v-else class="image-error"><up-icon name="photo" size="38" color="#8da1b7"></up-icon><text>图片加载失败</text></view>
				</view>
			</view>
			<view v-else class="empty-images">
				<up-icon name="photo" size="42" color="#94a3b8"></up-icon><text>暂未上传现场图片</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { projectCaseApi } from '@/api/api.js'

const caseId = ref('')
const caseData = ref(null)
const loading = ref(true)
const loadError = ref('')
const failedImages = ref({})

function markImageFailed(url) {
	failedImages.value[url] = true
}

async function loadDetail() {
	if (!caseId.value) {
		loading.value = false
		loadError.value = '未找到案例编号'
		return
	}
	loading.value = true
	loadError.value = ''
	try {
		const response = await projectCaseApi.getDetail(caseId.value)
		if (response.code !== 200) {
			loadError.value = response.msg || '服务暂时不可用，请稍后重试'
			caseData.value = null
			return
		}
		caseData.value = response.data
		uni.setNavigationBarTitle({ title: response.data.siteName || '案例详情' })
	} catch {
		loadError.value = '网络连接失败，请检查网络后重试'
		caseData.value = null
	} finally {
		loading.value = false
	}
}

function preview(index) {
	const urls = caseData.value.images.filter(image => image.url && !failedImages.value[image.url]).map(image => image.url)
	const current = caseData.value.images[index]?.url
	if (current && urls.includes(current)) uni.previewImage({ current, urls })
}

onLoad((options) => {
	caseId.value = options?.id || ''
	loadDetail()
})
onShareAppMessage(() => ({ title: caseData.value?.siteName || '项目案例', path: `/pages/cases/case-detail?id=${caseId.value}` }))
onShareTimeline(() => ({ title: caseData.value?.siteName || '项目案例' }))
</script>

<style scoped lang="scss">
@import '@/uni.scss';
.page { min-height: 100vh; padding: 24rpx; box-sizing: border-box; background: $bg-page; }
.content { display: flex; flex-direction: column; gap: 20rpx; }
.title-card { display: flex; flex-direction: column; padding: 30rpx; border-radius: 20rpx; background: linear-gradient(135deg, #0b63ce, #317ed9); box-shadow: 0 10rpx 24rpx rgba(11, 99, 206, .18); }
.eyebrow { color: rgba(255, 255, 255, .74); font-size: 22rpx; letter-spacing: 2rpx; }
.site-name { margin-top: 12rpx; color: #fff; font-size: 38rpx; font-weight: 700; line-height: 1.35; }
.image-summary { margin-top: 14rpx; color: rgba(255, 255, 255, .83); font-size: 23rpx; }
.photo-list { display: flex; flex-direction: column; gap: 18rpx; }
.photo-card { overflow: hidden; border-radius: 18rpx; background: #fff; box-shadow: 0 5rpx 18rpx rgba(28, 57, 91, .08); }
.photo { display: block; width: 100%; min-height: 220rpx; }
.image-error, .empty-images, .state-card { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16rpx; color: $text-sub; }
.image-error { min-height: 360rpx; background: #eef3f7; font-size: 24rpx; }
.empty-images { min-height: 320rpx; border-radius: 18rpx; background: #fff; font-size: 25rpx; }
.state-card { min-height: 520rpx; padding: 32rpx; text-align: center; font-size: 25rpx; }
.state-title { color: $text-main; font-size: 30rpx; font-weight: 600; }.state-desc { color: $text-sub; font-size: 24rpx; }
.retry-button { margin-top: 12rpx; padding: 14rpx 32rpx; color: #fff; border-radius: 30rpx; background: #0b63ce; font-size: 25rpx; }
</style>
