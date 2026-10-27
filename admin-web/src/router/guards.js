export const APP_TITLE = '颐邻管理后台'

// 未登录时带上回跳地址，登录后回到原页面（含筛选条件与订单 id）
export function loginGuard(to, isLoggedIn, role = '') {
  // 已登录访问 /login 直接回工作台（RT2）
  if (to.path === '/login') return isLoggedIn ? '/dashboard' : true
  if (isLoggedIn) return hasRole(role, to.meta?.roles) ? true : '/dashboard'
  return to.fullPath && to.fullPath !== '/' ? { path: '/login', query: { redirect: to.fullPath } } : '/login'
}

export function pageTitle(to) {
  return to.meta?.title ? `${to.meta.title} · ${APP_TITLE}` : APP_TITLE
}

// 路由 meta.roles / v-permission 的角色判断；未声明即所有管理员可见（RT5/S4 预留）
export function hasRole(role, roles) {
  if (!roles || !roles.length) return true
  return roles.includes(role)
}
