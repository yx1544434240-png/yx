<template>
  <el-select
    :model-value="modelValue"
    filterable
    remote
    :remote-method="debouncedSearch"
    :loading="loading"
    placeholder="输入姓名搜索服务人员"
    style="width: 100%"
    @visible-change="open => open && !options.length && search('')"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-option
      v-for="staff in options"
      :key="staff.profile.id"
      :label="label(staff)"
      :value="String(staff.profile.id)"
    />
    <template #empty>
      <p class="staff-select-empty">{{ loading ? '正在搜索…' : '没有找到符合条件的服务人员' }}</p>
    </template>
  </el-select>
</template>

<script setup>
// 远程搜索服务人员（C4）：支持防抖、AbortController 中止及异步 seed 合并
import { onBeforeUnmount, ref, watch } from 'vue'
import request from '../utils/request'
import { statusText } from '../utils/presentation.js'
const props = defineProps({
  modelValue: { type: String, default: null },
  // 预置选项（如派单推荐的人员），保证预选人员能显示姓名
  seed: { type: Array, default: () => [] },
  debounceMs: { type: Number, default: 200 }
})
defineEmits(['update:modelValue'])

function mergeOptions(primary = [], secondary = []) {
  const seen = new Set()
  const merged = []
  for (const item of [...primary, ...secondary]) {
    const id = item?.profile?.id
    if (id == null) continue
    const key = String(id)
    if (seen.has(key)) continue
    seen.add(key)
    merged.push(item)
  }
  return merged
}

const options = ref(mergeOptions(props.seed))
const loading = ref(false)
let seq = 0
let controller = null
let timer = null

watch(
  () => props.seed,
  newSeed => {
    options.value = mergeOptions(options.value, newSeed)
  },
  { deep: true }
)

async function search(name) {
  clearTimeout(timer)
  const id = ++seq
  controller?.abort()
  controller = typeof AbortController === 'undefined' ? null : new AbortController()
  loading.value = true
  try {
    const result = await request.get('/staff/page', {
      params: { pageNum: 1, pageSize: 20, auditStatus: 'APPROVED', name: name || undefined },
      signal: controller?.signal,
      silent: true
    })
    if (id === seq) {
      const records = result?.records || []
      options.value = name ? records : mergeOptions(records, props.seed)
    }
  } catch (err) {
    if (id === seq && err?.kind !== 'canceled') options.value = []
  } finally {
    if (id === seq) loading.value = false
  }
}

function debouncedSearch(name) {
  clearTimeout(timer)
  if (!props.debounceMs) return search(name)
  loading.value = true
  timer = setTimeout(() => {
    search(name)
  }, props.debounceMs)
}

function label(staff) {
  const area = staff.profile.serviceArea || '社区服务'
  return `${staff.user?.realName || '服务人员'} · ${area} · ${statusText('staffWork', staff.profile.currentStatus)}`
}

onBeforeUnmount(() => {
  seq++
  clearTimeout(timer)
  controller?.abort()
})

defineExpose({ search, debouncedSearch, options })
</script>

<style scoped>
.staff-select-empty {
  padding: 12px;
  color: var(--muted);
  font-size: 13px;
  text-align: center;
}
</style>
