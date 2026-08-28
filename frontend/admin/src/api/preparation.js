import request from '@/utils/request'
import { createCsvBlob } from '@/utils/export'

/**
 * 订单备货模块 API
 * 后端接口对接说明：
 *   - 列表：GET /preparation/list，分页 + 搜索 + 状态筛选
 *   - 详情（含耗材清单）：GET /preparation/detail/:id
 *   - 提交备货：POST /preparation/:id/prepare，更新耗材勾选状态
 *   - 确认完成：POST /preparation/:id/finish
 * 统一分页返回：{ list: [], total: number }
 */

/**
 * 获取订单备货分页列表
 * @param {Object} params
 * @param {number} [params.page=1]
 * @param {number} [params.pageSize=10]
 * @param {string} [params.keyword] - 订单号/产品名称
 * @param {string} [params.status] - 备货状态：'pending' | 'preparing' | 'done'
 * @returns {Promise<{list: Object[], total: number}>}
 */
export function getPreparationListApi(params) {
  return request({
    url: '/preparation/list',
    method: 'get',
    params
  })
}

/**
 * 获取备货详情（含耗材勾选清单）
 * @param {string|number} id - 订单备货 ID
 * @returns {Promise<Object>}
 */
export function getPreparationDetailApi(id) {
  return request({
    url: `/preparation/detail/${id}`,
    method: 'get'
  })
}

/**
 * 提交备货（保存耗材勾选状态）
 * @param {string|number} id
 * @param {Object} data
 * @param {Array<{id: number, checked: boolean}>} data.materials - 耗材勾选列表
 * @returns {Promise<void>}
 */
export function submitPreparationApi(id, data) {
  return request({
    url: `/preparation/${id}/prepare`,
    method: 'post',
    data
  })
}

/**
 * 确认备货完成
 * @param {string|number} id
 * @returns {Promise<void>}
 */
export function finishPreparationApi(id) {
  return request({
    url: `/preparation/${id}/finish`,
    method: 'post'
  })
}

/**
 * 批量导出备货清单（文件流下载）
 * @param {Object} [params] - 导出筛选条件，同列表搜索参数
 * @param {string} [params.keyword]
 * @param {string} [params.status]
 * @returns {Promise<Blob>}
 */
export async function exportPreparationApi(params) {
  const first = await getPreparationListApi({ ...params, page: 1, pageSize: 100 })
  const rows = [...(first.list || [])]
  const pages = Math.ceil(Number(first.total || 0) / 100)
  for (let page = 2; page <= pages; page++) {
    const result = await getPreparationListApi({ ...params, page, pageSize: 100 })
    rows.push(...(result.list || []))
  }
  return createCsvBlob([
    { label: '申请编号', value: row => row.requestNo },
    { label: '订单编号', value: row => row.orderNo },
    { label: '订单名称', value: row => row.productName },
    { label: '状态', value: row => row.statusLabel || row.status },
    { label: '申请时间', value: row => row.createTime }
  ], rows)
}
