<template>
	<view class="page">
		<!-- 封面图 + 悬浮按钮 -->
		<view class="cover-section">
			<image v-if="!isMockImage(detail.image)" class="cover-img" :src="detail.image" mode="widthFix"></image>
			<view v-else class="article-cover">
				<view class="cover-ring ring-one"></view><view class="cover-ring ring-two"></view>
				<up-icon name="file-text-fill" size="54" color="#ffffff"></up-icon>
				<text class="cover-kicker">鑫立创 · 暖通知识库</text>
			</view>
			<!-- 左上角返回按钮 -->
			<view class="float-back" @click="goBack">
				<up-icon name="arrow-left" size="18" color="#fff"></up-icon>
			</view>

		</view>

		<!-- 正文区域 -->
		<view class="content-area">
			<!-- 标题 -->
			<view class="title-section">
				<text class="article-title">{{ detail.title }}</text>
				<view class="article-meta">
					<text class="article-date">{{ detail.date }}</text>
					<text class="article-views">阅读 {{ detail.views }}</text>
					<up-icon name="more-dot-fill" size="18" color="#94a3b8" @click="handleMore"></up-icon>
				</view>

			</view>

			<!-- 正文 -->
			<view class="article-body">
				<rich-text class="article-text" :nodes="detail.content"></rich-text>
			</view>

			<view class="read-info">
				<text class="read-text">阅读 {{ detail.views }}</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import {
	ref
} from 'vue'
import {
	onLoad,
	onShareAppMessage
} from '@dcloudio/uni-app'
import { dynamicApi } from '@/api/api.js'

// 文章 ID（用于分享路径）
const articleId = ref('')

const detail = ref({
	title: '',
	date: '',
	views: '',
	image: '',
	content: '',
})

const isMockImage = (url) => !url || String(url).includes('picsum.photos')

onLoad(async (options) => {
	const id = options?.id
	if (!id) return
	articleId.value = id
	try {
		const res = await dynamicApi.getDetail(id)
		if (res.code !== 200) return
		// 资讯字段已与模板对齐（title/date/views/image/content），res.data 直接赋值
		detail.value = res.data || {}
	} catch (err) {
		// request.js 已统一处理错误提示
	}
})

// 分享文章给好友
onShareAppMessage(() => ({
	title: detail.value.title || '鑫立创平台公告',
	path: `/packageA/notices-detail/notices-detail?id=${articleId.value}`
}))

const goBack = () => {
	uni.navigateBack({
		fail: () => uni.switchTab({
			url: '/pages/index/index'
		})
	})
}

const handleMore = () => {
	uni.showActionSheet({
		itemList: ['收藏文章', '复制文章标题', '联系客服'],
		success: (res) => {
			if (res.tapIndex === 0) {
				const favorites = uni.getStorageSync('articleFavorites') || []
				if (!favorites.includes(articleId.value)) favorites.push(articleId.value)
				uni.setStorageSync('articleFavorites', favorites)
				uni.showToast({ title: '已收藏', icon: 'none' })
			} else if (res.tapIndex === 1) {
				uni.setClipboardData({ data: detail.value.title })
			} else {
				uni.showActionSheet({
					itemList: ['027-82710326', '027-82710380'],
					success: (phoneRes) => {
						const phones = ['02782710326', '02782710380']
						uni.makePhoneCall({ phoneNumber: phones[phoneRes.tapIndex] })
					}
				})
			}
		},
	})
}
</script>

<style scoped lang="scss">
// 页面里直接用
@import '@/uni.scss';
$text-main: #142434;
$text-light: #94a3b8;

.page {
	min-height: 100vh;
	background: #fff;
}

/* ═══ 封面图 + 悬浮按钮 ═══ */
.cover-section {
	position: relative;
	width: 100%;
	overflow: hidden;
}

.cover-img {
	width: 100%;
	display: block;
}

.article-cover { height: 380rpx; background: linear-gradient(145deg, #082f5d 0%, #0b63ce 68%, #3198e5 100%); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18rpx; position: relative; overflow: hidden; }
.cover-kicker { color: rgba(255,255,255,.76); font-size: 23rpx; letter-spacing: 2rpx; }.cover-ring { position: absolute; border-radius: 50%; border: 1rpx solid rgba(255,255,255,.14); }
.ring-one { width: 360rpx; height: 360rpx; right: -150rpx; top: -210rpx; }.ring-two { width: 240rpx; height: 240rpx; left: -100rpx; bottom: -150rpx; }

/* 左上角返回按钮 */
.float-back {
	position: absolute;
	top: calc(var(--status-bar-height, 44rpx) + 66rpx);
	left: 24rpx;
	width: 64rpx;
	height: 64rpx;
	background: rgba(0, 0, 0, 0.35);
	backdrop-filter: blur(10rpx);
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	z-index: 99;

	&:active {
		background: rgba(0, 0, 0, 0.5);
		transform: scale(0.92);
	}
}

/* 右上角更多按钮 */
.float-more {
	position: absolute;
	top: calc(var(--status-bar-height, 44rpx) + 16rpx);
	right: 24rpx;
	width: 64rpx;
	height: 64rpx;
	background: rgba(0, 0, 0, 0.35);
	backdrop-filter: blur(10rpx);
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	z-index: 99;

	&:active {
		background: rgba(0, 0, 0, 0.5);
		transform: scale(0.92);
	}
}

/* ═══ 正文区域 ═══ */
.content-area {
	background: #fff;
}

.title-section {
	padding: 32rpx 32rpx 0;
}

.article-title {
	font-size: 36rpx;
	font-weight: 700;
	color: $text-main;
	line-height: 1.5;
	display: block;
}

.article-meta {
	display: flex;
	align-items: center;
	gap: 32rpx;
	margin-top: 20rpx;
	padding-bottom: 24rpx;
	border-bottom: 1rpx solid #f0f1f3;
}

.article-date {
	font-size: 24rpx;
	color: $text-light;
}

.article-views {
	font-size: 24rpx;
	color: $text-light;
}

.article-body {
	padding: 24rpx 32rpx 48rpx;
}

.article-text {
	font-size: 30rpx;
	color: $text-main;
	line-height: 1.8;
	white-space: pre-wrap;
}

.read-info {
	padding: 32rpx;
	text-align: center;
	border-top: 1rpx solid #f0f1f3;
}

.read-text {
	font-size: 24rpx;
	color: $text-light;
}
</style>
