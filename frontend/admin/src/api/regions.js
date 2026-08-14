import request from '@/utils/request'

function normalizeNode(item = {}) {
  const name = item.name || item.label || item.regionName || ''
  const children = Array.isArray(item.children) ? item.children.map(normalizeNode).filter(Boolean) : undefined
  if (!name) return null
  return {
    value: name,
    label: name,
    code: item.code || item.value || item.regionCode || '',
    ...(children?.length ? { children } : {})
  }
}

/**
 * 获取省/市/区三级行政区树。
 *
 * 后端契约：GET /api/v1/regions/tree
 * 返回 Array<{ code: string, name: string, children?: Region[] }>。
 * 当前订单接口的 addressArea 仍接收三级名称数组，因此级联选择器 value 使用 name，
 * code 作为扩展字段保留，待订单契约升级后可无损切换为行政区划代码。
 */
export function getRegionTreeApi() {
  return request({ url: '/regions/tree', method: 'get', silent: true }).then(result => {
    const rows = Array.isArray(result) ? result : (result?.list || result?.tree || [])
    return rows.map(normalizeNode).filter(Boolean)
  })
}
