<script setup lang="ts">
/**
 * 页面状态容器：加载、失败、空三种分支收口，页面只关心成功态。
 * 文案只在"失败"时出现，因为失败信息本身要可读；
 * 加载与空态用形态表达（骨架条 / 空框），不再各页写一句解释。
 */
defineProps<{
  loading?: boolean
  error?: string
  empty?: boolean
}>()
</script>

<template>
  <div v-if="loading" class="state-skeleton" role="status" aria-label="加载中">
    <i /><i /><i />
  </div>
  <div v-else-if="error" class="notice">{{ error }}</div>
  <div v-else-if="empty" class="state-empty" aria-label="暂无数据">
    <span />
  </div>
  <slot v-else />
</template>

<style scoped>
.state-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 0;
}

.state-skeleton i {
  display: block;
  height: 38px;
  border-radius: var(--radius-sm);
  background: linear-gradient(90deg, var(--chip), var(--hover), var(--chip));
  background-size: 200% 100%;
  animation: sweep 1.4s linear infinite;
}

.state-skeleton i:nth-child(2) {
  width: 86%;
}

.state-skeleton i:nth-child(3) {
  width: 72%;
}

.state-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 96px;
  border: 1px dashed var(--line);
  border-radius: var(--radius);
}

.state-empty span {
  width: 28px;
  height: 2px;
  background: var(--line);
}

@keyframes sweep {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}
</style>
