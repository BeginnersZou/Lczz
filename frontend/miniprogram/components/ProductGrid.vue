<template>
	<view class="product-grid">
		<template v-if="loading && items.length === 0">
			<view class="skeleton-card" v-for="i in 4" :key="i">
				<view class="skeleton-img"></view>
				<view class="skeleton-body"><view class="skeleton-line long"></view><view class="skeleton-line short"></view></view>
			</view>
		</template>
		<template v-else>
			<view class="product-card" hover-class="hover-card" :hover-stay-time="80"
				v-for="(item, index) in items" :key="item.id || index" @click="emit('select', item)">
				<view class="product-visual" :class="`visual-${item.type || 'aux'}`">
					<LazyProductImage v-if="item.image" class="product-cover" :src="item.image" mode="aspectFill" :eager="index < 4" />
					<view class="visual-ring ring-one"></view><view class="visual-ring ring-two"></view>
					<up-icon v-if="!item.image" :name="productIcon(item.type)" size="40" color="#ffffff"></up-icon>
					<text class="visual-label">{{ item.category || categoryName(item.type) }}</text>
					<view class="product-tag" :style="{ background: item.tagColor }" v-if="item.tag">{{ item.tag }}</view>
				</view>
				<view class="product-body">
					<text class="product-title">{{ item.title }}</text>
					<text class="product-spec">库存 {{ item.stockSummary || ((item.stock ?? 0) + ' ' + (item.unit || '件')) }} · {{ item.skuCount > 1 ? (item.skuCount + '种规格') : (item.spec || '通用规格') }}</text>
					<view class="product-meta"><text class="product-sales">{{ (item.skus || []).some(sku => sku.enabled && sku.stock > 0) ? '有库存' : '暂无库存' }}</text></view>
				</view>
			</view>
			<slot></slot>
		</template>
	</view>
</template>

<script setup>
import LazyProductImage from '@/components/LazyProductImage.vue'
const props = defineProps({
	items: { type: Array, default: () => [] },
	loading: { type: Boolean, default: false },
	fallbackCategory: { type: String, default: '空调配件' }
})
const emit = defineEmits(['select'])
const productIcon = type => ({ copper: 'integral', bracket: 'grid-fill', cable: 'share-fill', refrigerant: 'hourglass-half-fill', aux: 'bag-fill' }[type] || 'bag-fill')
const categoryName = type => ({ copper: '铜管类', bracket: '支架类', cable: '电缆线类', refrigerant: '冷媒类', aux: '辅材类' }[type] || props.fallbackCategory)
</script>

<style scoped lang="scss">
@import '@/uni.scss';
.product-grid { display: flex; flex-wrap: wrap; gap: 20rpx; padding: 24rpx 24rpx 0; }
.product-card { width: calc(50% - 10rpx); border: 1rpx solid #edf1f5; border-radius: 22rpx; overflow: hidden; box-sizing: border-box; background: #fff; }
.product-visual { height: 184rpx; position: relative; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.product-cover { position: absolute; inset: 0; width: 100%; height: 100%; }
.product-cover ~ .visual-ring { display: none; }
.product-cover ~ .visual-label { padding: 5rpx 10rpx; border-radius: 8rpx; background: rgba(15, 23, 42, .62); color: #fff; }
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
.product-sales { color: $text-light; font-size: 19rpx; }
.skeleton-card { width: calc(50% - 10rpx); border-radius: 22rpx; overflow: hidden; border: 1rpx solid #edf1f5; box-sizing: border-box; }
.skeleton-img { width: 100%; height: 184rpx; }.skeleton-body { padding: 18rpx; }
.skeleton-line { height: 22rpx; margin-bottom: 12rpx; }.skeleton-line.long { width: 100%; }.skeleton-line.short { width: 55%; }
</style>
