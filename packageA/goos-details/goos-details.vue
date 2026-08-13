<template>
	<view class="page">
		<!-- ═══ 顶部导航栏 ═══ -->
		<view class="nav-bar">
			<view class="nav-back" @click="goBack">
				<up-icon name="arrow-left" size="20" color="#142434"></up-icon>
			</view>
			<text class="nav-title">{{ goods.title }}</text>
			<!-- <view class="nav-share" @click="handleShare">
        <up-icon name="share-square" size="18" color="#142434"></up-icon>
      </view> -->
		</view>

		<!-- ═══ 封面图片 ═══ -->
		<view class="cover-section">
			<image v-if="!isMockImage(goods.image)" class="cover-img" :src="goods.image" mode="aspectFill"></image>
			<view v-else class="cover-visual" :class="`visual-${goods.type || 'aux'}`">
				<view class="cover-ring ring-large"></view>
				<view class="cover-ring ring-small"></view>
				<up-icon :name="productIcon(goods.type)" size="68" color="#ffffff"></up-icon>
				<text>{{ goods.category || '空调配件' }}</text>
			</view>
			<view class="cover-mask"></view>
			<view class="cover-tag" v-if="goods.tag">{{ goods.tag }}</view>
		</view>

		<!-- ═══ 商品信息卡片 ═══ -->
		<view class="info-card">
			<view class="info-header"><view class="display-only-badge">产品展示 · 暂不支持线上购买</view></view>

			<text class="info-title">{{ goods.title }}</text>
			<view class="info-tags">
				<view class="info-tag" v-for="(tag, i) in goods.tags" :key="i">{{ tag }}</view>
			</view>
			<view class="info-desc">{{ goods.desc }}</view>
		</view>

		<!-- ═══ 规格选择 ═══ -->
		<view class="spec-card">
			<view class="spec-row">
				<text class="spec-label">规格</text>
				<text class="spec-value">{{ goods.spec || '以实物标识为准' }}</text>
			</view>
			<view class="spec-row">
				<text class="spec-label">库存</text>
				<text class="spec-value">展示库存仅供参考</text>
				<text class="spec-stock" :class="{ inStock: goods.stock > 0 }">
					{{ goods.stock > 0 ? '有货' : '缺货' }}
				</text>
			</view>
		</view>

		<!-- ═══ 服务保障 ═══ -->
		<view class="service-card">
			<view class="service-item">
				<up-icon name="checkmark-circle" size="16" color="#07c160"></up-icon>
				<text class="service-text">正品保证</text>
			</view>
			<view class="service-item"><up-icon name="checkmark-circle" size="16" color="#07c160"></up-icon><text class="service-text">规格透明</text></view>
			<view class="service-item"><up-icon name="checkmark-circle" size="16" color="#07c160"></up-icon><text class="service-text">电话咨询</text></view>
			<view class="service-item">
				<up-icon name="checkmark-circle" size="16" color="#07c160"></up-icon>
				<text class="service-text">上门安装</text>
			</view>
		</view>

		<!-- ═══ 商品详情图片 ═══ -->
		<view class="detail-img-card">
			<view class="detail-header">
				<view class="header-line"></view>
				<text class="detail-title">商品详情</text>
				<view class="header-line"></view>
			</view>
			<image v-for="(img, index) in displayDetailImages" :key="index" :src="img" mode="widthFix" class="detail-img"
				lazy-load></image>
			<view class="detail-specs" v-if="displayDetailImages.length === 0">
				<view class="detail-spec-row"><text>产品型号</text><text>{{ goods.model || '以实物标识为准' }}</text></view>
				<view class="detail-spec-row"><text>产品规格</text><text>{{ goods.spec || '以实物标识为准' }}</text></view>
				<view class="detail-spec-row"><text>产品分类</text><text>{{ goods.category || '空调配件' }}</text></view>
				<view class="detail-note"><up-icon name="info-circle" size="16" color="#0b63ce"></up-icon><text>{{ goods.desc }}</text></view>
			</view>
		</view>

		<view class="bottom-placeholder"></view>

		<!-- ═══ 底部操作栏 ═══ -->
		<view class="bottom-bar">
			<view class="bar-icons">
				<view class="bar-icon-item" hover-class="hover-press" :hover-stay-time="80" @click="goHome">
					<up-icon name="home" size="22" color="#475569"></up-icon>
					<text class="bar-icon-text">首页</text>
				</view>
				<view class="bar-icon-item" hover-class="hover-press" :hover-stay-time="80" @click="handleFavor">
					<up-icon :name="isFavor ? 'heart-fill' : 'heart'" size="22"
						:color="isFavor ? '#ff4d4f' : '#475569'"></up-icon>
					<text class="bar-icon-text" :class="{ active: isFavor }">{{ isFavor ? '已收藏' : '收藏' }}</text>
				</view>
				<view class="bar-icon-item" hover-class="hover-press" :hover-stay-time="80" @click="handleService">
					<up-icon name="kefu-ermai" size="22" color="#475569"></up-icon>
					<text class="bar-icon-text">客服</text>
				</view>
			</view>
			<view class="bar-actions"><view class="action-btn buy" hover-class="hover-mask" :hover-stay-time="80" @click="handleService">电话咨询</view></view>
		</view>

		<!-- ═══ 分享弹窗 ═══ -->
		<up-popup :show="showShare" mode="bottom" round="20" @close="showShare = false">
			<view class="share-popup">
				<text class="share-title">分享到</text>
				<view class="share-options">
					<view class="share-item" @click="shareTo('wechat')">
						<view class="share-icon wechat">
							<up-icon name="weixin-fill" size="28" color="#fff"></up-icon>
						</view>
						<text class="share-label">微信好友</text>
					</view>
					<view class="share-item" @click="shareTo('moment')">
						<view class="share-icon moment">
							<up-icon name="moments" size="28" color="#fff"></up-icon>
						</view>
						<text class="share-label">朋友圈</text>
					</view>
					<view class="share-item" @click="shareTo('poster')">
						<view class="share-icon poster">
							<up-icon name="photo" size="28" color="#fff"></up-icon>
						</view>
						<text class="share-label">生成海报</text>
					</view>
				</view>
				<view class="share-cancel" @click="showShare = false">取消</view>
			</view>
		</up-popup>
	</view>
