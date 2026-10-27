// 统一错误形状：所有请求失败都 reject 一个 ApiError，页面按 kind 判断
export class ApiError extends Error {
  constructor(kind, message, extra = {}) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind // business | auth | forbidden | server | timeout | network | http
    Object.assign(this, extra)
  }
}

// 业务响应体（HTTP 200）→ 数据或 ApiError
export function fromBody(body) {
  if (!body || typeof body !== 'object' || typeof body.code !== 'number') {
    return new ApiError('server', '服务器响应格式异常，请稍后重试')
  }
  if (body.code === 200) return null
  if (body.code === 401) return new ApiError('auth', '登录已过期，请重新登录', { code: 401 })
  if (body.code === 403) return new ApiError('forbidden', body.message || '没有权限执行该操作', { code: 403 })
  return new ApiError('business', body.message || '操作失败', { code: body.code, data: body.data })
}

// axios 错误（非 2xx、超时、断网）→ ApiError，文案全部中文
export function fromAxiosError(error) {
  if (error instanceof ApiError) return error
  if (error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError') return new ApiError('canceled', '请求已取消')
  const status = error?.response?.status
  const message = error?.response?.data?.message
  if (status === 401) return new ApiError('auth', '登录已过期，请重新登录', { status })
  if (status === 403) return new ApiError('forbidden', message || '没有权限执行该操作', { status })
  if (status >= 500) return new ApiError('server', '服务暂时不可用，请稍后再试', { status })
  if (status) return new ApiError('http', message || `请求失败（${status}）`, { status })
  if (error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT' || /timeout/i.test(error?.message || '')) {
    return new ApiError('timeout', '请求超时，请稍后重试')
  }
  return new ApiError('network', '网络连接异常，请检查网络后重试')
}
