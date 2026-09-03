<template>
	<view class="page">
		<view class="state-card" v-if="loading">
			<up-loading-icon mode="circle" color="#0b63ce" size="28"></up-loading-icon>
			<text>正在加载订单评价...</text>
		</view>
		<view class="state-card" v-else-if="loadFailed">
			<up-icon name="reload" size="34" color="#94a3b8"></up-icon>
			<text>评价信息加载失败</text>
			<view class="retry-btn" @click="loadPage"><text>重新加载</text></view>
		</view>
		<template v-else>
		<view class="order-card">
			<view class="order-icon"><up-icon name="home-fill" size="30" color="#0b63ce"></up-icon></view>
			<view class="order-info">
				<text class="order-name">{{ orderInfo.serviceName || '空调服务' }}</text>
				<text class="order-product">{{ orderInfo.productName || orderInfo.description }}</text>
				<text class="order-no">订单编号：{{ orderInfo.orderNo }}</text>
			</view>
		</view>

		<template v-if="submitted">
			<view class="section-card submission-success">
				<view class="success-icon"><up-icon name="checkmark" size="34" color="#16a34a"></up-icon></view>
				<text class="success-title">评价已提交</text>
				<text class="success-desc">感谢您的真实反馈，评价内容已交由管理员查看</text>
				<view class="success-privacy">
					<up-icon name="lock" size="16" color="#64748b"></up-icon>
					<text>评价内容仅管理员可见，安装师傅不可见</text>
				</view>
			</view>
		</template>

		<template v-else>
			<view class="privacy-notice">
				<view class="privacy-icon"><up-icon name="lock" size="20" color="#0b63ce"></up-icon></view>
				<view class="privacy-copy">
					<text class="privacy-title">请对该师傅做出真实的评价</text>
					<text class="privacy-desc">评价内容仅管理员可见，安装师傅不可见</text>
				</view>
			</view>
			<view class="section-card">
				<view class="section-title required-title">服务评分</view>
				<view class="star-row">
					<view class="star-item" v-for="index in 5" :key="index" @click="score = index">
						<up-icon :name="index <= score ? 'star-fill' : 'star'" size="48"
							:color="index <= score ? '#f59e0b' : '#cbd5e1'"></up-icon>
					</view>
				</view>
				<text class="score-label">{{ scoreLabels[score - 1] }}</text>
				<view class="like-row" @click="liked = !liked">
					<up-icon :name="liked ? 'thumb-up-fill' : 'thumb-up'" size="22" :color="liked ? '#0b63ce' : '#64748b'"></up-icon>
					<text :class="{ active: liked }">愿意推荐本次服务</text>
				</view>
			</view>

			<view class="section-card">
				<view class="section-title-wrap">
					<text class="section-title">服务标签</text>
					<text class="section-tip">最多选择5个</text>
				</view>
				<view class="label-list">
					<text class="label-item" v-for="label in availableLabels" :key="label"
						:class="{ active: labels.includes(label) }" @click="toggleLabel(label)">{{ label }}</text>
				</view>
			</view>

			<view class="section-card">
				<view class="section-title required-title">评价内容</view>
				<view class="textarea-wrap">
					<textarea class="evaluate-textarea" v-model="content" placeholder="请描述本次安装服务体验"
						placeholder-class="placeholder-style" maxlength="2000"></textarea>
					<text class="text-count">{{ content.length }}/2000</text>
				</view>
			</view>

			<view class="section-card">
				<view class="section-title-wrap">
					<text class="section-title">评价图片</text>
					<text class="section-tip">选填，最多9张</text>
				</view>
				<view class="image-grid">
					<view class="image-item" v-for="(image, index) in images" :key="image.id">
						<image class="preview-img" :src="image.url" mode="aspectFill" @click="previewImages(index)"></image>
						<view class="image-delete" @click.stop="removeImage(index)"><up-icon name="close" size="12" color="#fff"></up-icon></view>
					</view>
					<view class="image-add" v-if="images.length < 9" @click="chooseImages">
						<up-icon name="plus" size="30" color="#94a3b8"></up-icon>
						<text>上传图片</text>
					</view>
				</view>
				<text class="upload-progress" v-if="uploadProgress">{{ uploadProgress }}</text>
			</view>

			<view class="bottom-space"></view>
			<view class="submit-bar">
				<view class="submit-btn" :class="{ disabled: !canSubmit || submitting }" @click="submitEvaluation">
					<text>{{ submitting ? '提交中...' : '提交评价' }}</text>
				</view>
			</view>
		</template>
		</template>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onBackPress, onLoad } from '@dcloudio/uni-app'
