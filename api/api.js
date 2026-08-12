/**
 * 接口统一出口
 * 与后台 conditioner-web 共用同一套 RESTful 接口
 *
 * 统一返回结构：{ code, data, msg }
 *   - code===200 成功，页面用 res.data 取数据
 *   - code===401 登录失效（request.js 自动跳登录）
 *   - 其他业务码：request.js 已统一 toast(res.msg)，页面无需重复提示
 *
 * Mock 模式：isMockMode() 为 true 时（虚拟登录后），每个接口直接返回 mockSuccess(mockData)，
 *           数据字段已与页面模板对齐，页面无需任何 mapXxx 转换。
 * 真实后端为默认模式；仅通过 VITE_USE_MOCK_LOGIN=true 显式启用离线预览。
 *           （只需保证后端返回的 data 字段名与 mock.js 一致即可）。
 *
 * 使用示例：
 *   import { orderApi } from '@/api/api.js'
 *   const res = await orderApi.getList({ page: 1, pageSize: 10 })
 *   if (res.code === 200) { list.value = res.data.list }
 */
import http from '../utils/request.js'
import {
	mockConsumables,
	mockOrders,
	mockDynamics,
	mockUserInfo,
	mockDashboard,
	mockEvaluations,
	getEvaluationByOrderId,
	isMockMode,
	mockSuccess,
	mockPaging
} from '../utils/mock.js'

// ====================== 认证相关 ======================
export const authApi = {
	// 微信一键登录（传微信 code；已注册用户返回 { token, userInfo }，新用户返回 { needPhone: true }）
	// 注：mock 模式下 login.vue 走 virtualLogin，不会调用此接口，故不加 mock 分支
	loginWithWechat: (data) => http.post('/auth/wechat/login', data),
	// 手机号授权绑定
	bindPhone: (data) => http.post('/auth/wechat/bind-phone', data),
	// 账号密码登录（与后台管理端通用）
	login: (data) => http.post('/auth/login', data),
	// 获取当前登录用户信息
	getUserInfo: async () => {
		if (isMockMode()) return mockSuccess(mockUserInfo)
		return http.get('/auth/info')
	},
	// 退出登录
	logout: async () => {
		if (isMockMode()) return mockSuccess(true)
		return http.post('/auth/logout')
	},
	// 修改密码 { oldPassword, newPassword }
	changePassword: (data) => http.post('/auth/password', data)
}

// ====================== 订单相关 ======================
export const orderApi = {
	// 订单列表（分页） params: { page, pageSize, status, keyword, startDate, endDate }
	getList: async (params) => {
		if (isMockMode()) return mockSuccess(mockPaging(mockOrders, params))
		return http.get('/orders/list', params)
	},
	// 订单详情
	getDetail: async (id) => {
		if (isMockMode()) {
			const o = mockOrders.find(item => String(item.id) === String(id)) || mockOrders[0]
			return mockSuccess(o)
		}
		return http.get(`/orders/detail/${id}`)
	},
	// 编辑订单（更新订单状态、完工信息等）
	update: async (id, data) => {
		if (isMockMode()) return mockSuccess({ success: true })
		return http.put(`/orders/${id}`, data)
	},
	// 作废订单
	cancel: (id) => http.post(`/orders/${id}/cancel`),
	// 可指派师傅列表
	getMasters: (params) => http.get('/orders/masters', params),
	// 上传订单附件 / 安装图片（返回 { url }）
	uploadImage: (filePath, formData = {}, options = {}) => {
		if (isMockMode()) {
			return mockSuccess({ url: 'https://picsum.photos/300/300?random=' + Date.now() })
		}
		return http.upload({
			url: '/orders/upload',
			filePath,
			name: 'file',
			formData,
			...options
		})
	}
}

// ====================== 耗材相关 ======================
export const consumablesApi = {
	// 耗材列表（分页） params: { page, pageSize, keyword, category }
	getList: async (params) => {
		if (isMockMode()) return mockSuccess(mockPaging(mockConsumables, params))
		return http.get('/consumables/list', params)
	},
	// 耗材详情
	getDetail: async (id) => {
		if (isMockMode()) {
			const c = mockConsumables.find(item => String(item.id) === String(id)) || mockConsumables[0]
			return mockSuccess(c)
		}
		return http.get(`/consumables/detail/${id}`)
	}
}

// ====================== 动态资讯 / 平台公告 ======================
export const dynamicApi = {
	// 资讯列表（分页） params: { page, pageSize, keyword, category }
	getList: async (params) => {
		if (isMockMode()) return mockSuccess(mockPaging(mockDynamics, params))
		return http.get('/dynamic/list', params)
	},
	// 资讯详情
	getDetail: async (id) => {
		if (isMockMode()) {
			const d = mockDynamics.find(item => String(item.id) === String(id)) || mockDynamics[0]
			return mockSuccess(d)
		}
		return http.get(`/dynamic/detail/${id}`)
	}
}

// ====================== 仪表盘 / 工作台概览 ======================
export const dashboardApi = {
	// 核心指标概览（供「我的」页 stats）
	getOverview: async () => {
		if (isMockMode()) return mockSuccess(mockDashboard)
		return http.get('/dashboard/overview')
	},
	// 待办列表
	getTodo: async (params) => {
		if (isMockMode()) return mockSuccess(mockPaging([], params))
		return http.get('/dashboard/todo', params)
	}
}

// ====================== 订单评价 ======================
export const evaluationApi = {
	// 根据订单 ID 获取评价（返回评价对象或 null）
	getByOrderId: async (orderId) => {
		if (isMockMode()) return mockSuccess(getEvaluationByOrderId(orderId) || null)
		return http.get(`/orders/evaluation/${orderId}`)
	},
	// 获取已评价订单 ID 列表（供订单列表快速标记"已评价"）
	getEvaluatedOrderIds: async () => {
		if (isMockMode()) return mockSuccess(mockEvaluations.map(item => String(item.orderId)))
		return http.get('/orders/evaluation/ids')
	},
	// 提交评价 { orderId, score, content, images }
	submit: async (data) => {
		if (isMockMode()) {
			// 更新本地 mock 数据，提交后立即能在订单详情看到
			const index = mockEvaluations.findIndex(item => String(item.orderId) === String(data.orderId))
			const record = {
				orderId: data.orderId,
				score: data.score,
				content: data.content,
				images: data.images || [],
				createTime: '2026-08-04 12:00',
				labels: [data.label || '']
			}
			if (index >= 0) {
				mockEvaluations.splice(index, 1, record)
			} else {
				mockEvaluations.push(record)
			}
			return mockSuccess({ success: true })
		}
		return http.post('/orders/evaluation', data)
	}
}

// ====================== 通用图片上传 ======================
export const uploadApi = {
	// 通用图片上传（返回图片 url）
	uploadImage: (filePath, formData = {}, options = {}) => {
		if (isMockMode()) {
			return Promise.resolve(mockSuccess({ url: 'https://picsum.photos/300/300?random=' + Date.now() }))
		}
		return http.upload({
			url: '/upload/image',
			filePath,
			name: 'file',
			formData,
			...options
		})
	}
}
