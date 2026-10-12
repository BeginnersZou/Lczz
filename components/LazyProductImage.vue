<template>
	<view class="lazy-image-host" :class="{ 'width-fix-host': mode === 'widthFix' }" @click="emit('click')">
		<view v-if="!loaded" class="image-placeholder">
			<up-loading-icon v-if="loadEnabled && !failed" mode="circle" color="#8ca4bd" size="22"></up-loading-icon>
			<up-icon v-else-if="!failed" name="photo" size="24" color="#9aacbf"></up-icon>
		</view>
		<image v-if="requestUrl && !failed" class="lazy-image" :class="{ visible: loaded }" :src="requestUrl"
			:mode="mode" lazy-load :show-menu-by-longpress="false" @load="handleLoad" @error="handleError"></image>
		<view v-if="failed" class="image-retry" @click.stop="retry">
			<up-icon name="reload" size="18" color="#52677d"></up-icon><text>加载失败，点击重试</text>
		</view>
	</view>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { retryableImageUrl } from '@/utils/product-images.js'

const props = defineProps({
	src: { type: String, default: '' },
	mode: { type: String, default: 'aspectFill' },
	eager: { type: Boolean, default: false },
	viewport: { type: Boolean, default: true }
})
const emit = defineEmits(['click', 'load', 'error'])
const instance = getCurrentInstance()
const loadEnabled = ref(props.eager)
const loaded = ref(false)
const failed = ref(false)
const retryNonce = ref(0)
let observer

const requestUrl = computed(() => loadEnabled.value && props.src && !failed.value
	? retryableImageUrl(props.src, retryNonce.value) : '')

const disconnect = () => {
	if (observer) observer.disconnect()
	observer = null
}

const enable = () => {
	loadEnabled.value = true
	disconnect()
}

const observe = async () => {
	if (!props.viewport) return
	if (loadEnabled.value || !props.src || typeof uni.createIntersectionObserver !== 'function') {
		enable()
		return
	}
	await nextTick()
	observer = uni.createIntersectionObserver(instance?.proxy, { thresholds: [0, 0.01] })
	observer.relativeToViewport({ top: 200, bottom: 200 }).observe('.lazy-image-host', result => {
		if (result.intersectionRatio > 0) enable()
	})
}

const handleLoad = event => {
	loaded.value = true
	failed.value = false
	emit('load', event)
}

const handleError = event => {
	loaded.value = false
	failed.value = true
	emit('error', event)
}

const retry = () => {
	failed.value = false
	loaded.value = false
	loadEnabled.value = true
	retryNonce.value = Date.now()
}

watch(() => props.src, () => {
	loaded.value = false
	failed.value = false
	retryNonce.value = 0
	loadEnabled.value = props.eager
	disconnect()
	observe()
})
watch(() => props.eager, eager => { if (eager) enable() })
watch(() => props.viewport, viewport => { if (viewport && !loadEnabled.value) observe() })
onMounted(observe)
onBeforeUnmount(disconnect)
</script>

<style scoped>
.lazy-image-host, .lazy-image, .image-placeholder, .image-retry { width: 100%; height: 100%; }
.lazy-image-host { position: relative; overflow: hidden; background: #edf2f7; }
.lazy-image { position: absolute; inset: 0; opacity: 0; transition: opacity .18s ease; }
.lazy-image.visible { opacity: 1; }
.width-fix-host { height: auto; min-height: 360rpx; }
.width-fix-host .lazy-image { position: relative; inset: auto; height: auto; min-height: 360rpx; display: block; }
.width-fix-host .image-placeholder, .width-fix-host .image-retry { position: absolute; inset: 0; height: 360rpx; }
.image-placeholder, .image-retry { display: flex; align-items: center; justify-content: center; box-sizing: border-box; }
.image-placeholder { background: linear-gradient(110deg, #edf2f7 25%, #f7f9fb 42%, #edf2f7 60%); }
.image-retry { flex-direction: column; gap: 8rpx; padding: 16rpx; color: #52677d; font-size: 20rpx; text-align: center; }
</style>
