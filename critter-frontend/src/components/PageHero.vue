<script setup lang="ts">
/**
 * 页头：标题 + 右侧操作区，只承担这两件事。
 * 不给说明文字留入口——页面如果需要一句话才看得懂，那是布局没表达清楚，
 * 应当改表头、字段标签或分组，而不是往这里补文案。
 */
defineProps<{ title: string }>()
</script>

<template>
  <div class="page-head">
    <h1>{{ title }}</h1>
    <div class="page-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-actions {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
}
</style>
