<template>
	<view class="page">
		<!-- ═══ 订单信息简卡 ═══ -->
		<view class="order-card">
			<view class="order-service">
				<image class="order-img" :src="orderInfo.image" mode="aspectFill" lazy-load></image>
				<view class="order-info">
					<text class="order-name">{{ orderInfo.serviceName }}</text>
					<text class="order-product">{{ orderInfo.productName }}</text>
					<text class="order-spec">{{ orderInfo.productSpec }}</text>
				</view>
			</view>
		</view>

		<!-- ═══ 星级评分 ═══ -->
		<view class="section-card">
			<view class="section-title">服务评价</view>
			<view class="star-row">
				<view class="star-item" v-for="i in 5" :key="i" @click="setScore(i)">
					<up-icon :name="i <= score ? 'star-fill' : 'star'" size="56"
						:color="i <= score ? '#ff9800' : '#ddd'"></up-icon>
				</view>
			</view>
			<view class="star-labels">
				<text class="star-label" v-for="(label, index) in starLabels" :key="index"
					:class="{ active: score === index + 1 }">{{ label }}</text>
			</view>
		</view>

		<!-- ═══ 评价内容 ═══ -->
		<view class="section-card">
			<view class="section-title">评价内容</view>
			<view class="textarea-wrap">
				<textarea class="evaluate-textarea" v-model="content" placeholder="本次安装体验怎么样？"
					placeholder-class="placeholder-style" maxlength="500"></textarea>
				<text class="text-count">{{ content.length }}/500</text>
			</view>
		</view>

		<!-- ═══ 上传图片 ═══ -->
		<view class="section-card">
			<view class="section-title-wrap">
				<text class="section-title">上传图片</text>
				<text class="upload-tip">（最多9张）</text>
			</view>
			<view class="image-grid" v-if="imageList.length > 0">
				<view class="image-item" v-for="(img, index) in imageList" :key="index">
					<image class="preview-img" :src="img" mode="aspectFill" @click="previewImage(index)"></image>
					<view class="image-delete" @click.stop="deleteImage(index)">
						<up-icon name="close" size="12" color="#fff"></up-icon>
					</view>
				</view>
				<view class="image-add" v-if="imageList.length < 9" @click="chooseImage">
					<up-icon name="plus" size="32" color="#ccc"></up-icon>
					<text class="add-text">上传图片</text>
				</view>
			</view>
			<view class="image-add" v-else @click="chooseImage">
				<up-icon name="plus" size="32" color="#ccc"></up-icon>
				<text class="add-text">上传图片</text>
			</view>
		</view>

		<view class="bottom-space"></view>

		<!-- ═══ 提交按钮 ═══ -->
		<view class="submit-bar">
			<view class="submit-btn" :class="{ disabled: !canSubmit }" @click="handleSubmit">
				<text>提交</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import {
	ref,
	computed
} from 'vue'
import {
	onLoad
} from '@dcloudio/uni-app'
import {
	orderApi,
	evaluationApi,
	uploadApi
} from '@/api/api.js'

const orderId = ref('')
const orderInfo = ref({
	serviceName: '',
	productName: '',
	productSpec: '',
	image: ''
})

const score = ref(5)
const starLabels = ['非常差', '较差', '一般', '推荐', '超赞']
const content = ref('')
const imageList = ref([])
const submitting = ref(false)

const canSubmit = computed(() => score.value > 0 && content.value.trim().length > 0)

const setScore = (i) => {
	score.value = i
}

const chooseImage = () => {
	const remaining = 9 - imageList.value.length
	if (remaining <= 0) {
		uni.showToast({ title: '最多上传9张图片', icon: 'none' })
		return
	}
	uni.chooseImage({
		count: remaining,
		sizeType: ['compressed'],
		sourceType: ['album', 'camera'],
		success: async (res) => {
			const tempFilePaths = res.tempFilePaths || []
			if (tempFilePaths.length === 0) {
				uni.showToast({ title: '未选择图片', icon: 'none' })
				return
			}
			uni.showLoading({ title: '上传中...', mask: true })
			let failCount = 0
			try {
				for (const path of tempFilePaths) {
					try {
						const upRes = await uploadApi.uploadImage(path, {}, { loading: false })
						if (upRes.code === 200 && upRes.data && upRes.data.url) {
							imageList.value.push(upRes.data.url)
						} else {
							failCount++
						}
					} catch (e) {
						failCount++
					}
				}
				if (failCount > 0) {
					uni.showToast({ title: `${failCount}张上传失败，请重试`, icon: 'none' })
				}
			} finally {
				uni.hideLoading()
			}
		},
		fail: (err) => {
			const errMsg = (err && err.errMsg) || ''
			console.error('chooseImage fail:', err)
			// 用户取消不提示
			if (errMsg.indexOf('cancel') !== -1) return
			// 隐私协议未同意等具体错误给出明确提示
			if (errMsg.indexOf('privacy') !== -1 || errMsg.indexOf('authorize') !== -1) {
				uni.showToast({ title: '请同意隐私协议并授权相册/相机权限', icon: 'none', duration: 2000 })
			} else {
				uni.showToast({ title: `图片选择失败：${errMsg || '请重试'}`, icon: 'none', duration: 2000 })
			}
		}
	})
}

const deleteImage = (index) => {
	uni.showModal({
		title: '提示',
		content: '确定删除这张图片吗？',
		success: (res) => {
			if (res.confirm) imageList.value.splice(index, 1)
		}
	})
}

const previewImage = (index) => {
	uni.previewImage({
		current: imageList.value[index],
		urls: imageList.value
	})
}

