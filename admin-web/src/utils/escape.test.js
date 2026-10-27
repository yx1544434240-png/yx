import test from 'node:test'
import assert from 'node:assert/strict'
import { escapeHtml } from './escape.js'

test('escapeHtml escapes markup and tolerates empty values', () => {
  assert.equal(escapeHtml('<img src=x onerror="a">&\''), '&lt;img src=x onerror=&quot;a&quot;&gt;&amp;&#39;')
  assert.equal(escapeHtml(null), '')
})
