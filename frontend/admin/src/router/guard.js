import router from './index'
import { useUserStore } from '@/stores/user'

// 记录已经通过 /auth/info 校验的 token；token 变化后必须重新校验。
let validatedToken = ''

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  userStore.syncSession()

  if (!userStore.token) {
    validatedToken = ''
  } else if (validatedToken !== userStore.token) {
    const info = await userStore.fetchUserInfo()
    validatedToken = info ? userStore.token : ''
  }

  if (to.path === '/login') {
    // 已登录用户访问登录页，重定向到首页
    if (userStore.isLoggedIn) {
      const redirect = typeof to.query.redirect === 'string' && to.query.redirect.startsWith('/') && !to.query.redirect.startsWith('//')
        ? to.query.redirect
        : '/dashboard'
      next(redirect)
      return
    }
    next()
    return
  }

  if (!userStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 后台只允许管理员进入。即使客户端缓存被修改，也必须以 /auth/info 返回角色为准。
  if (to.path !== '/403' && !userStore.isAdmin) {
    next({ path: '/403', query: { from: to.fullPath } })
    return
  }

  next()
})

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} - 力创之尊业务系统` : '力创之尊业务系统'
})
