<script setup lang="ts">
/** 指标卡（原型 .metric）：label + num + sub。 */
defineProps<{
  label: string
  value: string
  sub?: string
  subStrong?: string
}>()
</script>

<template>
  <div class="metric">
    <div class="label">{{ label }}</div>
    <div class="num">{{ value }}</div>
    <div class="sub">
      <template v-if="sub">{{ sub }}</template>
      <b v-if="subStrong">{{ subStrong }}</b>
    </div>
  </div>
</template>
