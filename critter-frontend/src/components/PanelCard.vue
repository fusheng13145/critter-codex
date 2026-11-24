<script setup lang="ts">
/**
 * 内容卡：标题 + 右上角控件区 + 内容。
 * 副标题、口径说明、单位注释一律不在这里承载：单位写进表头，口径写进字段名，
 * 真正的操作（筛选、切换、导出）放进 extra 插槽。
 */
defineProps<{ title?: string }>()
</script>

<template>
  <div class="card">
    <div v-if="title" class="card-head">
      <h2>{{ title }}</h2>
      <slot name="extra" />
    </div>
    <slot />
  </div>
</template>