</template>

<script setup>
import {
	ref,
	computed
} from 'vue'
import {
	onLoad,
	onShareAppMessage
} from '@dcloudio/uni-app'
import { consumablesApi } from '@/api/api.js'

// 商品 ID（onLoad 时获取，用于分享路径）
const goodsId = ref('')

// 商品信息（onLoad 时由接口填充）
const goods = ref({
	title: '',
	image: '',
	tag: '',
	price: '',
	oldPrice: '',
	sales: '',
	stock: 0,
	desc: '',
	tags: [],
})

const detailImages = ref([])
const displayDetailImages = computed(() => detailImages.value.filter(img => !isMockImage(img)))

const isFavor = ref(false)
const showShare = ref(false)

const isMockImage = (url) => !url || String(url).includes('picsum.photos')
const productIcon = (type) => ({
	copper: 'integral', bracket: 'grid-fill', cable: 'share-fill', refrigerant: 'hourglass-half-fill', aux: 'bag-fill'
}[type] || 'bag-fill')


onLoad(async (options) => {
	const id = options?.id
	if (!id) return
	goodsId.value = id
	try {
		const res = await consumablesApi.getDetail(id)
		if (res.code !== 200) return
		// 耗材字段已与模板对齐，res.data 直接赋值
		goods.value = res.data || {}
		detailImages.value = (res.data && res.data.detailImages) || []
	} catch (err) {
		// request.js 已统一处理错误提示
	}
})

// 分享商品详情给好友
onShareAppMessage(() => ({
	title: goods.value.title || '鑫立创配件详情',
	path: `/packageA/goos-details/goos-details?id=${goodsId.value}`
}))

const goBack = () => {
	uni.navigateBack({
		fail: () => uni.switchTab({
			url: '/pages/index/index'
		})
	})
}

const goHome = () => {
	uni.switchTab({
		url: '/pages/index/index'
	})
}

const handleFavor = () => {
	isFavor.value = !isFavor.value
	uni.showToast({
		title: isFavor.value ? '已收藏' : '取消收藏',
		icon: 'none'
	})
}

