/**
 * 接口统一出口
 * 与后台 conditioner-web 共用同一套 RESTful 接口
 *
 * 统一返回结构：{ code, data, msg }
 *   - code===200 成功，页面用 res.data 取数据
 *   - code===401 登录失效（request.js 自动跳登录）
 *   - 其他业务码：request.js 已统一 toast(res.msg)，页面无需重复提示
 *
 * 使用示例：
 *   import { orderApi } from '@/api/api.js'
 *   const res = await orderApi.getList({ page: 1, pageSize: 10 })
 *   if (res.code === 200) { list.value = res.data.list }
 */
import http from '../utils/request.js'

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
	detailImages: (item.detailImages || []).map(file => typeof file === 'string' ? file : file.url).filter(Boolean)
})

const normalizeOrder = (item = {}) => ({
	...item,
	visitTime: item.visitTime || item.orderStartTime || '',
	quantity: item.quantity || 1,
	status: item.status || item.statusLabel || item.statusCode || '',
	name: item.name || item.customerName || '',
	phone: item.phone || item.customerPhone || ''
})

const normalizeProgress = (item = {}) => ({
	...item,
	typeLabel: item.type === 'COMPLETION' ? '完工记录' : '施工进度',
	images: (item.images || []).map(file => ({
		...file,
		id: Number(file.id),
		url: file.url || ''
	})).filter(file => file.url)
})

const normalizeEvaluation = (item = {}) => ({
	...item,
	score: Number(item.score || 0),
	liked: Boolean(item.liked),
	content: item.content || '',
	labels: item.labels || [],
	images: (item.images || []).filter(Boolean),
	createTime: item.createTime || item.createdAt || ''
})

// ====================== 认证相关 ======================
export const authApi = {
	// 微信一键登录（传微信 code；已注册用户返回 { token, userInfo }，新用户返回 { needPhone: true }）
	loginWithWechat: (data) => http.post('/auth/wechat/login', data).then(normalizeAuthResponse),
	// 手机号授权绑定
	bindPhone: (data) => http.post('/auth/wechat/bind-phone', data).then(normalizeAuthResponse),
	// 获取当前登录用户信息
	getUserInfo: async (options = {}) => {
		const res = await http.get('/auth/info', {}, options)
		if (res.code === 200) res.data = normalizeRole(res.data)
		return res
	},
	// 退出登录
	logout: () => http.post('/auth/logout')
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
	getDetail: async (id) => {
		const res = await http.get(`/orders/detail/${id}`)
		if (res.code === 200) res.data = normalizeOrder(res.data)
		return res
	},
	// 编辑订单（更新订单状态、完工信息等）
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
	complete: (id, data) => http.post(`/orders/${id}/completion`, data),
	// 上传订单附件 / 安装图片（返回 { url }）
	uploadImage: (filePath, formData = {}, options = {}) => {
		return http.upload({
			url: '/orders/upload',
			filePath,
			name: 'file',
			formData,
			...options
		})
	}
}

// ====================== 订单评价相关 ======================
export const evaluationApi = {
	getByOrder: async (orderId, options = {}) => {
		const res = await http.get(`/orders/evaluation/${orderId}`, {}, options)
		if (res.code === 200 && res.data) res.data = normalizeEvaluation(res.data)
		return res
	},
	getReviewedIds: () => http.get('/orders/evaluation/ids'),
	submit: async (data) => {
		const res = await http.post('/orders/evaluation', data)
		if (res.code === 200 && res.data) res.data = normalizeEvaluation(res.data)
		return res
	}
}

// ====================== 耗材相关 ======================
export const consumablesApi = {
	// 耗材列表（分页） params: { page, pageSize, keyword, category }
	getList: async (params) => {
		const res = await http.get('/consumables/list', params)
		if (res.code === 200 && res.data) res.data.list = (res.data.list || []).map(normalizeProduct)
		return res
	},
	// 耗材详情
	getDetail: async (id) => {
		const res = await http.get(`/consumables/detail/${id}`)
		if (res.code === 200) res.data = normalizeProduct(res.data)
		return res
	}
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
	}
}
