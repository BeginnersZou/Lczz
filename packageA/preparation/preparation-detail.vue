<template>
	<view class="page">
		<view v-if="loading" class="state"><up-loading-icon color="#0b63ce"></up-loading-icon><text>正在加载备货详情</text></view>
		<view v-else-if="error" class="state"><text class="state-title">{{ error }}</text><button class="outline-button" @click="loadDetail">重新加载</button></view>
		<template v-else>
			<view class="summary-card">
				<view class="summary-head"><view class="summary-title"><text>{{ detail.productName || '工程订单耗材' }}</text><text>{{ detail.orderNo }}</text></view><text class="status" :class="statusClass">{{ detail.statusLabel }}</text></view>
				<view class="summary-row"><text>安装师傅</text><text>{{ detail.submitterName || '-' }}</text></view>
				<view v-if="detail.submitterPhone" class="summary-row"><text>师傅手机号</text><text class="phone" @click="callInstaller">{{ detail.submitterPhone }}</text></view>
				<view class="summary-row"><text>客户</text><text>{{ detail.customerName || '-' }}</text></view>
				<view class="summary-row"><text>提交时间</text><text>{{ formatDateTime(detail.createTime) }}</text></view>
				<view v-if="detail.remark" class="remark"><text>申请备注</text><text>{{ detail.remark }}</text></view>
			</view>

			<view class="materials-card">
				<view class="section-head"><view><text class="section-title">耗材清单</text><text class="section-desc">共 {{ detail.materials.length }} 项</text></view><text v-if="editable" class="progress">已备 {{ preparedCount }}/{{ detail.materials.length }}</text></view>
				<checkbox-group @change="changePrepared">
					<label v-for="item in detail.materials" :key="item.id" class="material-item" :class="{ checked: item.checked }">
						<checkbox :value="String(item.id)" :checked="item.checked" :disabled="!editable || saving" color="#0b63ce" />
						<view class="material-copy"><text class="material-name">{{ item.name }}</text><text class="material-spec">{{ item.spec || '通用规格' }} · {{ item.skuCode || '默认规格' }}</text><text class="material-stock">{{ stockText(item) }}</text></view>
						<view class="quantity"><text>申请数量</text><text>{{ formatQuantity(item.count) }} {{ item.unit }}</text></view>
					</label>
				</checkbox-group>
				<view v-if="!detail.materials.length" class="empty-materials">暂无耗材明细</view>
			</view>

			<view v-if="editable" class="action-space"></view>
			<view v-if="editable" class="action-bar">
				<view class="action-summary"><text>{{ allChecked ? '全部耗材已核对' : `已备 ${preparedCount} 项` }}</text><text>{{ allChecked ? '提交后完成本单备货' : '可随时保存当前进度' }}</text></view>
				<button class="primary-button" :disabled="saving" @click="save">{{ saving ? '提交中…' : (allChecked ? '保存并完成备货' : '保存备货进度') }}</button>
			</view>
			<view v-else class="readonly-tip"><up-icon name="info-circle" size="18" color="#64748b"></up-icon><text>{{ detail.statusCode === 'DONE' ? '该订单已完成备货，仅供查看。' : '该耗材申请已作废，仅供查看。' }}</text></view>
		</template>
	</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import { preparationApi } from '@/api/api.js'
import { getAuthUserInfo } from '@/utils/auth-session.js'
import { formatDateTime } from '@/utils/time.js'
import { allPrepared, canPrepare, hasAdminRole, normalizePreparationDetail, preparationPayload } from '@/utils/admin-preparation.js'

const id = ref('')
const source = ref('W')
const detail = ref({ materials: [] })
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const editable = computed(() => canPrepare(detail.value))
const allChecked = computed(() => allPrepared(detail.value.materials))
const preparedCount = computed(() => detail.value.materials.filter(item => item.checked).length)
const statusClass = computed(() => `status-${String(detail.value.statusCode || '').toLowerCase()}`)

function requireAdmin() {
	if (hasAdminRole(getAuthUserInfo())) return true
	uni.showToast({ title: '仅管理员可处理订单备货', icon: 'none' })
	setTimeout(() => uni.navigateBack(), 300)
	return false
}

async function loadDetail() {
	if (!requireAdmin() || !id.value) return
	loading.value = true
	error.value = ''
	try {
		const res = await preparationApi.getDetail(id.value, source.value, { loading: false })
		if (res.code === 200 && res.data) detail.value = normalizePreparationDetail(res.data)
		else error.value = res.msg || '备货详情加载失败'
	} catch (e) {
		error.value = '网络异常，请稍后重试'
	} finally {
		loading.value = false
		uni.stopPullDownRefresh()
	}
}

function changePrepared(event) {
	if (!editable.value || saving.value) return
	const checked = new Set((event.detail?.value || []).map(String))
	detail.value.materials = detail.value.materials.map(item => ({ ...item, checked: checked.has(String(item.id)) }))
}

async function confirmFinish() {
	return new Promise(resolve => uni.showModal({
		title: '完成备货',
		content: '确认全部耗材已经备齐？提交后本单将标记为已备货。',
		confirmText: '确认完成',
		success: result => resolve(Boolean(result.confirm)),
		fail: () => resolve(false)
	}))
}

