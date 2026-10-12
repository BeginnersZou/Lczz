export const normalizeProductImage = (value, resolveUrl = url => url || '') => {
	const source = value && typeof value === 'object' ? value : { url: value }
	const fallback = source.url || source.previewUrl || ''
	const cardUrl = resolveUrl(source.cardUrl || source.thumbnail || fallback)
	const displayUrl = resolveUrl(source.displayUrl || source.display || fallback || cardUrl)
	const originalUrl = resolveUrl(source.originalUrl || source.original || fallback || displayUrl)
	return {
		...(value && typeof value === 'object' ? value : {}),
		id: Number(source.id || 0) || undefined,
		url: displayUrl,
		cardUrl,
		displayUrl,
		originalUrl
	}
}

export const retryableImageUrl = (url, nonce) => {
	if (!url || !nonce) return url || ''
	return `${url}${url.includes('?') ? '&' : '?'}imageRetry=${nonce}`
}
