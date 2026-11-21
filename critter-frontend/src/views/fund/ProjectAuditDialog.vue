<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as fundApi from '@/api/fund'
import { BizError } from '@/api/http'

/** 项目审核：通过转 FUNDING，驳回必须填理由；审核结果由后端状态机决定。 */
const props = defineProps<{
  modelValue: boolean
  projectNo: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  audited: []
}>()

const form = reactive({
  decision: 'APPROVE' as 'APPROVE' | 'REJECT',
  reason: '',
})

const submitting = ref(false)
const errors = reactive<Record<string, string>>({})

function reset(): void {
  form.decision = 'APPROVE'
  form.reason = ''
  Object.keys(errors).forEach((key) => delete errors[key])
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) reset()
  },
)

function validate(): boolean {
  Object.keys(errors).forEach((key) => delete errors[key])
  if (form.decision === 'REJECT' && (!form.reason.trim() || form.reason.trim().length < 5)) {
    errors.reason = '驳回理由至少 5 个字'
  }
  if (form.reason.length > 255) errors.reason = '驳回理由最多 255 个字'
  return Object.keys(errors).length === 0
}

async function submit(): Promise<void> {
  if (submitting.value || !validate()) return
  submitting.value = true
  try {
    await fundApi.auditProject(props.projectNo, {
      decision: form.decision,
      reason: form.reason.trim() || undefined,
    })
    ElMessage.success(form.decision === 'APPROVE' ? '项目已通过审核，进入募捐中' : '项目已驳回')
    emit('audited')
    emit('update:modelValue', false)
  } catch (error) {
    if (error instanceof BizError) {
      for (const item of error.fieldErrors) errors[item.field] = item.reason
      ElMessage.error(error.message)
    } else {
      ElMessage.error('审核失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="项目审核"
    width="520px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="field">
      <label>审核结论</label>
      <div class="decision-row">
        <button
          class="decision"
          :class="{ on: form.decision === 'APPROVE' }"
          type="button"
          @click="form.decision = 'APPROVE'"
        >
          通过并上架
        </button>
        <button
          class="decision danger"
          :class="{ on: form.decision === 'REJECT' }"
          type="button"
          @click="form.decision = 'REJECT'"
        >
          驳回
        </button>
      </div>
    </div>

    <div class="field">
      <label>审核意见 {{ form.decision === 'REJECT' ? '（必填）' : '（可选）' }}</label>
      <textarea
        v-model="form.reason"
        class="textarea"
        maxlength="255"
        placeholder="通过时可填写备注；驳回时必须说明原因，至少 5 个字"
        :class="{ invalid: errors.reason }"
      />
      <div v-if="errors.reason" class="field-error">{{ errors.reason }}</div>
    </div>

    <div class="notice">
      通过后项目状态变为 FUNDING，公开页可见；驳回后管理员可修改并重新提交。
    </div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '确认审核' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.decision-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.decision {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--muted);
  cursor: pointer;
}

.decision.on {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.decision.danger.on {
  border-color: var(--danger);
  background: var(--danger-soft);
  color: var(--danger);
}
</style>
