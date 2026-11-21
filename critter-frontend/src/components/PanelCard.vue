<script setup lang="ts">
/** 内容卡（原型 .card）：标题 + 右上角辅助信息 + 默认插槽。 */
defineProps<{
  title?: string
  more?: string
}>()
</script>

<template>
  <div class="card">
    <div v-if="title" class="card-head">
      <h2>{{ title }}</h2>
      <span v-if="more" class="more">{{ more }}</span>
      <slot name="extra" />
    </div>
    <slot />
  </div>
</template>
