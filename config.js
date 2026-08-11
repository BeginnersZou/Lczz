// 自动判断：开发环境(本地) / 生产环境(线上)
let baseUrl = ''

// 开发环境 —— 本地运行时自动用这个
if (process.env.NODE_ENV === 'development') {
	baseUrl = 'https://www.lczzcompany.com/api'
}
// 生产环境 —— 打包发布后自动用这个
else {
	baseUrl = 'https://www.lczzcompany.com/api'
}

// ═══ 虚拟登录开关 ═══
// true：后端不可用时，微信登录失败自动走虚拟登录（mock token），
//       后续接口请求由 mock.js 拦截返回模拟数据，可预览全部页面。
// false：走真实后端登录与接口。
// 发版时改为 false 即可。
export const USE_MOCK_LOGIN = true

export default baseUrl