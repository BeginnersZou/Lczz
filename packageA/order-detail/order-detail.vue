<template>
	<view class="page">
		<!-- ═══ 订单信息卡片 ═══ -->
		<view class="order-card">
			<view class="order-top">
				<view class="order-service">
					<image class="order-img" :src="orderInfo.image" mode="aspectFill" lazy-load></image>
					<view class="order-info">
						<text class="order-name">{{ orderInfo.serviceName }}</text>
						<text class="order-product">{{ orderInfo.productName }}</text>
						<text class="order-spec">{{ orderInfo.productSpec }}</text>
						<text class="order-qty">× {{ orderInfo.quantity }}</text>
					</view>
				</view>
				<view class="order-status" :class="statusClass">
					<text>{{ orderInfo.status }}</text>
				</view>
			</view>

			<view class="info-divider"></view>

			<view class="info-row">
				<text class="info-label">订单编号</text>
				<text class="info-value">{{ orderInfo.orderNo }}</text>
				<view class="copy-btn" @click="copyOrderNo(orderInfo.orderNo)">
					<up-icon name="file-text" size="16" color="#0b63ce"></up-icon>
				</view>
			</view>
			<view class="info-row">
				<text class="info-label">收货人</text>
				<text class="info-value">{{ orderInfo.name }} {{ orderInfo.phone }}</text>
			</view>
			<view class="info-row">
				<text class="info-label">地址</text>
				<text class="info-value">{{ orderInfo.address }}</text>
			</view>
			<view class="info-row">
				<text class="info-label">上门时间</text>
				<text class="info-value highlight">{{ orderInfo.visitTime }}</text>
			</view>
		</view>

		<!-- ═══ 工具清单 ═══ -->
		<view class="section-card">
			<view class="section-header">
				<view class="section-title-wrap">
					<text class="required" v-if="!isReadonly">*</text>
					<text class="section-title">工具清单</text>
				</view>
				<view class="add-btn" @click="openToolPopup" v-if="!isReadonly">
					<up-icon name="plus" size="14" color="#fff"></up-icon>
					<text>添加</text>
				</view>
			</view>

			<view class="tool-list" v-if="toolList.length > 0">
				<view class="tool-item" v-for="(tool, index) in toolList" :key="index">
					<view class="tool-index">{{ index + 1 }}</view>
					<view class="tool-content">
						<text class="tool-name">{{ tool.title }}</text>
						<view class="tool-meta">
							<text class="tool-spec-text">规格：{{ tool.spec }}</text>
						</view>
						<view class="tool-meta">
							<text class="tool-qty-text">数量：</text>
							<view class="qty-control" v-if="!isReadonly">
								<view class="qty-btn" @click="changeQty(index, -1)"><text>-</text></view>
								<text class="qty-num">{{ tool.qty }}</text>
								<view class="qty-btn" @click="changeQty(index, 1)"><text>+</text></view>
							</view>
							<text class="tool-qty-value" v-else>{{ tool.qty }}</text>
						</view>
					</view>
					<view class="tool-delete" @click="deleteTool(index)" v-if="!isReadonly">
						<up-icon name="trash" size="18" color="#ff4d4f"></up-icon>
					</view>
				</view>
			</view>

			<view class="tool-empty" v-else>
				<up-icon name="file-text" size="50" color="#ddd"></up-icon>
				<text class="tool-empty-text" v-if="!isReadonly">点击"添加"选择工具</text>
				<text class="tool-empty-text" v-else>暂无工具记录</text>
			</view>
		</view>

		<!-- ═══ 完成情况 ═══ -->
		<view class="section-card">
			<view class="section-header">
				<view class="section-title-wrap">
					<text class="required" v-if="!isReadonly">*</text>
					<text class="section-title">完成情况</text>
				</view>
			</view>
			<view class="textarea-wrap" v-if="!isReadonly">
				<textarea class="complete-textarea" v-model="completeText" placeholder="请填写安装完成情况..."
					placeholder-class="placeholder-style" maxlength="500"></textarea>
				<text class="text-count">{{ completeText.length }}/500</text>
			</view>
			<view class="complete-readonly" v-else>
				<text class="complete-text">{{ completeText || '暂无完成情况记录' }}</text>
			</view>
		</view>

		<!-- ═══ 安装图片 ═══ -->
		<view class="section-card">
			<view class="section-header">
				<view class="section-title-wrap">
					<text class="section-title">{{ isReadonly ? '安装图片' : '上传安装图片' }}</text>
					<text class="upload-tip" v-if="!isReadonly">（最多9张）</text>
				</view>
			</view>

			<view class="image-grid" v-if="imageList.length > 0">
				<view class="image-item" v-for="(img, index) in imageList" :key="index">
					<image class="preview-img" :src="img" mode="aspectFill" @click="previewImage(index)"></image>
					<view class="image-delete" @click.stop="deleteImage(index)" v-if="!isReadonly">
						<up-icon name="close" size="12" color="#fff"></up-icon>
					</view>
				</view>
				<view class="image-add" v-if="!isReadonly && imageList.length < 9" @click="chooseImage">
					<up-icon name="plus" size="32" color="#ccc"></up-icon>
					<text class="add-text">上传图片</text>
				</view>
			</view>

			<view class="image-add" v-else-if="!isReadonly" @click="chooseImage">
				<up-icon name="plus" size="32" color="#ccc"></up-icon>
				<text class="add-text">上传图片</text>
			</view>

			<view class="tool-empty" v-else>
				<up-icon name="photo" size="50" color="#ddd"></up-icon>
				<text class="tool-empty-text">暂无安装图片</text>
			</view>
		</view>

		<!-- ═══ 用户评价 ═══ -->
		<view class="section-card" v-if="isReadonly">
			<view class="section-header">
				<view class="section-title-wrap">
					<text class="section-title">{{ userRole === 'master' ? '用户评价' : '我的评价' }}</text>
				</view>
			</view>

			<view class="evaluation-content" v-if="evaluation">
				<view class="star-display">
					<up-icon v-for="i in 5" :key="i" :name="i <= evaluation.score ? 'star-fill' : 'star'"
						size="32" :color="i <= evaluation.score ? '#ff9800' : '#ddd'"></up-icon>
					<text class="star-label">{{ evaluation.labels && evaluation.labels[0] }}</text>
				</view>
				<text class="evaluate-text">{{ evaluation.content }}</text>
				<view class="evaluate-images" v-if="evaluation.images && evaluation.images.length > 0">
					<image class="evaluate-img" v-for="(img, idx) in evaluation.images" :key="idx" :src="img"
						mode="aspectFill" @click="previewEvaluateImage(idx)"></image>
				</view>
			</view>

			<view class="tool-empty" v-else>
				<up-icon name="chat" size="50" color="#ddd"></up-icon>
				<text class="tool-empty-text">评价内容为空</text>
			</view>
		</view>

		<view class="bottom-space"></view>

		<!-- ═══ 底部提交按钮 ═══ -->
		<view class="submit-bar" v-if="!isReadonly">
			<view class="submit-btn" :class="{ disabled: !canSubmit }" @click="handleSubmit">
				<text>完成提交</text>
			</view>
		</view>

		<!-- ═══ 工具选择弹出框 ═══ -->
		<up-popup :show="showToolPopup" mode="bottom" round="20" :closeOnClickOverlay="true" @close="closeToolPopup"
			:customStyle="{ width: '100%' }">
			<view class="tool-popup" :style="{ height: popupHeight + 'px' }">
				<!-- 头部 -->
				<view class="popup-header">
					<text class="popup-title">选择工具</text>
					<view class="popup-close" @click="closeToolPopup">
						<up-icon name="close" size="20" color="#999"></up-icon>
					</view>
				</view>

				<!-- 搜索框 -->
				<view class="popup-search">
					<up-search v-model="searchKeyword" placeholder="搜索工具名称" :showAction="false" bgColor="#f5f5f5"
						shape="round" height="40">
					</up-search>
					<view class="search-btn">搜索</view>
					<!-- <up-button style="width:160rpx;height:80rpx" type="primary" plain shape="circle"   text="搜索"></up-button> -->
				</view>

				<!-- 分类标签 — 小程序scroll-x必须用inline-block -->
				<scroll-view scroll-x class="category-bar" :show-scrollbar="false" enhanced :bounces="false">
					<view class="category-scroll-inner">
						<view class="category-item" :class="{ active: currentCategory === index }"
							v-for="(cat, index) in categories" :key="index" @click="switchCategory(index)">
							<text>{{ cat }}</text>
						</view>
					</view>
				</scroll-view>

				<!-- 工具列表 — 动态高度，内部滚动 -->
				<scroll-view scroll-y class="popup-list" :style="{ height: popupListHeight + 'px' }">
					<!-- 两行式布局：上行=图片+名称，下行=规格+价格+操作 -->
					<view class="popup-tool-card" v-for="(tool, index) in filteredTools" :key="index">
						<view class="popup-tool-row1">
							<image class="popup-tool-img" :src="tool.image" mode="aspectFill"></image>
							<text class="popup-tool-name">{{ tool.title }}</text>
						</view>
						<view class="popup-tool-row2">
							<text class="popup-tool-spec">{{ tool.spec }}</text>
							<text class="popup-tool-price">¥{{ tool.price }}</text>
							<view class="popup-tool-action">
								<view class="popup-qty-wrap" v-if="getToolQty(tool) > 0">
									<view class="popup-qty-btn" @click="changePopupQty(tool, -1)"><text>-</text></view>
									<text class="popup-qty-num">{{ getToolQty(tool) }}</text>
									<view class="popup-qty-btn" @click="changePopupQty(tool, 1)"><text>+</text></view>
								</view>
								<view class="popup-add-btn" v-else @click="addToolToCart(tool)">
									<text class="popup-add-label">添加</text>
								</view>
							</view>
						</view>
					</view>
					<view class="popup-empty" v-if="filteredTools.length === 0">
						<up-icon name="empty-list" size="60" color="#9aa8b6"></up-icon>
						<text class="popup-empty-text">暂无相关工具</text>
					</view>
					<view style="height: 20rpx;"></view>
				</scroll-view>

				<!-- 底部确认 -->
				<view class="popup-footer">
					<text class="popup-selected">已选 {{ selectedCount }} 件</text>
					<view class="popup-confirm-btn" @click="confirmTools">
						<text>确认添加</text>
					</view>
				</view>
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
		onLoad
	} from '@dcloudio/uni-app'
	import {
	orderApi,
	consumablesApi,
	authApi,
	evaluationApi
} from '@/api/api.js'

	// ===== 动态计算弹出框高度 =====
	const sysInfo = uni.getSystemInfoSync()
	const popupHeight = ref(Math.floor(sysInfo.windowHeight * 0.7))
	// 列表高度 = 弹出框总高 - 头部(50) - 搜索(60) - 分类(50) - 底部(60)
	const popupListHeight = ref(Math.floor(sysInfo.windowHeight * 0.7 - 220))

	// 订单 ID（onLoad 时由路由参数获取）
	const orderId = ref('')

	const orderInfo = ref({
		serviceName: '',
		productName: '',
		productSpec: '',
		quantity: 1,
		status: '',
		orderNo: '',
		name: '',
		phone: '',
		address: '',
		visitTime: '',
		image: ''
	})

	const isReadonly = ref(false)
