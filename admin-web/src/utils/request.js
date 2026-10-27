import axios from 'axios'
import { ElMessage } from 'element-plus'
import { ApiError, fromAxiosError, fromBody } from './api-error.js'
import { clearSession, getToken } from './admin-session.js'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: Number(import.meta.env.VITE_API_TIMEOUT) || 10000
})

let expiring = false
// 同一时间多个 401 只提示一次、只跳转一次；回跳地址保留当前页面
async function expireSession() {
  if (expiring) return
  expiring = true
  clearSession()
  ElMessage.error('登录已过期，请重新登录')
  try {
    const { default: router } = await import('../router/index.js')
    const current = router.currentRoute.value
    if (current.path !== '/login') await router.replace({ path: '/login', query: { redirect: current.fullPath } })
  } finally {
    expiring = false
  }
}

function fail(error, config) {
  if (error.kind === 'auth') expireSession()
  else if (error.kind !== 'canceled' && !config?.silent) ElMessage.error(error.message)
  return Promise.reject(error)
}

service.interceptors.request.use(config => {
  const token = getToken()
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

service.interceptors.response.use(
  response => {
    if (response.config.responseType === 'blob') return response.data
    const error = fromBody(response.data)
    return error ? fail(error, response.config) : response.data.data
  },
  error => fail(fromAxiosError(error), error?.config)
)

export { ApiError }
export default service
