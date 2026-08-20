// API 地址统一包含版本前缀；Vue 3 优先读取 Vite 环境变量，同时兼容 Node 构建环境注入的 VUE_APP_*。
// 微信小程序运行时没有全局 process，必须先检测再读取，避免 appservice 启动阶段直接报错。
const viteEnv = import.meta.env || {}
const nodeEnv = typeof process !== 'undefined' && process.env ? process.env : {}
const configuredApiBaseUrl = viteEnv.VITE_API_BASE_URL || nodeEnv.VUE_APP_API_BASE_URL
// 环境变量未正确加载时仍回退到线上 API，避免开发工具或真机误连局域网地址。
const baseUrl = (configuredApiBaseUrl || 'https://admin.whlczz.cn/api/v1').replace(/\/+$/, '')

export default baseUrl
