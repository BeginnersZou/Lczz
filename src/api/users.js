import request from '@/utils/request'

/**
 * 用户管理模块 API
 * 后端接口对接说明：
 *   - 列表：GET /users/list，分页 + 搜索 + 角色筛选
 *   - 详情：GET /users/detail/:id
 *   - 更新：PUT /users/:id
 *   - 加入/移出黑名单：POST /users/:id/blacklist
 *   - 删除：DELETE /users/:id
 * 统一分页返回：{ list: [], total: number }
 */

/**
 * 获取用户分页列表
 * @param {Object} params
 * @param {number} [params.page=1]
 * @param {number} [params.pageSize=10]
 * @param {string} [params.keyword] - 昵称/姓名/手机号
 * @param {string} [params.role] - 角色
 * @param {number} [params.blacklist] - 黑名单状态：0 全部 1 正常 2 黑名单
 * @returns {Promise<{list: Object[], total: number}>}
 */
export function getUserListApi(params) {
  return request({
    url: '/users/list',
    method: 'get',
    params
  })
}

/**
 * 获取用户详情
 * @param {string|number} id
 * @returns {Promise<Object>}
 */
export function getUserDetailApi(id) {
  return request({
    url: `/users/detail/${id}`,
    method: 'get'
  })
}

/**
 * 更新用户信息
 * @param {string|number} id
 * @param {Object} data
 * @returns {Promise<Object>}
 */
export function updateUserApi(id, data) {
  return request({
    url: `/users/${id}`,
    method: 'put',
    data
  })
}

/**
 * 加入/移出黑名单
 * @param {string|number} id
 * @param {Object} data
 * @param {boolean} data.blacklist - true 加入，false 移出
 * @returns {Promise<void>}
 */
export function toggleBlacklistApi(id, data) {
  return request({
    url: `/users/${id}/blacklist`,
    method: 'post',
    data
  })
}

/**
 * 删除用户
 * @param {string|number} id
 * @returns {Promise<void>}
 */
export function deleteUserApi(id) {
  return request({
    url: `/users/${id}`,
    method: 'delete'
  })
}
