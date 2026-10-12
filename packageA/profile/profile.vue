<template>
	<view class="page">
		<view class="notice">
			<up-icon name="info-circle" size="18" color="#0b63ce"></up-icon>
			<text>资料仅用于账号展示和服务沟通，不会修改历史订单中的姓名。</text>
		</view>

		<view v-if="loading" class="state-card">
			<text>正在加载个人资料...</text>
		</view>
		<view v-else class="form-card">
			<view class="field-row">
				<view class="field-label">
					<text>昵称</text><text class="required">*</text>
				</view>
				<input v-model="form.nickname" class="field-input" :maxlength="64" confirm-type="done" placeholder="请输入昵称" />
				<text class="counter">{{ form.nickname.length }}/64</text>
			</view>

			<view class="field-row last-row">
				<view class="field-label">
					<text>真实姓名</text><text v-if="installer" class="required">*</text>
				</view>
				<input v-model="form.realName" class="field-input" :maxlength="64" confirm-type="done"
					:placeholder="installer ? '安装师傅必须填写真实姓名' : '选填'" />
				<text class="counter">{{ form.realName.length }}/64</text>
			</view>
		</view>

		<text v-if="installer && !loading" class="installer-tip">真实姓名将用于订单指派和服务记录。</text>
		<button class="save-btn" :class="{ disabled: loading || submitting }" :disabled="loading || submitting" @click="saveProfile">
			{{ submitting ? '保存中...' : '保存' }}
		</button>
	</view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { authApi } from '@/api/api.js'
import { getAuthToken, getAuthUserInfo, saveAuthUserInfo } from '@/utils/auth-session.js'
import { isInstallerProfile, profileForm, userProfilePayload, validateUserProfile } from '../utils/user-profile.js'

const loading = ref(true)
const submitting = ref(false)
const userInfo = ref({})
const form = reactive({ nickname: '', realName: '' })
const installer = computed(() => isInstallerProfile(userInfo.value))

const applyUserInfo = (value = {}) => {
	userInfo.value = value
	Object.assign(form, profileForm(value))
}

const loadProfile = async () => {
	if (!getAuthToken()) {
		uni.switchTab({ url: '/pages/index/index' })
		return
	}
	loading.value = true
	applyUserInfo(getAuthUserInfo())
	try {
		const response = await authApi.getUserInfo({ silent: true })
		if (response.code !== 200 || !response.data) return
		if (!saveAuthUserInfo(response.data)) return
		applyUserInfo(response.data)
	} finally {
		loading.value = false
	}
}

const saveProfile = async () => {
	if (submitting.value) return
	const message = validateUserProfile(form, userInfo.value)
	if (message) {
		uni.showToast({ title: message, icon: 'none' })
		return
	}
	submitting.value = true
	try {
		const response = await authApi.updateProfile(userProfilePayload(form))
		if (response.code !== 200 || !response.data) return
		if (!saveAuthUserInfo(response.data)) {
			uni.showToast({ title: '资料缓存失败，请重新登录', icon: 'none' })
			return
		}
		applyUserInfo(response.data)
		uni.showToast({ title: '保存成功', icon: 'success' })
		setTimeout(() => uni.navigateBack(), 600)
	} finally {
		submitting.value = false
	}
}

onShow(loadProfile)
</script>

<style scoped lang="scss">
@import '@/uni.scss';

.page {
	min-height: 100vh;
	box-sizing: border-box;
	padding: 24rpx 24rpx calc(56rpx + env(safe-area-inset-bottom));
	background: $bg-page;
}

.notice {
	display: flex;
	align-items: flex-start;
	gap: 14rpx;
	padding: 24rpx 26rpx;
	border: 1rpx solid #d9e8f8;
	border-radius: $radius-lg;
	background: #f3f8fe;
	color: #49657f;
	font-size: $font-sm;
	line-height: 1.6;
}

.state-card,
.form-card {
	margin-top: 24rpx;
	border-radius: $radius-xl;
	background: $bg-card;
	box-shadow: $shadow-card;
}

.state-card {
	padding: 80rpx 32rpx;
	text-align: center;
	color: $text-light;
	font-size: $font-md;
}

.field-row {
	position: relative;
	padding: 30rpx 28rpx 26rpx;
	border-bottom: 1rpx solid #f0f1f3;
}

.last-row {
	border-bottom: none;
}

.field-label {
	display: flex;
	align-items: center;
	margin-bottom: 18rpx;
	font-size: $font-md;
	font-weight: 600;
	color: $text-main;
}

.required {
	margin-left: 6rpx;
	color: $danger;
}

.field-input {
	box-sizing: border-box;
	height: 80rpx;
	padding: 0 96rpx 0 22rpx;
	border: 1rpx solid #dfe5ec;
	border-radius: $radius-md;
	background: #fafbfd;
	font-size: $font-md;
	color: $text-main;
}

.counter {
	position: absolute;
	right: 48rpx;
	bottom: 51rpx;
	font-size: $font-xs;
	color: $text-light;
}

.installer-tip {
	display: block;
	margin: 18rpx 12rpx 0;
	font-size: $font-sm;
	color: $text-light;
}

.save-btn {
	height: 92rpx;
	margin: 42rpx 0 0;
	border: none;
	border-radius: $radius-xl;
	background: linear-gradient(135deg, #1479e8 0%, #0b63ce 100%);
	color: #fff;
	font-size: $font-lg;
	font-weight: 600;
	line-height: 92rpx;
}

.save-btn::after {
	border: none;
}

.save-btn.disabled {
	opacity: 0.6;
}
</style>
