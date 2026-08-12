import request from '@/utils/request'

/**
 * 用户审核模块 API
 * 后端接口对接说明：
 *   - 审核列表：GET /audit/list，分页 + 搜索 + 状态筛选
 *   - 审核详情：GET /audit/detail/:id
 *   - 审核通过：POST /audit/:id/approve
 *   - 审核驳回：POST /audit/:id/reject
 * 统一分页返回：{ list: [], total: number }
 */

/**
 * 获取用户审核分页列表
 * @param {Object} params
 * @param {number} [params.page=1]
 * @param {number} [params.pageSize=10]
 * @param {string} [params.keyword] - 昵称/真实姓名/手机号/企业名称
 * @param {string} [params.status] - 审核状态：'pending' | 'approved' | 'rejected'
 * @param {string} [params.auditType] - 认证类型：'personal' | 'enterprise'
 * @returns {Promise<{list: Object[], total: number}>}
 */
export function getAuditListApi(params, config = {}) {
  return request({
    url: '/audit/list',
    method: 'get',
    params,
    ...config
  })
}

/**
 * 获取审核详情（含身份信息 + 相关材料）
 * @param {string|number} id
 * @returns {Promise<Object>}
 */
export function getAuditDetailApi(id) {
  return request({
    url: `/audit/detail/${id}`,
    method: 'get'
  })
}

/**
 * 审核通过
 * @param {string|number} id
 * @returns {Promise<void>}
 */
export function approveAuditApi(id) {
  return request({
    url: `/audit/${id}/approve`,
    method: 'post'
  })
}

/**
 * 审核驳回
 * @param {string|number} id
 * @param {Object} data
 * @param {string} data.reason - 驳回原因
 * @returns {Promise<void>}
 */
export function rejectAuditApi(id, data) {
  return request({
    url: `/audit/${id}/reject`,
    method: 'post',
    data
  })
}