import { authApi, evaluationApi, orderApi, uploadApi } from '@/api/api.js'
import { requireLogin } from '@/utils/auth-guard.js'

const orderId = ref('')
const orderInfo = ref({})
const submitted = ref(false)
const score = ref(5)
const liked = ref(true)
const content = ref('')
const labels = ref([])
const images = ref([])
const submitting = ref(false)
const uploadProgress = ref('')
const allowLeave = ref(false)
const loading = ref(true)
const loadFailed = ref(false)
const scoreLabels = ['非常差', '较差', '一般', '推荐', '超赞']
const availableLabels = ['准时上门', '服务专业', '沟通顺畅', '现场整洁', '安装细致', '响应及时']

const canSubmit = computed(() => score.value >= 1 && score.value <= 5 && Boolean(content.value.trim()))
const hasDraft = computed(() => Boolean(content.value.trim()) || labels.value.length > 0 || images.value.length > 0 || score.value !== 5 || !liked.value)

const toggleLabel = (label) => {
	if (labels.value.includes(label)) {
		labels.value = labels.value.filter(item => item !== label)
		return
	}
	if (labels.value.length >= 5) {
		uni.showToast({ title: '评价标签最多选择5个', icon: 'none' })
		return
	}
	labels.value.push(label)
}

const previewImages = (index) => {
	const urls = images.value.map(image => image.url)
	uni.previewImage({ current: urls[index], urls })
}

const removeImage = (index) => images.value.splice(index, 1)

const chooseImages = () => {
	const remaining = 9 - images.value.length
	if (remaining <= 0) return
	uni.chooseImage({
		count: remaining,
		sizeType: ['compressed'],
		sourceType: ['album', 'camera'],
		success: async ({ tempFilePaths = [] }) => {
			let failed = 0
			for (let index = 0; index < tempFilePaths.length; index++) {
				uploadProgress.value = `正在上传 ${index + 1}/${tempFilePaths.length}`
				const result = await uploadApi.uploadImage(tempFilePaths[index], {}, {
					loading: false,
					onProgress: event => {
						uploadProgress.value = `正在上传 ${index + 1}/${tempFilePaths.length}（${event.progress}%）`
					}
				})
				const file = result.code === 200 ? result.data : null
				if (file && file.id && file.url) images.value.push({ id: Number(file.id), url: file.url })
				else failed++
			}
			uploadProgress.value = ''
			if (failed) uni.showToast({ title: `${failed}张上传失败，可重新选择`, icon: 'none' })
		},
		fail: error => {
			if (!String(error?.errMsg || '').includes('cancel')) uni.showToast({ title: '图片选择失败，请重试', icon: 'none' })
		}
	})
}

const submitEvaluation = () => {
	if (!canSubmit.value || submitting.value) {
		if (!content.value.trim()) uni.showToast({ title: '请填写评价内容', icon: 'none' })
		return
	}
	uni.showModal({
		title: '确认提交评价',
		content: '评价提交后不能修改或重复提交。评价内容仅管理员可见，安装师傅不可见。',
		success: async ({ confirm }) => {
			if (!confirm) return
			submitting.value = true
			try {
				const result = await evaluationApi.submit({
					orderId: Number(orderId.value),
					score: score.value,
					content: content.value.trim(),
					liked: liked.value,
					labels: labels.value,
					fileIds: images.value.map(image => image.id)
				})
				if (result.code !== 200) return
				submitted.value = true
				allowLeave.value = true
				uni.showToast({ title: '评价成功', icon: 'success' })
			} finally {
				submitting.value = false
			}
		}
	})
}

