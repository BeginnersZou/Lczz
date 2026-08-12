const SUPPORTED_ROLES = ['admin', 'customer', 'installer', 'dealer']

export function getAuthToken() {
  return uni.getStorageSync('token') || ''
}

export function getAuthUserInfo() {
  return uni.getStorageSync('userInfo') || {}
}

export function isValidAuthUser(userInfo) {
  return Boolean(
    userInfo &&
    typeof userInfo === 'object' &&
    SUPPORTED_ROLES.includes(userInfo.role)
  )
}

export function saveAuthSession(token, userInfo) {
  if (!token || !isValidAuthUser(userInfo)) return false
  uni.setStorageSync('token', token)
  uni.setStorageSync('userInfo', userInfo)
  return true
}

export function saveAuthUserInfo(userInfo) {
  if (!isValidAuthUser(userInfo)) return false
  uni.setStorageSync('userInfo', userInfo)
  return true
}

export function clearAuthSession() {
  uni.removeStorageSync('token')
  uni.removeStorageSync('userInfo')
}

export function getRoleLabel(role) {
  const labels = {
    admin: '管理员',
    customer: '认证用户',
    installer: '服务工程师',
    dealer: '经销商'
  }
  return labels[role] || '认证用户'
}

export function maskPhone(phone) {
  if (!phone) return ''
  const value = String(phone).replace(/\s+/g, '')
  if (/^1\d{10}$/.test(value)) return `${value.slice(0, 3)}****${value.slice(-4)}`
  if (value.length > 7) return `${value.slice(0, 3)}****${value.slice(-4)}`
  return '已绑定'
}
