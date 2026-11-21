<script setup lang="ts">
import { computed, ref } from 'vue'
import AuthImage from '@/components/AuthImage.vue'

/** 附件缩略图组：点开看大图，PDF 等非图片附件保留文件名占位。 */
const props = withDefaults(
  defineProps<{
    fileIds: string[]
    emptyText?: string
    size?: number
  }>(),
  { emptyText: '未上传附件', size: 76 },
)

const preview = ref<string | null>(null)
const dialogVisible = computed({
  get: () => preview.value !== null,
  set: (value: boolean) => {
    if (!value) preview.value = null
  },
})

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(auto-fill, minmax(${props.size}px, 1fr))`,
}))
</script>

<template>
  <div v-if="fileIds.length" class="photo-grid" :style="gridStyle">
    <button
      v-for="fileId in fileIds"
      :key="fileId"
      class="thumb"
      type="button"
      :title="fileId"
      @click="preview = fileId"
    >
      <AuthImage :file-id="fileId" fit="cover" />
    </button>
  </div>
  <div v-else class="muted photo-empty">{{ emptyText }}</div>

  <el-dialog v-model="dialogVisible" title="附件预览" width="720px" append-to-body>
    <div class="preview-box">
      <AuthImage v-if="preview" :file-id="preview" fit="contain" />
    </div>
    <template #footer>
      <span class="mono">{{ preview }}</span>
    </template>
  </el-dialog>
</template>

<style scoped>
.photo-grid {
  display: grid;
  gap: 8px;
}

.thumb {
  width: 100%;
  aspect-ratio: 1;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--chip);
  cursor: zoom-in;
  transition: 0.15s;
}

.thumb:hover {
  border-color: var(--accent-2);
  transform: translateY(-1px);
}

.photo-empty {
  font-size: 12px;
}

.preview-box {
  height: min(68vh, 620px);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--chip);
}
</style>
