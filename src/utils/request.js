import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

/**
 * Axios 全局实例封装
 *
 * 统一约定后端返回结构：{ code: number, message: string, data: any }
 *   - code === 200 视为业务成功，直接返回 data
 *   - code === 401 未登录/登录失效，清除 token 并跳登录
 *   - 其他 code 视为业务失败，弹出 message 并 reject
 *
 * HTTP 错误码统一处理：
 *   - 400 参数错误  401 未授权  403 禁止访问  404 资源不存在
 *   - 500 服务器错误  502 网关错误  503 服务不可用  504 网关超时
 *
 * 文件上传（FormData）：自动清除默认 Content-Type，由浏览器设置含 boundary 的正确值
 * 文件下载（blob）：responseType: 'blob' 时直接返回原始数据
 *
 * 对接后端时，仅需调整 SUCCESS_CODE、TOKEN_KEY、响应结构字段名即可
 */

// ====================== 常量配置 ======================
// 业务成功状态码（按后端实际约定修改）
const SUCCESS_CODE = 200
// 未授权状态码（按后端实际约定修改）
const UNAUTHORIZED_CODE = 401
// token 在 localStorage 的 key
const TOKEN_KEY = 'token'

// ====================== Axios 实例 ======================
const service = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

// ====================== 请求拦截器 ======================
service.interceptors.request.use(
  (config) => {
    // 自动携带 token
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    // FormData 请求：删除默认 Content-Type，由浏览器自动设置 multipart/form-data; boundary=...
    // 手动设置 multipart/form-data 会导致 boundary 丢失，后端无法解析表单数据
    if (config.data instanceof FormData) {
      // axios v1.x 使用 AxiosHeaders 类，优先使用 delete 方法
      if (config.headers && typeof config.headers.delete === 'function') {
        config.headers.delete('Content-Type')
      } else if (config.headers) {
        delete config.headers['Content-Type']
      }
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ====================== 响应拦截器 ======================
service.interceptors.response.use(
  (response) => {
    const res = response.data
    // 文件流是对象，必须在业务 code 判断之前直接返回。
    if (response.config?.responseType === 'blob') {
      return res
    }
    // 非 JSON 场景直接返回原始数据
    if (res == null || typeof res !== 'object') {
      return res
    }
    // 业务成功
    if (res.code === SUCCESS_CODE) {
      return res.data
    }
    // 登录失效
    if (res.code === UNAUTHORIZED_CODE) {
      if (!response.config?.silent) handleUnauthorized()
      return Promise.reject(new Error(res.message || '登录已失效，请重新登录'))
    }
    // 其他业务错误
    if (!response.config?.silent) ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // silent 配置：静默失败，不弹出错误提示（用于登录等需要本地回退的场景）
    if (error.config?.silent) {
      return Promise.reject(error)
    }
    handleHttpError(error)
    return Promise.reject(error)
  }
)

// ====================== HTTP 错误统一处理 ======================
function handleHttpError(error) {
  const status = error.response?.status

  // 有 HTTP 状态码的错误
  if (status === 401) {
    handleUnauthorized()
  } else if (status === 403) {
    ElMessage.error('没有权限访问该资源（403）')
  } else if (status === 404) {
    ElMessage.error('请求的资源不存在（404）')
  } else if (status === 400) {
    const msg = error.response?.data?.message || '请求参数错误（400）'
    ElMessage.error(msg)
  } else if (status === 500) {
    ElMessage.error('服务器内部错误（500），请稍后重试')
  } else if (status === 502) {
    ElMessage.error('网关错误（502），服务暂不可用')
  } else if (status === 503) {
    ElMessage.error('服务暂不可用（503），请稍后重试')
  } else if (status === 504) {
    ElMessage.error('网关超时（504），请稍后重试')
  } else if (status) {
    ElMessage.error(`网络错误(${status})，请稍后重试`)
  } else if (error.code === 'ECONNABORTED') {
    // 请求超时
    ElMessage.error('请求超时，请检查网络')
  } else if (error.code === 'ERR_NETWORK') {
    // 网络连接失败（后端未启动 / 域名无法解析）
    ElMessage.error('网络连接失败，请检查网络或联系管理员')
  } else {
    ElMessage.error(error.message || '请求异常')
  }
}

// ====================== 登录失效处理 ======================
let isRedirecting = false
function handleUnauthorized() {
  if (isRedirecting) return
  isRedirecting = true
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem('user')
  ElMessage.error('登录已失效，请重新登录')
  const redirect = router.currentRoute.value.fullPath
  router.replace({ path: '/login', query: redirect && redirect !== '/login' ? { redirect } : {} }).finally(() => {
    isRedirecting = false
  })
}

// ====================== 请求方法封装 ======================
/**
 * 统一请求方法封装，方便调用
 * 所有方法返回 Promise，成功时 resolve(data)，失败时 reject(error)
 */
export const http = {
  /** GET 请求 */
  get(url, params, config = {}) {
    return service({ url, method: 'get', params, ...config })
  },
  /** POST 请求 */
  post(url, data, config = {}) {
    return service({ url, method: 'post', data, ...config })
  },
  /** PUT 请求 */
  put(url, data, config = {}) {
    return service({ url, method: 'put', data, ...config })
  },
  /** DELETE 请求 */
  delete(url, params, config = {}) {
    return service({ url, method: 'delete', params, ...config })
  },
  /**
   * 文件上传（FormData）
   * @param {string} url - 上传接口地址
   * @param {FormData} formData - 表单数据
   * @param {Object} [config] - 额外配置
   * @returns {Promise<any>}
   */
  upload(url, formData, config = {}) {
    return service({ url, method: 'post', data: formData, ...config })
  },
  /**
   * 文件下载（blob 流）
   * @param {string} url - 下载接口地址
   * @param {Object} [params] - 查询参数
   * @param {Object} [config] - 额外配置
   * @returns {Promise<Blob>}
   */
  download(url, params, config = {}) {
    return service({ url, method: 'get', params, responseType: 'blob', ...config })
  }
}

export default service