const handleSubmit = () => {
	if (!canSubmit.value) {
		uni.showToast({ title: '请选择评分并填写评价内容', icon: 'none' })
		return
	}
	if (submitting.value) return
	submitting.value = true
	uni.showLoading({ title: '提交中...', mask: true })
	evaluationApi.submit({
		orderId: orderId.value,
		score: score.value,
		content: content.value.trim(),
		images: imageList.value,
		label: starLabels[score.value - 1]
	}).then(res => {
		uni.hideLoading()
		if (res.code === 200) {
			uni.showToast({ title: '评价成功', icon: 'success' })
			setTimeout(() => uni.navigateBack(), 1500)
		}
	}).catch(() => {
		uni.hideLoading()
	}).finally(() => {
		submitting.value = false
	})
}

onLoad(async (options) => {
	const id = options && options.id
	if (!id) {
		uni.showToast({ title: '订单ID缺失', icon: 'none' })
		return
	}
	orderId.value = id
	try {
		const res = await orderApi.getDetail(id)
		if (res.code !== 200) return
		const data = res.data || {}
		orderInfo.value = {
			serviceName: data.serviceName || '',
			productName: data.productName || '',
			productSpec: data.productSpec || '',
			image: data.image || ''
		}
	} catch (err) {
		// request.js 已统一处理错误提示
	}
})
</script>

<style scoped lang="scss">
$primary: #0b63ce;
$bg: #f4f7fb;
$text-main: #142434;
$text-sub: #64748b;
$text-light: #94a3b8;
@import '@/uni.scss';

.page {
	min-height: 100vh;
	background: $bg;
	padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
}

.order-card,
.section-card {
	background: #fff;
	border-radius: 16rpx;
	padding: 24rpx;
	margin: 16rpx 24rpx 0;
	box-shadow: 0 2rpx 12rpx rgba(30, 41, 59, 0.04);
}

.order-service {
	display: flex;
	align-items: center;
}

.order-img {
	width: 120rpx;
	height: 120rpx;
	border-radius: 12rpx;
	background-color: #e8f4fd;
	flex-shrink: 0;
}

.order-info {
	margin-left: 20rpx;
	flex: 1;
	display: flex;
	flex-direction: column;
	min-width: 0;
}

.order-name {
	font-size: 28rpx;
	font-weight: 600;
	color: $text-main;
	margin-bottom: 8rpx;
}

.order-product {
	font-size: 24rpx;
	color: $text-sub;
	margin-bottom: 6rpx;
	line-height: 1.4;
	word-break: break-all;
}

.order-spec {
	font-size: 22rpx;
	color: $text-light;
}

.section-title {
	font-size: 30rpx;
	font-weight: 600;
	color: $text-main;
	margin-bottom: 24rpx;
}

.section-title-wrap {
	display: flex;
	align-items: center;
	margin-bottom: 24rpx;

	.section-title {
		margin-bottom: 0;
	}

	.upload-tip {
		font-size: 24rpx;
		color: $text-light;
		margin-left: 8rpx;
	}
}

.star-row {
	display: flex;
	justify-content: center;
	gap: 32rpx;
}

.star-item {
	display: flex;
	align-items: center;
	justify-content: center;

	&:active {
		opacity: 0.7;
	}
}

.star-labels {
	display: flex;
	justify-content: center;
	gap: 40rpx;
	margin-top: 16rpx;
}

.star-label {
	font-size: 24rpx;
	color: $text-light;

	&.active {
		color: #ff9800;
		font-weight: 500;
	}
}

.textarea-wrap {
	position: relative;
}

.evaluate-textarea {
	width: 100%;
	height: 240rpx;
	background-color: #f9f9fb;
	border-radius: 12rpx;
	padding: 24rpx;
	font-size: 28rpx;
	color: $text-main;
	box-sizing: border-box;
}

.placeholder-style {
	color: #ccc;
}

.text-count {
	position: absolute;
	right: 24rpx;
	bottom: 16rpx;
	font-size: 22rpx;
	color: $text-light;
}

.image-grid {
	display: flex;
	flex-wrap: wrap;
	gap: 16rpx;
}

.image-item {
	position: relative;
	width: 200rpx;
	height: 200rpx;
	border-radius: 12rpx;
	overflow: hidden;
}

.preview-img {
	width: 100%;
	height: 100%;
}

.image-delete {
	position: absolute;
	top: 0;
	right: 0;
	width: 40rpx;
	height: 40rpx;
	border-radius: 0 12rpx 0 12rpx;
	background-color: rgba(0, 0, 0, 0.5);
	display: flex;
	align-items: center;
	justify-content: center;
}

.image-add {
	width: 200rpx;
	height: 200rpx;
	border-radius: 12rpx;
	border: 2rpx dashed #ddd;
	background-color: #fafafa;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;

	&:active {
		background-color: #f0f0f0;
	}
}

.add-text {
	font-size: 22rpx;
	color: $text-light;
	margin-top: 10rpx;
}

.bottom-space {
	height: 140rpx;
}

.submit-bar {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	padding: 20rpx 24rpx;
	padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
	background: #fff;
	box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
	z-index: 100;
}

.submit-btn {
	height: 88rpx;
	border-radius: 44rpx;
	background: linear-gradient(135deg, #3b8eea, #0b63ce);
	display: flex;
	align-items: center;
	justify-content: center;

	text {
		font-size: 32rpx;
		font-weight: 600;
		color: #fff;
	}

	&.disabled {
		opacity: 0.5;
	}

	&:active {
		opacity: 0.85;
	}
}
</style>
