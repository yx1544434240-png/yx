import test from 'node:test'
import assert from 'node:assert/strict'
import { useRowSubmit, useSubmit } from './useSubmit.js'

test('double submit runs the action only once and resets after failure', async () => {
  let calls = 0
  let release
  const { run, pending } = useSubmit(() => {
    calls++
    if (calls > 1) return Promise.resolve()
    return new Promise((_, reject) => (release = reject))
  })
  const first = run().catch(() => {})
  run()
  assert.equal(calls, 1)
  assert.equal(pending.value, true)
  release(new Error('x'))
  await first
  assert.equal(pending.value, false)
  await run().catch(() => {})
  assert.equal(calls, 2)
})

test('row submit remembers which row is busy', async () => {
  const seen = []
  const { run, pendingKey } = useRowSubmit(async id => seen.push(id))
  const task = run(7)
  assert.equal(pendingKey.value, 7)
  run(8)
  await task
  assert.deepEqual(seen, [7])
  assert.equal(pendingKey.value, null)
})
