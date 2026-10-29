export const PREPARATION_STATUSES = Object.freeze([
  { label: '全部', value: '' },
  { label: '待备货', value: 'PENDING' },
  { label: '备货中', value: 'PREPARING' },
  { label: '已备货', value: 'DONE' },
  { label: '已作废', value: 'VOIDED' }
])

export function hasAdminRole(user = {}) {
  return String(user.role || '').toLowerCase() === 'admin'
}

export function canPrepare(record = {}) {
  return String(record.source || 'W').toUpperCase() === 'W'
    && ['PENDING', 'PREPARING'].includes(String(record.statusCode || '').toUpperCase())
}

export function normalizePreparationDetail(value = {}) {
  return {
    ...value,
    source: String(value.source || 'W').toUpperCase(),
    materials: (value.materials || []).map(item => ({
      ...item,
      checked: Boolean(item.checked),
      count: Number(item.count || item.quantity || 0),
      preparedQuantity: Number(item.preparedQuantity || 0)
    }))
  }
}

export function preparationPayload(materials = []) {
  return materials.map(item => ({ id: Number(item.id), checked: Boolean(item.checked) }))
}

export function allPrepared(materials = []) {
  return materials.length > 0 && materials.every(item => Boolean(item.checked))
}
