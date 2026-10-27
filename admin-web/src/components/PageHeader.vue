<template>
  <div class="page-intro">
    <div>
      <span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span>
      <h1>
        <slot name="title">{{ title }}</slot>
      </h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="intro-actions"><slot name="actions" /></div>
  </div>
</template>

<script setup>
defineProps({
  eyebrow: { type: String, default: '' },
  title: { type: String, default: '' },
  description: { type: String, default: '' }
})
</script>