onBackPress(() => {
	if (allowLeave.value || submitted.value || !hasDraft.value || submitting.value) return false
	uni.showModal({
		title: '离开评价页面？',
		content: '尚未提交的评分、文字和图片将不会保存。',
		success: ({ confirm }) => {
			if (!confirm) return
			allowLeave.value = true
			uni.navigateBack()
		}
	})
	return true
})

const loadPage = async () => {
	loading.value = true
	loadFailed.value = false
	try {
		const [orderResult, userResult] = await Promise.all([
			orderApi.getDetail(orderId.value),
			authApi.getUserInfo()
		])
		if (orderResult.code !== 200 || userResult.code !== 200) {
			loadFailed.value = true
			return
		}
		orderInfo.value = orderResult.data || {}
		const role = userResult.data?.role || ''
		if (orderInfo.value.statusCode === 'REVIEWED') {
			allowLeave.value = true
			uni.showToast({ title: '评价已提交，仅管理员可见', icon: 'none' })
			setTimeout(() => uni.navigateBack(), 1200)
			return
		}
		if (!['customer', 'dealer'].includes(role) || orderInfo.value.statusCode !== 'PENDING_REVIEW') {
			allowLeave.value = true
			uni.showToast({ title: '当前订单不可评价', icon: 'none' })
			setTimeout(() => uni.navigateBack(), 1000)
		}
	} finally {
		loading.value = false
	}
}

onLoad(async (options) => {
	if (!requireLogin({ content: '提交服务评价需要关联你的订单。你可以暂不登录，继续浏览产品和服务。' })) return
	if (!options?.id) {
		loading.value = false
		loadFailed.value = true
		uni.showToast({ title: '订单ID缺失', icon: 'none' })
		return
	}
	orderId.value = options.id
	await loadPage()
})
</script>

<style scoped lang="scss">
$primary: #0b63ce;
$text-main: #142434;
$text-sub: #64748b;
$text-light: #94a3b8;

