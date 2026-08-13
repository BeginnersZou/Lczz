// API 地址统一包含版本前缀；Vue 3 优先读取 Vite 环境变量，同时兼容 Node 构建环境注入的 VUE_APP_*。
// 微信小程序运行时没有全局 process，必须先检测再读取，避免 appservice 启动阶段直接报错。
const viteEnv = import.meta.env || {}
const nodeEnv = typeof process !== 'undefined' && process.env ? process.env : {}
const configuredApiBaseUrl = viteEnv.VITE_API_BASE_URL || nodeEnv.VUE_APP_API_BASE_URL
// 当前仅用于本地联调；真机需把 127.0.0.1 替换为后端电脑局域网 IP。
// 正式域名确定后通过环境变量覆盖，不在代码中提前写线上域名。
const baseUrl = (configuredApiBaseUrl || 'http://127.0.0.1:8080/api/v1').replace(/\/+$/, '')

// ═══ 虚拟登录开关 ═══
// true：后端不可用时，微信登录失败自动走虚拟登录（mock token），
//       后续接口请求由 mock.js 拦截返回模拟数据，可预览全部页面。
// false：走真实后端登录与接口。
// Mock 必须显式开启，避免测试 token 被误带入真实联调或生产包。
const configuredMockLogin = viteEnv.VITE_USE_MOCK_LOGIN || nodeEnv.VUE_APP_USE_MOCK_LOGIN
const isDevelopment = viteEnv.DEV || nodeEnv.NODE_ENV === 'development'
export const USE_MOCK_LOGIN = isDevelopment && configuredMockLogin === 'true'

export default baseUrl
