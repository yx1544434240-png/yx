import { getUser } from '../utils/admin-session.js'
import { hasRole } from '../router/guards.js'

// v-permission="['ADMIN']"：角色不满足时移除元素。目前只有 ADMIN，先作为统一入口预留（S4）
export const permission = {
  mounted(el, binding) {
    if (!hasRole(getUser()?.role, binding.value)) el.remove()
  }
}
