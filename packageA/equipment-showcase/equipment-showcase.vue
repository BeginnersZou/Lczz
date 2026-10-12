<template>
	<view class="equipment-page">
		<view class="page-heading"><text class="page-title">设备展示</text><text class="page-subtitle">分类选型 · 规格与库存清晰可查</text></view>
		<view class="search-bar">
			<up-icon name="search" size="18" color="#7e91a4"></up-icon>
			<input v-model="searchKeyword" class="search-input" placeholder="搜索设备名称、规格" confirm-type="search" @confirm="performSearch" />
			<view v-if="searchKeyword" @click="clearSearch"><up-icon name="close-circle-fill" size="17" color="#a4b0bd"></up-icon></view>
			<view class="search-button" @click="performSearch">搜索</view>
		</view>
		<view class="content-card">
			<scroll-view scroll-x class="tab-scroll" :show-scrollbar="false" scroll-with-animation :scroll-into-view="activeTabId" enable-flex enhanced>
				<view class="tab-list"><view v-for="(tab, index) in tabs" :key="tab.type" :id="`equipment-tab-${index}`"
					class="tab-item" :class="{ active: selectedType === tab.type }" @click="switchTab(tab.type)">{{ tab.name }}</view></view>
			</scroll-view>
			<view class="category-error" v-if="categoryError"><text>{{ categoryError }}</text><text class="retry-link" @click="refreshCatalog(true)">重新加载</text></view>
			<ProductGrid :items="products" :loading="categoryLoading || listLoading" fallback-category="设备" @select="openDetail">
				<view class="empty-state" v-if="products.length === 0 && !categoryLoading && !listLoading">
					<up-icon :name="listError || categoryError ? 'warning' : 'search'" size="34" color="#8b9aaa"></up-icon>
					<text class="empty-title">{{ listError ? '设备加载失败' : categoryError ? '分类加载失败' : '暂无设备可展示' }}</text>
					<text class="empty-text">{{ listError || categoryError || (root ? '换个关键词或分类试试' : '请稍后再来查看') }}</text>
					<view class="empty-action" v-if="listError || categoryError" @click="retry">重新加载</view>
					<view class="empty-action" v-else-if="root && (activeKeyword || selectedType !== 'all')" @click="showAll">查看全部设备</view>
				</view>
			</ProductGrid>
			<view class="load-status" v-if="products.length > 0">
				<text class="retry-link" v-if="listError" @click="retryList">{{ listError }}，点击重试</text>
				<text v-else>{{ categoryLoading || listLoading ? '加载中…' : loadStatus === 'noMore' ? '已展示全部' : '上拉加载更多' }}</text>
			</view>
		</view>
		<view class="safe-area"></view>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow, onUnload, onReachBottom, onPullDownRefresh, onShareAppMessage } from '@dcloudio/uni-app'
import { consumablesApi } from '@/api/api.js'
import ProductGrid from '@/components/ProductGrid.vue'
import { buildEquipmentCatalog, equipmentQuery } from './equipment-catalog.js'

const root = ref(null)
const tabs = ref([{ name: '全部', type: 'all' }])
const selectedType = ref('all')
const activeTabId = computed(() => `equipment-tab-${Math.max(0, tabs.value.findIndex(tab => tab.type === selectedType.value))}`)
const searchKeyword = ref('')
const activeKeyword = ref('')
const products = ref([])
const page = ref(1)
const total = ref(0)
const categoryLoading = ref(false)
const listLoading = ref(false)
const categoryError = ref('')
const listError = ref('')
const loadStatus = ref('')
let categoryRevision = 0
let listRevision = 0
let catalogSignature = ''
let firstShow = true
let hasLoadedProducts = false
let pendingRefresh = false
let unloaded = false

const fetchProducts = async (refresh = false) => {
	if (unloaded) return false
	if (categoryLoading.value) {
		if (refresh) { pendingRefresh = true; ++listRevision; products.value = []; listLoading.value = false }
		return false
	}
	if (!root.value || !refresh && (listLoading.value || loadStatus.value === 'noMore')) return false
	const revision = refresh ? ++listRevision : listRevision
	if (refresh) { products.value = []; page.value = 1; total.value = 0; listError.value = ''; loadStatus.value = '' }
	const tab = tabs.value.find(item => item.type === selectedType.value)
	const params = equipmentQuery(root.value, tab, activeKeyword.value, page.value)
	if (!params) return false
	listLoading.value = true
	try {
		const response = await consumablesApi.getList(params, { loading: false, silent: true })
		if (unloaded || revision !== listRevision) return false
		if (response.code !== 200) {
			listError.value = response.msg || '设备加载失败，请重试'; return false
		}
		if (!Array.isArray(response.data?.list)) { listError.value = '设备数据异常，请重试'; return false }
		const rows = response.data.list
		products.value = refresh ? rows : [...products.value, ...rows]
		total.value = Number(response.data.total) || 0
		page.value++
		loadStatus.value = products.value.length >= total.value || rows.length === 0 ? 'noMore' : ''
		listError.value = ''
		hasLoadedProducts = true
		return true
	} catch { if (revision === listRevision && !unloaded) listError.value = '网络连接失败，请重试'; return false }
	finally { if (revision === listRevision && !unloaded) listLoading.value = false }
}

