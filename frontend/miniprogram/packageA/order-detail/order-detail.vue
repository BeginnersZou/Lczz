<template>
	<view class="page">
		<view class="detail-state" v-if="detailLoading">
			<up-loading-icon mode="circle" color="#0b63ce" size="34"></up-loading-icon>
			<text class="state-title">正在加载订单详情</text>
			<text class="state-desc">请稍候</text>
		</view>
		<view class="detail-state" v-else-if="detailError">
			<view class="state-icon"><up-icon name="warning" size="38" color="#d97706"></up-icon></view>
			<text class="state-title">{{ detailError.title }}</text>
			<text class="state-desc">{{ detailError.message }}</text>
			<view class="state-action" @click="loadOrderDetails">重新加载</view>
		</view>

		<template v-else>
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

		<!-- ═══ 安装师傅耗材申请 ═══ -->
		<view class="section-card" v-if="isInstaller">
			<view class="section-header">
				<view class="section-title-wrap">
					<text class="required" v-if="!materialReadonly">*</text>
					<text class="section-title">耗材清单</text>
				</view>
				<view class="add-btn" @click="openToolPopup" v-if="!materialReadonly">
					<up-icon name="plus" size="14" color="#fff"></up-icon>
					<text>添加</text>
				</view>
				<text v-else-if="materialRequest" class="tool-qty-text">{{ materialRequest.statusLabel || materialRequest.status }}</text>
			</view>

			<view class="tool-list" v-if="toolList.length > 0">
				<view class="tool-item" v-for="(tool, index) in toolList" :key="index">
					<view class="tool-index">{{ index + 1 }}</view>
					<view class="tool-content">
						<text class="tool-name">{{ tool.title }}</text>
						<view class="tool-meta">
							<text class="tool-spec-text">规格：{{ tool.spec || '-' }}　单位：{{ tool.unit || '-' }}</text>
						</view>
						<view class="tool-meta">
							<text class="tool-qty-text">数量：</text>
							<view class="qty-control" v-if="!materialReadonly">
								<view class="qty-btn" @click="changeQty(index, -1)"><text>-</text></view>
								<text class="qty-num">{{ tool.qty }}</text>
								<view class="qty-btn" @click="changeQty(index, 1)"><text>+</text></view>
							</view>
							<text class="tool-qty-value" v-else>{{ tool.qty }}</text>
						</view>
					</view>
					<view class="tool-delete" @click="deleteTool(index)" v-if="!materialReadonly">
						<up-icon name="trash" size="18" color="#ff4d4f"></up-icon>
					</view>
				</view>
			</view>

			<view class="tool-empty" v-else>
				<up-icon name="file-text" size="50" color="#ddd"></up-icon>
				<text class="tool-empty-text" v-if="!materialReadonly">点击“添加”选择耗材</text>
				<text class="tool-empty-text" v-else>暂无耗材记录</text>
			</view>
		</view>

		<!-- ═══ 耗材申请备注 ═══ -->
		<view class="section-card" v-if="isInstaller">
			<view class="section-header">
				<view class="section-title-wrap">
					<text class="section-title">申请备注</text>
				</view>
			</view>
			<view class="textarea-wrap" v-if="!materialReadonly">
				<textarea class="complete-textarea" v-model="materialRemark" placeholder="选填：请填写耗材申请说明"
					placeholder-class="placeholder-style" maxlength="500"></textarea>
				<text class="text-count">{{ materialRemark.length }}/500</text>
			</view>
			<view class="complete-readonly" v-else>
				<text class="complete-text">{{ materialRemark || '暂无申请备注' }}</text>
			</view>
		</view>

		<!-- ═══ 施工时间线：客户与师傅均可查看 ═══ -->
		<view class="section-card">
			<view class="section-header">
				<view class="section-title-wrap">
					<text class="section-title">施工进度</text>
				</view>
			</view>
			<view class="progress-list" v-if="progressRecords.length">
				<view class="progress-item" v-for="record in progressRecords" :key="record.id">
					<view class="progress-dot" :class="{ completion: record.type === 'COMPLETION' }"></view>
					<view class="progress-content">
						<view class="progress-heading">
							<text class="progress-type">{{ record.typeLabel }}</text>
							<text class="progress-time">{{ record.submittedAt }}</text>
						</view>
						<text class="progress-description">{{ record.description }}</text>
						<view class="image-grid" v-if="record.images.length">
							<view class="image-item" v-for="(img, imageIndex) in record.images" :key="img.id">
								<image class="preview-img" :src="img.url" mode="aspectFill"
									@click="previewRecordImages(record.images, imageIndex)"></image>
							</view>
						</view>
					</view>
				</view>
			</view>
			<view class="tool-empty" v-else>
				<up-icon name="photo" size="50" color="#ddd"></up-icon>
				<text class="tool-empty-text">暂无施工进度</text>
			</view>
		</view>

		<!-- ═══ 客户评价 ═══ -->
		<view class="section-card">
			<view class="section-header">
				<view class="section-title-wrap"><text class="section-title">客户评价</text></view>
				<view class="review-action" v-if="canReview" @click="goEvaluate"><text>去评价</text></view>
			</view>
			<view class="review-content" v-if="evaluation">
				<view class="review-score">
					<up-icon v-for="index in 5" :key="index" :name="index <= evaluation.score ? 'star-fill' : 'star'"
						size="20" :color="index <= evaluation.score ? '#f59e0b' : '#cbd5e1'"></up-icon>
					<text>{{ evaluation.score }}.0</text>
					<text class="review-time">{{ evaluation.createTime }}</text>
				</view>
				<view class="review-labels" v-if="evaluation.labels.length">
					<text v-for="label in evaluation.labels" :key="label">{{ label }}</text>
				</view>
				<text class="review-text">{{ evaluation.content }}</text>
				<view class="image-grid" v-if="evaluation.images.length">
					<view class="image-item" v-for="(image, index) in evaluation.images" :key="image">
						<image class="preview-img" :src="image" mode="aspectFill" @click="previewEvaluationImages(index)"></image>
					</view>
				</view>
			</view>
			<view class="tool-empty review-empty" v-else-if="canReview">
				<up-icon name="edit-pen" size="42" color="#94a3b8"></up-icon>
				<text class="tool-empty-text">服务已完成，期待您的评价</text>
			</view>
			<view class="tool-empty review-empty" v-else>
				<up-icon name="chat" size="42" color="#cbd5e1"></up-icon>
				<text class="tool-empty-text">暂无评价</text>
			</view>
		</view>

		<!-- ═══ 指派师傅提交施工进度或完工 ═══ -->
		<view class="section-card" v-if="canOperateProgress">
			<view class="section-header">
				<view class="section-title-wrap"><text class="section-title">施工操作</text></view>
			</view>
			<view class="progress-tabs">
				<view class="progress-tab" :class="{ active: progressType === 'PROGRESS' }" @click="progressType = 'PROGRESS'">提交进度</view>
				<view class="progress-tab" :class="{ active: progressType === 'COMPLETION' }" @click="progressType = 'COMPLETION'">提交完工</view>
			</view>
			<view class="textarea-wrap">
				<textarea class="complete-textarea" v-model="progressDescription"
					:placeholder="progressType === 'COMPLETION' ? '必填：填写完工说明' : '必填：填写本次施工进度'"
					placeholder-class="placeholder-style" maxlength="2000"></textarea>
				<text class="text-count">{{ progressDescription.length }}/2000</text>
			</view>
			<view class="upload-heading">
				<text>{{ progressType === 'COMPLETION' ? '完工图片（至少1张）' : '施工图片（选填）' }}</text>
				<text class="upload-tip">最多9张</text>
			</view>
			<view class="image-grid">
				<view class="image-item" v-for="(img, index) in progressImages" :key="img.id">
					<image class="preview-img" :src="img.url" mode="aspectFill" @click="previewProgressImages(index)"></image>
					<view class="image-delete" @click.stop="deleteProgressImage(index)">
						<up-icon name="close" size="12" color="#fff"></up-icon>
					</view>
				</view>
				<view class="image-add" v-if="progressImages.length < 9" @click="chooseProgressImages">
					<up-icon name="plus" size="32" color="#ccc"></up-icon>
					<text class="add-text">上传图片</text>
				</view>
			</view>
			<text class="upload-progress" v-if="uploadProgress">{{ uploadProgress }}</text>
			<view class="inline-submit" :class="{ disabled: !canSubmitProgress || submittingProgress }" @click="handleProgressSubmit">
				<text>{{ submittingProgress ? '提交中...' : (progressType === 'COMPLETION' ? '确认完工' : '提交进度') }}</text>
			</view>
		</view>

		<view class="bottom-space"></view>

		<!-- ═══ 底部提交按钮 ═══ -->
		<view class="submit-bar" v-if="isInstaller && !materialReadonly">
			<view class="submit-btn" :class="{ disabled: !canSubmit }" @click="handleSubmit">
				<text>提交耗材申请</text>
			</view>
		</view>

		<!-- ═══ 耗材选择弹出框 ═══ -->
		<up-popup :show="showToolPopup" mode="bottom" round="20" :closeOnClickOverlay="true" @close="closeToolPopup"
			:customStyle="{ width: '100%' }">
			<view class="tool-popup" :style="{ height: popupHeight + 'px' }">
				<!-- 头部 -->
				<view class="popup-header">
					<text class="popup-title">选择耗材</text>
					<view class="popup-close" @click="closeToolPopup">
						<up-icon name="close" size="20" color="#999"></up-icon>
					</view>
				</view>

				<!-- 搜索框 -->
				<view class="popup-search">
					<up-search v-model="searchKeyword" placeholder="搜索耗材名称" :showAction="false" bgColor="#f5f5f5"
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
						<text class="popup-empty-text">暂无相关耗材</text>
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
		</template>
	</view>
