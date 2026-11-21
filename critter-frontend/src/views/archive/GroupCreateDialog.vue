<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import type { GroupDetail, PointView, SpeciesView } from '@/api/types'

/** 群体建档/编辑：规模始终是闭区间，前端不替用户取中位数；物种建后不可改。 */
const props = defineProps<{
  modelValue: boolean
  species: SpeciesView[]
  points: PointView[]
  group?: GroupDetail | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [groupCode: string]
}>()

const form = reactive({
  speciesId: null as number | null,
  features: '',
  pointIds: [] as number[],
  minSize: 1,
  maxSize: 1,
  sizeBasis: '',
})

const submitting = ref(false)
const errors = reactive<Record<string, string>>({})
const editing = computed(() => Boolean(props.group?.groupCode))
const dialogTitle = computed(() => (editing.value ? '编辑群体档案' : '新建群体档案'))

function reset(): void {
  Object.keys(errors).forEach((key) => delete errors[key])
  const current = props.group
  if (current) {
    form.speciesId = current.species.id
    form.features = current.features
    form.pointIds = current.points.map((point) => point.id)
    form.minSize = current.minSize
    form.maxSize = current.maxSize
    form.sizeBasis = current.sizeBasis
    return
  }
  form.speciesId = null
  form.features = ''
  form.pointIds = []
  form.minSize = 1
  form.maxSize = 1
  form.sizeBasis = ''
}

watch(
  [() => props.modelValue, () => props.group],
  ([open]) => {
    if (open) reset()
  },
)

function togglePoint(id: number): void {
  form.pointIds = form.pointIds.includes(id)
    ? form.pointIds.filter((item) => item !== id)
    : [...form.pointIds, id]
}

function validate(): boolean {
  Object.keys(errors).forEach((key) => delete errors[key])
  if (!form.speciesId) errors.speciesId = '请选择物种'
  if (!form.features.trim()) errors.features = '识别特征不能为空'
  if (!form.pointIds.length) errors.pointIds = '至少选择一个活动点位'
  if (form.minSize === null || form.minSize < 0) errors.minSize = '规模下限不能为负'
  if (form.maxSize === null || form.maxSize < 1) errors.maxSize = '规模上限至少为 1'
  if (form.minSize > form.maxSize) errors.maxSize = '规模下限不能大于上限'
  if (!form.sizeBasis.trim()) errors.sizeBasis = '计数依据不能为空'
  return Object.keys(errors).length === 0
}

async function submit(): Promise<void> {
  if (submitting.value || !validate()) return
  submitting.value = true
  try {
    const payload = {
      features: form.features.trim(),
      pointIds: form.pointIds,
      minSize: form.minSize,
      maxSize: form.maxSize,
      sizeBasis: form.sizeBasis.trim(),
    }
    const existing = props.group
    if (existing) {
      await archiveApi.updateGroup(existing.groupCode, payload)
      ElMessage.success(`群体档案已更新：${existing.groupCode}`)
      emit('created', existing.groupCode)
    } else {
      const result = await archiveApi.createGroup({
        speciesId: form.speciesId as number,
        ...payload,
      })
      ElMessage.success(`群体建档成功：${result.groupCode}`)
      emit('created', result.groupCode)
    }
    emit('update:modelValue', false)
  } catch (error) {
    if (error instanceof BizError) {
      for (const item of error.fieldErrors) errors[item.field] = item.reason
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
    :title="dialogTitle"
    width="720px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="field">
      <label>物种 <b>*</b></label>
      <select v-model.number="form.speciesId" class="select" :disabled="editing" :class="{ invalid: errors.speciesId }">
        <option :value="null" disabled>请选择物种名录中的一项</option>
        <option v-for="item in species" :key="item.id" :value="item.id">
          {{ item.commonName }} · {{ item.category }} · 保护级别 {{ item.protectionLevel }}
        </option>
      </select>
      <div v-if="errors.speciesId" class="field-error">{{ errors.speciesId }}</div>
    </div>

    <div class="field">
      <label>识别特征 <b>*</b></label>
      <textarea
        v-model="form.features"
        class="textarea"
        placeholder="例如：黄褐色羽毛，常在图书馆北侧草坪成群觅食"
        :class="{ invalid: errors.features }"
      />
      <div v-if="errors.features" class="field-error">{{ errors.features }}</div>
    </div>

    <div class="field">
      <label>活动点位 <b>*</b></label>
      <div class="point-select">
        <button
          v-for="point in points"
          :key="point.id"
          class="point-option"
          :class="{ on: form.pointIds.includes(point.id) }"
          type="button"
          @click="togglePoint(point.id)"
        >
          {{ point.name }}
        </button>
      </div>
      <div v-if="errors.pointIds" class="field-error">{{ errors.pointIds }}</div>
    </div>

    <div class="size-row">
      <div class="field">
        <label>规模下限 <b>*</b></label>
        <input v-model.number="form.minSize" class="input" type="number" min="0" step="1" />
        <div v-if="errors.minSize" class="field-error">{{ errors.minSize }}</div>
      </div>
      <div class="field">
        <label>规模上限 <b>*</b></label>
        <input v-model.number="form.maxSize" class="input" type="number" min="1" step="1" />
        <div v-if="errors.maxSize" class="field-error">{{ errors.maxSize }}</div>
      </div>
    </div>

    <div class="field">
      <label>计数依据 <b>*</b></label>
      <input
        v-model="form.sizeBasis"
        class="input"
        maxlength="100"
        placeholder="例如：一次目击计数，保守估计"
        :class="{ invalid: errors.sizeBasis }"
      />
      <div v-if="errors.sizeBasis" class="field-error">{{ errors.sizeBasis }}</div>
    </div>

    <div class="notice">
      {{ editing ? '物种建后不可改；规模区间与计数依据可直接修正，保存后立即生效。' : '群体不参与认养；群体编号由服务端生成，规模以区间返回，不得把中位数当作事实。' }}
    </div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : editing ? '保存修改' : '提交建档' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.field label b {
  color: var(--danger);
  font-weight: 500;
}

.point-select {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.point-option {
  border: 1px solid var(--line);
  background: var(--panel);
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  color: var(--muted);
  cursor: pointer;
}

.point-option:hover,
.point-option.on {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

.size-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
</style>
