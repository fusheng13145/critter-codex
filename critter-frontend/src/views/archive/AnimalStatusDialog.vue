<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import { animalStatusLabel } from '@/utils/domain'
import type { AnimalDetail, AnimalStatusCode } from '@/api/types'

/** 动物状态迁移：只能选择后端允许的目标状态，原因写入 animal_status_log。 */
const props = defineProps<{
  modelValue: boolean
  animal: AnimalDetail | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const form = reactive({
  targetStatus: '' as AnimalStatusCode | '',
  reason: '',
})
const submitting = ref(false)
const errorText = ref('')

const targets = computed(() => props.animal?.allowedStatuses ?? [])
const preview = computed(() =>
  form.targetStatus ? animalStatusLabel(form.targetStatus) : '选择目标状态',
)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.targetStatus = props.animal?.allowedStatuses?.[0] ?? ''
    form.reason = ''
    errorText.value = ''
  },
)

async function submit(): Promise<void> {
  const animal = props.animal
  if (!animal) return
  errorText.value = ''
  if (!form.targetStatus) {
    errorText.value = '请选择目标状态'
    return
  }
  if (form.reason.trim().length < 5) {
    errorText.value = '请填写至少 5 个字的迁移原因'
    return
  }
  submitting.value = true
  try {
    await archiveApi.changeAnimalStatus(animal.code, {
      targetStatus: form.targetStatus,
      reason: form.reason.trim(),
    })
    ElMessage.success(`状态已更新为「${animalStatusLabel(form.targetStatus)}」`)
    emit('update:modelValue', false)
    emit('saved')
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '状态迁移失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="迁移动物状态"
    width="560px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="dialog-note">
      当前状态：<b>{{ animal ? animalStatusLabel(animal.status) : '—' }}</b>
      · 编号 <span class="mono">{{ animal?.code }}</span>
      · 迁移会写入不可篡改的状态日志。
    </div>

    <div v-if="!targets.length" class="notice">
      当前状态没有可用的下一步；终态不可再迁移。
    </div>

    <template v-else>
      <div class="field">
        <label>目标状态 <b>*</b></label>
        <select v-model="form.targetStatus" class="select">
          <option v-for="status in targets" :key="status" :value="status">
            {{ animalStatusLabel(status) }}
          </option>
        </select>
      </div>

      <div v-if="form.targetStatus === 'RELEASED'" class="notice" style="margin-top: 12px">
        转「已放归」要求该个体已有绝育/TNR 病史记录，否则后端会拒绝迁移。
      </div>

      <div class="field" style="margin-top: 14px">
        <label>迁移原因 <b>*</b></label>
        <textarea
          v-model="form.reason"
          class="textarea"
          maxlength="255"
          placeholder="例如：术后恢复良好，放归原点位，至少 5 个字"
        />
      </div>

      <div v-if="errorText" class="notice danger" style="margin-top: 12px">{{ errorText }}</div>

      <div class="preview">
        迁移预览：{{ animal ? animalStatusLabel(animal.status) : '—' }}
        <span class="arrow">→</span>
        <b>{{ preview }}</b>
      </div>
    </template>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button v-if="targets.length" class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '确认迁移' }}
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

.field label b {
  color: var(--danger);
  font-weight: 500;
}

.preview {
  margin-top: 14px;
  padding: 10px 12px;
  border: 1px dashed var(--line);
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--muted);
}

.preview b {
  color: var(--ink);
}

.arrow {
  margin: 0 8px;
  color: var(--brand);
}

.notice.danger {
  color: var(--danger);
}
</style>
