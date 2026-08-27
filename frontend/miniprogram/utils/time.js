const pad = value => String(value).padStart(2, '0')

export function formatDateTime(value) {
	if (value === null || value === undefined || value === '') return ''
	const numeric = Number(value)
	const source = Number.isFinite(numeric) && /^\d+$/.test(String(value))
		? new Date(String(value).length === 10 ? numeric * 1000 : numeric)
		: new Date(value)
	if (Number.isNaN(source.getTime())) {
		return String(value).replace('T', ' ').replace(/\.\d{1,9}(?=(?:Z|[+-]\d{2}:?\d{2})?$)/, '').replace(/Z$/, '')
	}
	return `${source.getFullYear()}-${pad(source.getMonth() + 1)}-${pad(source.getDate())} ${pad(source.getHours())}:${pad(source.getMinutes())}`
}

// 兼容旧页面的十位秒级时间戳方法。
export function timeTransFrom(time) {
	return formatDateTime(Number(time) * 1000)
}