.page { min-height: 100vh; background: #f4f7fb; padding-bottom: calc(120rpx + env(safe-area-inset-bottom)); }
.state-card { margin: 160rpx 48rpx 0; padding: 60rpx 30rpx; border-radius: 22rpx; background: #fff; display: flex; flex-direction: column; align-items: center; gap: 20rpx; font-size: 26rpx; color: $text-sub; }
.retry-btn { padding: 12rpx 30rpx; border-radius: 30rpx; background: #eaf3ff; color: $primary; }
.order-card, .section-card { background: #fff; border-radius: 22rpx; padding: 26rpx; margin: 18rpx 24rpx 0; box-shadow: 0 8rpx 24rpx rgba(20,54,84,.05); }
.order-card { display: flex; align-items: center; }
.order-icon { width: 92rpx; height: 92rpx; border-radius: 22rpx; background: #eaf3ff; display: flex; align-items: center; justify-content: center; }
.order-info { display: flex; flex-direction: column; margin-left: 20rpx; min-width: 0; }
.order-name { font-size: 29rpx; font-weight: 650; color: $text-main; }
.order-product { font-size: 25rpx; color: $text-sub; margin-top: 7rpx; }
.order-no { font-size: 22rpx; color: $text-light; margin-top: 8rpx; }
.privacy-notice { display: flex; align-items: center; margin: 18rpx 24rpx 0; padding: 22rpx 24rpx; border-radius: 18rpx; background: #eef6ff; border: 1rpx solid #d7e9ff; }
.privacy-icon { width: 58rpx; height: 58rpx; border-radius: 16rpx; background: #fff; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.privacy-copy { display: flex; flex-direction: column; min-width: 0; margin-left: 16rpx; }
.privacy-title { font-size: 25rpx; font-weight: 600; color: $text-main; }
.privacy-desc { font-size: 21rpx; color: $text-sub; margin-top: 7rpx; }
.section-title-wrap { display: flex; align-items: center; justify-content: space-between; margin-bottom: 22rpx; }
.section-title { font-size: 30rpx; font-weight: 650; color: $text-main; margin-bottom: 22rpx; }
.section-title-wrap .section-title { margin-bottom: 0; }
.required-title::before { content: '*'; color: #ef4444; margin-right: 5rpx; }
.section-tip { font-size: 23rpx; color: $text-light; }
.star-row { display: flex; align-items: center; justify-content: center; gap: 24rpx; }
.star-row.readonly { justify-content: flex-start; gap: 10rpx; }
.star-item:active { opacity: .65; }
.score-label { display: block; text-align: center; margin-top: 14rpx; font-size: 25rpx; color: #d97706; }
.like-row { display: flex; align-items: center; justify-content: center; gap: 10rpx; margin-top: 24rpx; font-size: 25rpx; color: $text-sub; }
.like-row .active { color: $primary; }
.label-list { display: flex; flex-wrap: wrap; gap: 14rpx; }
.label-item { padding: 10rpx 20rpx; border-radius: 28rpx; background: #f1f5f9; font-size: 24rpx; color: $text-sub; }
.label-item.active { background: #e7f2ff; color: $primary; }
.textarea-wrap { position: relative; }
.evaluate-textarea { width: 100%; height: 250rpx; box-sizing: border-box; border-radius: 14rpx; padding: 22rpx; background: #f8fafc; font-size: 27rpx; color: $text-main; }
.placeholder-style { color: #cbd5e1; }
.text-count { position: absolute; right: 18rpx; bottom: 14rpx; font-size: 21rpx; color: $text-light; }
.image-grid { display: flex; flex-wrap: wrap; gap: 15rpx; margin-top: 20rpx; }
.image-item, .image-add { width: 196rpx; height: 196rpx; border-radius: 14rpx; overflow: hidden; position: relative; }
.preview-img { width: 100%; height: 100%; }
.image-add { border: 2rpx dashed #cbd5e1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 22rpx; color: $text-light; box-sizing: border-box; }
.image-add text { margin-top: 8rpx; }
.image-delete { position: absolute; top: 0; right: 0; width: 42rpx; height: 42rpx; background: rgba(0,0,0,.55); display: flex; align-items: center; justify-content: center; border-radius: 0 0 0 12rpx; }
.upload-progress { display: block; margin-top: 14rpx; font-size: 23rpx; color: $primary; }
.submission-success { display: flex; flex-direction: column; align-items: center; padding: 54rpx 32rpx; text-align: center; }
.success-icon { width: 88rpx; height: 88rpx; border-radius: 50%; background: #dcfce7; display: flex; align-items: center; justify-content: center; }
.success-title { margin-top: 22rpx; font-size: 31rpx; font-weight: 650; color: $text-main; }
.success-desc { margin-top: 12rpx; font-size: 24rpx; line-height: 1.6; color: $text-sub; }
.success-privacy { display: flex; align-items: center; gap: 8rpx; margin-top: 24rpx; padding: 12rpx 20rpx; border-radius: 24rpx; background: #f1f5f9; font-size: 22rpx; color: $text-sub; }
.bottom-space { height: 130rpx; }
.submit-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -4rpx 18rpx rgba(15,23,42,.06); }
.submit-btn { height: 88rpx; border-radius: 44rpx; background: linear-gradient(135deg,#3b8eea,#0b63ce); display: flex; align-items: center; justify-content: center; }
.submit-btn text { font-size: 30rpx; color: #fff; font-weight: 600; }
.submit-btn.disabled { opacity: .5; }
</style>
