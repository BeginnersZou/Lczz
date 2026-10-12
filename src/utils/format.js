const beijingOffsetMs = 8 * 60 * 60 * 1000

// Zoned API times are instants; legacy unzoned strings are Beijing wall times.
export function parseDateTime(value) {
  if (value == null || value === '') return null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value

  const text = String(value).trim()
  if (!text) return null
  let date
  if (typeof value === 'number' || /^-?\d+$/.test(text)) {
    const numeric = Number(value)
    // Ten-digit Unix seconds and millisecond timestamps (including legacy 12-digit values).
    date = new Date(/^-?\d{10}$/.test(text) ? numeric * 1000 : numeric)
  } else {
    let normalized = text.replace(' ', 'T').replace(/(\.\d{3})\d+/, '$1')
    if (/^\d{4}-\d{2}-\d{2}$/.test(normalized)) normalized += 'T00:00:00'
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(normalized)) {
      normalized += '+08:00'
    }
    normalized = normalized.replace(/([+-]\d{2})(\d{2})$/, '$1:$2')
    date = new Date(normalized)
  }
  return Number.isNaN(date.getTime()) ? null : date
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function beijingDate(value) {
  const date = parseDateTime(value)
  return date ? new Date(date.getTime() + beijingOffsetMs) : null
}

/** Always display Beijing time, regardless of the browser's timezone. */
export function formatDateTime(value, options = {}) {
  const date = beijingDate(value)
  if (!date) return value ? String(value) : '-'
  const result = `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`
  return options.seconds ? `${result}:${pad(date.getUTCSeconds())}` : result
}

export function formatDate(value) {
  const date = beijingDate(value)
  if (!date) return value ? String(value) : '-'
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}

/** Date picker model is a Beijing wall time, not a zoned API string. */
export function formatDateTimeInput(value) {
  return parseDateTime(value) ? formatDateTime(value, { seconds: true }).replace(' ', 'T') : ''
}

export function formatPhone(value) {
  const phone = value == null ? '' : String(value).trim()
  return phone || '-'
}

export function formatNumber(value, fallback = '0') {
  const number = Number(value)
  return Number.isFinite(number) ? number.toLocaleString('zh-CN') : fallback
}
