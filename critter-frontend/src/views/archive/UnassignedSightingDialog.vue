<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import PhotoUploader from '@/components/PhotoUploader.vue'
import type { PointView } from '@/api/types'

/** 先登记待归属目击：群体未知时也能留档，后续在待归属列表指派。 */
const props = defineProps<{
  modelValue: boolean
  points: PointView[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: []
}>()

const form = reactive({
  pointId: null as number | null,
  seenAt: nowLocal(),
  countEstimate: null as number | null,
  behavior: '',
  fileIds: [] as string[],
})
const submitting = ref(false)
const uploading = ref(false)
const errorText = ref('')

const canSubmit = computed(() => Boolean(form.pointId) && Boolean(form.seenAt))

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.pointId = null
    form.seenAt = nowLocal()
    form.countEstimate = null
    form.behavior = ''
    form.fileIds = []
    errorText.value = ''
  },
)

function nowLocal(): string {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function toOffsetIso(local: string): string {
  const value = local.length === 16 ? `${local}:00` : local
  const date = new Date(value)
  const offsetMinutes = -date.getTimezoneOffset()
  const sign = offsetMinutes >= 0 ? '+' : '-'
  const hours = String(Math.floor(Math.abs(offsetMinutes) / 60)).padStart(2, '0')
  const minutes = String(Math.abs(offsetMinutes) % 60).padStart(2, '0')
  return `${value}${sign}${hours}:${minutes}`
}

async function submit(): Promise<void> {
  if (submitting.value || uploading.value || !canSubmit.value) return
  if (form.pointId == null) return
  if (form.behavior.trim().length > 64) {
    ElMessage.warning('行为描述最多 64 个字')
    return
  }
  const pointId = form.pointId
  submitting.value = true
  try {
    await archiveApi.createUnassignedSighting({
      pointId,
      seenAt: toOffsetIso(form.seenAt),
      countEstimate: form.countEstimate ?? undefined,
      behavior: form.behavior.trim() || undefined,
      fileIds: form.fileIds,
    })
    ElMessage.success('待归属目击已登记')
    emit('created')
    emit('update:modelValue', false)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '登记失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="登记待归属目击"
    width="640px"
    append-to-body
    @update:model-value="emit('update:modelValue', false)"
  >
    <div class="dialog-note">
      先不指定群体，记录会以 <span class="mono">UNASSIGNED</span> 落库；在群体档案页的“待归属目击”里指派。
    </div>
    <div class="form-grid">
      <div class="field">
        <label>目击点位 <b>*</b></label>
        <select v-model.number="form.pointId" class="select">
          <option :value="null" disabled>请选择点位</option>
          <option v-for="point in points" :key="point.id" :value="point.id">
            {{ point.name }} · {{ point.area }}
          </option>
        </select>
      </div>
      <div class="field">
        <label>目击时间 <b>*</b></label>
        <input v-model="form.seenAt" class="input" type="datetime-local" />
      </div>
      <div class="field">
        <label>估计数量</label>
        <input v-model.number="form.countEstimate" class="input" type="number" min="1" placeholder="可空" />
      </div>
      <div class="field">
        <label>行为描述</label>
        <input v-model="form.behavior" class="input" maxlength="64" placeholder="例如：在林下觅食" />
      </div>
      <div class="field form-span-2">
        <label>现场照片</label>
        <PhotoUploader v-model="form.fileIds" :max="6" biz-type="SIGHTING" @uploading="uploading = $event" />
      </div>
    </div>
    <div v-if="errorText" class="notice">{{ errorText }}</div>
    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting || uploading || !canSubmit" @click="submit">
        {{ submitting ? '登记中…' : '登记待归属' }}
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

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}

.form-span-2 {
  grid-column: span 2;
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .form-span-2 {
    grid-column: span 1;
  }
}
</style>
