import test from 'node:test'
import assert from 'node:assert/strict'
import { computed } from 'vue'
import {
  hasAdminSession,
  safeRedirect,
  saveSession,
  updateUser,
  getToken,
  getUser,
  clearSession
} from './admin-session.js'

test('admin route accepts only an explicit admin session and clears malformed or non-admin sessions', () => {
  for (const [token, user, allowed] of [
    ['token', '{"role":"ADMIN"}', true],
    ['token', '{"role":"RESIDENT"}', false],
    ['token', '{}', false],
    ['token', 'invalid-json', false],
    ['', '{"role":"ADMIN"}', false]
  ]) {
    const values = new Map([
      ['admin_token', token],
      ['admin_user', user]
    ])
    const storage = { getItem: key => values.get(key), removeItem: key => values.delete(key) }
    assert.equal(hasAdminSession(storage), allowed)
    assert.equal(values.has('admin_token'), allowed)
  }
})

test('redirect after login only accepts in-site paths', () => {
  assert.equal(safeRedirect('/orders?status=WAIT_MANUAL'), '/orders?status=WAIT_MANUAL')
  for (const bad of ['//evil.com', 'https://evil.com', '/login', '/login?redirect=/x', '/\\evil', undefined, 5])
    assert.equal(safeRedirect(bad), '')
  assert.equal(safeRedirect(['/elders']), '/elders')
})

test('session helpers save, update, read and clear in one place with Vue reactivity', () => {
  const values = new Map()
  const storage = {
    getItem: k => values.get(k) ?? null,
    setItem: (k, v) => values.set(k, v),
    removeItem: k => values.delete(k)
  }
  const reactiveName = computed(() => getUser(storage)?.realName || '未登录')
  assert.equal(reactiveName.value, '未登录')

  saveSession({ token: 't', userInfo: { role: 'ADMIN', realName: '王' } }, storage)
  assert.equal(getToken(storage), 't')
  assert.equal(getUser(storage).realName, '王')
  assert.equal(reactiveName.value, '王')

  updateUser({ role: 'ADMIN', realName: '王主任' }, storage)
  assert.equal(reactiveName.value, '王主任')

  clearSession(storage)
  assert.equal(getToken(storage), '')
  assert.equal(getUser(storage), null)
  assert.equal(reactiveName.value, '未登录')
})