async function save() {
	if (!editable.value || saving.value) return
	const shouldFinish = allChecked.value
	if (shouldFinish && !(await confirmFinish())) return
	saving.value = true
	try {
		const prepared = await preparationApi.prepare(detail.value.id, preparationPayload(detail.value.materials), { loading: false })
		if (prepared.code !== 200) { await loadDetail(); return }
		if (shouldFinish) {
			const finished = await preparationApi.finish(detail.value.id, { loading: false })
			if (finished.code !== 200) { await loadDetail(); return }
		}
		uni.showToast({ title: shouldFinish ? '备货已完成' : '备货进度已保存', icon: 'success' })
		await loadDetail()
	} finally {
		saving.value = false
	}
}

function formatQuantity(value) { const number = Number(value || 0); return Number.isInteger(number) ? String(number) : String(number).replace(/0+$/, '').replace(/\.$/, '') }
function stockText(item) { return item.stock == null ? '库存未设置' : `当前库存：${formatQuantity(item.stock)} ${item.unit || ''}` }
function callInstaller() { if (detail.value.submitterPhone) uni.makePhoneCall({ phoneNumber: String(detail.value.submitterPhone).replace(/[^\d]/g, '') }) }

onLoad(options => {
	id.value = options?.id || ''
	source.value = String(options?.source || 'W').toUpperCase()
	if (!id.value || source.value !== 'W') {
		error.value = '备货订单参数无效'
		loading.value = false
		return
	}
	loadDetail()
})
onPullDownRefresh(loadDetail)
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:24rpx;box-sizing:border-box;background:#f4f7fb}.state{min-height:560rpx;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18rpx;color:#8b9aaa;font-size:24rpx}.state-title{color:#334155;font-size:27rpx;font-weight:650}.outline-button{height:70rpx;margin:8rpx 0 0;padding:0 32rpx;border:2rpx solid #0b63ce;border-radius:18rpx;background:#fff;color:#0b63ce;font-size:24rpx}.outline-button::after,.primary-button::after{border:0}.summary-card,.materials-card{padding:30rpx;border-radius:22rpx;background:#fff;box-shadow:0 4rpx 16rpx rgba(30,41,59,.05)}.summary-head,.summary-row,.section-head{display:flex;align-items:center;justify-content:space-between;gap:20rpx}.summary-title{min-width:0;flex:1}.summary-title text:first-child{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#142434;font-size:31rpx;font-weight:750}.summary-title text:last-child{display:block;margin-top:8rpx;color:#64748b;font-size:22rpx}.status{flex-shrink:0;padding:8rpx 17rpx;border-radius:18rpx;background:#edf2f7;color:#64748b;font-size:21rpx}.status-pending{background:#fff3df;color:#d97706}.status-preparing{background:#e7f1ff;color:#0b63ce}.status-done{background:#e8f8ef;color:#159455}.status-voided{background:#edf2f7;color:#64748b}.summary-row{margin-top:22rpx;padding-top:18rpx;border-top:1rpx solid #edf1f5;color:#94a3b8;font-size:23rpx}.summary-row text:last-child{color:#334155;text-align:right}.summary-row .phone{color:#0b63ce}.remark{margin-top:22rpx;padding:20rpx;border-radius:16rpx;background:#f7f9fc}.remark text:first-child{display:block;color:#64748b;font-size:22rpx}.remark text:last-child{display:block;margin-top:10rpx;color:#334155;font-size:24rpx;line-height:1.6}.materials-card{margin-top:20rpx}.section-title{display:block;color:#142434;font-size:29rpx;font-weight:700}.section-desc{display:block;margin-top:6rpx;color:#94a3b8;font-size:21rpx}.progress{color:#0b63ce;font-size:23rpx;font-weight:650}.material-item{display:flex;align-items:flex-start;gap:18rpx;padding:26rpx 0;border-bottom:1rpx solid #edf1f5}.material-item:last-child{border-bottom:0}.material-item.checked{background:linear-gradient(90deg,rgba(231,241,255,.45),transparent)}.material-item checkbox{margin-top:5rpx;transform:scale(.88)}.material-copy{min-width:0;flex:1}.material-name{display:block;color:#142434;font-size:27rpx;font-weight:650}.material-spec{display:block;margin-top:8rpx;color:#64748b;font-size:22rpx;line-height:1.5}.material-stock{display:block;margin-top:7rpx;color:#94a3b8;font-size:20rpx}.quantity{flex-shrink:0;text-align:right}.quantity text:first-child{display:block;color:#94a3b8;font-size:20rpx}.quantity text:last-child{display:block;margin-top:8rpx;color:#0b63ce;font-size:24rpx;font-weight:650}.empty-materials{padding:80rpx 0;text-align:center;color:#94a3b8;font-size:23rpx}.action-space{height:190rpx}.action-bar{position:fixed;z-index:20;left:0;right:0;bottom:0;padding:20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));display:flex;align-items:center;gap:20rpx;background:#fff;box-shadow:0 -8rpx 28rpx rgba(30,41,59,.1)}.action-summary{min-width:0;flex:1}.action-summary text:first-child{display:block;color:#334155;font-size:23rpx;font-weight:650}.action-summary text:last-child{display:block;margin-top:5rpx;color:#94a3b8;font-size:19rpx}.primary-button{width:300rpx;height:76rpx;margin:0;border-radius:20rpx;background:linear-gradient(135deg,#1479e8,#0b63ce);color:#fff;font-size:23rpx;font-weight:650}.primary-button[disabled]{background:#a9c9ed;color:#fff;opacity:1}.readonly-tip{display:flex;align-items:center;justify-content:center;gap:10rpx;margin-top:20rpx;padding:24rpx;border-radius:18rpx;background:#eef2f6;color:#64748b;font-size:22rpx}
</style>
