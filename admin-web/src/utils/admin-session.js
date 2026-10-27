import { ref } from 'vue'

// 管理后台会话的唯一读写入口（token 与用户信息）
export const TOKEN_KEY = 'admin_token'
export const USER_KEY = 'admin_user'

const sessionVersion = ref(0)
const defaultStorage = () => (typeof localStorage === 'undefined' ? null : localStorage)

export function getToken(storage = defaultStorage()) {
  void sessionVersion.value
  return storage?.getItem(TOKEN_KEY) || ''
}

export function getUser(storage = defaultStorage()) {
  void sessionVersion.value
  try {
    return JSON.parse(storage?.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export function saveSession({ token, userInfo }, storage = defaultStorage()) {
  storage.setItem(TOKEN_KEY, token)
  storage.setItem(USER_KEY, JSON.stringify(userInfo))
  sessionVersion.value++
}

export function updateUser(userInfo, storage = defaultStorage()) {
  if (!storage || !userInfo) return
  storage.setItem(USER_KEY, JSON.stringify(userInfo))
  sessionVersion.value++
}

export function clearSession(storage = defaultStorage()) {
  storage?.removeItem(TOKEN_KEY)
  storage?.removeItem(USER_KEY)
  sessionVersion.value++
}

export function hasAdminSession(storage = defaultStorage()) {
  try {
    if (storage.getItem(TOKEN_KEY) && JSON.parse(storage.getItem(USER_KEY) || '{}').role === 'ADMIN') return true
  } catch {
    /* malformed cached session */
  }
  clearSession(storage)
  return false
}

// 登录后回跳：只接受站内路径，拒绝 //evil.com、外链和登录页本身
export function safeRedirect(value) {
  const path = Array.isArray(value) ? value[0] : value
  if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//') || path.startsWith('/\\')) return ''
  if (path === '/login' || path.startsWith('/login?')) return ''
  return path
}
