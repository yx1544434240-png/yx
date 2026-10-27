<template>
  <el-image
    v-if="url"
    :src="url"
    :preview-src-list="effectivePreviewList"
    :initial-index="previewIndex"
    fit="cover"
    class="private-photo"
    :style="sizeStyle"
    preview-teleported
  />
  <span v-else class="photo-placeholder" :style="sizeStyle">{{ failed ? '照片暂不可用' : '照片加载中…' }}</span>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import request from '../utils/request.js'

const props = defineProps({
  source: { type: String, required: true },
  // 尺寸可配置（C7），默认 112×88
  width: { type: Number, default: 112 },
  height: { type: Number, default: 88 },
  // 同组多图预览列表（可选）
  previewList: { type: Array, default: () => [] }
})
const emit = defineEmits(['loaded'])
const PRIVATE_PHOTO =
  /^\/media\/private\/(?:fulfillment\/\d+\/\d+|credentials\/\d+)\/[a-f0-9-]{36}\.(jpg|jpeg|png|webp)$/
const url = ref('')
const failed = ref(false)
const sizeStyle = computed(() => ({ width: `${props.width}px`, height: `${props.height}px` }))
const effectivePreviewList = computed(() =>
  props.previewList?.length ? props.previewList : url.value ? [url.value] : []
)
const previewIndex = computed(() => {
  const idx = effectivePreviewList.value.indexOf(url.value)
  return idx >= 0 ? idx : 0
})
let controller
function clear() {
  controller?.abort()
  if (url.value) {
    URL.revokeObjectURL(url.value)
    emit('loaded', props.source, '')
  }
  url.value = ''
}
// 走统一 axios 封装（C6）：baseURL、超时与 401 重新登录与其它请求一致
watch(
  () => props.source,
  async source => {
    clear()
    failed.value = false
    const current = (controller = new AbortController())
    try {
      if (!PRIVATE_PHOTO.test(source)) throw new Error('invalid photo')
      const blob = await request.get(source, {
        responseType: 'blob',
        signal: current.signal,
        silent: true,
        headers: { 'Cache-Control': 'no-store' }
      })
      if (!blob?.type?.startsWith('image/')) throw new Error('photo unavailable')
      if (!current.signal.aborted) {
        url.value = URL.createObjectURL(blob)
        emit('loaded', source, url.value)
      }
    } catch {
      if (!current.signal.aborted) failed.value = true
    }
  },
  { immediate: true }
)
onBeforeUnmount(clear)
</script>
<style scoped>
.private-photo {
  border-radius: 12px;
  margin: 4px 12px 4px 0;
}
.photo-placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-muted, #f2f5ef);
  color: var(--muted);
  font-size: var(--fs-xs);
  border-radius: 12px;
  margin-right: 12px;
}
</style>