</template>

<script setup>
	import {
		ref,
		computed
	} from 'vue'
	import {
		onBackPress,
		onLoad,
		onShow
	} from '@dcloudio/uni-app'
	import {
	orderApi,
	consumablesApi,
	authApi,
	evaluationApi
	} from '@/api/api.js'
	import { getAuthToken } from '@/utils/auth-session.js'

	// ===== 动态计算弹出框高度 =====
	// #ifdef MP-WEIXIN
	const windowInfo = wx.getWindowInfo()
	// #endif
	// #ifndef MP-WEIXIN
	const windowInfo = uni.getSystemInfoSync()
	// #endif
	const popupHeight = ref(Math.floor(windowInfo.windowHeight * 0.7))
	// 列表高度 = 弹出框总高 - 头部(50) - 搜索(60) - 分类(50) - 底部(60)
	const popupListHeight = ref(Math.floor(windowInfo.windowHeight * 0.7 - 220))

	// 订单 ID（onLoad 时由路由参数获取）
	const orderId = ref('')
	const detailLoading = ref(true)
	const detailError = ref(null)

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

	const materialRequest = ref(null)
	// 耗材申请仅允许指派师傅提交一次；重复提交由服务端幂等/冲突规则兜底。
	const userRole = ref('')
	const evaluation = ref(null)
	const isInstaller = computed(() => userRole.value === 'installer')
	const canReview = computed(() => ['customer', 'dealer'].includes(userRole.value) && orderInfo.value.statusCode === 'PENDING_REVIEW' && !evaluation.value)
	const materialReadonly = computed(() => !isInstaller.value || !['待上门', '处理中'].includes(orderInfo.value.status) || Boolean(materialRequest.value))

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

	const materialRemark = ref('')
	const progressRecords = ref([])
	const progressType = ref('PROGRESS')
	const progressDescription = ref('')
	const progressImages = ref([])
	const uploadProgress = ref('')
	const submittingProgress = ref(false)
	const allowLeave = ref(false)
	const canOperateProgress = computed(() => isInstaller.value && ['待上门', '处理中'].includes(orderInfo.value.status))
	const hasMaterialDraft = computed(() => !materialReadonly.value && (toolList.value.length > 0 || Boolean(materialRemark.value.trim())))
	const canSubmitProgress = computed(() => {
		if (!progressDescription.value.trim()) return false
		return progressType.value !== 'COMPLETION' || progressImages.value.length > 0
	})

	const previewRecordImages = (images, index) => {
		const urls = images.map(image => image.url)
		uni.previewImage({ current: urls[index], urls })
	}

	const previewEvaluationImages = (index) => {
		uni.previewImage({ current: evaluation.value.images[index], urls: evaluation.value.images })
	}

	const goEvaluate = () => {
		uni.navigateTo({ url: `/packageA/order-evaluate/order-evaluate?id=${orderId.value}` })
	}

	const refreshOrderEvaluation = async () => {
		if (!orderId.value) return
		const [orderRes, evaluationRes] = await Promise.all([
			orderApi.getDetail(orderId.value),
			evaluationApi.getByOrder(orderId.value, { loading: false })
		])
		if (orderRes.code === 200) orderInfo.value = orderRes.data || {}
		if (evaluationRes.code === 200) evaluation.value = evaluationRes.data || null
	}

	onShow(() => refreshOrderEvaluation())

	const previewProgressImages = (index) => {
		const urls = progressImages.value.map(image => image.url)
		uni.previewImage({ current: urls[index], urls })
	}

	const deleteProgressImage = (index) => {
		progressImages.value.splice(index, 1)
	}

	const chooseProgressImages = () => {
		const remaining = 9 - progressImages.value.length
		if (remaining <= 0) return
		uni.chooseImage({
			count: remaining,
			sizeType: ['compressed'],
			sourceType: ['album', 'camera'],
			success: async (res) => {
				const paths = res.tempFilePaths || []
				let failed = 0
				for (let index = 0; index < paths.length; index++) {
					uploadProgress.value = `正在上传 ${index + 1}/${paths.length}`
					const uploadRes = await orderApi.uploadImage(paths[index], {}, {
						loading: false,
						onProgress: event => {
							uploadProgress.value = `正在上传 ${index + 1}/${paths.length}（${event.progress}%）`
						}
					})
					const file = uploadRes.code === 200 ? uploadRes.data : null
					if (file && file.id && file.url) {
						progressImages.value.push({ id: Number(file.id), url: file.url })
					} else {
						failed++
					}
				}
				uploadProgress.value = ''
				if (failed) uni.showToast({ title: `${failed}张上传失败，可重新选择`, icon: 'none' })
			},
			fail: (err) => {
				if (!String(err?.errMsg || '').includes('cancel')) {
					uni.showToast({ title: '图片选择失败，请重试', icon: 'none' })
				}
			}
		})
	}

	const refreshProgress = async () => {
		const res = await orderApi.getProgress(orderId.value, { loading: false })
		if (res.code === 200) progressRecords.value = res.data || []
	}

	const handleProgressSubmit = () => {
		if (progressType.value === 'COMPLETION' && hasMaterialDraft.value) {
			uni.showToast({ title: '请先提交或清空耗材申请', icon: 'none' })
			return
		}
		if (!canSubmitProgress.value || submittingProgress.value) {
			uni.showToast({
				title: progressType.value === 'COMPLETION' && !progressImages.value.length ? '完工至少上传一张图片' : '请填写施工说明',
				icon: 'none'
			})
			return
		}
		uni.showModal({
			title: progressType.value === 'COMPLETION' ? '确认提交完工' : '确认提交进度',
			content: progressType.value === 'COMPLETION' ? '提交后订单将进入待评价，且不能重复完工。' : '提交后客户可在订单详情查看本条进度。',
			success: async ({ confirm }) => {
				if (!confirm) return
				submittingProgress.value = true
				try {
					const payload = {
						description: progressDescription.value.trim(),
						fileIds: progressImages.value.map(image => image.id)
					}
					const res = progressType.value === 'COMPLETION'
						? await orderApi.complete(orderId.value, payload)
						: await orderApi.submitProgress(orderId.value, payload)
					if (res.code !== 200) return
					progressDescription.value = ''
					progressImages.value = []
					await refreshProgress()
					const orderRes = await orderApi.getDetail(orderId.value)
					if (orderRes.code === 200) orderInfo.value = orderRes.data || {}
					uni.showToast({ title: '提交成功', icon: 'success' })
				} finally {
					submittingProgress.value = false
				}
			}
		})
	}

	const hasUnsavedChanges = computed(() => {
		return hasMaterialDraft.value ||
			Boolean(progressDescription.value.trim()) || progressImages.value.length > 0
	})

	onBackPress(() => {
		if (allowLeave.value || !hasUnsavedChanges.value || submitting.value || submittingProgress.value) return false
		uni.showModal({
			title: '离开当前页面？',
			content: '尚未提交的耗材、施工说明或图片将不会保存。',
			success: ({ confirm }) => {
				if (!confirm) return
				allowLeave.value = true
				uni.navigateBack()
			}
		})
		return true
	})

	// ===== 耗材选择弹出框 =====
	const showToolPopup = ref(false)
	const searchKeyword = ref('')
	const currentCategory = ref(0)
	const categories = ref(['全部'])

	// 耗材列表（由后端耗材接口拉取）
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
				title: '请至少选择一个耗材',
				icon: 'none'
			});
			return
		}
		// 直接用完整对象重建 toolList，保留 id/image/price 等字段
		toolList.value = Object.values(popupSelected.value).map(item => ({
			id: item.id,
			title: item.title,
			spec: item.spec,
			unit: item.unit,
			price: item.price,
			image: item.image,
			qty: item.qty
		}))
		showToolPopup.value = false
		uni.showToast({
			title: '已更新耗材清单',
			icon: 'success'
		})
	}

	// ===== 提交 =====
	const canSubmit = computed(() => toolList.value.length > 0)
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
			if (toolList.value.length === 0) tips.push('耗材清单')
			uni.showToast({
				title: `请填写${tips.join('、')}`,
				icon: 'none'
			})
			return
		}
		uni.showModal({
			title: '确认提交',
			content: '提交后后台可查看备货清单，请确认耗材和数量无误。',
			success: async (res) => {
				if (!res.confirm) return
				if (submitting.value) return
				submitting.value = true
				uni.showLoading({
					title: '提交中...',
					mask: true
				})
				try {
					const submitRes = await orderApi.submitMaterials(orderId.value, {
						items: toolList.value.map(t => ({ productId: t.id, quantity: t.qty })),
						remark: materialRemark.value.trim()
					})
					if (submitRes.code !== 200) return
					materialRequest.value = submitRes.data || { statusLabel: '待备货' }
					const orderRes = await orderApi.getDetail(orderId.value)
					if (orderRes.code === 200) orderInfo.value = orderRes.data || {}
					uni.showToast({
						title: '耗材申请已提交',
						icon: 'success'
					})
				} catch (err) {
					// request.js 已统一处理错误提示
				} finally {
					uni.hideLoading()
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

	const setDetailError = (response) => {
		if (response?.code === 403) {
			detailError.value = { title: '暂无访问权限', message: response.msg || '当前账号无权查看该订单' }
			return
		}
		if (response?.code === -1) {
			detailError.value = { title: '网络连接失败', message: response.msg || '请检查网络后重试' }
			return
		}
		detailError.value = { title: '订单加载失败', message: response?.msg || '订单不存在或服务暂时不可用' }
	}

	const loadOrderDetails = async () => {
		if (!orderId.value) {
			detailLoading.value = false
			detailError.value = { title: '订单参数有误', message: '未找到需要查看的订单' }
			return
		}
		detailLoading.value = true
		detailError.value = null
		try {
			// 并行获取订单详情与当前用户角色
			const [orderRes, userRes, progressRes, evaluationRes] = await Promise.all([
				orderApi.getDetail(orderId.value),
				authApi.getUserInfo(),
				orderApi.getProgress(orderId.value, { loading: false }),
				evaluationApi.getByOrder(orderId.value, { loading: false })
			])
			const failedResponse = [orderRes, userRes, progressRes, evaluationRes].find(res => res.code !== 200)
			if (failedResponse) {
				setDetailError(failedResponse)
				return
			}
			if (userRes.code === 200 && userRes.data) {
				userRole.value = userRes.data.role || ''
			}
			// 订单字段已与模板对齐，res.data 直接赋值
			const data = orderRes.data || {}
			orderInfo.value = data
			if (progressRes.code === 200) progressRecords.value = progressRes.data || []
			if (evaluationRes.code === 200) evaluation.value = evaluationRes.data || null
			if (userRole.value === 'installer') {
				const materialsRes = await orderApi.getMaterials(orderId.value, { loading: false, silent: true })
				// 后端以 404 表示该订单尚未提交耗材申请，此时应展示可填写的空表单。
				if (![200, 404].includes(materialsRes.code)) {
					setDetailError(materialsRes)
					return
				}
				if (materialsRes.code === 200 && materialsRes.data) {
					materialRequest.value = materialsRes.data
					materialRemark.value = materialsRes.data.remark || ''
					toolList.value = (materialsRes.data.materials || []).map(item => ({
						id: item.productId,
						title: item.name || '',
						spec: item.spec || '',
						unit: item.unit || '',
						qty: Number(item.count || 1),
						price: Number(item.displayPrice || 0)
					}))
				}
			}
		} catch (err) {
			setDetailError({ code: -1, msg: '请求异常，请稍后重试' })
		} finally {
			detailLoading.value = false
		}
	}

	onLoad((options) => {
		if (!getAuthToken()) {
			uni.reLaunch({ url: '/pages/login/login' })
			return
		}
		orderId.value = options?.id || ''
		loadOrderDetails()
	})

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

	.detail-state {
		min-height: calc(100vh - 160rpx);
		padding: 180rpx 48rpx 80rpx;
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
	}
	.state-icon { width: 96rpx; height: 96rpx; border-radius: 50%; background: #fff7e6; display: flex; align-items: center; justify-content: center; }
	.state-title { margin-top: 24rpx; font-size: 30rpx; font-weight: 700; color: $text-main; }
	.state-desc { margin-top: 10rpx; font-size: 24rpx; color: $text-light; line-height: 1.6; }
	.state-action { margin-top: 28rpx; padding: 14rpx 34rpx; border-radius: 30rpx; color: $primary; background: #eaf3ff; font-size: 24rpx; }

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

	.progress-list {
		display: flex;
		flex-direction: column;
	}

	.progress-item {
		position: relative;
		display: flex;
		padding: 0 0 28rpx 32rpx;
		border-left: 2rpx solid #dbeafe;
		margin-left: 8rpx;

		&:last-child {
			padding-bottom: 0;
		}
	}

	.progress-dot {
		position: absolute;
		left: -9rpx;
		top: 2rpx;
		width: 16rpx;
		height: 16rpx;
		border-radius: 50%;
		background: $primary;
		border: 4rpx solid #e8f2ff;

		&.completion {
			background: #16a34a;
			border-color: #dcfce7;
		}
	}

	.progress-content {
		width: 100%;
	}

	.progress-heading {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 12rpx;
	}

	.progress-type {
		font-size: 27rpx;
		font-weight: 600;
		color: $text-main;
	}

	.progress-time {
		font-size: 22rpx;
		color: $text-light;
	}

	.progress-description {
		display: block;
		font-size: 26rpx;
		line-height: 1.6;
		color: $text-sub;
		margin-bottom: 16rpx;
	}

	.progress-tabs {
		display: flex;
		padding: 6rpx;
		background: #f1f5f9;
		border-radius: 12rpx;
		margin-bottom: 20rpx;
	}

	.progress-tab {
		flex: 1;
		padding: 16rpx 0;
		border-radius: 10rpx;
		text-align: center;
		font-size: 26rpx;
		color: $text-sub;

		&.active {
			background: #fff;
			color: $primary;
			font-weight: 600;
			box-shadow: 0 2rpx 8rpx rgba(15, 23, 42, 0.08);
		}
	}

	.upload-heading {
		display: flex;
		align-items: center;
		margin: 24rpx 0 16rpx;
		font-size: 26rpx;
		color: $text-main;
	}

	.upload-progress {
		display: block;
		font-size: 24rpx;
		color: $primary;
		margin-top: 14rpx;
	}

	.inline-submit {
		height: 80rpx;
		border-radius: 40rpx;
		background: linear-gradient(135deg, #3b8eea, #0b63ce);
		display: flex;
		align-items: center;
		justify-content: center;
		margin-top: 24rpx;

		text {
			font-size: 28rpx;
			font-weight: 600;
			color: #fff;
		}

		&.disabled {
			opacity: 0.5;
		}
	}

	.review-action {
		padding: 8rpx 22rpx;
		border: 2rpx solid $primary;
		border-radius: 28rpx;
		font-size: 24rpx;
		color: $primary;
	}

	.review-score {
		display: flex;
		align-items: center;
		gap: 6rpx;
		font-size: 25rpx;
		font-weight: 600;
		color: #d97706;
		margin-bottom: 18rpx;
	}

	.review-time {
		margin-left: auto;
		font-size: 22rpx;
		font-weight: 400;
		color: $text-light;
	}

	.review-labels {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		margin-bottom: 16rpx;

		text {
			padding: 7rpx 16rpx;
			border-radius: 22rpx;
			background: #eef6ff;
			font-size: 23rpx;
			color: $primary;
		}
	}

	.review-text {
		display: block;
		font-size: 27rpx;
		line-height: 1.7;
		color: $text-main;
		margin-bottom: 18rpx;
	}

	.review-empty {
		padding: 38rpx 0 44rpx;
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
