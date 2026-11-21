<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { getBlob } from '@/api/http'

/**
 * 带鉴权的图片。
 * 访问令牌只存在内存，<img src> 不会自动带 Authorization；
 * 因此这里用 axios 取 Blob，再转 objectURL，避免把私有附件暴露成公开直链。
 */
const props = withDefaults(
  defineProps<{
    fileId?: string | null
    alt?: string
    fit?: 'cover' | 'contain'
  }>(),
  { fileId: null, alt: '附件', fit: 'cover' },
)

const url = ref('')
const loading = ref(false)
const failed = ref(false)
let objectUrl = ''

function revoke(): void {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
}

async function load(): Promise<void> {
  revoke()
  url.value = ''
  failed.value = false
  if (!props.fileId) return
  loading.value = true
  try {
    const blob = await getBlob(`/files/${props.fileId}/content`)
    objectUrl = URL.createObjectURL(blob)
    url.value = objectUrl
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

watch(() => props.fileId, load, { immediate: true })
onUnmounted(revoke)
</script>

<template>
  <div class="auth-image" :class="{ loading }">
    <img v-if="url" :src="url" :alt="alt" :style="{ objectFit: fit }" />
    <span v-else class="placeholder">{{ loading ? '···' : failed ? '附件' : '无图' }}</span>
  </div>
</template>

<style scoped>
.auth-image {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--chip);
  color: var(--muted);
}

.auth-image img {
  width: 100%;
  height: 100%;
  display: block;
}

.placeholder {
  font-size: 11px;
  letter-spacing: 1px;
}

.loading .placeholder {
  animation: fade 1s ease-in-out infinite;
}

@keyframes fade {
  50% {
    opacity: 0.35;
  }
}
</style>