const userRole = ref('')
const evaluation = ref(null)
const evaluationLoading = ref(false)

const statusClass = computed(() => {
		const s = orderInfo.value.status
		if (s === '已完成') return 'status-done'
		if (s === '待上门') return 'status-waiting'
		if (s === '处理中') return 'status-processing'
		return ''
	})

	// ===== 工具清单 =====
	const toolList = ref([])

	const changeQty = (index, delta) => {
		const tool = toolList.value[index]
		tool.qty += delta
		if (tool.qty < 1) tool.qty = 1
	}

	const deleteTool = (index) => {
		uni.showModal({
			title: '提示',
			content: `确定删除"${toolList.value[index].title}"吗？`,
			success: (res) => {
				if (res.confirm) toolList.value.splice(index, 1)
			}
		})
	}

	// ===== 完成情况 =====
	const completeText = ref('')

	// ===== 图片上传 =====
	const imageList = ref([])

	const chooseImage = () => {
		const remaining = 9 - imageList.value.length
		if (remaining <= 0) {
			uni.showToast({
				title: '最多上传9张图片',
				icon: 'none'
			});
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
				uni.showLoading({
					title: '上传中...',
					mask: true
				})
				let failCount = 0
				try {
					// 逐张上传（loading 由本处统一控制，upload 传 loading:false 避免双重 loading 冲突）
					for (const path of tempFilePaths) {
						try {
							const upRes = await orderApi.uploadImage(path, {}, { loading: false })
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

const previewEvaluateImage = (index) => {
	if (!evaluation.value || !evaluation.value.images) return
	uni.previewImage({
		current: evaluation.value.images[index],
		urls: evaluation.value.images
	})
}

	// ===== 工具选择弹出框 =====
	const showToolPopup = ref(false)
	const searchKeyword = ref('')
	const currentCategory = ref(0)
	const categories = ref(['全部'])

	// 工具列表（由后端耗材接口拉取）
	const popupTools = ref([])
	const popupLoading = ref(false)

	// 按分类 + 关键词过滤
	const filteredTools = computed(() => {
		let result = popupTools.value
		if (currentCategory.value > 0) {
			const cat = categories.value[currentCategory.value]
			result = result.filter(t => t.category === cat)
		}
		if (searchKeyword.value) {
			result = result.filter(t => t.title.includes(searchKeyword.value))
		}
		return result
	})

	const switchCategory = (index) => {
		currentCategory.value = index
	}

	// 拉取耗材列表（同时构建分类标签）
	const fetchTools = async () => {
		popupLoading.value = true
		try {
			const res = await consumablesApi.getList({
				page: 1,
				pageSize: 100
			})
			if (res.code !== 200) return
			// 耗材字段已与弹窗对齐（id/title/spec/price/image/category），res.data.list 直接使用
			const list = (res.data && res.data.list) || []
			popupTools.value = list
			// 动态构建分类：首项"全部" + 去重后的实际分类
			const cats = ['全部']
			popupTools.value.forEach(t => {
				if (t.category && !cats.includes(t.category)) cats.push(t.category)
			})
			categories.value = cats
		} catch (err) {
			// request.js 已统一处理错误提示
		} finally {
			popupLoading.value = false
		}
	}

	// 用 id 作为唯一 key（后端耗材有 id；兼容无 id 场景回退 title+spec）
	const getToolKey = (tool) => {
		if (tool && tool.id != null) return String(tool.id)
		return `${tool.title}_${tool.spec}`
	}

	// popupSelected 存储完整对象 { [key]: { ...tool, qty } }，避免反解 key 丢失字段
	const popupSelected = ref({})

	const openToolPopup = async () => {
		// 用已选工具初始化弹窗选中态（保留完整对象）
		popupSelected.value = {}
		toolList.value.forEach(tool => {
			popupSelected.value[getToolKey(tool)] = {
				...tool
			}
		})
		searchKeyword.value = ''
		currentCategory.value = 0
		showToolPopup.value = true
		// 首次打开时拉取耗材列表
		if (popupTools.value.length === 0) {
			await fetchTools()
		}
	}

	const closeToolPopup = () => {
		showToolPopup.value = false
	}

	const getToolQty = (tool) => {
		const item = popupSelected.value[getToolKey(tool)]
		return (item && item.qty) || 0
	}

	const addToolToCart = (tool) => {
		popupSelected.value[getToolKey(tool)] = {
			...tool,
			qty: 1
		}
	}

	const changePopupQty = (tool, delta) => {
		const key = getToolKey(tool)
		const item = popupSelected.value[key]
		const current = (item && item.qty) || 0
		const newVal = current + delta
		if (newVal <= 0) {
			delete popupSelected.value[key]
		} else {
			popupSelected.value[key] = {
				...tool,
				qty: newVal
			}
		}
	}

	const selectedCount = computed(() => {
		let count = 0
		Object.values(popupSelected.value).forEach(item => {
			count += (item && item.qty) || 0
		})
		return count
	})

	const confirmTools = () => {
		if (selectedCount.value === 0) {
			uni.showToast({
				title: '请至少选择一个工具',
				icon: 'none'
			});
			return
		}
		// 直接用完整对象重建 toolList，保留 id/image/price 等字段
		toolList.value = Object.values(popupSelected.value).map(item => ({
			id: item.id,
			title: item.title,
			spec: item.spec,
			price: item.price,
			image: item.image,
			qty: item.qty
		}))
		showToolPopup.value = false
		uni.showToast({
			title: '已更新工具清单',
			icon: 'success'
		})
	}

	// ===== 提交 =====
	const canSubmit = computed(() => toolList.value.length > 0 && completeText.value.trim().length > 0)
	const submitting = ref(false)

	const handleSubmit = () => {
		if (!orderId.value) {
			uni.showToast({
				title: '订单信息异常，请重试',
				icon: 'none'
			})
			return
		}
		if (!canSubmit.value) {
			const tips = []
			if (toolList.value.length === 0) tips.push('工具清单')
			if (completeText.value.trim().length === 0) tips.push('完成情况')
			uni.showToast({
				title: `请填写${tips.join('、')}`,
				icon: 'none'
			})
			return
		}
		uni.showModal({
			title: '确认提交',
			content: '提交后订单状态将变更为"已完成"，请确认信息无误。',
			success: async (res) => {
				if (!res.confirm) return
				if (submitting.value) return
				submitting.value = true
				uni.showLoading({
					title: '提交中...',
					mask: true
				})
				try {
					await orderApi.update(orderId.value, {
						status: 'done',
						completeText: completeText.value,
						tools: toolList.value.map(t => ({
							id: t.id,
							title: t.title,
							spec: t.spec,
							qty: t.qty
						})),
						images: imageList.value
					})
					uni.hideLoading()
					uni.showToast({
						title: '提交成功',
						icon: 'success'
					})
					setTimeout(() => uni.navigateBack(), 1500)
				} catch (err) {
					uni.hideLoading()
					// request.js 已统一处理错误提示
				} finally {
					submitting.value = false
				}
			}
		})
	}

	const copyOrderNo = (no) => {
		uni.setClipboardData({
			data: no,
			success: () => uni.showToast({
				title: '复制成功',
				icon: 'success'
			})
		})
	}

	onLoad(async (options) => {
	const id = options && options.id
	if (!id) {
		uni.showToast({
			title: '订单ID缺失',
			icon: 'none'
		})
		return
	}
	orderId.value = id
	try {
		// 并行获取订单详情、当前用户角色、评价
		const [orderRes, userRes] = await Promise.all([
			orderApi.getDetail(id),
			authApi.getUserInfo()
		])
		if (userRes.code === 200 && userRes.data) {
			userRole.value = userRes.data.role || ''
		}
		if (orderRes.code !== 200) return
		// 订单字段已与模板对齐，res.data 直接赋值
		const data = orderRes.data || {}
		orderInfo.value = data
		// 已完成订单为只读，回填完工信息（tools 字段与耗材统一用 title）
		if (data.status === '已完成') {
			isReadonly.value = true
			completeText.value = data.completeText || ''
			toolList.value = (data.tools || []).map(t => ({
				id: t.id,
				title: t.title || '',
				spec: t.spec || '',
				qty: t.qty || 1,
				image: t.image || '',
				price: t.price || 0
			}))
			imageList.value = data.images || []
			// 加载评价
			loadEvaluation(id)
		}
	} catch (err) {
		// request.js 已统一处理错误提示
	}
})

const loadEvaluation = async (id) => {
	evaluationLoading.value = true
	try {
		const res = await evaluationApi.getByOrderId(id)
		if (res.code === 200) {
			evaluation.value = res.data || null
		}
	} catch (err) {
		// request.js 已统一处理错误提示
	} finally {
		evaluationLoading.value = false
	}
}
</script>

<style scoped lang="scss">
	$primary: #0b63ce;
	$bg: #f4f7fb;
	$text-main: #142434;
	$text-sub: #64748b;
	$text-light: #94a3b8;

	.page {
		min-height: 100vh;
		background: $bg;
		padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
	}

	/* ═══ 卡片通用 ═══ */
	.order-card,
	.section-card {
		background: #fff;
		border-radius: 16rpx;
		padding: 24rpx;
		margin: 16rpx 24rpx 0;
		box-shadow: 0 2rpx 12rpx rgba(30, 41, 59, 0.04);
	}

	/* ═══ 订单信息 ═══ */
	.order-top {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
	}

	.order-service {
		display: flex;
		flex: 1;
		min-width: 0;
	}

	.order-img {
		width: 160rpx;
		height: 160rpx;
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
		font-size: 30rpx;
		font-weight: 600;
		color: $text-main;
		margin-bottom: 10rpx;
	}

	.order-product {
		font-size: 26rpx;
		color: $text-sub;
		margin-bottom: 8rpx;
		line-height: 1.4;
		word-break: break-all;
	}

	.order-spec {
		font-size: 24rpx;
		color: $text-light;
		margin-bottom: 8rpx;
	}

	.order-qty {
		font-size: 24rpx;
		color: $text-light;
	}

	.order-status {
		padding: 6rpx 16rpx;
		border-radius: 20rpx;
		flex-shrink: 0;
		margin-left: 10rpx;

		text {
			font-size: 24rpx;
			white-space: nowrap;
		}

		&.status-processing {
			background-color: rgba(255, 152, 0, 0.1);

			text {
				color: #ff9800;
			}
		}

		&.status-done {
			background-color: rgba(76, 175, 80, 0.1);

			text {
				color: #4caf50;
			}
		}

		&.status-waiting {
			background-color: rgba(60, 156, 255, 0.1);

			text {
				color: $primary;
			}
		}
	}

	.info-divider {
		height: 1rpx;
		background-color: #f0f0f0;
		margin: 24rpx 0;
	}

	.info-row {
		display: flex;
		align-items: center;
		padding: 8rpx 0;
	}

	.info-label {
		font-size: 24rpx;
		color: $text-light;
		width: 140rpx;
		flex-shrink: 0;
	}

	.info-value {
		font-size: 24rpx;
		color: $text-main;
		flex: 1;
		word-break: break-all;

		&.highlight {
			color: $primary;
			font-weight: 500;
		}
	}

	.copy-btn {
		margin-left: 10rpx;
		flex-shrink: 0;
	}

	/* ═══ 区块标题 ═══ */
	.section-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 24rpx;
	}

	.section-title-wrap {
		display: flex;
		align-items: center;
	}

	.required {
		color: #ff4d4f;
		font-size: 28rpx;
		margin-right: 4rpx;
	}

	.section-title {
		font-size: 30rpx;
		font-weight: 600;
		color: $text-main;
	}

	.upload-tip {
		font-size: 24rpx;
		color: $text-light;
		margin-left: 8rpx;
	}

	.add-btn {
		display: flex;
		align-items: center;
		background: linear-gradient(135deg, #3b8eea, #0b63ce);
		border-radius: 30rpx;
		padding: 8rpx 24rpx;
		flex-shrink: 0;

		text {
			font-size: 24rpx;
			color: #fff;
			margin-left: 6rpx;
		}

		&:active {
			opacity: 0.85;
		}
	}

	/* ═══ 工具清单列表 ═══ */
	.tool-list {
		background: #f9f9fb;
		border-radius: 12rpx;
		padding: 0 24rpx;
	}

	.tool-item {
		display: flex;
		align-items: center;
		padding: 24rpx 0;
		border-bottom: 1rpx solid #eee;

		&:last-child {
			border-bottom: none;
		}
	}

	.tool-index {
		width: 40rpx;
		height: 40rpx;
		border-radius: 50%;
		background: $primary;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		margin-right: 20rpx;

		text {
			font-size: 24rpx;
			color: #fff;
			font-weight: 600;
		}
	}

	.tool-content {
		flex: 1;
		display: flex;
		flex-direction: column;
		min-width: 0;
	}

	.tool-name {
		font-size: 28rpx;
		font-weight: 600;
		color: $text-main;
		margin-bottom: 12rpx;
	}

	.tool-meta {
		display: flex;
		align-items: center;
		margin-bottom: 8rpx;

		&:last-child {
			margin-bottom: 0;
		}
	}

	.tool-spec-text {
		font-size: 24rpx;
		color: $text-sub;
	}

	.tool-qty-text {
		font-size: 24rpx;
		color: $text-sub;
	}

	.tool-qty-value {
		font-size: 24rpx;
		color: $text-main;
		font-weight: 500;
	}

	.qty-control {
		display: flex;
		align-items: center;
	}

	.qty-btn {
		width: 44rpx;
		height: 44rpx;
		border-radius: 8rpx;
		background-color: #fff;
		border: 1rpx solid #ddd;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;

		text {
			font-size: 28rpx;
			color: $text-main;
		}

		&:active {
			background-color: #f0f0f0;
		}
	}

	.qty-num {
		font-size: 28rpx;
		color: $text-main;
		margin: 0 16rpx;
		min-width: 40rpx;
		text-align: center;
	}

	.tool-delete {
		width: 60rpx;
		height: 60rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		margin-left: 16rpx;

		&:active {
			opacity: 0.6;
		}
	}

	.tool-empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 60rpx 0;
	}

	.tool-empty-text {
		font-size: 26rpx;
		color: $text-light;
		margin-top: 16rpx;
	}

	/* ═══ 完成情况 ═══ */
	.textarea-wrap {
		position: relative;
	}

	.complete-textarea {
		width: 100%;
		height: 200rpx;
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

	.complete-readonly {
		background-color: #f9f9fb;
		border-radius: 12rpx;
		padding: 24rpx;
		min-height: 120rpx;
	}

	.complete-text {
		font-size: 28rpx;
		color: $text-main;
		line-height: 1.6;
	}

	/* ═══ 图片上传 ═══ */
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

/* ═══ 用户评价展示 ═══ */
.evaluation-content {
	display: flex;
	flex-direction: column;
}

.star-display {
	display: flex;
	align-items: center;
	gap: 8rpx;
	margin-bottom: 16rpx;
}

.star-label {
	font-size: 24rpx;
	color: #ff9800;
	margin-left: 12rpx;
	font-weight: 500;
}

.evaluate-text {
	font-size: 28rpx;
	color: $text-main;
	line-height: 1.6;
	margin-bottom: 16rpx;
}

.evaluate-images {
	display: flex;
	flex-wrap: wrap;
	gap: 16rpx;
}

.evaluate-img {
	width: 200rpx;
	height: 200rpx;
	border-radius: 12rpx;
}

.bottom-space {
	height: 140rpx;
}

	/* ═══ 提交按钮 ═══ */
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

	/* ═══════════════════════════════════════════════
 * 工具选择弹出框 — 两行式布局（美团/京东风格）
 * 上行 = 图片 + 名称（横排）
 * 下行 = 规格 + 价格 + 操作按钮（横排）
 * ═══════════════════════════════════════════════ */

	/* 弹出框容器 — 加 width:100% overflow:hidden 防止内容溢出屏幕 */
	.tool-popup {
		display: flex;
		flex-direction: column;
		width: 100%;
		overflow: hidden;
		box-sizing: border-box;
	}

	/* 头部 */
	.popup-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 20rpx 24rpx;
		flex-shrink: 0;
	}

	.popup-title {
		font-size: 30rpx;
		font-weight: 600;
		color: $text-main;
	}

	.popup-close {
		width: 56rpx;
		height: 56rpx;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	/* 搜索框 */
	.popup-search {
		padding: 0 24rpx 16rpx;
		flex-shrink: 0;
		margin: 10rpx 0;
		display: flex;
		align-items: center;
	}

	.search-btn {
		margin-left: 20rpx;
		color: #0b63ce;

	}

	/* 分类标签 — 小程序scroll-x必须用inline-block + white-space:nowrap */
	.category-bar {
		width: 100%;
		white-space: nowrap;
		padding: 0 24rpx 16rpx;
		flex-shrink: 0;
	}

	.category-scroll-inner {
		white-space: nowrap;
		display: inline-block;
	}

	.category-item {
		display: inline-block;
		padding: 10rpx 24rpx;
		margin-right: 16rpx;
		border-radius: 30rpx;
		background-color: #f5f5f5;
		vertical-align: middle;

		text {
			font-size: 26rpx;
			color: $text-sub;
			white-space: nowrap;
		}

		&.active {
			background-color: $primary;

			text {
				color: #fff;
			}
		}
	}

	/* 工具列表 scroll-view */
	.popup-list {
		flex: 1;
	}

	/* ══════════════════════════════════════
 * 单个工具卡片 — 两行式布局
 * row1: [图片] 名称（单行截断）
 * row2: 规格 | ¥价格 | [操作按钮]
 * ══════════════════════════════════════ */
	.popup-tool-card {
		padding: 16rpx 24rpx;
		border-bottom: 1rpx solid #f5f5f5;
		width: 100%;
		box-sizing: border-box;
		overflow: hidden;

		&:active {
			background-color: #fafafa;
		}
	}

	/* 上行：图片 + 名称 横排 — overflow:hidden防止溢出 */
	.popup-tool-row1 {
		display: flex;
		align-items: center;
		margin-bottom: 8rpx;
		overflow: hidden;
	}

	.popup-tool-img {
		width: 80rpx;
		height: 80rpx;
		border-radius: 8rpx;
		background-color: #e8f4fd;
		border: 1rpx solid #eee;
		flex-shrink: 0;
		margin-right: 12rpx;
	}

	.popup-tool-name {
		font-size: 26rpx;
		font-weight: 600;
		color: $text-main;
		line-height: 1.3;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		flex: 1;
		min-width: 0;
		/* flex:1必须配合min-width:0才能真正收缩 */
	}

	/* 下行：规格 + 价格 + 操作按钮 — overflow:hidden防止溢出 */
	.popup-tool-row2 {
		display: flex;
		align-items: center;
		justify-content: space-between;
		overflow: hidden;
	}

	.popup-tool-spec {
		font-size: 22rpx;
		color: $text-light;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		flex: 1;
		min-width: 0;
		/* 确保能收缩 */
		max-width: 55%;
		/* 留出空间给价格和操作按钮 */
	}

	.popup-tool-price {
		font-size: 30rpx;
		font-weight: 700;
		color: #ff4d4f;
		flex-shrink: 0;
		margin-right: 8rpx;
	}

	/* 操作区：数量控制或添加按钮 */
	.popup-tool-action {
		flex-shrink: 0;
		margin-left: 8rpx;
	}

	.popup-qty-wrap {
		display: flex;
		align-items: center;
	}

	.popup-qty-btn {
		width: 40rpx;
		height: 40rpx;
		border-radius: 6rpx;
		background-color: #f5f5f5;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;

		text {
			font-size: 28rpx;
			color: $text-main;
		}

		&:active {
			background-color: #e8e8e8;
		}
	}

	.popup-qty-num {
		font-size: 28rpx;
		color: $text-main;
		margin: 0 4rpx;
		min-width: 24rpx;
		text-align: center;
	}

	.popup-add-btn {
		padding: 6rpx 20rpx;
		border-radius: 20rpx;
		font-size: 30rpx;
		background: linear-gradient(135deg, #ff6b6b, #ee5a24);
		min-width: 56rpx;
		display: flex;
		align-items: center;
		justify-content: center;

		&:active {
			opacity: 0.8;
		}
	}

	.popup-add-label {
		font-size: 26rpx;
		font-weight: 500;
		color: #fff;
	}

	/* 空状态 */
	.popup-empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 80rpx 0;
	}

	.popup-empty-text {
		font-size: 26rpx;
		color: $text-light;
		margin-top: 16rpx;
	}

	/* 底部确认 */
	.popup-footer {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 12rpx 24rpx;
		padding-bottom: calc(12rpx + env(safe-area-inset-bottom));
		border-top: 1rpx solid #f0f0f0;
		flex-shrink: 0;
		background: #fff;
	}

	.popup-selected {
		flex-shrink: 0;
		font-size: 26rpx;
		color: $text-sub;
	}

	.popup-confirm-btn {
		flex: 1;
		margin-left: 24rpx;
		height: 72rpx;
		border-radius: 36rpx;
		background: linear-gradient(135deg, #3b8eea, #0b63ce);
		display: flex;
		align-items: center;
		justify-content: center;

		text {
			font-size: 28rpx;
			font-weight: 600;
			color: #fff;
		}

		&:active {
			opacity: 0.85;
		}
	}
</style>
