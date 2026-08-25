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

      <view class="login-action">
        <button class="wechat-login-btn" open-type="getPhoneNumber"
          hover-class="button-pressed" @getphonenumber="onGetPhoneNumber"
          :disabled="isLogging || !isAgree">
          <up-icon name="weixin-fill" size="25" color="#ffffff"></up-icon>
          <text class="btn-text">{{ isLogging ? '正在登录…' : '授权手机号一键登录' }}</text>
        </button>
        <text class="login-tip">{{ isAgree ? '微信安全验证，仅用于注册登录和订单服务' : '请先阅读并勾选下方协议' }}</text>
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
const isLogging = ref(false)  // 登录中标志，防重复点击
const toggleAgreement = () => { isAgree.value = !isAgree.value }

const goToAgreement = (type) => {
  uni.navigateTo({
    url: type === 'user' ? '/packageA/agreement/user' : '/packageA/agreement/privacy'
  })
}

// ============ 微信手机号一键登录（新用户自动注册为普通客户） ============
const onGetPhoneNumber = async (e) => {
  if (isLogging.value) return
  const detail = (e && e.detail) || {}
  const phoneCode = typeof detail.code === 'string' ? detail.code.trim() : ''
  const errMsg = String(detail.errMsg || detail.err_msg || '')
  const errno = detail.errno ?? detail.err_no

  // 不记录一次性手机号 code，只输出微信返回的状态，便于真机联调定位。
  console.info('[auth] getPhoneNumber result', {
    success: Boolean(phoneCode),
    errMsg,
    errno: errno ?? ''
  })

  // 微信官方以动态令牌 code 作为成功凭证；不要依赖 errMsg 的精确文案。
  if (!phoneCode) {
    const denied = /deny|cancel/i.test(errMsg)
    const normalizedErrno = Number(errno)
    let reason = `微信未返回手机号授权凭证${errno == null ? '' : `（错误码：${errno}）`}，请稍后重试。`
    if (normalizedErrno === 112) {
      reason = '当前小程序尚未在微信公众平台《用户隐私保护指引》中声明手机号信息，请完成声明并等待配置生效后重试。'
    } else if (denied) {
      reason = '你已取消手机号授权，请重新点击并选择手机号。'
    }
    uni.showModal({
      title: '手机号授权未完成',
      content: reason,
      showCancel: false,
      confirmText: '知道了'
    })
    return
  }
  isLogging.value = true
  uni.showLoading({ title: '正在登录...', mask: true })
  try {
    // 手机号由微信原生选择框授权；随后获取登录 code 完成身份识别。
    const code = await getWxCode()
    const loginRes = await authApi.loginWithWechat({ code })
    if (loginRes.code !== 200) return

    const loginData = loginRes.data || {}
    if (!loginData.needPhone) {
      await handleLoginSuccess(loginData)
      return
    }

    // 首次登录：任意合法微信手机号均自动注册为普通客户并签发登录态。
    const bindRes = await authApi.bindPhone({ code, phoneCode })
    if (bindRes.code === 200) await handleLoginSuccess(bindRes.data || {})
  } catch (err) {
    uni.showToast({ title: err?.msg || '手机号登录失败，请重试', icon: 'none' })
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
.wechat-login-btn { width: 100%; height: 92rpx; margin: 0; padding: 0; border: 0; border-radius: 22rpx; background: linear-gradient(135deg, #14b875, #08a162); display: flex; align-items: center; justify-content: center; box-shadow: 0 10rpx 24rpx rgba(15,164,101,.2); }
.wechat-login-btn::after { border: 0; }.wechat-login-btn[disabled] { opacity: .55; background: linear-gradient(135deg, #14b875, #08a162); color: #fff; }
.button-pressed { opacity: .84; transform: scale(.99); }.btn-text { color: #fff; font-size: 29rpx; font-weight: 650; margin-left: 12rpx; }
.login-tip { display: block; text-align: center; margin-top: 16rpx; color: #9aa8b6; font-size: 20rpx; }

.agreement-row { display: flex; align-items: center; gap: 10rpx; margin-top: 30rpx; padding-top: 25rpx; border-top: 1rpx solid #edf1f5; }
.agreement-text { display: block; flex: 1; min-width: 0; color: $text-sub; font-size: 21rpx; line-height: 1.55; }.link-text { color: $primary; }
.company-footer { display: flex; flex-direction: column; align-items: center; gap: 8rpx; margin-top: auto; padding: 40rpx 20rpx 0; color: #9aa8b6; font-size: 20rpx; }
</style>
