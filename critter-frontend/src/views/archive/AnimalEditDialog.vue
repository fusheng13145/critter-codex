<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import { fetchDicts } from '@/api/dict'
import { ANIMAL_GENDER_OPTIONS, type SelectOption } from '@/utils/domain'
import type { AnimalDetail, PointView } from '@/api/types'

/** 编辑档案：每次保存必须填变更原因，后端会写字段级 change_log。 */
const props = defineProps<{
  modelValue: boolean
  animal: AnimalDetail | null
  points: PointView[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const form = reactive({
  name: '',
  gender: 'UNKNOWN',
  color: '',
  estimatedAge: null as number | null,
  features: '',
  pointId: null as number | null,
  internalNote: '',
  reason: '',
})

const colors = ref<SelectOption[]>([])
const submitting = ref(false)
const errors = reactive<Record<string, string>>({})
/** 详情接口当前可能不返回 internalNote；记录打开时的原值，未改动就不回传，避免误清空。 */
let initialInternalNote = ''

const colorChoices = computed(() => {
  const list = [...colors.value]
  if (form.color && !list.some((item) => item.value === form.color)) {
    list.unshift({ value: form.color, label: form.color })
  }
  return list
})

async function loadColors(): Promise<void> {
  try {
    const bundle = await fetchDicts(['ANIMAL_COLOR'])
    colors.value = (bundle.dicts.ANIMAL_COLOR ?? []).map((item) => ({
      value: item.label,
      label: item.label,
    }))
  } catch {
    colors.value = []
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const animal = props.animal
    if (!animal) return
    form.name = animal.name ?? ''
    form.gender = animal.gender
    form.color = animal.color
    form.estimatedAge = animal.estimatedAge
    form.features = animal.features
    form.pointId = animal.point?.id ?? null
    form.internalNote = animal.internalNote ?? ''
    initialInternalNote = form.internalNote
    form.reason = ''
    Object.keys(errors).forEach((key) => delete errors[key])
    loadColors()
  },
)

function validate(): boolean {
  Object.keys(errors).forEach((key) => delete errors[key])
  if (!form.color.trim()) errors.color = '花色不能为空'
  if (!form.features.trim()) errors.features = '识别特征不能为空'
  if (!form.pointId) errors.pointId = '请选择常驻点位'
  if (!form.reason.trim() || form.reason.trim().length < 5) {
    errors.reason = '变更原因至少 5 个字'
  }
  if (form.estimatedAge !== null && (form.estimatedAge < 0 || form.estimatedAge > 25)) {
    errors.estimatedAge = '估计年龄需在 0—25 之间'
  }
  return Object.keys(errors).length === 0
}

async function submit(): Promise<void> {
  const animal = props.animal
  if (!animal || submitting.value || !validate()) return
  submitting.value = true
  try {
    await archiveApi.updateAnimal(animal.code, {
      name: form.name.trim() || undefined,
      gender: form.gender,
      color: form.color.trim(),
      estimatedAge: form.estimatedAge ?? undefined,
      features: form.features.trim(),
      pointId: form.pointId as number,
      ...(form.internalNote !== initialInternalNote
        ? { internalNote: form.internalNote.trim() }
        : {}),
      reason: form.reason.trim(),
    })
    ElMessage.success('档案已更新，变更已留痕')
    emit('saved')
    emit('update:modelValue', false)
  } catch (error) {
    if (error instanceof BizError) {
      for (const item of error.fieldErrors) errors[item.field] = item.reason
      ElMessage.error(error.message)
    } else {
      ElMessage.error('保存失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑档案"
    width="760px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="dialog-note">
      编号 <span class="mono">{{ animal?.code }}</span>
    </div>

    <div class="form-grid">
      <div class="field">
        <label>昵称</label>
        <input v-model="form.name" class="input" maxlength="32" placeholder="可不填，例如“三花”" />
      </div>

      <div class="field">
        <label>性别</label>
        <select v-model="form.gender" class="select">
          <option v-for="item in ANIMAL_GENDER_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </div>

      <div class="field">
        <label>花色</label>
        <select v-model="form.color" class="select" :class="{ invalid: errors.color }">
          <option v-for="item in colorChoices" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <div v-if="errors.color" class="field-error">{{ errors.color }}</div>
      </div>

      <div class="field">
        <label>估计年龄（岁）</label>
        <input
          v-model.number="form.estimatedAge"
          class="input"
          type="number"
          min="0"
          max="25"
          step="1"
          :class="{ invalid: errors.estimatedAge }"
        />
        <div v-if="errors.estimatedAge" class="field-error">{{ errors.estimatedAge }}</div>
      </div>

      <div class="field form-span-2">
        <label>常驻点位</label>
        <select v-model.number="form.pointId" class="select" :class="{ invalid: errors.pointId }">
          <option :value="null" disabled>请选择点位</option>
          <option v-for="point in points" :key="point.id" :value="point.id">
            {{ point.name }} · {{ point.area }}
          </option>
        </select>
        <div v-if="errors.pointId" class="field-error">{{ errors.pointId }}</div>
      </div>

      <div class="field form-span-2">
        <label>识别特征</label>
        <textarea
          v-model="form.features"
          class="textarea"
          maxlength="255"
          :class="{ invalid: errors.features }"
        />
        <div v-if="errors.features" class="field-error">{{ errors.features }}</div>
      </div>

      <div class="field form-span-2">
        <label>内部备注（仅管理员可见）</label>
        <textarea v-model="form.internalNote" class="textarea" maxlength="255" placeholder="可不填" />
      </div>

      <div class="field form-span-2">
        <label>变更原因 <b>*</b></label>
        <input
          v-model="form.reason"
          class="input"
          maxlength="255"
          placeholder="例如：复查体重后更新特征，至少 5 个字"
          :class="{ invalid: errors.reason }"
        />
        <div v-if="errors.reason" class="field-error">{{ errors.reason }}</div>
      </div>
    </div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '保存中…' : '保存并留痕' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.form-span-2 {
  grid-column: span 2;
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}

.dialog-note {
  margin-bottom: 14px;
  padding: 9px 12px;
  background: var(--chip);
  border-radius: var(--radius-sm);
  color: var(--muted);
  font-size: 12px;
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
