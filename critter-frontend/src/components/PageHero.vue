<script setup lang="ts">
/** 页头（原型 .hero）：标题 + 说明 + 右侧操作区。 */
defineProps<{
  title: string
  desc?: string
}>()
</script>

<template>
  <div class="hero">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="desc">{{ desc }}</p>
    </div>
    <div class="hero-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.hero-actions {
  display: flex;
  gap: 8px;
  position: relative;
  z-index: 1;
}
</style>
