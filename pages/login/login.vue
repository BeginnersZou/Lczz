<template>
  <view class="login-container">
    <view class="login-hero">
      <view class="hero-circle circle-one"></view>
      <view class="hero-circle circle-two"></view>
      <view class="brand-lockup">
        <view class="brand-icon-box">
          <text class="brand-icon">鑫</text>
          <view class="brand-dot"></view>
        </view>
        <view class="brand-copy">
          <text class="brand-name">鑫立创</text>
          <text class="brand-en">HVAC SERVICE</text>
        </view>
      </view>
      <text class="hero-title">让每一次冷暖服务，都更省心</text>
      <text class="hero-desc">专业安装、维修保养与正品配件一站式服务</text>

      <view class="promise-row">
        <view class="promise-item"><up-icon name="account-fill" size="17" color="#ffffff"></up-icon><text>持证上岗</text></view>
        <view class="promise-item"><up-icon name="checkmark-circle-fill" size="17" color="#ffffff"></up-icon><text>规范施工</text></view>
        <view class="promise-item"><up-icon name="server-fill" size="17" color="#ffffff"></up-icon><text>售后保障</text></view>
      </view>
    </view>

    <view class="login-panel">
      <view class="panel-heading">
        <text class="panel-title">欢迎使用</text>
        <text class="panel-subtitle">登录后查看订单进度与服务记录</text>
      </view>

      <view v-if="!showPhoneAuth" class="login-action">
        <view class="wechat-login-btn" hover-class="button-pressed"
          :class="{ 'btn-disabled': isLogging }" @click="handleWechatLogin">
          <up-icon name="weixin-fill" size="25" color="#ffffff"></up-icon>
          <text class="btn-text">{{ isLogging ? '正在登录…' : '微信快捷登录' }}</text>
        </view>
        <text class="login-tip">安全便捷，不会自动发布任何内容</text>
      </view>

      <view class="phone-auth-wrap" v-else>
        <view class="auth-icon"><up-icon name="phone-fill" size="27" color="#0b63ce"></up-icon></view>
        <text class="auth-title">完成手机号授权</text>
        <text class="auth-tip">首次使用需要绑定手机号，便于订单服务人员与您联系</text>
        <button class="phone-auth-btn" open-type="getPhoneNumber" @getphonenumber="onGetPhoneNumber" :disabled="isLogging">
          <text>{{ isLogging ? '正在提交…' : '确认授权手机号' }}</text>
        </button>
        <view class="back-text-btn" @click="resetToWechatLogin"><text>返回上一步</text></view>
      </view>

      <view class="agreement-row">
        <up-checkbox
          usedAlone
          v-model:checked="isAgree"
          shape="circle"
          size="18"
          activeColor="#0b63ce"
          :custom-style="{ margin: '0', flexShrink: 0 }"
        ></up-checkbox>
        <view class="agreement-text" @click="toggleAgreement">我已阅读并同意
          <text class="link-text" @click.stop="goToAgreement('user')">《用户服务协议》</text>和
          <text class="link-text" @click.stop="goToAgreement('privacy')">《隐私保护协议》</text>
        </view>
      </view>
    </view>

    <view class="company-footer">
      <text>武汉力创之尊机电设备有限公司</text>
      <text>以诚信之心 · 立潮流之品</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { authApi } from '@/api/api.js'
import {
  clearAuthSession,
  isValidAuthUser,
  saveAuthSession,
  saveAuthUserInfo
} from '@/utils/auth-session.js'

const isAgree = ref(false)
const showPhoneAuth = ref(false)
const wxLoginCode = ref('')   // 微信登录 code，手机号绑定步骤传给后端
const isLogging = ref(false)  // 登录中标志，防重复点击
const toggleAgreement = () => { isAgree.value = !isAgree.value }

const goToAgreement = (type) => {
  uni.navigateTo({
    url: type === 'user' ? '/packageA/agreement/user' : '/packageA/agreement/privacy'
  })
}

