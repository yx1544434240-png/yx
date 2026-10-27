import { ref } from 'vue'

// 防重复提交（R3）：同一个 action 执行中再次调用直接忽略
export function useSubmit(action) {
  const pending = ref(false)
  async function run(...args) {
    if (pending.value) return undefined
    pending.value = true
    try {
      return await action(...args)
    } finally {
      pending.value = false
    }
  }
  return { pending, run }
}

// 按 key（如行 id）区分的防重复：同一行的操作执行中不会再次触发
export function useRowSubmit(action) {
  const pendingKey = ref(null)
  async function run(key, ...args) {
    if (pendingKey.value !== null) return undefined
    pendingKey.value = key
    try {
      return await action(key, ...args)
    } finally {
      pendingKey.value = null
    }
  }
  return { pendingKey, run }
}
