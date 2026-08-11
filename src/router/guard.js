import router from './index'
import { useUserStore } from '@/stores/user'

// 标记是否已执行过首次用户信息拉取，避免每次跳转都请求
let userInfoFetched = false

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  userStore.syncSession()

  // 首次进入且有 token：向后端校验登录态
  if (!userInfoFetched && userStore.token) {
    userInfoFetched = true
    await userStore.fetchUserInfo()
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

  next()
})

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} - 力创之尊业务系统` : '力创之尊业务系统'
})
