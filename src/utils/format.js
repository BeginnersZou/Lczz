function toValidDate(value) {
  if (value == null || value === '') return null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value

  if (typeof value === 'number') {
    const timestamp = value < 1e12 ? value * 1000 : value
    const date = new Date(timestamp)
    return Number.isNaN(date.getTime()) ? null : date
  }

  const text = String(value).trim()
  if (!text) return null
  const normalized = /^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}/.test(text)
    ? text.replace(' ', 'T')
    : text
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

function pad(value) {
  return String(value).padStart(2, '0')
}

/** 统一展示为本地时区的 YYYY-MM-DD HH:mm，避免 ISO 字符串挤压表格。 */
export function formatDateTime(value, options = {}) {
  const date = toValidDate(value)
  if (!date) return value ? String(value) : '-'
  const result = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
  return options.seconds ? `${result}:${pad(date.getSeconds())}` : result
}

export function formatDate(value) {
  const date = toValidDate(value)
  if (!date) return value ? String(value) : '-'
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function formatPhone(value) {
  const phone = value == null ? '' : String(value).trim()
  if (!phone) return '-'
  return /^1\d{10}$/.test(phone) ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : phone
}

export function formatNumber(value, fallback = '0') {
  const number = Number(value)
  return Number.isFinite(number) ? number.toLocaleString('zh-CN') : fallback
}
