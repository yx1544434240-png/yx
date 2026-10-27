import request from './request.js'
import { clearSession, updateUser } from './admin-session.js'

// 调用 /auth/current-user 确认 token 仍有效且为管理员；401 由拦截器统一处理
export async function verifySession() {
  try {
    const user = await request.get('/auth/current-user', { silent: true })
    if (user?.role === 'ADMIN') {
      updateUser(user)
      return true
    }
  } catch (error) {
    // 网络/服务异常时不强制登出，交由后续请求处理
    if (error?.kind !== 'auth' && error?.kind !== 'forbidden') return true
  }
  clearSession()
  return false
}
