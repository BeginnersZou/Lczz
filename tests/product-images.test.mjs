import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const helperSource = readFileSync(new URL('../utils/product-images.js', import.meta.url), 'utf8')
const { normalizeProductImage, retryableImageUrl } = await import(
	`data:text/javascript;base64,${Buffer.from(helperSource).toString('base64')}`)

test('product image variants keep card, display and original roles separate', () => {
	const image = normalizeProductImage({
		id: 9,
		cardUrl: '/card/hash',
		displayUrl: '/display/hash',
		originalUrl: '/original/hash'
	}, value => `https://api.example.test${value}`)
	assert.equal(image.cardUrl, 'https://api.example.test/card/hash')
	assert.equal(image.displayUrl, 'https://api.example.test/display/hash')
	assert.equal(image.originalUrl, 'https://api.example.test/original/hash')
	assert.equal(image.url, image.displayUrl)
})

test('legacy string images remain compatible and retry only changes the failed request', () => {
	const image = normalizeProductImage('/legacy.jpg')
	assert.equal(image.cardUrl, '/legacy.jpg')
	assert.equal(image.displayUrl, '/legacy.jpg')
	assert.equal(image.originalUrl, '/legacy.jpg')
	assert.equal(retryableImageUrl('/card/hash', 12), '/card/hash?imageRetry=12')
	assert.equal(retryableImageUrl('/card/hash?x=1', 12), '/card/hash?x=1&imageRetry=12')
})

test('detail page fetches original only from the active preview action', () => {
	const source = readFileSync(new URL('../packageA/goos-details/goos-details.vue', import.meta.url), 'utf8')
	assert.match(source, /const originalUrl = image\?\.originalUrl \|\| image\?\.displayUrl/)
	assert.match(source, /uni\.getImageInfo\(\{[\s\S]*src: originalUrl/)
	assert.doesNotMatch(source, /:src="image\.originalUrl"/)
})

test('home products start before categories and non-leading images use viewport loading', () => {
	const source = readFileSync(new URL('../pages/index/index.vue', import.meta.url), 'utf8')
	const mounted = source.match(/onMounted\(\(\) => \{([\s\S]*?)\n\}\)/)?.[1] || ''
	assert.ok(mounted.indexOf('fetchList(true)') >= 0)
	assert.ok(mounted.indexOf('fetchList(true)') < mounted.indexOf('fetchCategories()'))
	assert.match(source, /:eager="index < 4"/)
	const component = readFileSync(new URL('../components/LazyProductImage.vue', import.meta.url), 'utf8')
	assert.match(component, /createIntersectionObserver/)
	assert.match(component, /relativeToViewport\(\{ top: 200, bottom: 200 \}\)/)
})
