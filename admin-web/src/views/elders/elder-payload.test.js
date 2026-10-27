import test from 'node:test'
import assert from 'node:assert/strict'
import { pickElderPayload } from './elder-payload.js'

test('elder save payload never carries idCard or server-only fields', () => {
  const payload = pickElderPayload({
    id: 1,
    name: ' 钱桂芳 ',
    idCard: '110101194611152345',
    idCardMasked: '110***********2345',
    status: 1,
    createdAt: 'x',
    phone: '13900001111'
  })
  assert.deepEqual(payload, { id: 1, name: '钱桂芳', phone: '13900001111' })
})
