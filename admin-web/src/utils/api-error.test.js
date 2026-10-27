import test from 'node:test'
import assert from 'node:assert/strict'
import { fromAxiosError, fromBody } from './api-error.js'

test('HTTP 401 and body code 401 both mean the session expired', () => {
  assert.equal(fromAxiosError({ response: { status: 401 } }).kind, 'auth')
  assert.equal(fromBody({ code: 401, message: 'x' }).kind, 'auth')
})

test('403, 5xx, timeout and offline errors get Chinese messages, never raw axios text', () => {
  const forbidden = fromAxiosError({ response: { status: 403 } })
  assert.equal(forbidden.kind, 'forbidden')
  assert.equal(forbidden.message, '没有权限执行该操作')
  assert.equal(fromAxiosError({ response: { status: 502 } }).kind, 'server')
  const timeout = fromAxiosError({ code: 'ECONNABORTED', message: 'timeout of 10000ms exceeded' })
  assert.equal(timeout.kind, 'timeout')
  assert.doesNotMatch(timeout.message, /timeout of/)
  assert.equal(fromAxiosError({ message: 'Network Error' }).kind, 'network')
  assert.equal(fromAxiosError({ code: 'ERR_CANCELED' }).kind, 'canceled')
})

test('business errors keep the server message and code; success returns null', () => {
  const error = fromBody({ code: 409, message: '订单已支付' })
  assert.equal(error.kind, 'business')
  assert.equal(error.code, 409)
  assert.equal(error.message, '订单已支付')
  assert.equal(fromBody({ code: 200, data: 1 }), null)
  assert.equal(fromBody('oops').kind, 'server')
})
