<script setup lang="ts">
/**
 * 页面状态容器：把"加载中 / 请求失败 / 暂无数据"三种分支收口，
 * 页面只关心成功态，避免每个列表页重复写三层 v-if。
 */
defineProps<{
  loading?: boolean
  error?: string
  empty?: boolean
  loadingText?: string
  emptyText?: string
}>()
</script>

<template>
  <div v-if="loading" class="page-state" role="status">
    <span class="pulse" />
    <span>{{ loadingText ?? '正在读取数据…' }}</span>
  </div>
  <div v-else-if="error" class="notice">{{ error }}</div>
  <div v-else-if="empty" class="page-state">
    <span class="ring" />
    <span>{{ emptyText ?? '暂无数据' }}</span>
  </div>
  <slot v-else />
</template>

<style scoped>
.page-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 120px;
  padding: 24px;
  color: var(--muted);
  background: var(--panel);
  border: 1px dashed var(--line);
  border-radius: var(--radius);
  font-size: 13px;
}

.pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  animation: pulse 1.1s ease-in-out infinite;
}

.ring {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid var(--line);
  border-top-color: var(--accent-2);
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.9);
  }
  50% {
    opacity: 1;
    transform: scale(1.15);
  }
}
</style>
