// API 地址统一包含版本前缀；Vue 3 优先读取 Vite 环境变量，同时兼容 HBuilderX 注入的 VUE_APP_*。
const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL || process.env.VUE_APP_API_BASE_URL
// 当前仅用于本地联调；正式域名确定后通过环境变量覆盖，不在代码中硬编码。
const baseUrl = (configuredApiBaseUrl || 'http://localhost:8080/api/v1').replace(/\/+$/, '')

// ═══ 虚拟登录开关 ═══
// true：后端不可用时，微信登录失败自动走虚拟登录（mock token），
//       后续接口请求由 mock.js 拦截返回模拟数据，可预览全部页面。
// false：走真实后端登录与接口。
// Mock 必须显式开启，避免测试 token 被误带入真实联调或生产包。
const configuredMockLogin = import.meta.env.VITE_USE_MOCK_LOGIN || process.env.VUE_APP_USE_MOCK_LOGIN
export const USE_MOCK_LOGIN = configuredMockLogin === 'true'

export default baseUrl
