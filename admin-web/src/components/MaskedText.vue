<template>
  <span v-if="value" class="masked-text">
    <span class="masked-value">{{ revealed ? value : masked }}</span>
    <el-button
      link
      type="primary"
      size="small"
      class="masked-toggle"
      :aria-label="revealed ? '隐藏' + label : '显示完整' + label"
      @click="revealed = !revealed"
    >
      {{ revealed ? '隐藏' : '显示' }}
    </el-button>
  </span>
  <span v-else class="masked-empty">{{ placeholder }}</span>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { maskIdCard, maskPhone } from '../utils/mask.js'
const props = defineProps({
  value: { type: String, default: '' },
  type: { type: String, default: 'phone' },
  placeholder: { type: String, default: '—' }
})
const revealed = ref(false)
const masked = computed(() => (props.type === 'idcard' ? maskIdCard(props.value) : maskPhone(props.value)))
const label = computed(() => (props.type === 'idcard' ? '证件号' : '电话'))
watch(
  () => props.value,
  () => (revealed.value = false)
)
</script>

<style scoped>
.masked-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-variant-numeric: tabular-nums;
}
.masked-toggle {
  min-height: 24px;
  padding: 0 4px;
}
.masked-empty {
  color: var(--muted);
}
</style>