const handleService = () => {
	uni.showActionSheet({
		itemList: ['027-82710326', '027-82710380'],
		success: (res) => {
			const phones = ['02782710326', '02782710380']
			uni.makePhoneCall({ phoneNumber: phones[res.tapIndex] })
		}
	})
}

// const handleShare = () => {
//   showShare.value = true
// }

const shareTo = (type) => {
	showShare.value = false
	const text = {
		wechat: '已复制链接，去微信粘贴分享',
		moment: '已生成分享图片',
		poster: '海报已保存到相册',
	}
	uni.showToast({
		title: text[type],
		icon: 'none'
	})
}

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
}

/* 导航栏 */
.nav-bar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	height: calc(88rpx + var(--status-bar-height, 44rpx));
	padding-top: var(--status-bar-height, 44rpx);
	background: #fff;
	display: flex;
	align-items: center;
	padding-left: 24rpx;
	padding-right: 24rpx;
	z-index: 999;
	box-shadow: 0 1rpx 0 rgba(0, 0, 0, 0.04);
}

.nav-back,
.nav-share {
	width: 64rpx;
	height: 64rpx;
	display: flex;
	align-items: center;
	justify-content: center;

	&:active {
		opacity: 0.6;
	}
}

.nav-title {
	flex: 1;
	font-size: 32rpx;
	font-weight: 600;
	color: $text-main;
	text-align: center;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
	padding: 0 16rpx;
}

/* 封面图 */
.cover-section {
	position: relative;
	width: 100%;
	height: 420rpx;
	margin-top: calc(88rpx + var(--status-bar-height, 44rpx));
	overflow: hidden;
}

