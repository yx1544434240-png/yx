import test from 'node:test'
import assert from 'node:assert/strict'
import { createPagedQuery, fromQuery, toQuery } from './paged-query-core.js'

const deferred = () => {
  let resolve, reject
  const promise = new Promise((res, rej) => ((resolve = res), (reject = rej)))
  return { promise, resolve, reject }
}

test('filters and paging round-trip through the URL query with only non-default values', () => {
  const defaults = { status: '', orderNo: '' }
  const state = fromQuery({ status: 'WAIT_MANUAL', page: '3', size: '50', other: 'x' }, defaults)
  assert.deepEqual(state, { filters: { status: 'WAIT_MANUAL', orderNo: '' }, pageNum: 3, pageSize: 50 })
  assert.deepEqual(
    toQuery({ other: 'x', page: '3' }, { filters: { status: '', orderNo: 'A1' }, pageNum: 1, pageSize: 20 }, defaults),
    {
      other: 'x',
      orderNo: 'A1'
    }
  )
  assert.deepEqual(fromQuery({ b_page: '2' }, { status: '' }, { prefix: 'b_' }).pageNum, 2)
  assert.equal(fromQuery({ page: '-1', size: '7' }, {}).pageNum, 1)
  assert.equal(fromQuery({ size: '7' }, {}).pageSize, 20)
})

test('only the latest response is applied when requests race', async () => {
  const pending = []
  const list = createPagedQuery({
    fetcher: () => (pending.push(deferred()), pending.at(-1).promise),
    defaults: { status: '' }
  })
  const first = list.load()
  list.filters.status = 'DONE'
  const second = list.search()
  pending[1].resolve({ records: [{ id: 'new' }], total: 1 })
  await second
  pending[0].resolve({ records: [{ id: 'old' }], total: 9 })
  await first
  assert.deepEqual(
    list.rows.value.map(row => row.id),
    ['new']
  )
  assert.equal(list.total.value, 1)
  assert.equal(list.loading.value, false)
})

test('a failed load sets error without clearing rows, and query is written back', async () => {
  let fail = false
  const writes = []
  const list = createPagedQuery({
    fetcher: async params => {
      if (fail) throw new Error('offline')
      return { records: [{ id: params.pageNum }], total: 40 }
    },
    readQuery: () => ({}),
    writeQuery: query => writes.push(query)
  })
  await list.setPage(2)
  assert.deepEqual(writes.at(-1), { page: '2' })
  fail = true
  await list.load()
  assert.equal(list.error.value, true)
  assert.equal(list.rows.value.length, 1)
})

test('an emptied last page falls back to the previous page', async () => {
  const list = createPagedQuery({
    fetcher: async params => (params.pageNum === 3 ? { records: [], total: 40 } : { records: [{ id: 1 }], total: 40 })
  })
  await list.setPage(3)
  assert.equal(list.pageNum.value, 2)
  assert.equal(list.rows.value.length, 1)
})

test('syncFromQuery reloads only when external route query parameters change', async () => {
  let calls = 0
  const list = createPagedQuery({
    fetcher: async params => {
      calls++
      return { records: [{ status: params.status || 'ALL' }], total: 1 }
    },
    defaults: { status: '' }
  })
  await list.load()
  assert.equal(calls, 1)
  // Same query state should be a no-op
  await list.syncFromQuery({})
  assert.equal(calls, 1)
  // External navigation changes query
  await list.syncFromQuery({ status: 'WAIT_MANUAL', page: '2' })
  assert.equal(calls, 2)
  assert.equal(list.filters.status, 'WAIT_MANUAL')
  assert.equal(list.pageNum.value, 2)
})
