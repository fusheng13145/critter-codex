<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as interactionApi from '@/api/interactions'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { InteractionBizType } from '@/api/types'

const props = withDefaults(defineProps<{
  bizType: InteractionBizType
  bizId: string
  label?: string
}>(), {
  label: '关注',
})

const router = useRouter()
const auth = useAuthStore()

const followId = ref<number | null>(null)
const busy = ref(false)

async function toggle(): Promise<void> {
  if (!auth.isLoggedIn) {
    ElMessage.warning('登录后才能关注')
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  if (busy.value) return
  busy.value = true
  try {
    if (followId.value !== null) {
      await interactionApi.unfollowTarget(followId.value)
      followId.value = null
      ElMessage.success('已取消关注')
      return
    }
    const result = await interactionApi.followTarget(props.bizType, props.bizId)
    followId.value = result.followId
    ElMessage.success(result.newlyCreated ? '已关注，状态变化会通知你' : '你已关注过该对象')
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '关注操作失败')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <button
    class="btn"
    :class="{ ghost: followId !== null }"
    type="button"
    :disabled="busy"
    @click="toggle"
  >
    {{ busy ? '处理中…' : followId !== null ? '已关注' : label }}
  </button>
</template>
