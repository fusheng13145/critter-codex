<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadFile } from '@/api/file'
import { BizError } from '@/api/http'
import AuthImage from '@/components/AuthImage.vue'

/**
 * 两步上传的第一步：选文件 → POST /files 拿 fileId。
 * 这里只维护 fileId 数组，业务页面提交时再挂到动物/上报/项目上。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string[]
    max?: number
    bizType?: string
    accept?: string
    hint?: string
  }>(),
  {
    max: 3,
    bizType: 'GENERAL',
    accept: 'image/jpeg,image/png,image/webp',
    hint: '单张不超过 5MB，支持 JPG / PNG / WebP',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
  uploading: [value: boolean]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const errorText = ref('')

const remaining = computed(() => Math.max(0, props.max - props.modelValue.length))

function choose(): void {
  if (uploading.value || remaining.value === 0) return
  inputRef.value?.click()
}

async function onChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length) return

  errorText.value = ''
  const accepted = files.slice(0, remaining.value)
  if (files.length > remaining.value) {
    ElMessage.warning(`最多上传 ${props.max} 个附件，已自动截取前 ${remaining.value} 个`)
  }

  uploading.value = true
  emit('uploading', true)
  const ids = [...props.modelValue]
  try {
    for (const file of accepted) {
      if (file.size > 5 * 1024 * 1024) {
        ElMessage.error(`${file.name} 超过 5MB，已跳过`)
        continue
      }
      const result = await uploadFile(file, props.bizType)
      ids.push(result.fileId)
      emit('update:modelValue', [...ids])
    }
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '附件上传失败，请稍后重试'
  } finally {
    uploading.value = false
    emit('uploading', false)
  }
}

function remove(index: number): void {
  const next = props.modelValue.filter((_, current) => current !== index)
  emit('update:modelValue', next)
}
</script>

<template>
  <div class="uploader">
    <div class="upload-grid">
      <div v-for="(fileId, index) in modelValue" :key="fileId" class="upload-item">
        <AuthImage :file-id="fileId" fit="cover" />
        <button class="remove" type="button" title="移除附件" @click="remove(index)">×</button>
      </div>

      <button
        v-if="remaining > 0"
        class="upload-add"
        type="button"
        :disabled="uploading"
        @click="choose"
      >
        <span>{{ uploading ? '…' : '＋' }}</span>
        <small>{{ uploading ? '上传中' : '添加附件' }}</small>
      </button>
    </div>

    <input
      ref="inputRef"
      class="file-input"
      type="file"
      :accept="accept"
      multiple
      @change="onChange"
    />

    <div class="upload-hint">
      {{ hint }} · 已上传 {{ modelValue.length }}/{{ max }}
    </div>
    <div v-if="errorText" class="field-error">{{ errorText }}</div>
  </div>
</template>

<style scoped>
.uploader {
  width: 100%;
}

.upload-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.upload-item {
  position: relative;
  width: 86px;
  height: 86px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.upload-item .remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: 50%;
  background: color-mix(in srgb, var(--ink) 72%, transparent);
  color: var(--panel);
  cursor: pointer;
  line-height: 1;
}

.upload-add {
  width: 86px;
  height: 86px;
  border: 1px dashed var(--line);
  border-radius: var(--radius-sm);
  background: var(--chip);
  color: var(--muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
}

.upload-add:hover:not(:disabled) {
  border-color: var(--accent-2);
  color: var(--accent);
  background: var(--hover);
}

.upload-add span {
  font-size: 20px;
  line-height: 1;
}

.upload-add small {
  font-size: 11px;
}

.file-input {
  display: none;
}

.upload-hint {
  margin-top: 6px;
  color: var(--muted);
  font-size: 12px;
}
</style>
