import baseUrl from '../config.js'

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
  return uni.getStorageSync('token') || ''
}

// ====================== 401 统一处理（防重复跳转） ======================
let isRedirecting = false

function handleUnauthorized() {
  if (isRedirecting) return
  isRedirecting = true
  // 仅清除登录态，保留其他本地数据
  uni.removeStorageSync('token')
  uni.removeStorageSync('userInfo')
  uni.showToast({ title: '请先登录', icon: 'none' })
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/login/login' })
    isRedirecting = false
  }, 1500)
}

// ====================== 核心请求方法 ======================
// 统一返回后端完整响应体 { code, data, msg }：
//  - code===200：业务成功，页面用 res.data 取数据
//  - code===401：登录失效，自动跳登录
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
    timeout = 10000  // 超时时间（毫秒）
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
        Authorization: getToken() ? `Bearer ${getToken()}` : '',
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
          handleUnauthorized()
          resolve({ code: 401, data: null, msg: body.msg })
          return
        }
        // 其他业务错误：统一 toast 后端返回的 msg
        uni.showToast({ title: body.msg || '请求失败', icon: 'none' })
        resolve(body)
      },
      fail: (err) => {
        const isTimeout = String(err.errMsg || '').includes('timeout')
        const msg = isTimeout ? '请求超时，请重试' : '网络异常，请检查网络'
        uni.showToast({ title: msg, icon: 'none' })
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
    loading = true
  } = options

  if (loading) showLoading()

  return new Promise((resolve) => {
    uni.uploadFile({
      url: baseUrl + url,
      filePath,
      name,
      formData,
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
        if (parsed && typeof parsed === 'object' && parsed.code !== undefined) {
          body = {
            code: parsed.code,
            data: parsed.data,
            msg: parsed.message || parsed.msg || '上传失败'
          }
        } else {
          body = {
            code: res.statusCode === 200 ? 200 : res.statusCode,
            data: parsed,
            msg: (parsed && (parsed.message || parsed.msg)) || '上传失败'
          }
        }

        if (body.code === 200) {
          resolve(body)
          return
        }
        if (body.code === 401) {
          handleUnauthorized()
          resolve({ code: 401, data: null, msg: body.msg })
          return
        }
        uni.showToast({ title: body.msg || '上传失败', icon: 'none' })
        resolve(body)
      },
      fail: (err) => {
        uni.showToast({ title: '上传失败，请检查网络', icon: 'none' })
        resolve({ code: -1, data: null, msg: '上传失败，请检查网络' })
      },
      complete: () => {
        if (loading) hideLoading()
      }
    })
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
