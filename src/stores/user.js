import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, getUserInfoApi, logoutApi } from '@/api/auth'

/**
 * 用户登录态 store
 * 接入真实后端接口：登录、退出、获取用户信息
 * token 持久化于 localStorage，刷新页面后通过 getUserInfoApi 恢复登录态
 */
export const useUserStore = defineStore('user', () => {
  const user = ref(null)
  const token = ref(localStorage.getItem('token') || '')

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || '')
  const isAdmin = computed(() => {
    const roles = Array.isArray(user.value?.roles) ? user.value.roles : []
    return role.value === 'admin' || roles.includes('admin')
  })
  const userName = computed(() => user.value?.name || user.value?.username || '管理员')

  /**
   * 登录
   * 仅调用真实后端接口，不再使用前端内置账号回退。
   * @param {Object} credentials - { username, password }
   * @returns {Promise<{success: boolean, message: string}>}
   */
  async function login(credentials) {
    try {
      const data = await loginApi(credentials)
      // 后端返回 { token, userInfo }
      if (!data?.token || !data?.userInfo) {
        throw new Error('登录响应缺少 token 或 userInfo')
      }
      const roles = Array.isArray(data.userInfo.roles) ? data.userInfo.roles : []
      if (data.userInfo.role !== 'admin' && !roles.includes('admin')) {
        throw new Error('该账号无后台管理权限')
      }
      token.value = data.token
      user.value = data.userInfo
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.userInfo))
      return { success: true, message: '登录成功' }
    } catch (err) {
      return {
        success: false,
        message: err?.response?.data?.message || err?.message || '登录失败，请检查账号密码'
      }
    }
  }

  /**
   * 退出登录：先调后端退出接口（忽略失败），再清理本地状态
   */
  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 后端退出失败也允许前端登出，避免卡死
    } finally {
      clearSession()
    }
  }

  /**
   * 通过 token 恢复登录态：先尝试本地缓存，再请求后端校验
   * 用于路由守卫刷新页面后的状态恢复
   */
  async function fetchUserInfo() {
    if (!token.value) {
      user.value = null
      return null
    }
    // 先用本地缓存快速恢复，避免页面闪烁
    const cached = localStorage.getItem('user')
    if (cached) {
      try {
        user.value = JSON.parse(cached)
      } catch {
        localStorage.removeItem('user')
      }
    }
    // 请求后端校验并获取最新用户信息
    try {
      const info = await getUserInfoApi()
      if (!info || typeof info !== 'object') {
        throw new Error('用户信息响应格式不正确')
      }
      user.value = info
      localStorage.setItem('user', JSON.stringify(info))
      return info
    } catch {
      // token 失效，清理本地态（拦截器已处理跳转）
      clearSession()
      return null
    }
  }

  function clearSession() {
    user.value = null
    token.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  function syncSession() {
    const storedToken = localStorage.getItem('token') || ''
    if (storedToken !== token.value) {
      token.value = storedToken
      if (!storedToken) user.value = null
    }
  }

  return {
    user,
    token,
    isLoggedIn,
    role,
    isAdmin,
    userName,
    login,
    logout,
    fetchUserInfo,
    clearSession,
    syncSession
  }
})
