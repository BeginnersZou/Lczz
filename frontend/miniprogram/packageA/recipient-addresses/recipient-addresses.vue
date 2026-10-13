<template>
	<view class="address-page">
		<view class="heading"><text class="title">{{ choosing ? '选择收货信息' : '我的常用地址' }}</text><text class="subtitle">可保存多个地址，下单时选择或临时填写</text></view>
		<view v-if="editing" class="form-card">
			<text class="card-title">{{ editingId ? '编辑常用地址' : '填写收货信息' }}</text>
			<view class="field"><text>收货人</text><input v-model="form.recipientName" maxlength="64" :disabled="busy" placeholder="请输入收货人姓名" /></view>
			<view class="field"><text>联系电话</text><input v-model="form.recipientPhone" type="text" maxlength="32" :disabled="busy" placeholder="手机号或固定电话" /></view>
			<view class="field"><text>收货地址</text><textarea v-model="form.recipientAddress" maxlength="500" :disabled="busy" placeholder="请填写省市区、街道及门牌号" auto-height /></view>
			<view v-if="choosing && !editingId" class="switch-row"><text>保存为常用地址</text><switch :checked="saveToBook" :disabled="busy" color="#0b63ce" @change="saveToBook = $event.detail.value" /></view>
			<view v-if="saveToBook || editingId || !choosing" class="switch-row"><text>设为默认地址</text><switch :checked="makeDefault" :disabled="busy" color="#0b63ce" @change="makeDefault = $event.detail.value" /></view>
			<text v-if="formError" class="error-text">{{ formError }}</text>
			<view class="form-actions"><button :disabled="busy" class="secondary" @click="editing = false">取消</button><button :disabled="busy" class="primary" @click="saveForm">{{ busy ? '保存中…' : choosing ? '确认使用' : '保存地址' }}</button></view>
		</view>
		<view v-else>
			<view class="toolbar"><button :disabled="busy" class="primary" @click="startNew">{{ choosing ? '填写新地址' : '新增常用地址' }}</button></view>
			<view v-if="loading" class="state"><up-loading-icon color="#0b63ce" /><text>正在加载常用地址</text></view>
			<view v-else-if="listError" class="state"><text>{{ listError }}</text><button class="secondary" @click="loadAddresses">重新加载</button></view>
			<view v-else-if="!addresses.length" class="state"><up-icon name="map" size="42" color="#94a3b8" /><text>暂无常用地址</text><text class="subtitle">点击上方按钮，填写收货信息</text></view>
			<view v-for="item in addresses" :key="item.id" class="address-card">
				<view class="address-body" @click="choose(item)"><view class="person"><text>{{ item.recipientName }}</text><text>{{ item.recipientPhone }}</text><text v-if="item.isDefault" class="default-badge">默认</text></view><text class="location">{{ item.recipientAddress }}</text></view>
				<view class="address-actions"><button v-if="choosing" :disabled="busy" @click="choose(item)">使用此地址</button><button v-if="!item.isDefault" :disabled="busy" @click="setDefault(item)">设为默认</button><button :disabled="busy" @click="startEdit(item)">编辑</button><button :disabled="busy" class="danger" @click="remove(item)">删除</button></view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, getCurrentInstance } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { recipientAddressApi } from '@/api/api.js'
import { getAuthToken } from '@/utils/auth-session.js'
import { normalizeRecipient, recipientError } from '@/utils/recipient.js'

const instance = getCurrentInstance()
const choosing = ref(false), editing = ref(false), editingId = ref(null)
const form = ref(normalizeRecipient()), saveToBook = ref(true), makeDefault = ref(false)
const addresses = ref([]), loading = ref(false), busy = ref(false), listError = ref(''), formError = ref('')
const session = getAuthToken()
let channel, alive = true, listRevision = 0
const current = () => alive && session && session === getAuthToken()
const toast = title => uni.showToast({ title, icon: 'none' })

onLoad(options => {
	choosing.value = ['select', 'manual'].includes(options?.mode)
	channel = instance?.proxy?.getOpenerEventChannel?.()
	channel?.on('initialRecipient', info => { if (current()) form.value = normalizeRecipient(info) })
	if (options?.mode === 'manual') { editing.value = true; saveToBook.value = false }
})
onShow(() => {
	if (!current()) { addresses.value = []; form.value = normalizeRecipient(); listError.value = '登录状态已变化，请返回后重新打开'; return }
	loadAddresses()
})
onUnload(() => { alive = false; listRevision++ })

const loadAddresses = async () => {
	if (!current()) return
	const revision = ++listRevision
	loading.value = true; listError.value = ''
	try {
		const res = await recipientAddressApi.list({ loading: false, silent: true })
		if (!current() || revision !== listRevision) return
		if (res.code !== 200 || !Array.isArray(res.data)) { listError.value = res.msg || '地址加载失败'; return }
		addresses.value = res.data
	} catch { if (current() && revision === listRevision) listError.value = '网络异常，请稍后重试' }
	finally { if (current() && revision === listRevision) loading.value = false }
}

