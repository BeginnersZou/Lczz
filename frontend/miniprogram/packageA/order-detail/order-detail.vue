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
					<image v-if="orderInfo.image" class="order-img" :src="orderInfo.image" mode="aspectFill" lazy-load></image>
					<view v-else class="order-img order-placeholder"><up-icon name="home-fill" size="34" color="#0b63ce"></up-icon></view>
					<view class="order-info">
						<text class="order-name">{{ orderInfo.serviceName }}</text>
						<text class="order-product">{{ orderInfo.productName }}</text>
						<text class="order-spec">{{ orderInfo.productSpec }}</text>
						<text class="order-qty" v-if="orderInfo.quantity != null">× {{ orderInfo.quantity }}</text>
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

		<!-- ═══ 安装师傅耗材申请：清单与备注属于同一业务模块 ═══ -->
		<view class="section-card material-request-card" v-if="isInstaller">
			<view class="section-header">
				<view class="section-title-wrap">
					<view class="section-title-icon"><up-icon name="bag-fill" size="18" color="#0b63ce"></up-icon></view>
					<view class="section-title-copy">
						<text class="section-title">耗材申请</text>
						<text class="section-subtitle">填写本次施工所需耗材及申请说明</text>
					</view>
				</view>
				<view class="add-btn" @click="openToolPopup" v-if="!materialReadonly">
					<up-icon name="plus" size="14" color="#fff"></up-icon>
					<text>添加耗材</text>
				</view>
				<text v-else-if="materialRequest" class="material-status">{{ materialRequest.statusLabel || materialRequest.status }}</text>
			</view>

			<view class="material-subheader">
				<view class="material-subtitle-wrap">
					<text class="required" v-if="!materialReadonly">*</text>
					<text class="material-subtitle">耗材清单</text>
				</view>
				<text class="material-count" v-if="toolList.length">共 {{ toolList.length }} 种</text>
			</view>
			<view class="tool-list" v-if="toolList.length > 0">
				<view class="tool-item" v-for="(tool, index) in toolList" :key="index">
					<view class="tool-index">{{ index + 1 }}</view>
					<view class="tool-content">
						<text class="tool-name">{{ tool.title }}</text>
						<view class="tool-meta">
							<text class="tool-spec-text">规格：{{ tool.spec || '-' }}　单位：{{ tool.unit || '-' }}　库存：{{ tool.stock ?? '-' }}</text>
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

			<view class="material-divider"></view>
			<view class="material-subheader material-remark-header">
				<text class="material-subtitle">申请备注</text>
				<text class="material-optional">选填</text>
			</view>
			<view class="textarea-wrap" v-if="!materialReadonly">
				<textarea class="complete-textarea material-remark-input" v-model="materialRemark" placeholder="补充用途、施工位置或其他申请说明"
					placeholder-class="placeholder-style" maxlength="500"></textarea>
				<text class="text-count">{{ materialRemark.length }}/500</text>
			</view>
			<view class="complete-readonly material-remark-readonly" v-else>
				<text class="complete-text">{{ materialRemark || '暂无申请备注' }}</text>
			</view>

			<view class="material-submit" v-if="!materialReadonly && toolList.length > 0"
				:class="{ disabled: submitting }" @click="handleSubmit">
				<text>{{ submitting ? '提交中...' : '提交耗材申请' }}</text>
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
						<view class="image-grid" v-if="record.media.length">
							<view class="image-item" v-for="(media, mediaIndex) in record.media" :key="media.id">
								<video v-if="media.mimeType?.startsWith('video/')" class="preview-img" :src="media.url"
									controls object-fit="cover" :show-center-play-btn="true"></video>
								<image v-else class="preview-img" :src="media.url" mode="aspectFill"
									@click="previewRecordImages(record.images, record.images.findIndex(item => item.id === media.id))"></image>
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
			<view class="upload-panel">
				<view class="upload-heading">
					<view class="upload-title-group">
						<view class="upload-title-icon"><up-icon name="camera-fill" size="20" color="#0b63ce"></up-icon></view>
						<view class="upload-title-copy">
							<view class="upload-title-line">
								<text class="upload-title">施工附件</text>
								<text class="upload-required" v-if="progressType === 'COMPLETION'">至少上传1个</text>
								<text class="upload-optional" v-else>选填</text>
							</view>
							<text class="upload-subtitle">图片与视频合计最多9个，可全选图片或全选视频</text>
						</view>
					</view>
					<text class="upload-count">{{ progressImages.length }}/9</text>
				</view>
				<view class="upload-limit-row">
					<text class="upload-limit-pill">图片 ≤ 10MB</text>
					<text class="upload-limit-pill">视频 ≤ 200MB</text>
					<text class="upload-limit-pill">最多 9 个</text>
				</view>
				<view class="upload-media-grid">
					<view class="upload-media-item" v-for="(media, index) in progressImages" :key="media.uid || media.id">
						<video v-if="media.mimeType?.startsWith('video/')" class="preview-img" :src="media.localPath || media.url"
							controls object-fit="cover" :show-center-play-btn="media.status === 'success'"></video>
						<image v-else class="preview-img" :src="media.localPath || media.url" mode="aspectFill"
							@click="previewProgressImage(media)"></image>
						<view class="media-status-mask" v-if="media.status === 'queued' || media.status === 'uploading'">
							<up-loading-icon mode="circle" color="#ffffff" size="22"></up-loading-icon>
							<text>{{ media.status === 'queued' ? '等待上传' : `${media.progress || 0}%` }}</text>
						</view>
						<view class="media-status-mask failed" v-else-if="media.status === 'failed'" @click.stop="retryProgressMedia(media)">
							<up-icon name="reload" size="22" color="#ffffff"></up-icon>
							<text>点击重试</text>
						</view>
						<view class="media-type-badge" v-if="media.mimeType?.startsWith('video/')">视频</view>
						<view class="image-delete" @click.stop="deleteProgressImage(index)">
							<up-icon name="close" size="12" color="#fff"></up-icon>
						</view>
					</view>
					<view class="upload-add-card" v-if="progressImages.length < 9" :class="{ disabled: isUploadingProgressMedia }" @click="chooseProgressMedia">
						<view class="upload-add-icon"><up-icon name="plus" size="28" color="#0b63ce"></up-icon></view>
						<text class="add-text">添加附件</text>
						<text class="add-remain">还可添加 {{ 9 - progressImages.length }} 个</text>
					</view>
				</view>
				<view class="upload-summary" v-if="uploadProgress || failedProgressMediaCount">
					<up-icon :name="failedProgressMediaCount ? 'error-circle' : 'clock'" size="16"
						:color="failedProgressMediaCount ? '#dc2626' : '#0b63ce'"></up-icon>
					<text :class="{ error: failedProgressMediaCount }">
						{{ failedProgressMediaCount ? `${failedProgressMediaCount}个附件上传失败，请点击缩略图重试或删除` : uploadProgress }}
					</text>
				</view>
			</view>
			<view class="inline-submit" :class="{ disabled: !canSubmitProgress || submittingProgress }" @click="handleProgressSubmit">
				<text>{{ submittingProgress ? '提交中...' : (progressType === 'COMPLETION' ? '确认完工' : '提交进度') }}</text>
			</view>
		</view>

		<view class="bottom-space"></view>

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
							<text class="popup-tool-spec">{{ tool.spec }} · 库存 {{ tool.stock }}{{ tool.unit || '' }}</text>
							<text class="popup-tool-price">¥{{ tool.price }}</text>
							<view class="popup-tool-action">
								<view class="popup-qty-wrap" v-if="getToolQty(tool) > 0">
									<view class="popup-qty-btn" @click="changePopupQty(tool, -1)"><text>-</text></view>
									<text class="popup-qty-num">{{ getToolQty(tool) }}</text>
									<view class="popup-qty-btn" @click="changePopupQty(tool, 1)"><text>+</text></view>
								</view>
								<view class="popup-add-btn" :class="{ disabled: tool.stock <= 0 }" v-else @click="addToolToCart(tool)">
									<text class="popup-add-label">{{ tool.stock > 0 ? '添加' : '无库存' }}</text>
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
	evaluationApi,
	resolveMediaUrl
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
		const max = Math.max(0, Number(tool.stock || 0))
		const next = Math.max(1, Number(tool.qty || 1) + delta)
		if (next > max) {
			uni.showToast({ title: `库存仅剩 ${max}${tool.unit || ''}`, icon: 'none' })
			return
		}
		tool.qty = next
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
	let progressMediaUid = 0
	const allowLeave = ref(false)
	const canOperateProgress = computed(() => isInstaller.value && ['待上门', '处理中'].includes(orderInfo.value.status))
	const hasMaterialDraft = computed(() => !materialReadonly.value && (toolList.value.length > 0 || Boolean(materialRemark.value.trim())))
	const uploadedProgressMediaCount = computed(() => progressImages.value.filter(media => media.status === 'success' && media.id).length)
	const failedProgressMediaCount = computed(() => progressImages.value.filter(media => media.status === 'failed').length)
	const isUploadingProgressMedia = computed(() => progressImages.value.some(media => media.status === 'queued' || media.status === 'uploading'))
	const canSubmitProgress = computed(() => {
		if (!progressDescription.value.trim()) return false
		if (isUploadingProgressMedia.value || failedProgressMediaCount.value > 0) return false
		return progressType.value !== 'COMPLETION' || uploadedProgressMediaCount.value > 0
	})

	const previewRecordImages = (images, index) => {
		if (index < 0 || images.length === 0) return
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

	const previewProgressImage = (media) => {
		if (media.mimeType?.startsWith('video/')) return
		const imageItems = progressImages.value.filter(item => !item.mimeType?.startsWith('video/') && item.status !== 'failed')
		const urls = imageItems.map(item => item.localPath || item.url).filter(Boolean)
		const current = media.localPath || media.url
		if (current && urls.includes(current)) uni.previewImage({ current, urls })
	}

	const deleteProgressImage = (index) => {
		progressImages.value.splice(index, 1)
	}

	const createProgressMediaDraft = (selected) => {
		const localPath = selected.tempFilePath || selected.path || ''
		const isVideo = selected.fileType === 'video' || /\.(mp4|mov|m4v)$/i.test(localPath)
		progressMediaUid += 1
		return {
			uid: `progress-media-${Date.now()}-${progressMediaUid}`,
			id: null,
			url: localPath,
			localPath,
			mimeType: isVideo ? 'video/mp4' : 'image/jpeg',
			status: 'queued',
			progress: 0,
			error: ''
		}
	}

	const uploadProgressMedia = async (media) => {
		const target = progressImages.value.find(item => item.uid === media.uid)
		if (!target) return false
		target.status = 'uploading'
		target.progress = 0
		target.error = ''
		const uploadRes = await orderApi.uploadMedia(target.localPath, {}, {
			loading: false,
			timeout: 10 * 60 * 1000,
			showError: false,
			onProgress: event => {
				const current = progressImages.value.find(item => item.uid === media.uid)
				if (!current) return
				current.progress = Math.max(0, Math.min(100, Number(event.progress || 0)))
				uploadProgress.value = `正在上传附件（${uploadedProgressMediaCount.value}/${progressImages.value.length} 已完成）`
			}
		})
		const current = progressImages.value.find(item => item.uid === media.uid)
		if (!current) return false
		const file = uploadRes.code === 200 ? uploadRes.data : null
		if (file && file.id && file.url) {
			current.id = Number(file.id)
			current.url = resolveMediaUrl(file.url)
			current.mimeType = file.mimeType || current.mimeType
			current.status = 'success'
			current.progress = 100
			current.error = ''
			return true
		}
		current.status = 'failed'
		current.error = uploadRes.msg || '文件上传失败，请重试'
		return false
	}

	const retryProgressMedia = async (media) => {
		if (isUploadingProgressMedia.value || media.status !== 'failed') return
		uploadProgress.value = '正在重新上传附件'
		await uploadProgressMedia(media)
		uploadProgress.value = ''
	}

	const chooseProgressMedia = () => {
		const MAX_IMAGE_BYTES = 10 * 1024 * 1024
		const MAX_VIDEO_BYTES = 200 * 1024 * 1024
		if (isUploadingProgressMedia.value) {
			uni.showToast({ title: '请等待当前附件上传完成', icon: 'none' })
			return
		}
		const remaining = 9 - progressImages.value.length
		if (remaining <= 0) return
		uni.chooseMedia({
			count: remaining,
			mediaType: ['image', 'video'],
			sourceType: ['album', 'camera'],
			maxDuration: 60,
			camera: 'back',
			success: async (res) => {
				const files = res.tempFiles || []
				let oversized = 0
				const accepted = []
				for (const selected of files) {
					const localPath = selected.tempFilePath || selected.path || ''
					const isVideo = selected.fileType === 'video' || /\.(mp4|mov|m4v)$/i.test(localPath)
					const maxBytes = isVideo ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES
					if (Number(selected.size || 0) > maxBytes) {
						oversized++
						continue
					}
					if (localPath) accepted.push(createProgressMediaDraft(selected))
				}
				progressImages.value.push(...accepted)
				for (let index = 0; index < accepted.length; index++) {
					uploadProgress.value = `正在上传 ${index + 1}/${accepted.length}`
					await uploadProgressMedia(accepted[index])
				}
				uploadProgress.value = ''
				if (oversized) {
					uni.showModal({
						title: '文件过大',
						content: `${oversized}个文件超过限制：图片不能超过10MB，视频不能超过200MB。`,
						showCancel: false
					})
				}
			},
			fail: (err) => {
				if (!String(err?.errMsg || '').includes('cancel')) {
					uni.showToast({ title: '图片或视频选择失败，请重试', icon: 'none' })
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
		if (isUploadingProgressMedia.value) {
			uni.showToast({ title: '附件仍在上传，请稍候', icon: 'none' })
			return
		}
		if (failedProgressMediaCount.value) {
			uni.showToast({ title: '请重试或删除上传失败的附件', icon: 'none' })
			return
		}
		if (!canSubmitProgress.value || submittingProgress.value) {
			uni.showToast({
				title: progressType.value === 'COMPLETION' && !uploadedProgressMediaCount.value ? '完工至少上传一个附件' : '请填写施工说明',
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
						fileIds: progressImages.value.filter(media => media.status === 'success' && media.id).map(media => media.id)
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
		if (Number(tool.stock || 0) <= 0) {
			uni.showToast({ title: '该耗材暂无库存', icon: 'none' })
			return
		}
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
		} else if (newVal > Number(tool.stock || 0)) {
			uni.showToast({ title: `库存仅剩 ${tool.stock}${tool.unit || ''}`, icon: 'none' })
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
			stock: Number(item.stock || 0),
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
						stock: Number(item.stock || 0),
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

	.order-placeholder { display: flex; align-items: center; justify-content: center; }

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
		min-width: 0;
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

	.material-request-card {
		padding: 28rpx;

		.section-header {
			padding-bottom: 24rpx;
			margin-bottom: 24rpx;
			border-bottom: 1rpx solid #edf2f7;
		}
	}

	.section-title-icon {
		width: 58rpx;
		height: 58rpx;
		border-radius: 16rpx;
		background: #eaf3ff;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		margin-right: 16rpx;
	}

	.section-title-copy {
		display: flex;
		flex-direction: column;
		min-width: 0;
	}

	.section-subtitle {
		font-size: 21rpx;
		line-height: 1.4;
		color: $text-light;
		margin-top: 5rpx;
	}

	.material-status {
		padding: 8rpx 18rpx;
		border-radius: 24rpx;
		background: #fff7e6;
		color: #d97706;
		font-size: 23rpx;
		font-weight: 500;
		white-space: nowrap;
	}

	.material-subheader {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 18rpx;
	}

	.material-subtitle-wrap {
		display: flex;
		align-items: center;
	}

	.material-subtitle {
		font-size: 27rpx;
		font-weight: 600;
		color: $text-main;
	}

	.material-count,
	.material-optional {
		font-size: 21rpx;
		color: $text-light;
		background: #f1f5f9;
		padding: 6rpx 14rpx;
		border-radius: 20rpx;
	}

	.material-divider {
		height: 1rpx;
		background: #edf2f7;
		margin: 28rpx 0 24rpx;
	}

	.material-remark-header {
		margin-bottom: 16rpx;
	}

	.material-remark-input {
		height: 168rpx;
		background: #f8fafc;
		border: 1rpx solid #edf2f7;
	}

	.material-remark-readonly {
		min-height: 96rpx;
		background: #f8fafc;
		border: 1rpx solid #edf2f7;
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
		justify-content: space-between;
		gap: 18rpx;
	}

	.upload-panel {
		margin-top: 24rpx;
		padding: 22rpx;
		border: 2rpx solid #e2edf9;
		border-radius: 20rpx;
		background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
	}

	.upload-title-group,
	.upload-title-line,
	.upload-limit-row,
	.upload-summary {
		display: flex;
		align-items: center;
	}

	.upload-title-group {
		min-width: 0;
		gap: 14rpx;
	}

	.upload-title-icon {
		width: 62rpx;
		height: 62rpx;
		border-radius: 18rpx;
		background: #e7f2ff;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}

	.upload-title-copy {
		display: flex;
		flex-direction: column;
		min-width: 0;
		gap: 4rpx;
	}

	.upload-title-line {
		gap: 10rpx;
		flex-wrap: wrap;
	}

	.upload-title {
		font-size: 28rpx;
		font-weight: 650;
		color: $text-main;
	}

	.upload-required,
	.upload-optional {
		padding: 4rpx 12rpx;
		border-radius: 18rpx;
		font-size: 20rpx;
	}

	.upload-required {
		background: #fff1f2;
		color: #e11d48;
	}

	.upload-optional {
		background: #eef2f7;
		color: $text-sub;
	}

	.upload-subtitle {
		font-size: 22rpx;
		color: $text-light;
	}

	.upload-count {
		padding: 8rpx 16rpx;
		border-radius: 22rpx;
		background: #e7f2ff;
		font-size: 23rpx;
		font-weight: 600;
		color: $primary;
		white-space: nowrap;
	}

	.upload-limit-row {
		gap: 10rpx;
		flex-wrap: wrap;
		margin: 18rpx 0 20rpx;
	}

	.upload-limit-pill {
		padding: 7rpx 13rpx;
		border: 1rpx solid #dce8f5;
		border-radius: 20rpx;
		background: #fff;
		font-size: 21rpx;
		color: $text-sub;
	}

	.upload-media-grid {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 14rpx;
	}

	.upload-media-item,
	.upload-add-card {
		position: relative;
		height: 184rpx;
		border-radius: 16rpx;
		overflow: hidden;
		box-sizing: border-box;
	}

	.upload-media-item {
		background: #e8eef5;
		box-shadow: 0 4rpx 14rpx rgba(20, 54, 84, .08);
	}

	.upload-add-card {
		border: 2rpx dashed #b7d2ef;
		background: #f4f9ff;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 7rpx;

		&.disabled {
			opacity: .55;
		}
	}

	.upload-add-icon {
		width: 54rpx;
		height: 54rpx;
		border-radius: 50%;
		background: #e2f0ff;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.add-remain {
		font-size: 19rpx;
		color: $text-light;
	}

	.media-status-mask {
		position: absolute;
		inset: 0;
		background: rgba(15, 35, 55, .58);
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 9rpx;
		font-size: 21rpx;
		color: #fff;

		&.failed {
			background: rgba(153, 27, 27, .72);
		}
	}

	.media-type-badge {
		position: absolute;
		left: 8rpx;
		bottom: 8rpx;
		padding: 4rpx 10rpx;
		border-radius: 14rpx;
		background: rgba(15, 23, 42, .68);
		font-size: 19rpx;
		color: #fff;
	}

	.upload-summary {
		gap: 8rpx;
		margin-top: 16rpx;
		font-size: 22rpx;
		color: $primary;

		text.error {
			color: #dc2626;
		}
	}

	.inline-submit {
		height: 88rpx;
		border-radius: 44rpx;
		background: linear-gradient(135deg, #3b8eea, #0b63ce);
		display: flex;
		align-items: center;
		justify-content: center;
		margin-top: 40rpx;
		box-shadow: 0 10rpx 24rpx rgba(11, 99, 206, 0.18);

		text {
			font-size: 28rpx;
			font-weight: 600;
			color: #fff;
		}

		&.disabled {
			opacity: 0.5;
			box-shadow: none;
		}

		&:active {
			opacity: 0.86;
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
		font-weight: 600;
		color: $primary;
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
	height: 40rpx;
}

	/* 耗材清单内提交按钮：仅在已选择耗材时展示 */
	.material-submit {
		height: 88rpx;
		border-radius: 44rpx;
		background: linear-gradient(135deg, #3b8eea, #0b63ce);
		display: flex;
		align-items: center;
		justify-content: center;
		margin-top: 32rpx;
		box-shadow: 0 8rpx 20rpx rgba(11, 99, 206, 0.16);

		text {
			font-size: 28rpx;
			font-weight: 600;
			color: #fff;
		}

		&.disabled {
			opacity: 0.5;
			box-shadow: none;
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

		&.disabled {
			background: #cbd5e1;
			opacity: .75;
		}
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
