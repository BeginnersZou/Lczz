import request from '@/utils/request'

/**
 * 空调产品模块 API
 * 后端接口对接说明：
 *   - 列表：GET /airConditioner/list，分页 + 搜索
 *   - 详情：GET /airConditioner/detail/:id
 *   - 新增：POST /airConditioner
 *   - 更新：PUT /airConditioner/:id
 *   - 删除：DELETE /airConditioner/:id
 *   - 上传图片/视频：POST /airConditioner/upload
 * 统一分页返回：{ list: [], total: number }
 */

/**
 * 获取空调产品分页列表
 * @param {Object} params
 * @param {number} [params.page=1]
 * @param {number} [params.pageSize=10]
 * @param {string} [params.keyword] - 名称/品牌
 * @param {string} [params.status] - 状态：'published' | 'draft'
 * @returns {Promise<{list: Object[], total: number}>}
 */
export function getAirConditionerListApi(params) {
  return request({
    url: '/airConditioner/list',
    method: 'get',
    params
  })
}

/**
 * 获取空调产品详情
 * @param {string|number} id
 * @returns {Promise<Object>}
 */
export function getAirConditionerDetailApi(id) {
  return request({
    url: `/airConditioner/detail/${id}`,
    method: 'get'
  })
}

/**
 * 新增空调产品
 * @param {Object} data - 空调产品表单数据
 * @returns {Promise<Object>}
 */
export function addAirConditionerApi(data) {
  return request({
    url: '/airConditioner',
    method: 'post',
    data
  })
}

/**
 * 更新空调产品
 * @param {string|number} id
 * @param {Object} data
 * @returns {Promise<Object>}
 */
export function updateAirConditionerApi(id, data) {
  return request({
    url: `/airConditioner/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除空调产品
 * @param {string|number} id
 * @returns {Promise<void>}
 */
export function deleteAirConditionerApi(id) {
  return request({
    url: `/airConditioner/${id}`,
    method: 'delete'
  })
}

/**
 * 上传图片/视频
 * @param {FormData} formData - 包含 file 字段
 * @returns {Promise<{url: string}>}
 */
export function uploadAirConditionerFileApi(formData) {
  return request({
    url: '/airConditioner/upload',
    method: 'post',
    data: formData
  })
}
