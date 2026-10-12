const pad = value => String(value).padStart(2, '0')
const beijingOffsetMs = 8 * 60 * 60 * 1000

// Zoned API times are instants; legacy unzoned strings are Beijing wall times.
// Do not append Z to every value: database and appointment times are already local.
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

export function formatDateTime(value, options = {}) {
	const source = parseDateTime(value)
	if (!source) return value == null ? '' : String(value)
	// UTC getters on an offset copy keep display independent of the phone's timezone.
	const date = new Date(source.getTime() + beijingOffsetMs)
	const result = `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`
	return options.seconds ? `${result}:${pad(date.getUTCSeconds())}` : result
}

// 兼容旧页面的十位秒级时间戳方法。
export function timeTransFrom(time) {
	return formatDateTime(new Date(Number(time) * 1000))
}
