/**
 * 接口统一出口
 * 与后台 conditioner-web 共用同一套 RESTful 接口
 *
 * 统一返回结构：{ code, data, msg }
 *   - code===200 成功，页面用 res.data 取数据
 *   - code===401 登录失效（request.js 清除登录态并提供可拒绝的登录引导）
 *   - 其他业务码：request.js 已统一 toast(res.msg)，页面无需重复提示
 *
 * 使用示例：
 *   import { orderApi } from '@/api/api.js'
 *   const res = await orderApi.getList({ page: 1, pageSize: 10 })
 *   if (res.code === 200) { list.value = res.data.list }
 */
import http from '../utils/request.js'
import baseUrl from '../config.js'
import { formatDateTime } from '../utils/time.js'

const apiOrigin = (String(baseUrl).match(/^https?:\/\/[^/]+/i) || [''])[0]

export const resolveMediaUrl = (value) => {
	const url = typeof value === 'string' ? value : (value?.url || value?.previewUrl || '')
	if (!url) return ''
	if (/^(?:https?:|wxfile:|blob:|data:|file:)/i.test(url)) return url
	return url.startsWith('/') ? `${apiOrigin}${url}` : `${apiOrigin}/${url}`
}

const normalizeFile = (file = {}) => ({
	...(typeof file === 'object' ? file : {}),
	id: Number(file?.id || 0) || undefined,
	mimeType: file?.mimeType || file?.type || '',
	url: resolveMediaUrl(file)
})

const normalizeRole = (user = {}) => ({
	...user,
	role: String(user.role || '').toLowerCase(),
	roles: (user.roles || []).map(role => String(role).toLowerCase())
})

const normalizeAuthResponse = (res) => {
	if (res && res.code === 200 && res.data) {
		if (res.data.userInfo) res.data.userInfo = normalizeRole(res.data.userInfo)
		if (res.data.login && !res.data.token) Object.assign(res.data, res.data.login)
	}
	return res
}

const normalizeProduct = (item = {}) => ({
	...item,
	title: item.title || item.name || '',
	desc: item.desc || item.remark || '',
	model: item.model || item.code || '',
	category: Array.isArray(item.category) ? item.category[item.category.length - 1] : (item.category || ''),
	tags: item.tags || [],
	stock: Number(item.stock ?? item.quantity ?? 0),
	image: resolveMediaUrl(item.image || item.coverImage || item.thumbnail),
	detailImages: (item.detailImages || item.images || []).map(resolveMediaUrl).filter(Boolean)
})

const normalizeOrder = (item = {}) => {
	const fileList = (item.fileList || item.attachments || item.images || []).map(normalizeFile).filter(file => file.url)
	const firstImage = fileList.find(file => !file.mimeType || file.mimeType.startsWith('image/'))
	return {
		...item,
		visitTime: formatDateTime(item.visitTime || item.orderStartTime || ''),
		customerConfirmedAt: item.customerConfirmedAt ? formatDateTime(item.customerConfirmedAt) : '',
		quantity: item.quantity == null ? null : Number(item.quantity),
		status: item.status || item.statusLabel || item.statusCode || '',
		name: item.name || item.customerName || '',
		phone: item.phone || item.customerPhone || '',
		fileList,
		image: resolveMediaUrl(item.image) || firstImage?.url || ''
	}
}

const normalizeProgress = (item = {}) => {
	const media = (item.media || item.files || item.images || []).map(normalizeFile).filter(file => file.url)
	return {
		...item,
		typeLabel: item.type === 'COMPLETION' ? '完工记录' : '施工进度',
		submittedAt: formatDateTime(item.submittedAt || item.createTime || item.createdAt),
		media,
		images: media.filter(file => !file.mimeType || file.mimeType.startsWith('image/')),
		videos: media.filter(file => file.mimeType.startsWith('video/'))
	}
}

const normalizeEvaluation = (item = {}) => ({
	...item,
	score: Number(item.score || 0),
	liked: Boolean(item.liked),
	content: item.content || '',
	labels: item.labels || [],
	images: (item.images || []).map(resolveMediaUrl).filter(Boolean),
	createTime: formatDateTime(item.createTime || item.createdAt || '')
})