const refreshCatalog = async (force = true) => {
	if (unloaded || !force && categoryLoading.value) return false
	const revision = ++categoryRevision
	if (force) { ++listRevision; listLoading.value = false }
	categoryLoading.value = true
	categoryError.value = ''
	try {
		const response = await consumablesApi.getCategories({ loading: false, silent: true })
		if (unloaded || revision !== categoryRevision) return false
		if (response.code !== 200) { categoryError.value = response.msg || '分类加载失败，请重试'; return false }
		if (!Array.isArray(response.data)) { categoryError.value = '分类数据异常，请重试'; return false }
		const catalog = buildEquipmentCatalog(response.data)
		const signature = JSON.stringify([catalog.root?.id, catalog.root?.code, catalog.tabs])
		const changed = signature !== catalogSignature
		catalogSignature = signature
		root.value = catalog.root
		tabs.value = catalog.tabs
		if (!tabs.value.some(tab => tab.type === selectedType.value)) selectedType.value = 'all'
		categoryLoading.value = false
		if (!root.value) {
			++listRevision; products.value = []; total.value = 0; page.value = 1; listLoading.value = false; listError.value = ''; loadStatus.value = ''; hasLoadedProducts = false; pendingRefresh = false
			return true
		}
		if (force || changed || pendingRefresh || !hasLoadedProducts) { pendingRefresh = false; return await fetchProducts(true) }
		return true
	} catch { if (revision === categoryRevision && !unloaded) categoryError.value = '分类加载失败，请检查网络后重试'; return false }
	finally { if (revision === categoryRevision && !unloaded) categoryLoading.value = false }
}

const performSearch = () => { activeKeyword.value = searchKeyword.value.trim(); return fetchProducts(true) }
const clearSearch = () => { searchKeyword.value = ''; activeKeyword.value = ''; return fetchProducts(true) }
const showAll = () => { selectedType.value = 'all'; return clearSearch() }
const switchTab = type => {
	if (type === selectedType.value || !tabs.value.some(tab => tab.type === type)) return
	selectedType.value = type
	return fetchProducts(true)
}
const retryList = () => fetchProducts(products.value.length === 0)
const retry = () => categoryError.value || !root.value ? refreshCatalog(true) : retryList()
const openDetail = item => uni.navigateTo({ url: `/packageA/goos-details/goos-details?id=${item.id}` })

onLoad(() => refreshCatalog(true))
onShow(() => { if (firstShow) { firstShow = false; return }; return refreshCatalog(false) })
onUnload(() => { unloaded = true; ++categoryRevision; ++listRevision })
onReachBottom(() => fetchProducts())
onPullDownRefresh(async () => { try { await refreshCatalog(true) } finally { uni.stopPullDownRefresh() } })
onShareAppMessage(() => ({ title: '力创之尊 — 设备展示', path: '/packageA/equipment-showcase/equipment-showcase' }))
</script>

<style scoped lang="scss">
@import '@/uni.scss';
.equipment-page { min-height: 100vh; padding: 28rpx 24rpx 0; box-sizing: border-box; background: $bg-page; }
.page-heading { margin-bottom: 24rpx; }.page-title { display: block; color: $text-main; font-size: 38rpx; font-weight: 750; }
.page-subtitle { display: block; color: $text-light; font-size: 23rpx; margin-top: 10rpx; }
.search-bar { display: flex; align-items: center; gap: 14rpx; padding: 16rpx 18rpx; background: #fff; border: 1rpx solid #e8eef5; border-radius: 18rpx; margin-bottom: 24rpx; }
.search-input { flex: 1; min-width: 0; font-size: 25rpx; }.search-button { color: #fff; background: $primary; padding: 12rpx 22rpx; border-radius: 12rpx; font-size: 24rpx; }
.content-card { background: #fff; border-radius: 24rpx; border: 1rpx solid #e8eef5; overflow: hidden; padding-bottom: 24rpx; }
.tab-scroll { width: 100%; border-bottom: 1rpx solid #edf1f5; }.tab-list { display: inline-flex; min-width: 100%; padding: 0 16rpx; white-space: nowrap; box-sizing: border-box; }
.tab-item { flex-shrink: 0; min-width: 112rpx; height: 88rpx; padding: 0 20rpx; box-sizing: border-box; display: flex; align-items: center; justify-content: center; font-size: 25rpx; color: $text-sub; position: relative; }
.tab-item.active { color: $primary; font-weight: 700; }.tab-item.active::after { content: ''; position: absolute; width: 32rpx; height: 5rpx; border-radius: 4rpx; bottom: 5rpx; left: 50%; transform: translateX(-50%); background: $primary; }
.category-error { padding: 20rpx 24rpx; display: flex; justify-content: space-between; gap: 16rpx; color: #b45309; background: #fff7e6; font-size: 22rpx; }
.retry-link { color: $primary; flex-shrink: 0; }.empty-state { width: 100%; padding: 90rpx 0 70rpx; display: flex; flex-direction: column; align-items: center; }
.empty-title { font-size: 28rpx; color: $text-main; font-weight: 650; margin-top: 20rpx; }.empty-text { font-size: 23rpx; color: $text-light; margin-top: 10rpx; }
.empty-action { margin-top: 24rpx; padding: 13rpx 30rpx; color: $primary; background: #eaf3ff; border-radius: 28rpx; font-size: 23rpx; }
.load-status { padding: 30rpx 24rpx 0; text-align: center; font-size: 22rpx; color: $text-light; }.safe-area { height: calc(32rpx + env(safe-area-inset-bottom)); }
</style>
