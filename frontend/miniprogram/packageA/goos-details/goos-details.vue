<template>
	<view class="page">
		<view class="detail-state" v-if="detailLoading">
			<up-loading-icon mode="circle" color="#0b63ce" size="34"></up-loading-icon>
			<text class="state-title">正在加载产品信息</text>
			<text class="state-desc">请稍候</text>
		</view>
		<view class="detail-state" v-else-if="detailError">
			<view class="state-icon"><up-icon name="warning" size="38" color="#d97706"></up-icon></view>
			<text class="state-title">{{ detailError.title }}</text>
			<text class="state-desc">{{ detailError.message }}</text>
			<view class="state-action" @click="loadDetail">重新加载</view>
		</view>

		<template v-else>

		<!-- ═══ 耗材图片轮播 ═══ -->
		<view class="cover-section">
			<swiper v-if="carouselImages.length" class="cover-swiper" circular :autoplay="carouselImages.length > 1"
				:indicator-dots="carouselImages.length > 1" :interval="4000" :duration="400"
				indicator-color="rgba(255,255,255,.45)" indicator-active-color="#ffffff">
				<swiper-item v-for="(imageUrl, index) in carouselImages" :key="`${imageUrl}-${index}`">
					<image class="cover-img" :src="imageUrl" mode="aspectFill"></image>
				</swiper-item>
			</swiper>
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
			<view class="info-header"><view class="display-only-badge">{{ isInstaller ? '安装耗材 · 请选择具体规格' : '产品展示 · 电话咨询' }}</view></view>

			<text class="info-title">{{ goods.title }}</text>
			<view class="info-tags">
				<view class="info-tag" v-for="(tag, i) in goods.tags" :key="i">{{ tag }}</view>
			</view>
			<view class="info-desc">{{ goods.desc }}</view>
		</view>

		<!-- ═══ 动态规格选择：规格名称和值完全由后台配置 ═══ -->
		<view class="spec-card">
			<view v-for="dimension in goods.specDimensions" :key="dimension.id || dimension.name" class="dynamic-spec-group">
				<text class="dynamic-spec-title">{{ dimension.name }}</text>
				<view class="dynamic-spec-options">
					<view v-for="value in dimension.values" :key="value.id || value.value" class="dynamic-spec-option"
						:class="{ selected: selectedSpecs[dimension.name] === value.value, disabled: isSpecValueDisabled(dimension.name, value.value) }"
						@click="selectSpecValue(dimension.name, value.value)">{{ value.value }}</view>
				</view>
			</view>
			<view v-if="!goods.specDimensions?.length" class="spec-row">
				<text class="spec-label">规格</text><text class="spec-value">{{ currentSku?.specLabel || goods.spec || '通用规格' }}</text>
			</view>
			<view class="spec-row">
				<text class="spec-label">已选</text>
				<text class="spec-value">{{ selectedSpecLabel }}</text>
				<text class="spec-stock" :class="{ inStock: currentSku && currentSku.stock > 0 }">
					{{ currentSku ? `${currentSku.stock} ${currentSku.unit}` : '请选择完整规格' }}
				</text>
			</view>
			<view v-if="isInstaller" class="spec-row quantity-row">
				<text class="spec-label">数量</text>
				<view class="quantity-control">
					<view class="quantity-button" :class="{ disabled: quantity <= 1 }" @click="changeQuantity(-1)">−</view>
					<input class="quantity-input" type="number" :value="quantity" @blur="handleQuantityInput" />
					<view class="quantity-button" :class="{ disabled: !currentSku || quantity >= currentSku.stock }" @click="changeQuantity(1)">＋</view>
				</view>
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
				<view class="bar-icon-item" hover-class="hover-press" :hover-stay-time="80" @click="handleService">
					<up-icon name="kefu-ermai" size="22" color="#475569"></up-icon>
					<text class="bar-icon-text">客服</text>
				</view>
				<view v-if="isInstaller" class="bar-icon-item cart-entry" hover-class="hover-press" @click="goCart">
					<up-icon name="shopping-cart" size="22" color="#475569"></up-icon>
					<text class="bar-icon-text">购物车</text><text v-if="cartCount" class="cart-badge">{{ cartCount > 99 ? '99+' : cartCount }}</text>
				</view>
			</view>
			<view class="bar-actions">
				<view v-if="isInstaller" class="action-btn buy" :class="{ disabled: !canAddToCart }" @click="addToCart">{{ adding ? '加入中…' : '加入购物车' }}</view>
				<view v-else class="action-btn buy" hover-class="hover-mask" :hover-stay-time="80" @click="handleService">电话咨询</view>
			</view>
		</view>
		</template>
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
import { consumablesApi, installerMaterialApi } from '@/api/api.js'
import { getAuthUserInfo } from '@/utils/auth-session.js'

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
const selectedSpecs = ref({})
const quantity = ref(1)
const adding = ref(false)
const cartCount = ref(0)
const isInstaller = computed(() => getAuthUserInfo()?.role === 'installer')
const currentSku = computed(() => {
	const skus = goods.value.skus || []
	const dimensions = goods.value.specDimensions || []
	if (!dimensions.length) return skus.find(sku => sku.enabled) || null
	if (dimensions.some(dimension => !selectedSpecs.value[dimension.name])) return null
	return skus.find(sku => sku.enabled && dimensions.every(
		dimension => sku.specValues?.[dimension.name] === selectedSpecs.value[dimension.name])) || null
})
const selectedSpecLabel = computed(() => {
	if (currentSku.value?.specLabel) return currentSku.value.specLabel
	const dimensions = goods.value.specDimensions || []
	if (!dimensions.length) return goods.value.spec || '通用规格'
	const selected = dimensions.filter(d => selectedSpecs.value[d.name]).map(d => `${d.name}：${selectedSpecs.value[d.name]}`)
	return selected.length ? selected.join(' / ') : '请选择规格'
})
const canAddToCart = computed(() => isInstaller.value && currentSku.value && currentSku.value.stock > 0
	&& quantity.value >= 1 && quantity.value <= Math.floor(currentSku.value.stock) && !adding.value)
