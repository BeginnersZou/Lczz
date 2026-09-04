<template>
	<view class="page">
		<view class="page-heading"><view><text class="title">耗材购物车</text><text class="subtitle">按具体规格提交取货申请</text></view><view v-if="items.length" class="text-action" @click="confirmClear">清空</view></view>
		<view v-if="loading" class="state"><up-loading-icon color="#0b63ce"></up-loading-icon><text>正在加载购物车</text></view>
		<view v-else-if="loadError" class="state"><up-icon name="warning" size="36" color="#d97706"></up-icon><text>{{ loadError }}</text><view class="outline-button small" @click="loadCart">重新加载</view></view>
		<view v-else-if="!items.length" class="state"><up-icon name="shopping-cart" size="48" color="#cbd5e1"></up-icon><text class="state-title">购物车还是空的</text><text class="state-desc">请从耗材详情选择具体规格加入</text><view class="primary-button small" @click="goHome">去选择耗材</view></view>
		<view v-else class="cart-list">
			<view v-for="item in items" :key="item.id" class="cart-card" :class="{ invalid: !item.available }">
					<view class="item-main"><view><text class="item-name">{{ item.productName }}</text><text class="item-spec">{{ item.specLabel || '通用规格' }}</text><text class="item-stock">库存 {{ item.stock }} {{ item.unit }}</text></view><view class="danger-outline-button" @click="confirmRemove(item)">删除</view></view>
				<view v-if="!item.available" class="invalid-tip">{{ item.unavailableReason || '该规格暂不可用' }}</view>
					<view class="item-footer"><text class="quantity-label">数量</text><view class="quantity-control"><view class="quantity-button" :class="{ disabled: !item.available || item.quantity <= 1 || savingId === item.id }" @click="changeQuantity(item, -1)">−</view><input class="quantity-input" type="number" :value="item.quantity" :disabled="!item.available || savingId === item.id" @blur="inputQuantity(item, $event)"/><view class="quantity-button" :class="{ disabled: !item.available || item.quantity >= item.stock || savingId === item.id }" @click="changeQuantity(item, 1)">＋</view></view></view>
			</view>
		</view>
		<view v-if="items.length" class="footer"><view><text class="footer-label">已选</text><text class="footer-count">{{ items.length }} 种规格</text></view><view class="primary-button submit" :class="{ disabled: !canSubmit }" @click="confirmSubmit">{{ submitting ? '提交中…' : '提交取货申请' }}</view></view>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { installerMaterialApi } from '@/api/api.js'

const items = ref([])
const loading = ref(true)
const loadError = ref('')
const savingId = ref(null)
const submitting = ref(false)
const submitRequestId = ref('')
const canSubmit = computed(() => !submitting.value && items.value.length > 0 && items.value.every(item => item.available && item.quantity > 0 && item.quantity <= item.stock))

const applyCart = data => { items.value = data?.items || [] }
const loadCart = async () => {
	loading.value = true; loadError.value = ''
	try { const res = await installerMaterialApi.getCart(); if (res.code === 200) applyCart(res.data); else loadError.value = res.msg || '购物车加载失败' }
	catch { loadError.value = '网络异常，请稍后重试' } finally { loading.value = false }
}
onShow(loadCart)

