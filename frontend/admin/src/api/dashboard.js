import request from '@/utils/request'

/**
 * 首页仪表盘 API
 * 后端接口对接说明：
 *   - 统计概览：GET /dashboard/overview，返回各项核心指标
 *   - 订单趋势：GET /dashboard/order-trend，返回近7天/30天趋势
 *   - 订单状态分布：GET /dashboard/order-status，返回饼图数据
 *   - 待办事项：GET /dashboard/todo，返回待办列表
 */

/**
 * 获取统计概览（订单数、用户数、营收等核心指标）
 * @returns {Promise<Object>} 概览数据
 */
export function getOverviewApi(params, config = {}) {
  return request({
    url: '/dashboard/overview',
    method: 'get',
    params,
    ...config
  })
}

/**
 * 获取订单趋势（折线图）
 * @param {Object} params
 * @param {string} [params.range] - 时间范围：'7d' | '30d'
 * @returns {Promise<{xAxis: string[], series: Object[]}>}
 */
export function getOrderTrendApi(params, config = {}) {
  return request({
    url: '/dashboard/order-trend',
    method: 'get',
    params,
    ...config
  })
}

/**
 * 获取订单状态分布（饼图）
 * @returns {Promise<{name: string, value: number}[]>}
 */
export function getOrderStatusApi(config = {}) {
  return request({
    url: '/dashboard/order-status',
    method: 'get',
    ...config
  })
}

/**
 * 获取待办事项列表
 * @returns {Promise<Object[]>}
 */
export function getTodoListApi() {
  return request({
    url: '/dashboard/todo',
    method: 'get'
  })
}
