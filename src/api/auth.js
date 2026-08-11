import request from '@/utils/request'

/**
 * 认证模块 API
 * 后端接口对接说明：
 *   - 登录：POST /auth/login，入参 { username, password }，返回 { token, userInfo }
 *   - 获取当前登录用户信息：GET /auth/info，返回 userInfo
 *   - 退出登录：POST /auth/logout
 *   - 修改密码：POST /auth/password，入参 { oldPassword, newPassword }
 */

/**
 * 登录
 * silent: true —— 后端未就绪时静默失败，由 store 回退到测试模式（admin/123456）
 * @param {Object} data
 * @param {string} data.username - 用户名
 * @param {string} data.password - 密码
 * @returns {Promise<{token: string, userInfo: Object}>}
 */
export function loginApi(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data,
    silent: true
  })
}

/**
 * 获取当前登录用户信息（用于刷新页面后恢复登录态）
 * @returns {Promise<Object>} userInfo
 */
export function getUserInfoApi() {
  return request({
    url: '/auth/info',
    method: 'get'
  })
}

/**
 * 退出登录
 * @returns {Promise<void>}
 */
export function logoutApi() {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}

/**
 * 修改当前账号密码
 * @param {Object} data
 * @param {string} data.oldPassword - 原密码
 * @param {string} data.newPassword - 新密码
 * @returns {Promise<void>}
 */
export function changePasswordApi(data) {
  return request({
    url: '/auth/password',
    method: 'post',
    data
  })
}
