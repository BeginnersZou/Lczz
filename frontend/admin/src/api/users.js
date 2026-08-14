import request from '@/utils/request'

/**
 * 用户管理模块 API
 * 后端接口对接说明：
 *   - 列表：GET /users/list，分页 + 搜索 + 角色/账号状态/黑名单筛选
 *   - 详情：GET /users/detail/:id
 *   - 更新：PUT /users/:id
 *   - 启用/停用：PATCH /users/:id/status
 *   - 加入/移出黑名单：POST /users/:id/blacklist
 * 统一分页返回：{ list: [], total: number }
 */

/**
 * 获取用户分页列表
 * @param {Object} params
 * @param {number} [params.page=1]
 * @param {number} [params.pageSize=10]
 * @param {string} [params.keyword] - 昵称/姓名/手机号
 * @param {string} [params.role] - 角色
 * @param {string} [params.accountStatus] - ENABLED | DISABLED
 * @param {boolean} [params.blacklist] - 是否在黑名单
 * @returns {Promise<{list: Object[], total: number}>}
 */
export function getUserListApi(params, config = {}) {
  return request({
    url: '/users/list',
    method: 'get',
    params,
    ...config
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
 * 启用或停用账号
 * @param {string|number} id
 * @param {'ENABLED'|'DISABLED'} accountStatus
 * @returns {Promise<Object>}
 */
export function changeUserStatusApi(id, accountStatus) {
  return request({
    url: `/users/${id}/status`,
    method: 'patch',
    data: { accountStatus }
  })
}

/**
 * 加入/移出黑名单
 * @param {string|number} id
 * @param {Object} data
 * @param {boolean} data.blacklist - true 加入，false 移出
 * @param {string} data.reason - 操作原因，2-500 字符
 * @returns {Promise<Object>}
 */
export function toggleBlacklistApi(id, data) {
  return request({
    url: `/users/${id}/blacklist`,
    method: 'post',
    data
  })
}
