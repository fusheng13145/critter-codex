<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import type { GroupDetail } from '@/api/types'

/** 群体合并：主群体保留，副群体置 GONE 并记录 mergedIntoCode，目击迁主。 */
const props = defineProps<{
  modelValue: boolean
  group: GroupDetail | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [mainCode: string]
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

async function submit(): Promise<void> {
  if (!props.group || submitting.value) return
  const codes = form.sourceCodes
    .split(/[\s,，;；]+/)
    .map((item) => item.trim())
    .filter((code) => code && code !== props.group?.groupCode)
  if (!codes.length) {
    ElMessage.warning('请填写至少一个需要并入的副群体编号')
    return
  }
  if (form.reason.trim().length < 5) {
    ElMessage.warning('合并原因至少 5 个字')
    return
  }
  submitting.value = true
  try {
    const result = await archiveApi.mergeGroups({
      mainCode: props.group.groupCode,
      sourceCodes: codes,
      reason: form.reason.trim(),
    })
    ElMessage.success(`已合并 ${result.mergedCodes.length} 个副群体，迁移目击 ${result.movedSightings} 条`)
    emit('saved', result.mainCode)
    emit('update:modelValue', false)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '群体合并失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="合并群体"
    width="660px"
    append-to-body
    @update:model-value="emit('update:modelValue', false)"
  >
    <div class="dialog-note">
      主群体 <span class="mono">{{ group?.groupCode }}</span>
    </div>
    <div class="field">
      <label>并入的副群体编号 <b>*</b></label>
      <textarea v-model="form.sourceCodes" class="textarea" rows="3" placeholder="多个编号用逗号或换行分隔" />
    </div>
    <div class="field">
      <label>合并原因 <b>*</b></label>
      <input v-model="form.reason" class="input" maxlength="255" placeholder="至少 5 个字" />
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