// ====================== 认证相关 ======================
export const authApi = {
	// 微信一键登录（传微信 code；已注册用户返回 { token, userInfo }，新用户返回 { needPhone: true }）
	loginWithWechat: (data) => http.post('/auth/wechat/login', data, { auth: false, redirectOnUnauthorized: false }).then(normalizeAuthResponse),
	// 手机号授权绑定
	bindPhone: (data) => http.post('/auth/wechat/bind-phone', data, { auth: false, redirectOnUnauthorized: false }).then(normalizeAuthResponse),
	// 获取当前登录用户信息
	getUserInfo: async (options = {}) => {
		const res = await http.get('/auth/info', {}, options)
		if (res.code === 200) res.data = normalizeRole(res.data)
		return res
	},
	// 退出登录
	logout: () => http.post('/auth/logout'),
	// 用户主动注销账号并删除/匿名化账号个人信息
	cancelAccount: () => http.post('/auth/account/cancel', { confirmed: true })
}

// ====================== 订单相关 ======================
export const orderApi = {
	// 订单列表（分页） params: { page, pageSize, status, keyword, startDate, endDate }
	getList: async (params) => {
		const res = await http.get('/orders/list', params)
		if (res.code === 200 && res.data) res.data.list = (res.data.list || []).map(normalizeOrder)
		return res
	},
	// 订单详情
	getDetail: async (id, options = {}) => {
		const res = await http.get(`/orders/detail/${id}`, {}, options)
		if (res.code === 200) res.data = normalizeOrder(res.data)
		return res
	},
	// 管理员编辑订单基础信息
	update: (id, data) => http.put(`/orders/${id}`, data),
	// 作废订单
	cancel: (id) => http.post(`/orders/${id}/cancel`),
	// 可指派师傅列表
	getMasters: (params) => http.get('/orders/masters', params),
	getMaterials: (id, options = {}) => http.get(`/orders/${id}/materials`, {}, options),
	submitMaterials: (id, data) => http.post(`/orders/${id}/materials`, data),
	getProgress: async (id, options = {}) => {
		const res = await http.get(`/orders/${id}/progress`, {}, options)
		if (res.code === 200) res.data = (res.data || []).map(normalizeProgress)
		return res
	},
	submitProgress: (id, data) => http.post(`/orders/${id}/progress`, data),
	confirmCompletion: async (id) => {
		const res = await http.post(`/orders/${id}/confirm-completion`)
		if (res.code === 200 && res.data?.customerConfirmedAt) res.data.customerConfirmedAt = formatDateTime(res.data.customerConfirmedAt)
		return res
	},
	// 上传订单附件 / 安装图片（返回 { url }）
	uploadMedia: (filePath, formData = {}, options = {}) => {
		return http.upload({
			url: '/orders/upload',
			filePath,
			name: 'file',
			formData,
			...options
		})
	},
	uploadImage: (filePath, formData = {}, options = {}) => orderApi.uploadMedia(filePath, formData, options)
}

// ====================== 订单评价相关 ======================
export const evaluationApi = {
	getByOrder: async (orderId, options = {}) => {
		const res = await http.get(`/orders/evaluation/${orderId}`, {}, options)
		if (res.code === 200 && res.data) res.data = normalizeEvaluation(res.data)
		return res
	},
	getReviewedIds: () => http.get('/orders/evaluation/ids'),
	// 提交成功仅返回提交凭证；评价正文、评分、标签和图片仅管理员查询接口可见。
	submit: (data) => http.post('/orders/evaluation', data)
}

// ====================== 耗材相关 ======================
export const consumablesApi = {
	// 耗材列表（分页） params: { page, pageSize, keyword, category }
	getList: async (params) => {
		const res = await http.get('/consumables/list', params, { auth: false, redirectOnUnauthorized: false })
		if (res.code === 200 && res.data) res.data.list = (res.data.list || []).map(normalizeProduct)
		return res
	},
	// 耗材详情
	getDetail: async (id) => {
		const res = await http.get(`/consumables/detail/${id}`, {}, { auth: false, redirectOnUnauthorized: false })
		if (res.code === 200) res.data = normalizeProduct(res.data)
		return res
	},
	getCategories: () => http.get('/consumables/categories', {}, { auth: false, redirectOnUnauthorized: false })
}

// ====================== 仪表盘 / 工作台概览 ======================
export const dashboardApi = {
	// 角色可见的待办列表与订单统计
	getTodo: (params, options = {}) => http.get('/dashboard/todo', params, options)
}

// ====================== 通用图片上传 ======================
export const uploadApi = {
	// 通用图片上传（返回图片 url）
	uploadImage: (filePath, formData = {}, options = {}) => {
		return http.upload({
			url: '/files/upload',
			filePath,
			name: 'file',
			formData,
			...options
		})
	},
	// 删除尚未提交、未绑定业务的临时文件，防止退出页面后残留孤立附件。
	deleteTemporary: (id, options = {}) => http.delete(`/files/${id}`, {}, options)
}
