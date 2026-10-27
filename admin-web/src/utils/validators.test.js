import test from 'node:test'
import assert from 'node:assert/strict'
import { check, isTimeAfter, maxLength, phoneRule, required } from './validators.js'

test('phone rule accepts mobile and landline, rejects malformed', async () => {
  assert.equal(await check(phoneRule('联系电话'), '13900001111'), '')
  assert.equal(await check(phoneRule('联系电话'), '010-66551234'), '')
  assert.equal(await check(phoneRule('联系电话'), '1390000'), '联系电话格式不正确')
  assert.equal(await check(phoneRule('联系电话'), ''), '请填写联系电话')
  assert.equal(await check(phoneRule('联系电话', { optional: true }), ''), '')
})

test('required and max length rules', async () => {
  assert.equal(await check(required('请填写姓名'), '  '), '请填写姓名')
  assert.equal(await check(maxLength(4, '姓名'), '一二三四五'), '姓名不超过4字')
  assert.equal(isTimeAfter('18:00:00', '08:00:00'), true)
  assert.equal(isTimeAfter('08:00:00', '18:00:00'), false)
})
