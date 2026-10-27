import { verifySession } from '../utils/verify-session.js'
import { getUser, hasAdminSession } from '../utils/admin-session.js'
import { createRouter, createWebHistory } from 'vue-router'
import { loginGuard, pageTitle } from './guards.js'

const routes = [
  {
    path: '/login',
    name: 'Login',
    meta: { title: '登录' },
    component: () => import('../views/login/index.vue')
  },
  {
    path: '/',
    component: () => import('../views/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'Odometer' }
      },
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('../views/orders/index.vue'),
        meta: { title: '服务订单', icon: 'Document' }
      },
      {
        path: 'dispatch',
        name: 'Dispatch',
        component: () => import('../views/dispatch/index.vue'),
        meta: { title: '智能派单', icon: 'Connection' }
      },
      {
        path: 'elders',
        name: 'Elders',
        component: () => import('../views/elders/index.vue'),
        meta: { title: '老人档案', icon: 'User' }
      },
      {
        path: 'services',
        name: 'Services',
        component: () => import('../views/services/index.vue'),
        meta: { title: '服务项目', icon: 'ShoppingBag' }
      },
      {
        path: 'staff',
        name: 'Staff',
        component: () => import('../views/staff/index.vue'),
        meta: { title: '服务人员', icon: 'UserFilled' }
      },
      {
        path: 'complaints',
        name: 'Complaints',
        component: () => import('../views/complaints/index.vue'),
        meta: { title: '反馈处理', icon: 'ChatLineRound' }
      },
      {
        path: 'operation-logs',
        name: 'OperationLogs',
        component: () => import('../views/operation-logs/index.vue'),
        meta: { title: '操作日志', icon: 'Tickets' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/not-found/index.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 首次进入后台时向后端校验一次 token（RT5），本地缓存的角色仅作为初筛
let verified = false
router.beforeEach(async to => {
  let loggedIn = hasAdminSession(localStorage)
  if (loggedIn && !verified && to.path !== '/login') {
    verified = await verifySession()
    loggedIn = verified
  }
  return loginGuard(to, loggedIn, getUser()?.role)
})
router.afterEach(to => {
  document.title = pageTitle(to)
})

export default router
