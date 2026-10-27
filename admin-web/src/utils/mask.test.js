import test from 'node:test'
import assert from 'node:assert/strict'
import { maskIdCard, maskPhone } from './mask.js'

test('phone numbers keep only the first 3 and last 4 digits', () => {
  assert.equal(maskPhone('13900001111'), '139****1111')
  assert.equal(maskPhone('010-6655 1234'), '***-**** 1234')
  assert.equal(maskPhone('12345'), '*****')
  assert.equal(maskPhone(''), '')
  assert.equal(maskPhone(null), '')
})

test('id cards keep only the first 3 and last 4 characters', () => {
  assert.equal(maskIdCard('110101194611152345'), '110***********2345')
  assert.equal(maskIdCard(''), '')
})
