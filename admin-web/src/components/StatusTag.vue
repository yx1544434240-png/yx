<template>
  <el-tag :type="statusTag(kind, value)" :size="size">{{ statusText(kind, value) }}</el-tag>
</template>

<script setup>
import { statusTag, statusText } from '../utils/presentation.js'
defineProps({
  kind: { type: String, required: true },
  value: { type: String, default: '' },
  size: { type: String, default: 'default' }
})
</script>
