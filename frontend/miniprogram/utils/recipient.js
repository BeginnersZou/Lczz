export function normalizeRecipient(value = {}) {
  return {
    recipientName: String(value.recipientName || '').trim(),
    recipientPhone: String(value.recipientPhone || '').replace(/\s+/g, ''),
    recipientAddress: String(value.recipientAddress || '').trim()
  }
}

export function recipientError(value) {
  const info = normalizeRecipient(value)
  if (!info.recipientName || info.recipientName.length > 64) return '请填写收货人，最多64个字符'
  if (!/^(?:1[3-9]\d{9}|0\d{2,3}-?\d{7,8})$/.test(info.recipientPhone)) return '请填写有效的手机号或固定电话'
  if (!info.recipientAddress || info.recipientAddress.length > 500) return '请填写完整收货地址，最多500个字符'
  return ''
}