const startNew = () => {
	if (busy.value || !current()) return
	editingId.value = null; form.value = normalizeRecipient(); makeDefault.value = false
	saveToBook.value = !choosing.value; formError.value = ''; editing.value = true
}
const startEdit = item => {
	if (busy.value || !current()) return
	editingId.value = item.id; form.value = normalizeRecipient(item); makeDefault.value = Boolean(item.isDefault)
	saveToBook.value = true; formError.value = ''; editing.value = true
}
const choose = item => {
	if (!choosing.value || busy.value || !current()) return
	if (!channel) return toast('请从购物车进入选择地址')
	channel.emit('recipientSelected', normalizeRecipient(item))
	uni.navigateBack()
}

const saveForm = async () => {
	if (busy.value || !current()) return
	formError.value = recipientError(form.value)
	if (formError.value) return
	const info = normalizeRecipient(form.value)
	if (choosing.value && !saveToBook.value && !editingId.value) return choose(info)
	busy.value = true
	try {
		const data = { ...info, isDefault: makeDefault.value }
		const res = editingId.value ? await recipientAddressApi.update(editingId.value, data, { silent: true })
			: await recipientAddressApi.create(data, { silent: true })
		if (!current()) return
		if (res.code !== 200 || !res.data?.id) { formError.value = res.msg || '保存地址失败'; return }
		editing.value = false
		if (choosing.value) { busy.value = false; choose(res.data) }
		else { toast('地址已保存'); await loadAddresses() }
	} catch { if (current()) formError.value = '保存失败，请检查网络后重试' }
	finally { if (current()) busy.value = false }
}

const setDefault = async item => {
	if (busy.value || !current()) return
	busy.value = true
	try {
		const res = await recipientAddressApi.setDefault(item.id, { silent: true })
		if (!current()) return
		if (res.code !== 200) return toast(res.msg || '设置默认地址失败')
		await loadAddresses()
	} catch { if (current()) toast('设置失败，请稍后重试') }
	finally { if (current()) busy.value = false }
}
const remove = item => {
	if (busy.value || !current()) return
	uni.showModal({ title: '删除常用地址', content: `确认删除“${item.recipientName}”的常用地址吗？已下单订单的收货信息会保留。`, success: async result => {
		if (!result.confirm || busy.value || !current()) return
		busy.value = true
		try {
			const res = await recipientAddressApi.remove(item.id, { silent: true })
			if (!current()) return
			if (res.code !== 200) return toast(res.msg || '删除地址失败')
			await loadAddresses()
		} catch { if (current()) toast('删除失败，请稍后重试') }
		finally { if (current()) busy.value = false }
	} })
}
</script>

<style scoped lang="scss">
.address-page { min-height: 100vh; padding: 28rpx 24rpx calc(32rpx + env(safe-area-inset-bottom)); box-sizing: border-box; background: #f4f7fb; }
.heading { margin-bottom: 24rpx; }.title { display: block; font-size: 36rpx; color: #142434; font-weight: 750; }
.subtitle { display: block; margin-top: 10rpx; font-size: 24rpx; color: #8291a3; }
.form-card,.address-card { padding: 28rpx; margin-bottom: 24rpx; border-radius: 22rpx; background: #fff; }
.card-title { display: block; margin-bottom: 18rpx; font-size: 30rpx; color: #142434; font-weight: 700; }
.field { padding: 20rpx 0; border-bottom: 1rpx solid #edf1f5; }.field text { display: block; margin-bottom: 14rpx; font-size: 25rpx; color: #475569; }
.field input,.field textarea { width: 100%; font-size: 28rpx; line-height: 1.7; color: #142434; }.field textarea { min-height: 140rpx; }
.switch-row { display: flex; align-items: center; justify-content: space-between; min-height: 96rpx; font-size: 26rpx; color: #475569; }
.toolbar { margin-bottom: 24rpx; }.primary,.secondary { min-height: 86rpx; font-size: 27rpx; border-radius: 18rpx; }.primary { background: #0b63ce; color: #fff; }.secondary { background: #fff; color: #0b63ce; }
.form-actions { display: flex; gap: 20rpx; margin-top: 24rpx; }.form-actions button { flex: 1; }
.error-text { display: block; margin-top: 14rpx; font-size: 24rpx; color: #dc2626; }
.state { display: flex; min-height: 340rpx; flex-direction: column; align-items: center; justify-content: center; gap: 20rpx; color: #64748b; font-size: 27rpx; }
.person { display: flex; flex-wrap: wrap; align-items: center; gap: 20rpx; font-size: 28rpx; font-weight: 650; color: #142434; }
.default-badge { padding: 4rpx 12rpx; background: #eaf3ff; border-radius: 10rpx; color: #0b63ce; font-size: 22rpx; }
.location { display: block; margin-top: 18rpx; font-size: 26rpx; line-height: 1.7; overflow-wrap: anywhere; color: #64748b; }
.address-actions { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 20rpx; padding-top: 16rpx; border-top: 1rpx solid #edf1f5; }
.address-actions button { margin: 0; padding: 0 14rpx; background: #fff; color: #0b63ce; font-size: 24rpx; line-height: 2.5; }.address-actions button:after { border: 0; }.address-actions .danger { color: #dc2626; }
</style>
