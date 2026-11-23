<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import type { AnimalDetail } from '@/api/types'

/** 二维码预览与打印：二维码接口要鉴权，因此走 Blob 而不是直接 img src。 */
const props = defineProps<{
  modelValue: boolean
  animal: AnimalDetail | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const objectUrl = ref('')
const loading = ref(false)
const errorText = ref('')

function revoke(): void {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
}

async function load(): Promise<void> {
  if (!props.animal) return
  loading.value = true
  errorText.value = ''
  revoke()
  try {
    const blob = await archiveApi.fetchAnimalQrCode(props.animal.code)
    objectUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '二维码生成失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      load()
    } else {
      revoke()
    }
  },
)

onBeforeUnmount(revoke)

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function printCard(): void {
  if (!objectUrl.value || !props.animal) return
  const win = window.open('', '_blank', 'width=520,height=680')
  if (!win) {
    ElMessage.warning('浏览器拦截了打印窗口，请允许弹出窗口后重试')
    return
  }
  const title = escapeHtml(props.animal.name || props.animal.code)
  const code = escapeHtml(props.animal.code)
  win.document.write(`<!doctype html><html><head><title>档案二维码 ${code}</title>
    <style>
      body { font-family: "Microsoft YaHei", sans-serif; text-align: center; padding: 36px 24px; color: #20313a; }
      h1 { font-size: 20px; margin: 0 0 6px; }
      .code { color: #6b7f87; font-size: 13px; letter-spacing: .08em; }
      img { width: 300px; height: 300px; margin: 22px auto 10px; display: block; }
      .hint { font-size: 12px; color: #6b7f87; margin-top: 18px; }
    </style></head><body>
      <h1>${title}</h1>
      <div class="code">${code}</div>
      <img src="${objectUrl.value}" alt="档案二维码" />
      <div class="hint">扫码查看青檐万灵册公开档案</div>
      <script>window.onload = function () { window.print(); }<\/script>
    </body></html>`)
  win.document.close()
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="档案二维码"
    width="520px"
    append-to-body
    @update:model-value="emit('update:modelValue', false)"
  >
    <div v-if="errorText" class="notice">{{ errorText }}</div>
    <div class="qr-wrap">
      <div v-if="loading" class="muted">正在生成二维码…</div>
      <img v-else-if="objectUrl" :src="objectUrl" alt="档案二维码" class="qr-image" />
      <div v-else class="muted">暂无二维码</div>
      <div class="qr-meta">
        <b>{{ animal?.name || animal?.code }}</b>
        <span class="mono">{{ animal?.code }}</span>
      </div>
      <p class="muted qr-hint">
        二维码内容为公开扫码页 <span class="mono">/p/{{ animal?.code }}</span>，可直接贴牌打印。
      </p>
    </div>
    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">关闭</button>
      <button class="btn" type="button" :disabled="!objectUrl" @click="printCard">打印贴牌</button>
    </template>
  </el-dialog>
</template>

<style scoped>
.qr-wrap {
  text-align: center;
  padding: 4px 0 8px;
}

.qr-image {
  width: 260px;
  height: 260px;
  margin: 0 auto 12px;
  display: block;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 10px;
  background: #fff;
}

.qr-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 14px;
}

.qr-meta .mono {
  color: var(--muted);
  font-size: 12px;
}

.qr-hint {
  margin-top: 10px;
  font-size: 12px;
}
</style>
