import request from '@/utils/request'
import { bindFileApi, getFileId, unbindFileApi, uploadFileApi } from './files'
import { createCsvBlob } from '@/utils/export'

function normalizeOrder(item = {}) {
  const masters = item.selectedMasterList || item.masterList || []
  return {
    ...item,
    taskType: item.taskType || item.productName || '',
    status: item.status || item.statusLabel || item.statusCode || '',
    assignMaster: masters.map(master => master.masterName).filter(Boolean).join('、'),
    createTime: item.createTime || item.createdAt || '',
    visitTime: item.visitTime || item.orderStartTime || '',
    fileList: (item.fileList || item.images || []).map(file => ({
      ...file,
      id: getFileId(file),
      url: typeof file === 'string' ? file : (file.url || '')
    }))
  }
}

async function getAllOrders(params = {}) {
  const first = await getOrderListApi({ ...params, page: 1, pageSize: 100 })
  const rows = [...(first.list || [])]
  const pages = Math.ceil(Number(first.total || 0) / 100)
  for (let page = 2; page <= pages; page++) {
    const result = await getOrderListApi({ ...params, page, pageSize: 100 })
    rows.push(...(result.list || []))
  }
  return rows
}

/**
 * 订单管理模块 API
 * 后端接口对接说明：
 *   - 列表：GET /orders/list，分页 + 多条件搜索
 *   - 详情：GET /orders/detail/:id
 *   - 新增：POST /orders
 *   - 更新：PUT /orders/:id
 *   - 删除：DELETE /orders/:id
 *   - 指派师傅：POST /orders/:id/assign-master
 * 统一分页返回：{ list: [], total: number }
 */

/**
 * 获取订单分页列表
 * @param {Object} params
 * @param {number} [params.page=1]
 * @param {number} [params.pageSize=10]
 * @param {string} [params.keyword] - 订单号/客户姓名/手机号
 * @param {string} [params.status] - 订单状态
 * @param {string} [params.startDate] - 起始日期
 * @param {string} [params.endDate] - 截止日期
 * @returns {Promise<{list: Object[], total: number}>}
 */
export function getOrderListApi(params) {
  return request({
    url: '/orders/list',
    method: 'get',
    params
  }).then(result => ({ ...result, list: (result?.list || []).map(normalizeOrder) }))
}

/**
 * 获取订单详情（编辑回显）
 * @param {string|number} id
 * @returns {Promise<Object>}
 */
export function getOrderDetailApi(id) {
  return request({
    url: `/orders/detail/${id}`,
    method: 'get'
  }).then(normalizeOrder)
}

/** 管理员只读详情：订单、施工进度、全部耗材申请及评价。 */
export function getAdminOrderDetailApi(id) {
  return request({
    url: `/admin/orders/${encodeURIComponent(id)}`,
    method: 'get'
  })
}

/**
 * 管理员查看指定订单的客户评价。
 * 评价内容属于后台管理信息，服务端会拒绝非管理员角色访问。
 * @param {string|number} orderId
 * @returns {Promise<Object|null>}
 */
export function getOrderEvaluationApi(orderId) {
  return request({
    url: `/orders/evaluation/${orderId}`,
    method: 'get'
  })
}

/**
 * 新增订单
 * @param {Object} data - 订单表单数据
 * @returns {Promise<Object>}
 */
export function addOrderApi(data) {
  return request({
    url: '/orders',
    method: 'post',
    data
  })
}

/**
 * 更新订单
 * @param {string|number} id
 * @param {Object} data
 * @returns {Promise<Object>}
 */
export function updateOrderApi(id, data) {
  return request({
    url: `/orders/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除订单
 * @param {string|number} id
 * @returns {Promise<void>}
 */
export function deleteOrderApi(id) {
  return request({
    url: `/orders/${id}`,
    method: 'delete'
  })
}

/**
 * 指派安装师傅
 * @param {string|number} orderId
 * @param {Object} data
 * @param {Array<number>} data.masterIds - 师傅 ID 列表
 * @returns {Promise<void>}
 */
export function assignMasterApi(orderId, data) {
  return request({
    url: `/orders/${orderId}/assign-master`,
    method: 'post',
    data
  })
}

/**
 * 获取可指派的师傅列表
 * @param {Object} [params]
 * @param {string} [params.keyword] - 姓名/手机号
 * @returns {Promise<Object[]>}
 */
export function getMasterListApi(params) {
  return request({
    url: '/orders/masters',
    method: 'get',
    params
  })
}

/**
 * 上传订单附件图片
 * @param {FormData} formData
 * @returns {Promise<{url: string}>}
 */
export function uploadOrderImageApi(formData) {
  return uploadFileApi(formData)
}

export function bindOrderFileApi(fileId, orderId, sortOrder) {
  return bindFileApi(fileId, {
    businessType: 'ORDER',
    businessId: Number(orderId),
    usageType: 'ATTACHMENT',
    sortOrder
  })
}

export function unbindOrderFileApi(fileId, orderId) {
  return unbindFileApi(fileId, {
    businessType: 'ORDER',
    businessId: Number(orderId),
    usageType: 'ATTACHMENT'
  })
}

/**
 * 作废订单
 * @param {string|number} id
 * @param {Object} [data]
 * @param {string} [data.reason] - 作废原因
 * @returns {Promise<void>}
 */
export function cancelOrderApi(id, data) {
  return request({
    url: `/orders/${id}/cancel`,
    method: 'post',
    data
  })
}

/**
 * 批量导出订单（文件流下载）
 * @param {Object} [params] - 导出筛选条件，同列表搜索参数
 * @param {string} [params.keyword]
 * @param {string} [params.status]
 * @returns {Promise<Blob>}
 */
export async function exportOrdersApi(params) {
  const rows = await getAllOrders(params)
  return createCsvBlob([
    { label: '订单编号', value: row => row.orderNo },
    { label: '任务类型', value: row => row.taskType },
    { label: '客户姓名', value: row => row.customerName },
    { label: '客户手机号', value: row => row.customerPhone },
    { label: '地址', value: row => row.address },
    { label: '安装师傅', value: row => row.assignMaster },
    { label: '状态', value: row => row.status },
    { label: '创建时间', value: row => row.createTime }
  ], rows)
}
