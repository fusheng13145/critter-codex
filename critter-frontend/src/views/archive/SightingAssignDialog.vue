<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import { dateTime } from '@/utils/format'
import type { GroupSummary, SightingView } from '@/api/types'

/** 待归属目击指派：可指定群体或重新标记待归属。 */
const props = defineProps<{
  modelValue: boolean
  sighting: SightingView | null
  groups: GroupSummary[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const form = reactive({ groupCode: '', reason: '' })
const submitting = ref(false)
const errorText = ref('')

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.groupCode = ''
    form.reason = '指派待归属目击'
    errorText.value = ''
  },
)

async function submit(): Promise<void> {
  if (!props.sighting || submitting.value) return
  if (!form.groupCode) {
    ElMessage.warning('请选择目标群体')
    return
  }
  if (form.reason.trim().length < 1) {
    ElMessage.warning('请填写指派说明')
    return
  }
  submitting.value = true
  try {
    await archiveApi.assignSighting(props.sighting.id, {
      groupCode: form.groupCode,
      reason: form.reason.trim(),
    })
    ElMessage.success('目击已指派到群体')
    emit('saved')
    emit('update:modelValue', false)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '指派失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="指派待归属目击"
    width="560px"
    append-to-body
    @update:model-value="emit('update:modelValue', false)"
  >
    <div class="dialog-note">
      目击 #{{ sighting?.id }} · {{ sighting?.point?.name }} · {{ dateTime(sighting?.seenAt) }}
      <span v-if="sighting?.countEstimate"> · 约 {{ sighting.countEstimate }} 只</span>
    </div>
    <div class="field">
      <label>目标群体 <b>*</b></label>
      <select v-model="form.groupCode" class="select mono">
        <option value="" disabled>选择群体</option>
        <option
          v-for="group in groups"
          :key="group.groupCode"
          :value="group.groupCode"
          :disabled="group.status === 'GONE'"
        >
          {{ group.groupCode }} · {{ group.species.commonName }} · {{ group.features }}
        </option>
      </select>
    </div>
    <div class="field">
      <label>指派说明</label>
      <input v-model="form.reason" class="input" maxlength="255" />
    </div>
    <div v-if="errorText" class="notice">{{ errorText }}</div>
    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '指派中…' : '确认指派' }}
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
}

.field {
  margin-bottom: 14px;
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}
</style>
