<script setup lang="ts">
import { computed } from 'vue'
import { statusTone } from '@/utils/domain'

/** 状态标签：颜色只表达风险，文案由调用方传入，避免各页各写一套枚举映射。 */
const props = defineProps<{
  code?: string | null
  label: string
  tone?: '' | 'warn' | 'danger' | 'gray'
}>()

const tagClass = computed(() => props.tone ?? statusTone(props.code))
</script>

<template>
  <span class="tag" :class="tagClass">{{ label }}</span>
</template>
