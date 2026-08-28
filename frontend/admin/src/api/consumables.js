import request from '@/utils/request'
import { uploadFileApi, getFileId } from './files'
import { createCsvBlob } from '@/utils/export'

function normalizeProduct(item = {}) {
  return {
    ...item,
    productCode: item.productCode || item.code || '',
    categoryId: item.categoryId == null ? null : Number(item.categoryId),
    category: Array.isArray(item.category) ? item.category : [],
    image: item.image || '',
    coverFileId: item.coverFileId || getFileId(item.image),
    detailImages: (item.detailImages || []).map(file => ({
      ...file,
      id: getFileId(file),
      url: typeof file === 'string' ? file : (file.url || '')
    })),
    createTime: item.createTime || item.createdAt || '',
    enabled: item.enabled !== false
  }
}

function toProductPayload(data = {}) {
  return {
    productCode: data.productCode || data.code || undefined,
    name: data.name,
    categoryId: data.categoryId == null ? null : Number(data.categoryId),
    spec: data.spec || '',
    unit: data.unit,
    stock: Number(data.stock || 0),
    price: Number(data.price || 0),
    remark: data.remark || '',
    coverFileId: data.coverFileId || getFileId(data.image),
    detailFileIds: (data.detailFileIds || data.detailImages || []).map(getFileId).filter(Boolean),
    enabled: data.enabled !== false,
    sortOrder: Number(data.sortOrder || 0)
  }
}

async function getAllProducts(params = {}) {
  const first = await getConsumablesListApi({ ...params, page: 1, pageSize: 100 })
  const rows = [...(first.list || [])]
  const pages = Math.ceil(Number(first.total || 0) / 100)
  for (let page = 2; page <= pages; page++) {
    const result = await getConsumablesListApi({ ...params, page, pageSize: 100 })
    rows.push(...(result.list || []))
  }
  return rows
}

/**
 * 耗材管理模块 API
 * 后端接口对接说明：
 *   - 列表：GET /consumables/list，分页 + 多条件搜索
 *   - 详情：GET /consumables/detail/:id
 *   - 新增：POST /consumables
 *   - 更新：PUT /consumables/:id
 *   - 删除：DELETE /consumables/:id
 * 统一分页返回：{ list: [], total: number }
 */

/**
 * 获取耗材分页列表
 * @param {Object} params
 * @param {number} [params.page=1]
 * @param {number} [params.pageSize=10]
 * @param {string} [params.keyword] - 耗材名称/规格
 * @param {string} [params.category] - 一级分类
 * @param {string} [params.subCategory] - 二级分类
 * @returns {Promise<{list: Object[], total: number}>}
 */
export function getConsumablesListApi(params) {
  return request({
    url: '/consumables/list',
    method: 'get',
    params
  }).then(result => ({ ...result, list: (result?.list || []).map(normalizeProduct) }))
}

/**
 * 获取耗材详情（编辑回显）
 * @param {string|number} id
 * @returns {Promise<Object>}
 */
export function getConsumablesDetailApi(id) {
  return request({
    url: `/consumables/detail/${id}`,
    method: 'get'
  }).then(normalizeProduct)
}

export function getConsumableCategoriesApi() {
  return request({ url: '/consumables/categories', method: 'get' })
}

/**
 * 新增耗材
 * @param {Object} data
 * @param {string} data.name - 耗材名称
 * @param {Array<string>} data.category - 二级分类 [一级, 二级]
 * @param {string} data.spec - 规格
 * @param {string} data.unit - 单位
 * @param {number} data.stock - 库存
 * @param {string} data.image - 主图 URL
 * @param {string} [data.remark] - 备注
 * @param {Array<{url: string}>} [data.detailImages] - 耗材详情图片（最多9张）
 * @returns {Promise<Object>}
 */
export function addConsumablesApi(data) {
  return request({
    url: '/consumables',
    method: 'post',
    data: toProductPayload(data)
  })
}

/**
 * 更新耗材
 * @param {string|number} id
 * @param {Object} data - 同新增
 * @returns {Promise<Object>}
 */
export function updateConsumablesApi(id, data) {
  return request({
    url: `/consumables/${id}`,
    method: 'put',
    data: toProductPayload(data)
  })
}

export function setConsumableEnabledApi(id, enabled) {
  return request({
    url: `/consumables/${id}/enabled`,
    method: 'patch',
    data: { enabled }
  })
}

/**
 * 原子调整耗材库存，并由后端记录调整原因与审计信息。
 * @param {string|number} id
 * @param {{type: 'IN'|'OUT', quantity: number, reason: string}} data
 * @returns {Promise<Object>}
 */
export function adjustConsumableStockApi(id, data) {
  return request({
    url: `/consumables/${id}/stock-adjustment`,
    method: 'post',
    data: {
      type: String(data.type || '').toUpperCase(),
      quantity: Number(data.quantity),
      reason: String(data.reason || '').trim()
    }
  }).then(normalizeProduct)
}

/**
 * 删除耗材
 * @param {string|number} id
 * @returns {Promise<void>}
 */
export function deleteConsumablesApi(id) {
  return request({
    url: `/consumables/${id}`,
    method: 'delete'
  })
}

/**
 * 上传耗材图片（主图 / 详情图）
 * @param {FormData} formData
 * @returns {Promise<{url: string}>}
 */
export function uploadConsumablesImageApi(formData) {
  return uploadFileApi(formData)
}

/**
 * 批量导出耗材（文件流下载）
 * @param {Object} [params] - 导出筛选条件，同列表搜索参数
 * @param {string} [params.keyword]
 * @param {string} [params.category]
 * @returns {Promise<Blob>}
 */
export async function exportConsumablesApi(params) {
  const rows = await getAllProducts(params)
  return createCsvBlob([
    { label: '产品编码', value: row => row.productCode },
    { label: '名称', value: row => row.name },
    { label: '分类', value: row => row.category.join('/') },
    { label: '规格', value: row => row.spec },
    { label: '单位', value: row => row.unit },
    { label: '展示库存', value: row => row.stock },
    { label: '状态', value: row => row.enabled ? '已上架' : '已下架' }
  ], rows)
}