const carouselImages = computed(() => {
	const images = goods.value.images?.length ? goods.value.images : [goods.value.image]
	return [...new Set(images.filter(img => !isPlaceholderImage(img)))]
})
const displayDetailImages = computed(() => detailImages.value.filter(img => !isPlaceholderImage(img)))
const detailLoading = ref(true)
const detailError = ref(null)

const isPlaceholderImage = (url) => !url || String(url).includes('picsum.photos')
const productIcon = (type) => ({
	copper: 'integral', bracket: 'grid-fill', cable: 'share-fill', refrigerant: 'hourglass-half-fill', aux: 'bag-fill'
}[type] || 'bag-fill')


const setDetailError = (response) => {
	if (response?.code === 403) {
		detailError.value = { title: '暂无访问权限', message: response.msg || '当前账号无权查看该产品' }
		return
	}
	if (response?.code === -1) {
		detailError.value = { title: '网络连接失败', message: response.msg || '请检查网络后重试' }
		return
	}
	detailError.value = { title: '产品加载失败', message: response?.msg || '产品不存在或服务暂时不可用' }
}

const loadDetail = async () => {
	if (!goodsId.value) {
		detailLoading.value = false
		detailError.value = { title: '产品参数有误', message: '未找到需要查看的产品' }
		return
	}
	detailLoading.value = true
	detailError.value = null
	try {
		const res = await consumablesApi.getDetail(goodsId.value)
		if (res.code !== 200) {
			setDetailError(res)
			return
		}
		// 耗材字段已与模板对齐，res.data 直接赋值
		goods.value = res.data || {}
		detailImages.value = (res.data && res.data.detailImages) || []
		uni.setNavigationBarTitle({ title: goods.value.title || '耗材详情' })
		const initial = {}
		;(goods.value.specDimensions || []).forEach(dimension => {
			if ((dimension.values || []).length === 1) initial[dimension.name] = dimension.values[0].value
		})
		selectedSpecs.value = initial
		quantity.value = 1
		if (isInstaller.value) loadCartCount()
	} catch (err) {
		setDetailError({ code: -1, msg: '请求异常，请稍后重试' })
	} finally {
		detailLoading.value = false
	}
}

onLoad((options) => {
	goodsId.value = options?.id || ''
	loadDetail()
})

const isSpecValueDisabled = (name, value) => {
	const dimensions = goods.value.specDimensions || []
	const currentIndex = dimensions.findIndex(dimension => dimension.name === name)
	const next = { [name]: value }
	dimensions.slice(0, currentIndex).forEach(dimension => {
		if (selectedSpecs.value[dimension.name]) next[dimension.name] = selectedSpecs.value[dimension.name]
	})
	return !(goods.value.skus || []).some(sku => sku.enabled && sku.stock > 0
		&& Object.entries(next).every(([key, selected]) => sku.specValues?.[key] === selected))
}

const selectSpecValue = (name, value) => {
	if (isSpecValueDisabled(name, value)) return
	const dimensions = goods.value.specDimensions || []
	const currentIndex = dimensions.findIndex(dimension => dimension.name === name)
	const next = { ...selectedSpecs.value, [name]: value }
	dimensions.slice(currentIndex + 1).forEach(dimension => { delete next[dimension.name] })
	selectedSpecs.value = next
	quantity.value = 1
}

const changeQuantity = (delta) => {
	const max = Math.max(1, Math.floor(currentSku.value?.stock || 1))
	quantity.value = Math.min(max, Math.max(1, quantity.value + delta))
}

