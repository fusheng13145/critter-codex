<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/** 二级导航：同一业务域内的页面入口，保持路由链接与权限显隐。 */
interface TabItem {
  label: string
  path: string
  adminOnly?: boolean
  /** 详情页仍高亮父级标签（如 /animals/:code 高亮 /animals） */
  matchPrefix?: boolean
}

const props = defineProps<{
  items: TabItem[]
}>()

const route = useRoute()
const auth = useAuthStore()

const visibleItems = computed(() => props.items.filter((item) => !item.adminOnly || auth.isAdmin))

function isActive(item: TabItem): boolean {
  if (route.path === item.path) return true
  if (item.matchPrefix && item.path !== '/report') {
    return route.path.startsWith(`${item.path}/`)
  }
  return false
}
</script>

<template>
  <nav class="section-tabs" aria-label="页面导航">
    <router-link
      v-for="item in visibleItems"
      :key="item.path"
      :to="item.path"
      class="section-tab"
      :class="{ active: isActive(item) }"
    >
      {{ item.label }}
    </router-link>
  </nav>
</template>

<style scoped>
.section-tabs {
  display: flex;
  flex-direction: row;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 5px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  width: fit-content;
  max-width: 100%;
}

.section-tab {
  padding: 7px 14px;
  border-radius: var(--radius-sm);
  color: var(--muted);
  font-size: 13px;
  white-space: nowrap;
}

.section-tab:hover {
  color: var(--ink);
  background: var(--hover);
}

.section-tab.active {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}
</style>
