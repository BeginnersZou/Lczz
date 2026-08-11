import request from '@/utils/request'

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
  })
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
  })
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
    data
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
    data
  })
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
  return request({
    url: '/consumables/upload',
    method: 'post',
    data: formData
  })
}

/**
 * 批量导出耗材（文件流下载）
 * @param {Object} [params] - 导出筛选条件，同列表搜索参数
 * @param {string} [params.keyword]
 * @param {string} [params.category]
 * @returns {Promise<Blob>}
 */
export function exportConsumablesApi(params) {
  return request({
    url: '/consumables/export',
    method: 'get',
    params,
    responseType: 'blob'
  })
}