const handleQuantityInput = (event) => {
	const max = Math.max(1, Math.floor(currentSku.value?.stock || 1))
	const next = Number(event?.detail?.value)
	quantity.value = Number.isInteger(next) ? Math.min(max, Math.max(1, next)) : 1
}

const loadCartCount = async () => {
	const res = await installerMaterialApi.getCart()
	if (res.code === 200) cartCount.value = Number(res.data?.totalQuantity || 0)
}

const addToCart = async () => {
	if (adding.value) return
	if (!currentSku.value) return uni.showToast({ title: '请先选择完整规格', icon: 'none' })
	if (!canAddToCart.value) return uni.showToast({ title: '数量不能超过当前库存', icon: 'none' })
	adding.value = true
	try {
		const res = await installerMaterialApi.addCartItem(currentSku.value.id, quantity.value)
		if (res.code === 200) {
			cartCount.value = Number(res.data?.totalQuantity || 0)
			uni.showToast({ title: '已加入购物车', icon: 'success' })
		}
	} finally { adding.value = false }
}

const goCart = () => uni.navigateTo({ url: '/packageA/material-cart/material-cart' })

// 分享商品详情给好友
onShareAppMessage(() => ({
	title: goods.value.title || '鑫立创配件详情',
	path: `/packageA/goos-details/goos-details?id=${goodsId.value}`
}))

const goHome = () => {
	uni.switchTab({
		url: '/pages/index/index'
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

.detail-state {
	min-height: calc(100vh - 88rpx);
	padding: 120rpx 48rpx 80rpx;
	box-sizing: border-box;
	display: flex;
	flex-direction: column;
	align-items: center;
	text-align: center;
}
.state-icon { width: 96rpx; height: 96rpx; border-radius: 50%; background: #fff7e6; display: flex; align-items: center; justify-content: center; }
.state-title { margin-top: 24rpx; font-size: 30rpx; font-weight: 700; color: $text-main; }
.state-desc { margin-top: 10rpx; font-size: 24rpx; color: $text-light; line-height: 1.6; }
.state-action { min-width: 180rpx; height: 76rpx; margin-top: 28rpx; padding: 0 34rpx; box-sizing: border-box; display: flex; align-items: center; justify-content: center; border: 2rpx solid $primary; border-radius: 38rpx; color: $primary; background: #fff; font-size: 24rpx; font-weight: 600; }

/* 封面图 */
.cover-section {
	position: relative;
	width: 100%;
	height: 420rpx;
	overflow: hidden;
}

.cover-swiper {
	width: 100%;
	height: 100%;
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

.dynamic-spec-group { padding: 26rpx 0 8rpx; border-bottom: 1rpx solid #f0f1f3; }
.dynamic-spec-title { display: block; margin-bottom: 18rpx; color: $text-main; font-size: 27rpx; font-weight: 650; }
.dynamic-spec-options { display: flex; flex-wrap: wrap; gap: 14rpx; }
.dynamic-spec-option { min-width: 104rpx; padding: 13rpx 20rpx; box-sizing: border-box; border: 2rpx solid transparent; border-radius: 12rpx; background: #f3f6fa; color: $text-sub; font-size: 24rpx; text-align: center; }
.dynamic-spec-option.selected { border-color: $primary; background: #eaf3ff; color: $primary; font-weight: 650; }
.dynamic-spec-option.disabled { background: #f7f7f8; color: #c4cbd4; text-decoration: line-through; }
.quantity-row { justify-content: space-between; }
.quantity-control { display: flex; align-items: center; overflow: hidden; border: 1rpx solid #dce4ed; border-radius: 12rpx; }
.quantity-button { width: 72rpx; height: 72rpx; display: flex; align-items: center; justify-content: center; background: #f4f7fb; color: $text-main; font-size: 32rpx; }
.quantity-button.disabled { color: #cbd2da; }
.quantity-input { width: 92rpx; height: 72rpx; border-left: 1rpx solid #dce4ed; border-right: 1rpx solid #dce4ed; color: $text-main; font-size: 26rpx; text-align: center; }

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
	height: calc(112rpx + env(safe-area-inset-bottom));
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
.cart-entry { position: relative; }
.cart-badge { position: absolute; top: -10rpx; right: -14rpx; min-width: 30rpx; height: 30rpx; padding: 0 6rpx; box-sizing: border-box; border-radius: 16rpx; background: #ef4444; color: #fff; font-size: 18rpx; line-height: 30rpx; text-align: center; }

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
	height: 88rpx;
	margin-right: 24rpx;
	border-radius: 44rpx;
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
.action-btn.disabled { background: #b8c8da; box-shadow: none; color: #eef3f8; }

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
		width: 72rpx;
		height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background: #f4f7fb;
		border-radius: 12rpx;
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

</style>