// ============ 步骤一：微信快捷登录 ============
const handleWechatLogin = async () => {
  if (isLogging.value) return
  if (!isAgree.value) {
    uni.showToast({ title: '请先同意用户协议', icon: 'none' })
    return
  }
  isLogging.value = true
  uni.showLoading({ title: '正在登录...', mask: true })
  try {
    // 1. 获取微信登录凭证 code
    const code = await getWxCode()
    // 2. 调后端：code 换登录态（已注册 → token；未注册 → needPhone）
    const res = await authApi.loginWithWechat({ code })
    if (res.code !== 200) return
    // 3. 根据后端返回（res.data）决定下一步
    const data = res.data || {}
    if (data.needPhone) {
      // 未绑定手机号 → 进入手机号授权步骤
      wxLoginCode.value = code
      showPhoneAuth.value = true
      uni.showToast({ title: '请授权手机号完成注册', icon: 'none' })
    } else {
      // 已注册 → 直接登录成功
      await handleLoginSuccess(data)
    }
  } catch (err) {
    uni.showToast({ title: err?.msg || '微信登录失败，请重试', icon: 'none' })
  } finally {
    uni.hideLoading()
    isLogging.value = false
  }
}

// ============ 步骤二：手机号授权绑定（新用户注册） ============
const onGetPhoneNumber = async (e) => {
  if (isLogging.value) return
  const detail = (e && e.detail) || {}
  // 用户拒绝授权，或未拿到手机号凭证
  if (detail.errMsg !== 'getPhoneNumber:ok' || !detail.code) {
    uni.showToast({ title: '需要授权手机号才能登录', icon: 'none' })
    return
  }
  // 微信登录 code 丢失（异常场景），回到第一步重新登录
  if (!wxLoginCode.value) {
    uni.showToast({ title: '登录状态已失效，请重新登录', icon: 'none' })
    resetToWechatLogin()
    return
  }
  isLogging.value = true
  uni.showLoading({ title: '正在注册...', mask: true })
  try {
    const res = await authApi.bindPhone({
      code: wxLoginCode.value,  // 步骤一拿到的微信 code
      phoneCode: detail.code    // 手机号授权 code（新版接口）
      // 旧版兼容：若后端用 encryptedData/iv 解密，改为传 detail.encryptedData、detail.iv
    })
    if (res.code === 200) {
      await handleLoginSuccess(res.data || {})
    } else {
      // 后端的一次性登录挑战可能已经被消费，重新从微信 code 步骤开始最可靠。
      resetToWechatLogin()
    }
  } catch (err) {
    uni.showToast({ title: err?.msg || '手机号绑定失败，请重新登录', icon: 'none' })
    resetToWechatLogin()
  } finally {
    uni.hideLoading()
    isLogging.value = false
  }
}

// ============ 登录成功统一处理 ============
// 入参 data 为后端 res.data：{ token, userInfo, needPhone }
const handleLoginSuccess = async (data = {}) => {
  if (!data.token || !isValidAuthUser(data.userInfo)) {
    uni.showToast({ title: '登录响应异常，请重试', icon: 'none' })
    clearAuthSession()
    return false
  }
  saveAuthSession(data.token, data.userInfo)

  // 立即用 /auth/info 校验 token，并刷新可能变化的角色与账号状态。
  const session = await authApi.getUserInfo({ loading: false })
  if (session.code === 200) {
    if (!saveAuthUserInfo(session.data)) {
      clearAuthSession()
      uni.showToast({ title: '用户信息响应异常，请重新登录', icon: 'none' })
      return false
    }
  } else if (session.code === 401 || session.code === 403) {
    clearAuthSession()
    resetToWechatLogin()
    return false
  }

  uni.showToast({ title: '登录成功', icon: 'success' })
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/index/index' })
  }, 1000)
  return true
}

// ============ 获取微信 code（Promise 化，失败有明确提示） ============
const getWxCode = () => {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (res) => {
        if (res.code) {
          resolve(res.code)
        } else {
          reject({ msg: '获取微信登录凭证失败' })
        }
      },
      fail: () => reject({ msg: '微信登录失败，请重试' })
    })
  })
}

// ============ 返回微信登录步骤 ============
const resetToWechatLogin = () => {
  showPhoneAuth.value = false
  wxLoginCode.value = ''
}
</script>

<style scoped lang="scss">
@import "@/uni.scss";

