<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import type { AnimalDetail } from '@/api/types'

/** 档案合并：主档保留，副档只留别名，旧编号仍可解析到主档。 */
const props = defineProps<{
  modelValue: boolean
  animal: AnimalDetail | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const form = reactive({ sourceCodes: '', reason: '' })
const submitting = ref(false)
const errorText = ref('')

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.sourceCodes = ''
    form.reason = ''
    errorText.value = ''
  },
)

function parseCodes(): string[] {
  return form.sourceCodes
    .split(/[\s,，;；]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

async function submit(): Promise<void> {
  if (!props.animal || submitting.value) return
  const codes = parseCodes().filter((code) => code !== props.animal?.code)
  if (!codes.length) {
    ElMessage.warning('请填写至少一个需要并入的副编号')
    return
  }
  if (form.reason.trim().length < 5) {
    ElMessage.warning('合并原因至少 5 个字')
    return
  }
  submitting.value = true
  try {
    const result = await archiveApi.mergeAnimal(props.animal.code, {
      sourceCodes: codes,
      reason: form.reason.trim(),
    })
    ElMessage.success(`已并入 ${result.mergedCodes.length} 个旧编号`)
    emit('saved')
    emit('update:modelValue', false)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '合并失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="合并重复档案"
    width="680px"
    append-to-body
    @update:model-value="emit('update:modelValue', false)"
  >
    <div class="dialog-note">
      主档 <span class="mono">{{ animal?.code }}</span> 保留；副档不会物理删除，旧编号会写入
      <span class="mono">animal_alias</span>，扫码或搜索旧编号仍会回到主档。有生效认养时后端返回 30403。
    </div>

    <div class="field">
      <label>并入的副编号 <b>*</b></label>
      <textarea
        v-model="form.sourceCodes"
        class="textarea"
        rows="3"
        placeholder="多个编号用逗号或换行分隔，例如 CAT-2025-0012、CAT-2025-0019"
      />
    </div>

    <div class="field">
      <label>合并原因 <b>*</b></label>
      <input
        v-model="form.reason"
        class="input"
        maxlength="255"
        placeholder="例如：现场复核确认为同一只猫，至少 5 个字"
      />
    </div>

    <div v-if="errorText" class="notice">{{ errorText }}</div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '合并中…' : '确认合并' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-note {
  margin-bottom: 14px;
  padding: 9px 12px;
  background: var(--chip);
  border-radius: var(--radius-sm);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.field {
  margin-bottom: 14px;
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}
</style>
