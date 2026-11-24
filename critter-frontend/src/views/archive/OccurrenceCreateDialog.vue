<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import PhotoUploader from '@/components/PhotoUploader.vue'
import type { SpeciesView, PointView } from '@/api/types'

/** 物种出现记录：数量使用枚举档位，避免伪精确计数。 */
const props = defineProps<{
  modelValue: boolean
  species: SpeciesView[]
  points: PointView[]
  presetSpeciesId?: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [speciesId: number]
}>()

const SCALE_OPTIONS = [
  { value: 'FEW', label: '少量（1—3）' },
  { value: 'SMALL_HERD', label: '小群（4—10）' },
  { value: 'LARGE_HERD', label: '大群（10+）' },
] as const

const form = reactive({
  speciesId: null as number | null,
  pointId: null as number | null,
  occurredAt: localDateTime(new Date()),
  scale: 'FEW' as 'FEW' | 'SMALL_HERD' | 'LARGE_HERD',
  habitat: '',
  fileIds: [] as string[],
})
const submitting = ref(false)
const errorText = ref('')

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.speciesId = props.presetSpeciesId ?? props.species[0]?.id ?? null
    form.pointId = props.points[0]?.id ?? null
    form.occurredAt = localDateTime(new Date())
    form.scale = 'FEW'
    form.habitat = ''
    form.fileIds = []
    errorText.value = ''
  },
)

async function submit(): Promise<void> {
  errorText.value = ''
  if (!form.speciesId) return void (errorText.value = '请选择物种')
  if (!form.pointId) return void (errorText.value = '请选择出现点位')
  if (!form.occurredAt) return void (errorText.value = '请选择出现时间')
  submitting.value = true
  try {
    await archiveApi.createOccurrence({
      speciesId: form.speciesId,
      pointId: form.pointId,
      occurredAt: new Date(form.occurredAt).toISOString(),
      scale: form.scale,
      habitat: form.habitat.trim() || undefined,
      fileIds: form.fileIds,
    })
    ElMessage.success('出现记录已登记')
    emit('update:modelValue', false)
    emit('created', form.speciesId)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '出现记录登记失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function localDateTime(value: Date): string {
  const offset = value.getTimezoneOffset() * 60_000
  return new Date(value.getTime() - offset).toISOString().slice(0, 16)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="登记物种出现记录"
    width="640px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >

    <div class="field">
      <label>物种 <b>*</b></label>
      <select v-model.number="form.speciesId" class="select">
        <option :value="null" disabled>请选择物种</option>
        <option v-for="item in species" :key="item.id" :value="item.id">
          {{ item.commonName }} · {{ item.category }}
        </option>
      </select>
    </div>

    <div class="grid-two">
      <div class="field">
        <label>出现点位 <b>*</b></label>
        <select v-model.number="form.pointId" class="select">
          <option :value="null" disabled>请选择点位</option>
          <option v-for="point in points" :key="point.id" :value="point.id">{{ point.name }}</option>
        </select>
      </div>
      <div class="field">
        <label>出现时间 <b>*</b></label>
        <input v-model="form.occurredAt" class="input" type="datetime-local" />
      </div>
    </div>

    <div class="field">
      <label>数量档位 <b>*</b></label>
      <div class="scale-row">
        <button
          v-for="option in SCALE_OPTIONS"
          :key="option.value"
          class="scale-option"
          :class="{ on: form.scale === option.value }"
          type="button"
          @click="form.scale = option.value"
        >
          {{ option.label }}
        </button>
      </div>
    </div>

    <div class="field">
      <label>生境描述</label>
      <input v-model="form.habitat" class="input" maxlength="255" placeholder="如：湿地边缘、香樟林下" />
    </div>

    <div class="field">
      <label>现场照片</label>
      <PhotoUploader v-model="form.fileIds" :max="6" biz-type="REPORT_PHOTO" />
    </div>

    <div v-if="errorText" class="notice danger" style="margin-top: 12px">{{ errorText }}</div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '登记出现记录' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.field label b {
  color: var(--danger);
  font-weight: 500;
}

.grid-two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.scale-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.scale-option {
  border: 1px solid var(--line);
  background: var(--panel);
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  color: var(--muted);
  cursor: pointer;
}

.scale-option.on {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}
</style>