.login-container {
  min-height: 100vh; background: $bg-page; box-sizing: border-box; padding-bottom: calc(28rpx + env(safe-area-inset-bottom));
  display: flex; flex-direction: column;
}
.login-hero {
  height: 600rpx; box-sizing: border-box; padding: calc(var(--status-bar-height, 44rpx) + 58rpx) 48rpx 0;
  background: linear-gradient(145deg, #082f5d 0%, #0b63ce 62%, #2088e2 100%); position: relative; overflow: hidden;
  border-radius: 0 0 52rpx 52rpx;
}
.hero-circle { position: absolute; border-radius: 50%; border: 1rpx solid rgba(255,255,255,.13); }
.circle-one { width: 420rpx; height: 420rpx; top: -230rpx; right: -180rpx; }
.circle-two { width: 300rpx; height: 300rpx; bottom: -160rpx; left: -130rpx; }
.brand-lockup { position: relative; z-index: 2; display: flex; align-items: center; }
.brand-icon-box {
  width: 88rpx; height: 88rpx; border-radius: 25rpx; background: rgba(255,255,255,.16); border: 1rpx solid rgba(255,255,255,.32);
  display: flex; align-items: center; justify-content: center; position: relative;
}
.brand-icon { font-size: 40rpx; color: #fff; font-weight: 800; }.brand-dot { position: absolute; width: 12rpx; height: 12rpx; border-radius: 50%; background: #41dec1; right: 9rpx; top: 9rpx; }
.brand-copy { margin-left: 20rpx; display: flex; flex-direction: column; }.brand-name { color: #fff; font-size: 38rpx; font-weight: 800; letter-spacing: 3rpx; }
.brand-en { color: rgba(255,255,255,.58); font-size: 17rpx; letter-spacing: 4rpx; margin-top: 5rpx; }
.hero-title { position: relative; z-index: 2; display: block; margin-top: 60rpx; color: #fff; font-size: 42rpx; font-weight: 800; line-height: 1.35; letter-spacing: 1rpx; }
.hero-desc { position: relative; z-index: 2; display: block; color: rgba(255,255,255,.7); font-size: 24rpx; margin-top: 16rpx; }
.promise-row { position: relative; z-index: 2; display: flex; margin-top: 52rpx; }
.promise-item { flex: 1; display: flex; align-items: center; gap: 8rpx; color: rgba(255,255,255,.86); font-size: 21rpx; }

.login-panel { margin: -60rpx 24rpx 0; padding: 40rpx 36rpx 34rpx; position: relative; z-index: 4; border-radius: 32rpx; background: #fff; box-shadow: 0 16rpx 50rpx rgba(20,54,84,.13); }
.panel-heading { display: flex; flex-direction: column; }.panel-title { color: $text-main; font-size: 36rpx; font-weight: 750; }.panel-subtitle { color: $text-light; font-size: 23rpx; margin-top: 9rpx; }
.login-action { margin-top: 34rpx; }
.wechat-login-btn { height: 92rpx; border-radius: 22rpx; background: linear-gradient(135deg, #14b875, #08a162); display: flex; align-items: center; justify-content: center; box-shadow: 0 10rpx 24rpx rgba(15,164,101,.2); }
.button-pressed { opacity: .84; transform: scale(.99); }.btn-text { color: #fff; font-size: 29rpx; font-weight: 650; margin-left: 12rpx; }.btn-disabled { opacity: .6; pointer-events: none; }
.login-tip { display: block; text-align: center; margin-top: 16rpx; color: #9aa8b6; font-size: 20rpx; }

.phone-auth-wrap { margin-top: 32rpx; display: flex; flex-direction: column; align-items: center; }
.auth-icon { width: 82rpx; height: 82rpx; border-radius: 24rpx; background: #eaf3ff; display: flex; align-items: center; justify-content: center; }
.auth-title { font-size: 30rpx; font-weight: 700; color: $text-main; margin-top: 18rpx; }.auth-tip { text-align: center; font-size: 23rpx; line-height: 1.6; color: $text-sub; margin: 10rpx 24rpx 24rpx; }
.phone-auth-btn { width: 100%; height: 88rpx; line-height: 88rpx; margin: 0; padding: 0; border-radius: 22rpx; background: $primary; color: #fff; font-size: 28rpx; font-weight: 600; }
.phone-auth-btn[disabled] { opacity: .6; }.back-text-btn { padding: 18rpx 24rpx 0; color: $text-light; font-size: 23rpx; }

.agreement-row { display: flex; align-items: center; gap: 10rpx; margin-top: 30rpx; padding-top: 25rpx; border-top: 1rpx solid #edf1f5; }
.agreement-text { display: block; flex: 1; min-width: 0; color: $text-sub; font-size: 21rpx; line-height: 1.55; }.link-text { color: $primary; }
.company-footer { display: flex; flex-direction: column; align-items: center; gap: 8rpx; margin-top: auto; padding: 40rpx 20rpx 0; color: #9aa8b6; font-size: 20rpx; }
</style>
