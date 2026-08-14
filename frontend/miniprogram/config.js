// API 地址统一包含版本前缀；Vue 3 优先读取 Vite 环境变量，同时兼容 Node 构建环境注入的 VUE_APP_*。
// 微信小程序运行时没有全局 process，必须先检测再读取，避免 appservice 启动阶段直接报错。
const viteEnv = import.meta.env || {}
const nodeEnv = typeof process !== 'undefined' && process.env ? process.env : {}
const configuredApiBaseUrl = viteEnv.VITE_API_BASE_URL || nodeEnv.VUE_APP_API_BASE_URL
// 当前默认使用已确认的局域网联调地址，开发者工具和真机保持同一 Wi-Fi 即可访问。
// 正式域名确定后通过环境变量覆盖，不在代码中提前写线上域名。
const baseUrl = (configuredApiBaseUrl || 'http://172.20.22.132:8080/api/v1').replace(/\/+$/, '')

export default baseUrl