.cover-visual { width: 100%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; position: relative; overflow: hidden; gap: 14rpx; color: rgba(255,255,255,.78); font-size: 24rpx; }
.visual-copper { background: linear-gradient(135deg, #a95e31, #dda064); }.visual-bracket { background: linear-gradient(135deg, #4e6275, #8ba1b4); }
.visual-cable { background: linear-gradient(135deg, #30455c, #617b96); }.visual-refrigerant { background: linear-gradient(135deg, #087782, #24b4aa); }.visual-aux { background: linear-gradient(135deg, #37659d, #79a5dc); }
.cover-ring { position: absolute; border-radius: 50%; border: 1rpx solid rgba(255,255,255,.17); }.ring-large { width: 380rpx; height: 380rpx; right: -130rpx; top: -220rpx; }.ring-small { width: 240rpx; height: 240rpx; left: -90rpx; bottom: -150rpx; }

.cover-img {
	width: 100%;
	height: 100%;
}

.cover-mask {
	position: absolute;
	bottom: 0;
	left: 0;
	right: 0;
	height: 120rpx;
	background: linear-gradient(180deg, transparent, rgba(0, 0, 0, 0.4));
}

.cover-tag {
	position: absolute;
	top: 64rpx;
	left: 24rpx;
	padding: 8rpx 20rpx;
	background: rgba(255, 77, 79, 0.9);
	color: #fff;
	font-size: 22rpx;
	font-weight: 600;
	border-radius: 8rpx;
}

/* 商品信息卡片 */
.info-card {
	background: #fff;
	margin: -40rpx 24rpx 0;
	position: relative;
	z-index: 10;
	border-radius: 20rpx;
	padding: 32rpx 28rpx;
	box-shadow: 0 4rpx 24rpx rgba(30, 41, 59, 0.08);
}

.info-header {
	display: flex;
	justify-content: space-between;
	align-items: flex-end;
	margin-bottom: 20rpx;
}

.price-area {
	display: flex;
	align-items: baseline;
}

.price-symbol {
	font-size: 28rpx;
	color: #ff4d4f;
	font-weight: 700;
}

.price-num {
	font-size: 52rpx;
	color: #ff4d4f;
	font-weight: 700;
	line-height: 1;
	margin-left: 4rpx;
}

.price-old {
	font-size: 24rpx;
	color: $text-light;
	text-decoration: line-through;
	margin-left: 16rpx;
}

.sales-badge {
	display: flex;
	align-items: baseline;
	gap: 4rpx;
}

.sales-num {
	font-size: 28rpx;
	font-weight: 600;
	color: $text-sub;
}

.sales-label {
	font-size: 22rpx;
	color: $text-light;
}

.info-title {
	font-size: 34rpx;
	font-weight: 700;
	color: $text-main;
	line-height: 1.45;
	display: block;
}

.info-tags {
	display: flex;
	gap: 12rpx;
	margin-top: 16rpx;
	flex-wrap: wrap;
}

.info-tag {
	font-size: 22rpx;
	color: $primary;
	background: rgba(60, 156, 255, 0.08);
	padding: 6rpx 16rpx;
	border-radius: 6rpx;
}

.info-desc {
	margin-top: 20rpx;
	font-size: 26rpx;
	color: $text-sub;
	line-height: 1.7;
	padding: 20rpx;
	background: #f8f9fb;
	border-radius: 12rpx;
}

/* 规格选择 */
.spec-card {
	background: #fff;
	margin: 24rpx 24rpx 0;
	border-radius: 20rpx;
	padding: 0 28rpx;
	box-shadow: 0 2rpx 12rpx rgba(30, 41, 59, 0.04);
}

.spec-row {
	display: flex;
	align-items: center;
	padding: 28rpx 0;
	border-bottom: 1rpx solid #f0f1f3;

	&:last-child {
		border-bottom: none;
	}

	&:active {
		background: #f8f9fb;
	}
}

.spec-label {
	font-size: 28rpx;
	color: $text-light;
	width: 80rpx;
}

.spec-value {
	flex: 1;
	font-size: 28rpx;
	color: $text-main;
}

.spec-stock {
	font-size: 24rpx;
	color: #ff4d4f;

	&.inStock {
		color: #07c160;
	}
}

/* 服务保障 */
.service-card {
	background: #fff;
	margin: 24rpx 24rpx 0;
	border-radius: 20rpx;
	padding: 24rpx 28rpx;
	display: flex;
	justify-content: space-between;
	box-shadow: 0 2rpx 12rpx rgba(30, 41, 59, 0.04);
}

.service-item {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.service-text {
	font-size: 24rpx;
	color: $text-sub;
}

/* 详情图片 */
.detail-img-card {
	background: #fff;
	margin: 24rpx 24rpx 0;
	border-radius: 20rpx;
	padding: 32rpx 0;
	overflow: hidden;
}

.detail-header {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 20rpx;
	margin-bottom: 24rpx;
}

.header-line {
	width: 48rpx;
	height: 2rpx;
	background: #e0e2e8;
}

.detail-title {
	font-size: 30rpx;
	font-weight: 700;
	color: $text-main;
}

.detail-img {
	width: 100%;
	display: block;
}

.detail-specs { padding: 0 28rpx 28rpx; }
.detail-spec-row { display: flex; align-items: center; justify-content: space-between; padding: 20rpx 0; border-bottom: 1rpx solid #edf1f5; font-size: 24rpx; color: $text-light; }
.detail-spec-row text:last-child { max-width: 68%; color: $text-main; text-align: right; }
.detail-note { margin-top: 24rpx; padding: 22rpx; border-radius: 16rpx; background: #eef5ff; display: flex; align-items: flex-start; gap: 12rpx; color: $text-sub; font-size: 23rpx; line-height: 1.6; }

.spec-popup-visual { background: linear-gradient(135deg, #37659d, #79a5dc); display: flex; align-items: center; justify-content: center; }

/* 底部 */
.bottom-placeholder {
	height: calc(120rpx + env(safe-area-inset-bottom));
}

.bottom-bar {
	position: fixed;
	bottom: 0;
	left: 0;
	right: 0;
	height: calc(100rpx + env(safe-area-inset-bottom));
	padding-bottom: env(safe-area-inset-bottom);
	background: #fff;
	display: flex;
	align-items: center;
	box-shadow: 0 -2rpx 16rpx rgba(0, 0, 0, 0.06);
	z-index: 100;
}

.bar-icons {
	display: flex;
	padding: 0 20rpx;
	gap: 24rpx;
}

.bar-icon-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 4rpx;

	&:active {
		opacity: 0.6;
	}
}

.bar-icon-text {
	font-size: 20rpx;
	color: $text-sub;

	&.active {
		color: #ff4d4f;
	}
}

.bar-actions {
	flex: 1;
	display: flex;
	height: 80rpx;
	margin-right: 24rpx;
	border-radius: 40rpx;
	overflow: hidden;
	box-shadow: 0 4rpx 20rpx rgba(60, 156, 255, 0.3);
}

.action-btn {
	flex: 1;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 28rpx;
	font-weight: 600;

	&:active {
		opacity: 0.85;
	}
}

.cart {
	background: linear-gradient(135deg, #ffd56e, #ffb340);
	color: #7a4a00;
}

.buy {
	background: linear-gradient(135deg, #0b63ce, #084b9b);
	color: #fff;
}

/* 规格弹窗 */
.spec-popup {
	padding: 32rpx 28rpx calc(20rpx + env(safe-area-inset-bottom));
}

.spec-popup-header {
	display: flex;
	align-items: center;
	padding-bottom: 24rpx;
	border-bottom: 1rpx solid #f0f1f3;
	margin-bottom: 24rpx;
}

.spec-popup-img {
	width: 160rpx;
	height: 160rpx;
	border-radius: 12rpx;
	margin-top: -60rpx;
	border: 4rpx solid #fff;
}

.spec-popup-info {
	flex: 1;
	margin-left: 20rpx;
}

.spec-popup-price {
	font-size: 36rpx;
	color: #ff4d4f;
	font-weight: 700;
	display: block;
}

.spec-popup-selected {
	font-size: 24rpx;
	color: $text-sub;
	margin-top: 8rpx;
}

.spec-group {
	margin-bottom: 28rpx;
}

.spec-group-title {
	font-size: 28rpx;
	color: $text-main;
	font-weight: 600;
	display: block;
	margin-bottom: 16rpx;
}

.spec-options {
	display: flex;
	flex-wrap: wrap;
	gap: 16rpx;
}

.spec-option {
	padding: 14rpx 32rpx;
	background: #f4f7fb;
	border-radius: 8rpx;
	font-size: 26rpx;
	color: $text-sub;
	border: 2rpx solid transparent;

	&.active {
		background: rgba(60, 156, 255, 0.08);
		color: $primary;
		border-color: $primary;
	}
}

.spec-quantity {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 40rpx;
}

.qty-control {
	display: flex;
	align-items: center;
	gap: 4rpx;
}

.qty-btn {
	width: 56rpx;
	height: 56rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #f4f7fb;
	border-radius: 8rpx;
	font-size: 32rpx;
	color: $text-sub;

	&:active {
		background: #e8e9eb;
	}
}

.qty-num {
	width: 80rpx;
	text-align: center;
	font-size: 28rpx;
	font-weight: 600;
	color: $text-main;
}

.spec-confirm {
	height: 88rpx;
	line-height: 88rpx;
	text-align: center;
	background: linear-gradient(135deg, #0b63ce, #084b9b);
	color: #fff;
	border-radius: 44rpx;
	font-size: 30rpx;
	font-weight: 600;

	&:active {
		opacity: 0.85;
	}
}

/* 分享弹窗 */
.share-popup {
	padding: 40rpx 32rpx calc(20rpx + env(safe-area-inset-bottom));
}

.share-title {
	font-size: 30rpx;
	font-weight: 600;
	color: $text-main;
	text-align: center;
	display: block;
	margin-bottom: 40rpx;
}

.share-options {
	display: flex;
	justify-content: space-around;
	margin-bottom: 40rpx;
}

.share-item {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12rpx;

	&:active {
		opacity: 0.7;
	}
}

.share-icon {
	width: 96rpx;
	height: 96rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;

	&.wechat {
		background: #07c160;
	}

	&.moment {
		background: #07c160;
	}

	&.poster {
		background: $primary;
	}
}

.share-label {
	font-size: 24rpx;
	color: $text-sub;
}

.share-cancel {
	height: 88rpx;
	line-height: 88rpx;
	text-align: center;
	background: #f5f5f5;
	border-radius: 44rpx;
	font-size: 30rpx;
	color: $text-sub;
}
</style>
