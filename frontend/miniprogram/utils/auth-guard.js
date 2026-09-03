import { getAuthToken } from './auth-session.js'

let isPromptVisible = false

export function showLoginChoice(options = {}) {
  if (isPromptVisible) return
  isPromptVisible = true
  uni.showModal({
    title: options.title || '登录后使用此功能',
    content: options.content || '该功能涉及你的个人订单或账号信息。你可以暂不登录，继续浏览产品和服务。',
    cancelText: '暂不登录',
    confirmText: '去登录',
    success: (res) => {
      if (res.confirm) {
        uni.navigateTo({ url: '/pages/login/login' })
      } else {
        uni.switchTab({ url: '/pages/index/index' })
      }
    },
    complete: () => { isPromptVisible = false }
  })
}

export function requireLogin(options = {}) {
  if (getAuthToken()) return true
  showLoginChoice(options)
  return false
}
