import { reactive, ref } from 'vue'

// URL query <-> 筛选条件/分页（RT4）。prefix 用于同一页面上的第二个列表（如老人页的家属审核）。
export function fromQuery(query = {}, defaults = {}, { prefix = '', pageSize = 20 } = {}) {
  const pick = key => {
    const value = query[prefix + key]
    return Array.isArray(value) ? value[0] : value
  }
  const filters = {}
  for (const [key, fallback] of Object.entries(defaults)) {
    const value = pick(key)
    filters[key] = value === undefined || value === null ? fallback : String(value)
  }
  const page = Number.parseInt(pick('page'), 10)
  const size = Number.parseInt(pick('size'), 10)
  return {
    filters,
    pageNum: page > 0 ? page : 1,
    pageSize: [10, 20, 50, 100].includes(size) ? size : pageSize
  }
}

export function toQuery(current = {}, state, defaults = {}, { prefix = '', pageSize = 20 } = {}) {
  const next = { ...current }
  for (const key of Object.keys(defaults)) {
    const value = state.filters[key]
    if (value === '' || value === null || value === undefined || value === defaults[key]) delete next[prefix + key]
    else next[prefix + key] = String(value)
  }
  if (state.pageNum > 1) next[prefix + 'page'] = String(state.pageNum)
  else delete next[prefix + 'page']
  if (state.pageSize !== pageSize) next[prefix + 'size'] = String(state.pageSize)
  else delete next[prefix + 'size']
  return next
}

function sameQuery(a = {}, b = {}) {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)])
  for (const key of keys) if (String(a[key] ?? '') !== String(b[key] ?? '')) return false
  return true
}

/**
 * 分页列表的通用状态机（C1/R4/R5）：
 * - 只接受最后一次请求的结果（竞态保护），新请求会 abort 上一个
 * - 失败时 error=true，保留旧数据
 * - readQuery/writeQuery 由调用方注入（组件里用 vue-router），便于单测
 */
export function createPagedQuery({ fetcher, defaults = {}, pageSize = 20, prefix = '', readQuery, writeQuery }) {
  const initial = fromQuery(readQuery?.() || {}, defaults, { prefix, pageSize })
  const filters = reactive({ ...initial.filters })
  const pageNum = ref(initial.pageNum)
  const size = ref(initial.pageSize)
  const rows = ref([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref(false)
  let seq = 0
  let controller = null

  function params() {
    const result = { pageNum: pageNum.value, pageSize: size.value }
    for (const [key, value] of Object.entries(filters))
      if (value !== '' && value !== null && value !== undefined) result[key] = value
    return result
  }

  function syncQuery() {
    if (!writeQuery) return
    const current = readQuery?.() || {}
    const next = toQuery(current, { filters, pageNum: pageNum.value, pageSize: size.value }, defaults, {
      prefix,
      pageSize
    })
    if (!sameQuery(current, next)) writeQuery(next)
  }

  async function load() {
    const id = ++seq
    controller?.abort()
    controller = typeof AbortController === 'undefined' ? null : new AbortController()
    loading.value = true
    error.value = false
    syncQuery()
    try {
      const result = await fetcher(params(), { signal: controller?.signal })
      if (id !== seq) return
      rows.value = result?.records || []
      total.value = Number(result?.total || 0)
      // 删除最后一页的最后一条后，自动退回上一页
      if (!rows.value.length && pageNum.value > 1 && total.value > 0) {
        pageNum.value = Math.max(1, Math.ceil(total.value / size.value))
        return load()
      }
    } catch (err) {
      if (id !== seq || err?.kind === 'canceled') return
      error.value = true
    } finally {
      if (id === seq) loading.value = false
    }
  }

  function syncFromQuery(query = readQuery?.() || {}) {
    const next = fromQuery(query, defaults, { prefix, pageSize })
    let changed = false
    for (const [key, value] of Object.entries(next.filters)) {
      if (filters[key] !== value) {
        filters[key] = value
        changed = true
      }
    }
    if (pageNum.value !== next.pageNum) {
      pageNum.value = next.pageNum
      changed = true
    }
    if (size.value !== next.pageSize) {
      size.value = next.pageSize
      changed = true
    }
    if (changed) return load()
    return Promise.resolve()
  }

  function search() {
    pageNum.value = 1
    return load()
  }

  function reset() {
    Object.assign(filters, defaults)
    return search()
  }

  function setPage(value) {
    pageNum.value = value
    return load()
  }

  function setPageSize(value) {
    size.value = value
    pageNum.value = 1
    return load()
  }

  function dispose() {
    seq++
    controller?.abort()
  }

  return {
    filters,
    pageNum,
    pageSize: size,
    rows,
    total,
    loading,
    error,
    load,
    syncFromQuery,
    search,
    reset,
    setPage,
    setPageSize,
    dispose
  }
}
