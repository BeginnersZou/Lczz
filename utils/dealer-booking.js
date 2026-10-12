export const BOOKING_TASKS = ['空调安装', '空调维修', '空调清洗', '空调移机']
export const BOOKING_DESCRIPTION = '例如品牌、主机信息、内机信息、主内机控制器、水箱等信息'

export function isDealer(user = {}) {
  return [user.role, ...(Array.isArray(user.roles) ? user.roles : [])]
    .some(role => String(role || '').toLowerCase() === 'dealer')
}

// Explicit whitelist: never forward role, dealer ID, installer or admin fields.
export function bookingPayload(form, files, requestId) {
  return {
    requestId,
    taskType: form.taskType,
    description: String(form.description || '').trim(),
    customerName: String(form.customerName || '').trim(),
    customerPhone: String(form.customerPhone || '').trim().replace(/^\+86/, ''),
    addressArea: [...form.addressArea],
    addressDetail: String(form.addressDetail || '').trim(),
    fileIds: files.map(file => Number(file.id))
  }
}

export function validateBooking(form, files) {
  if (!BOOKING_TASKS.includes(form.taskType)) return '请选择任务类型'
  if (!String(form.description || '').trim()) return '请填写安装需求描述'
  if (form.description.trim().length > 1000) return '描述不能超过1000字'
  if (!String(form.customerName || '').trim()) return '请填写客户姓名'
  if (form.customerName.trim().length > 64) return '客户姓名不能超过64字'
  if (!/^(?:\+86)?1[3-9]\d{9}$/.test(String(form.customerPhone || '').trim())) return '请填写正确的客户手机号'
  if (!Array.isArray(form.addressArea) || form.addressArea.length !== 3 || form.addressArea.some(value => !value)) return '请选择省、市、区'
  if (!String(form.addressDetail || '').trim()) return '请填写详细地址'
  if (form.addressDetail.trim().length > 500) return '详细地址不能超过500字'
  if (files.length > 9) return '附件最多9张'
  if (files.some(file => file.status !== 'done' || !Number.isSafeInteger(Number(file.id)) || Number(file.id) < 1)) return '请等待附件上传完成，失败图片请重试或移除'
  return ''
}
