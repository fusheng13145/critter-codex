<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import { fetchDicts } from '@/api/dict'
import PhotoUploader from '@/components/PhotoUploader.vue'
import {
  ANIMAL_CATEGORY_OPTIONS,
  ANIMAL_GENDER_OPTIONS,
  type SelectOption,
} from '@/utils/domain'
import type { PointView } from '@/api/types'

/** 建档只负责表单与契约校验；编号由服务端生成，前端不出现 code 输入。 */
const props = defineProps<{
  modelValue: boolean
  points: PointView[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [code: string]
}>()

const form = reactive({
  category: 'CAT',
  gender: 'UNKNOWN',
  color: '',
  estimatedAge: null as number | null,
  features: '',
  pointId: null as number | null,
  photos: [] as string[],
  healthNote: '',
})

const colors = ref<SelectOption[]>([])
const submitting = ref(false)
const errors = reactive<Record<string, string>>({})
let errorsFromServer: { field: string; reason: string }[] = []

function reset(): void {
  form.category = 'CAT'
  form.gender = 'UNKNOWN'
  form.color = colors.value[0]?.value ?? ''
  form.estimatedAge = null
  form.features = ''
  form.pointId = null
  form.photos = []
  form.healthNote = ''
  Object.keys(errors).forEach((key) => delete errors[key])
  errorsFromServer = []
}

async function loadColors(): Promise<void> {
  try {
    const bundle = await fetchDicts(['ANIMAL_COLOR'])
    colors.value = (bundle.dicts.ANIMAL_COLOR ?? []).map((item) => ({
      value: item.label,
      label: item.label,
    }))
    if (!form.color) form.color = colors.value[0]?.value ?? ''
  } catch {
    colors.value = []
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      reset()
      loadColors()
    }
  },
)

function validate(): boolean {
  Object.keys(errors).forEach((key) => delete errors[key])
  if (!form.category) errors.category = '请选择类别'
  if (!form.gender) errors.gender = '请选择性别'
  if (!form.color) errors.color = '请选择毛色'
  if (!form.features.trim() || form.features.trim().length < 5) {
    errors.features = '识别特征至少 5 个字'
  }
  if (!form.pointId) errors.pointId = '请选择常驻点位'
  if (form.photos.length < 1) errors.photos = '至少上传 1 张照片'
  if (form.photos.length > 6) errors.photos = '最多上传 6 张照片'
  if (form.estimatedAge !== null && (form.estimatedAge < 0 || form.estimatedAge > 25)) {
    errors.estimatedAge = '估计年龄需在 0—25 之间'
  }
  return Object.keys(errors).length === 0
}

async function submit(): Promise<void> {
  if (submitting.value || !validate()) return
  submitting.value = true
  try {
    const result = await archiveApi.createAnimal({
      category: form.category,
      gender: form.gender,
      color: form.color,
      estimatedAge: form.estimatedAge ?? undefined,
      features: form.features.trim(),
      pointId: form.pointId as number,
      photos: form.photos,
      healthNote: form.healthNote.trim() || undefined,
    })
    ElMessage.success(`建档成功：${result.code}`)
    emit('created', result.code)
    emit('update:modelValue', false)
  } catch (error) {
    if (error instanceof BizError) {
      errorsFromServer = error.fieldErrors
      for (const item of errorsFromServer) errors[item.field] = item.reason
      ElMessage.error(error.message)
    } else {
      ElMessage.error('建档失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="新建个体档案"
    width="760px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="form-grid">
      <div class="field">
        <label>类别 <b>*</b></label>
        <select v-model="form.category" class="select" :class="{ invalid: errors.category }">
          <option v-for="item in ANIMAL_CATEGORY_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <div v-if="errors.category" class="field-error">{{ errors.category }}</div>
      </div>

      <div class="field">
        <label>性别 <b>*</b></label>
        <select v-model="form.gender" class="select" :class="{ invalid: errors.gender }">
          <option v-for="item in ANIMAL_GENDER_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <div v-if="errors.gender" class="field-error">{{ errors.gender }}</div>
      </div>

      <div class="field">
        <label>毛色 <b>*</b></label>
        <select v-model="form.color" class="select" :class="{ invalid: errors.color }">
          <option value="" disabled>请选择字典毛色</option>
          <option v-for="item in colors" :key="item.value" :value="item.value">
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
          placeholder="可不填"
          :class="{ invalid: errors.estimatedAge }"
        />
        <div v-if="errors.estimatedAge" class="field-error">{{ errors.estimatedAge }}</div>
      </div>

      <div class="field form-span-2">
        <label>常驻点位 <b>*</b></label>
        <select v-model.number="form.pointId" class="select" :class="{ invalid: errors.pointId }">
          <option :value="null" disabled>请选择点位</option>
          <option v-for="point in points" :key="point.id" :value="point.id">
            {{ point.name }} · {{ point.area }}
          </option>
        </select>
        <div v-if="errors.pointId" class="field-error">{{ errors.pointId }}</div>
      </div>

      <div class="field form-span-2">
        <label>识别特征 <b>*</b></label>
        <textarea
          v-model="form.features"
          class="textarea"
          maxlength="255"
          placeholder="例如：右后腿跛行，左耳有缺口（至少 5 个字）"
          :class="{ invalid: errors.features }"
        />
        <div v-if="errors.features" class="field-error">{{ errors.features }}</div>
      </div>

      <div class="field form-span-2">
        <label>照片 <b>*</b></label>
        <PhotoUploader v-model="form.photos" :max="6" biz-type="ANIMAL_PUBLIC" />
        <div v-if="errors.photos" class="field-error">{{ errors.photos }}</div>
        <div class="footnote" style="text-align: left">
          公开照片使用 ANIMAL_PUBLIC，管理员审核通过后才会出现在公开页。
        </div>
      </div>

      <div class="field form-span-2">
        <label>初步体检备注</label>
        <textarea v-model="form.healthNote" class="textarea" maxlength="255" placeholder="可不填" />
      </div>
    </div>

    <div class="notice">编号由服务端按“类别-年份-流水”生成；同一类别流水连续，前端不得传 code。</div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '提交建档' }}
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

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .form-span-2 {
    grid-column: span 1;
  }
}
</style>
