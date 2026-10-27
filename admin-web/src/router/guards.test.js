import test from 'node:test'
import assert from 'node:assert/strict'
import { hasRole, loginGuard, pageTitle } from './guards.js'

test('unauthenticated visits keep the full path as redirect', () => {
  assert.deepEqual(loginGuard({ path: '/orders', fullPath: '/orders?orderId=9' }, false), {
    path: '/login',
    query: { redirect: '/orders?orderId=9' }
  })
  assert.equal(loginGuard({ path: '/', fullPath: '/' }, false), '/login')
  assert.equal(loginGuard({ path: '/login', fullPath: '/login' }, false), true)
  assert.equal(loginGuard({ path: '/orders', fullPath: '/orders' }, true), true)
})

test('page titles follow route meta', () => {
  assert.equal(pageTitle({ meta: { title: '服务订单' } }), '服务订单 · 颐邻管理后台')
  assert.equal(pageTitle({ meta: {} }), '颐邻管理后台')
})

test('logged-in users skip the login page and role-gated routes', () => {
  assert.equal(loginGuard({ path: '/login', fullPath: '/login' }, true), '/dashboard')
  assert.equal(loginGuard({ path: '/x', fullPath: '/x', meta: { roles: ['SUPER'] } }, true, 'ADMIN'), '/dashboard')
  assert.equal(loginGuard({ path: '/x', fullPath: '/x', meta: { roles: ['ADMIN'] } }, true, 'ADMIN'), true)
  assert.equal(hasRole('ADMIN', undefined), true)
  assert.equal(hasRole('STAFF', ['ADMIN']), false)
})
