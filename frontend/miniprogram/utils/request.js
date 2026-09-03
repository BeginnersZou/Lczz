import baseUrl from '../config.js'
import { clearAuthSession, getAuthToken } from './auth-session.js'
import { showLoginChoice } from './auth-guard.js'

// ====================== Loading 管理（计数器，支持并发请求） ======================
let loadingCount = 0

function showLoading() {
  if (loadingCount === 0) {
    uni.showLoading({ title: '加载中...', mask: true })
  }
  loadingCount++
}

function hideLoading() {
  loadingCount = Math.max(0, loadingCount - 1)
  if (loadingCount === 0) {
    uni.hideLoading()
  }
}

// ====================== Token 管理 ======================
function getToken() {
  return getAuthToken()
}

// ====================== 401 统一处理（允许用户拒绝登录并继续使用公共功能） ======================
function handleUnauthorized(redirectOnUnauthorized = true) {
  // 仅清除失效登录态，保留其他本地数据
  clearAuthSession()
  if (!redirectOnUnauthorized) return
  showLoginChoice()
}

// ====================== 核心请求方法 ======================
// 统一返回后端完整响应体 { code, data, msg }：
//  - code===200：业务成功，页面用 res.data 取数据
//  - code===401：清除失效登录态；受保护功能提供可拒绝的登录引导
//  - 其他业务码：统一 toast(res.msg)，页面无需重复提示
//  - 网络异常：统一 toast，返回 code:-1
// 页面用法：const res = await api(); if (res.code === 200) { list.value = res.data }
const request = (options = {}) => {
  const {
    url,             // 接口路径（不含 baseUrl）
    method = 'GET',  // 请求方法
    data = {},       // 请求参数
    header = {},     // 额外请求头（可覆盖默认）
    loading = true,  // 是否显示 loading
    timeout = 10000, // 超时时间（毫秒）
    silent = false,  // 预期性探测失败时不弹 Toast
    auth = true,     // 公共接口设为 false，不发送本地登录凭证
    redirectOnUnauthorized = true // 公共接口设为 false，避免游客被强制送往登录页
  } = options

  if (loading) showLoading()

  return new Promise((resolve) => {
    uni.request({
      url: baseUrl + url,
      method,
      data,
      timeout,
      header: {
        'Content-Type': 'application/json',
        ...(auth && getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
        ...header
      },
      success: (res) => {
        const { statusCode, data: resData } = res
        // 后端统一返回 { code, message, data }，归一化为 { code, data, msg }
        let body
        if (resData && typeof resData === 'object' && resData.code !== undefined) {
          // 归一化业务数据到 data：
          //  - 标准结构 {code,data,msg}：直接取 data
          //  - 平铺结构 {code,list,total,msg}：收集除 code/msg/message 外的字段为 data，保证前端统一用 res.data
          let data
          if (resData.data !== undefined) {
            data = resData.data
          } else {
            data = {}
            Object.keys(resData).forEach(k => {
              if (k !== 'code' && k !== 'msg' && k !== 'message') data[k] = resData[k]
            })
          }
          body = {
            code: resData.code,
            data,
            msg: resData.message || resData.msg || '请求失败'
          }
        } else {
          // 非标准结构，按 HTTP 状态码包装
          body = {
            code: statusCode === 200 ? 200 : statusCode,
            data: resData,
            msg: (resData && (resData.message || resData.msg)) || '请求失败'
          }
        }

        if (body.code === 200) {
          resolve(body)
          return
        }
        if (body.code === 401) {
          handleUnauthorized(redirectOnUnauthorized)
          resolve({ code: 401, data: null, msg: body.msg })
          return
        }
        if (body.code === 403) {
          if (!silent) uni.showToast({ title: body.msg || '无权执行此操作', icon: 'none' })
          resolve(body)
          return
        }
        // 其他业务错误：统一 toast 后端返回的 msg
        if (!silent) uni.showToast({ title: body.msg || '请求失败', icon: 'none' })
        resolve(body)
      },
      fail: (err) => {
        const isTimeout = String(err.errMsg || '').includes('timeout')
        const msg = isTimeout ? '请求超时，请重试' : '网络异常，请检查网络'
        if (!silent) uni.showToast({ title: msg, icon: 'none' })
        resolve({ code: -1, data: null, msg })
      },
      complete: () => {
        if (loading) hideLoading()
      }
    })
  })
}

// ====================== 文件上传（uni.uploadFile 封装） ======================
// 同样返回完整 { code, data, msg }
const upload = (options = {}) => {
  const {
    url,                       // 接口路径（不含 baseUrl）
    filePath,                  // 本地文件路径
    name = 'file',             // 后端接收的文件字段名
    formData = {},             // 附加参数
    header = {},
    loading = true,
    timeout = 10 * 60 * 1000, // 大视频上传默认允许 10 分钟
    showError = true,
    onProgress
  } = options

  if (loading) showLoading()

  return new Promise((resolve) => {
    const uploadTask = uni.uploadFile({
      url: baseUrl + url,
      filePath,
      name,
      formData,
      timeout,
      header: {
        Authorization: getToken() ? `Bearer ${getToken()}` : '',
        ...header
      },
      success: (res) => {
        // uni.uploadFile 返回的 data 是字符串，需手动解析
        let parsed = {}
        try {
          parsed = JSON.parse(res.data)
        } catch (e) {
          parsed = res.data
        }
        let body
        const statusCode = Number(res.statusCode || 0)
        const gatewayMessage = {
          413: '服务器上传上限尚未放开（HTTP 413），请联系管理员配置后重试',
          502: '上传网关暂不可用（HTTP 502），请稍后重试',
          503: '上传服务暂不可用（HTTP 503），请稍后重试',
          504: '上传网关等待超时（HTTP 504），请稍后重试'
        }[statusCode]
        if (parsed && typeof parsed === 'object' && parsed.code !== undefined) {
          body = {
            code: parsed.code,
            data: parsed.data,
            msg: parsed.message || parsed.msg || gatewayMessage || `上传失败（HTTP ${statusCode || '未知'}）`,
            httpStatus: statusCode
          }
        } else {
          body = {
            code: statusCode === 200 ? 200 : statusCode,
            data: parsed,
            msg: gatewayMessage || `上传失败（HTTP ${statusCode || '未知'}）`,
            httpStatus: statusCode
          }
        }

        if (body.code === 200) {
          resolve(body)
          return
        }
        if (body.code === 401) {
          handleUnauthorized(true)
          resolve({ code: 401, data: null, msg: body.msg })
          return
        }
        if (body.code === 403) {
          uni.showToast({ title: body.msg || '无权执行此操作', icon: 'none' })
          resolve(body)
          return
        }
        if (showError) uni.showToast({ title: body.msg || '上传失败', icon: 'none' })
        resolve(body)
      },
      fail: (err) => {
        const isTimeout = String(err?.errMsg || '').toLowerCase().includes('timeout')
        const msg = isTimeout ? '视频上传超时，请保持网络稳定后重试' : '上传失败，请检查网络'
        if (showError) uni.showToast({ title: msg, icon: 'none' })
        resolve({ code: -1, data: null, msg })
      },
      complete: () => {
        if (loading) hideLoading()
      }
    })
    if (typeof onProgress === 'function' && uploadTask && uploadTask.onProgressUpdate) {
      uploadTask.onProgressUpdate(onProgress)
    }
  })
}

// ====================== 导出 ======================
const http = {
  get: (url, data = {}, options = {}) => request({ url, method: 'GET', data, ...options }),
  post: (url, data = {}, options = {}) => request({ url, method: 'POST', data, ...options }),
  put: (url, data = {}, options = {}) => request({ url, method: 'PUT', data, ...options }),
  delete: (url, data = {}, options = {}) => request({ url, method: 'DELETE', data, ...options }),
  upload
}

export default http
