<script setup lang="ts">
import { computed } from 'vue'

/**
 * 纯 CSS 柱状图（原型 .bars）：不引图表库，避免为一个首页图多打包几百 KB。
 * 高度按最大值归一，最小 4% 保证"有数据但很小"时仍可见。
 */
const props = defineProps<{
  points: { label: string; value: number }[]
  /** 从第几个开始换用浅色（原型里近期月份用 accent-2） */
  altFrom?: number
}>()

const max = computed(() => Math.max(1, ...props.points.map((item) => item.value)))

function height(value: number): string {
  return `${Math.max(4, Math.round((value / max.value) * 100))}%`
}
</script>

<template>
  <div class="bars">
    <div v-for="(point, index) in points" :key="point.label" class="bar-wrap">
      <div
        class="bar"
        :class="{ alt: altFrom !== undefined && index >= altFrom }"
        :style="{ height: height(point.value) }"
        :title="`${point.label}：${point.value}`"
      />
      <div class="bar-label">{{ point.label }}</div>
    </div>
  </div>
</template>
