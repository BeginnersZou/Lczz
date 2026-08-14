import request from '@/utils/request'

/**
 * 动态资讯模块 API
 * 后端接口对接说明：
 *   - 列表：GET /dynamic/list，分页 + 关键词搜索
 *   - 详情：GET /dynamic/detail/:id
 *   - 新增：POST /dynamic
 *   - 更新：PUT /dynamic/:id
 *   - 删除：DELETE /dynamic/:id
 * 统一分页返回：{ list: [], total: number, page: number, pageSize: number }
 */

/**
 * 获取动态资讯分页列表
 * @param {Object} params
 * @param {number} [params.page=1] - 当前页
 * @param {number} [params.pageSize=10] - 每页条数
 * @param {string} [params.keyword] - 搜索关键词（标题/内容）
 * @param {string} [params.category] - 分类
 * @returns {Promise<{list: Object[], total: number}>}
 */
export function getDynamicListApi(params, config = {}) {
  return request({
    url: '/dynamic/list',
    method: 'get',
    params,
    ...config
  })
}

/**
 * 获取动态资讯详情
 * @param {string|number} id
 * @returns {Promise<Object>}
 */
export function getDynamicDetailApi(id) {
  return request({
    url: `/dynamic/detail/${id}`,
    method: 'get'
  })
}

/**
 * 新增动态资讯
 * @param {Object} data
 * @param {string} data.title - 标题
 * @param {string} data.coverImage - 封面图 URL
 * @param {string} data.contentHtml - 富文本内容
 * @param {string} [data.category] - 分类
 * @returns {Promise<Object>}
 */
export function addDynamicApi(data) {
  return request({
    url: '/dynamic',
    method: 'post',
    data
  })
}

/**
 * 更新动态资讯
 * @param {string|number} id
 * @param {Object} data - 同新增
 * @returns {Promise<Object>}
 */
export function updateDynamicApi(id, data) {
  return request({
    url: `/dynamic/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除动态资讯
 * @param {string|number} id
 * @returns {Promise<void>}
 */
export function deleteDynamicApi(id) {
  return request({
    url: `/dynamic/${id}`,
    method: 'delete'
  })
}

/**
 * 发布/上架动态资讯
 * @param {string|number} id
 * @returns {Promise<void>}
 */
export function publishDynamicApi(id) {
  return request({
    url: `/dynamic/${id}/publish`,
    method: 'post'
  })
}

/**
 * 上传图片（富文本内图片 / 封面图）
 * @param {FormData} formData - 包含 file 字段
 * @returns {Promise<{url: string}>} 图片访问 URL
 */
export function uploadDynamicImageApi(formData) {
  return request({
    url: '/dynamic/upload',
    method: 'post',
    data: formData
  })
}