const persistQuantity = async (item, quantity) => {
		if (savingId.value || !item.available) return
		const max = Math.floor(Number(item.stock || 0)); const next = Math.min(max, Math.max(1, Number(quantity) || 1))
		if (max < 1) return uni.showToast({ title: '该规格暂无可用库存', icon: 'none' })
	if (next === item.quantity) return
	savingId.value = item.id
	try { const res = await installerMaterialApi.updateCartItem(item.id, next); if (res.code === 200) applyCart(res.data) }
	finally { savingId.value = null }
}
const changeQuantity = (item, delta) => {
	if (savingId.value || !item.available) return
	const next = item.quantity + delta
	if (next < 1 || next > item.stock) return
	persistQuantity(item, next)
}
const inputQuantity = (item, event) => persistQuantity(item, Number(event?.detail?.value))
const confirmRemove = item => uni.showModal({ title: '删除耗材', content: `确定从购物车删除“${item.productName} ${item.specLabel || ''}”吗？`, confirmColor: '#dc2626', success: async result => { if (!result.confirm) return; const res = await installerMaterialApi.removeCartItem(item.id); if (res.code === 200) applyCart(res.data) } })
const confirmClear = () => uni.showModal({ title: '清空购物车', content: '确定删除购物车内全部耗材吗？', confirmColor: '#dc2626', success: async result => { if (!result.confirm) return; const res = await installerMaterialApi.clearCart(); if (res.code === 200) applyCart(res.data) } })
const confirmSubmit = () => {
	if (!canSubmit.value) return uni.showToast({ title: '请处理不可用或超库存的耗材', icon: 'none' })
	uni.showModal({ title: '提交取货申请', content: `将提交 ${items.value.length} 种耗材规格，请再次核对各规格数量。提交后不自动扣减库存。`, confirmText: '确认提交', success: async result => {
		if (!result.confirm || submitting.value) return
		submitting.value = true
		if (!submitRequestId.value) submitRequestId.value = `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
		try { const res = await installerMaterialApi.submitOrder(submitRequestId.value); if (res.code === 200) { submitRequestId.value = ''; uni.showModal({ title: '提交成功', content: `取货单号：${res.data.orderNo}`, showCancel: false, success: () => uni.redirectTo({ url: `/packageA/self-order-detail/self-order-detail?id=${res.data.id}` }) }) } }
		finally { submitting.value = false }
	} })
}
const goHome = () => uni.switchTab({ url: '/pages/index/index' })
</script>

<style scoped lang="scss">
	.page { min-height: 100vh; box-sizing: border-box; padding: 28rpx 24rpx 196rpx; background: #f4f7fb; }
	.page-heading { display:flex; align-items:center; justify-content:space-between; margin-bottom:24rpx; }.title{display:block;color:#142434;font-size:36rpx;font-weight:750}.subtitle{display:block;margin-top:8rpx;color:#8291a3;font-size:23rpx}.text-action{min-height:64rpx;padding:0 12rpx;display:flex;align-items:center;color:#dc2626;font-size:24rpx}.danger-outline-button{min-width:112rpx;height:64rpx;padding:0 18rpx;box-sizing:border-box;display:flex;align-items:center;justify-content:center;border:2rpx solid #fecaca;border-radius:14rpx;background:#fff7f7;color:#dc2626;font-size:23rpx}.state{min-height:520rpx;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16rpx;color:#8b9aaa;font-size:24rpx}.state-title{color:#334155;font-size:28rpx;font-weight:650}.state-desc{font-size:23rpx}.cart-list{display:flex;flex-direction:column;gap:20rpx}.cart-card{padding:28rpx;background:#fff;border:2rpx solid transparent;border-radius:22rpx;box-shadow:0 4rpx 18rpx rgba(30,41,59,.05)}.cart-card.invalid{border-color:#fecaca;background:#fffafa}.item-main,.item-footer{display:flex;align-items:center;justify-content:space-between}.item-name{display:block;color:#142434;font-size:29rpx;font-weight:700}.item-spec{display:block;margin-top:10rpx;color:#475569;font-size:24rpx}.item-stock{display:block;margin-top:8rpx;color:#94a3b8;font-size:22rpx}.invalid-tip{margin-top:18rpx;padding:14rpx 18rpx;border-radius:12rpx;background:#fff1f2;color:#be123c;font-size:22rpx}.item-footer{margin-top:24rpx;padding-top:20rpx;border-top:1rpx solid #edf1f5}.quantity-label{color:#64748b;font-size:24rpx}.quantity-control{display:flex;overflow:hidden;border:1rpx solid #dbe3ec;border-radius:14rpx}.quantity-button{width:72rpx;height:72rpx;display:flex;align-items:center;justify-content:center;background:#f4f7fb;color:#142434;font-size:32rpx}.quantity-button.disabled{color:#cbd5e1;background:#f8fafc}.quantity-input{width:92rpx;height:72rpx;border-left:1rpx solid #dbe3ec;border-right:1rpx solid #dbe3ec;text-align:center;font-size:26rpx}.footer{position:fixed;left:0;right:0;bottom:0;z-index:20;height:calc(136rpx + env(safe-area-inset-bottom));padding:20rpx 24rpx env(safe-area-inset-bottom);box-sizing:border-box;display:flex;align-items:center;justify-content:space-between;background:#fff;box-shadow:0 -4rpx 20rpx rgba(30,41,59,.08)}.footer-label{color:#64748b;font-size:22rpx}.footer-count{display:block;margin-top:3rpx;color:#142434;font-size:29rpx;font-weight:700}.primary-button,.outline-button{height:88rpx;padding:0 34rpx;border-radius:22rpx;display:flex;align-items:center;justify-content:center;font-size:27rpx;font-weight:650}.primary-button{background:#0b63ce;color:#fff}.primary-button.disabled{background:#b8c8da;color:#eef3f8}.outline-button{border:2rpx solid #0b63ce;background:#fff;color:#0b63ce}.small{height:76rpx;margin-top:12rpx}.submit{min-width:320rpx}
</style>
