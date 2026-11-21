<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as fundApi from '@/api/fund'
import { BizError } from '@/api/http'
import { TARGET_TYPE_OPTIONS, yuanToCents } from '@/utils/domain'
import type { ProjectDetail } from '@/api/types'

/** 项目发起/编辑：草稿或驳回项目可改；预算明细与目标金额在前端先校验一次，后端仍会回 40101。 */
const props = defineProps<{
  modelValue: boolean
  project?: ProjectDetail | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [projectNo: string]
}>()

interface BudgetRow {
  subject: string
  amountYuan: number | null
  note: string
}

const form = reactive({
  name: '',
  purpose: '',
  targetType: 'MIXED',
  targetYuan: null as number | null,
  startDate: '',
  endDate: '',
  budget: [] as BudgetRow[],
})

const submitting = ref(false)
const errors = reactive<Record<string, string>>({})
const editing = computed(() => Boolean(props.project?.projectNo))
const dialogTitle = computed(() => (editing.value ? '编辑募捐项目' : '发起募捐项目'))

const budgetTotalYuan = computed(() =>
  form.budget.reduce((sum, row) => sum + (row.amountYuan ?? 0), 0),
)

function today(offset = 0): string {
  const date = new Date()
  date.setDate(date.getDate() + offset)
  return date.toISOString().slice(0, 10)
}

function reset(): void {
  Object.keys(errors).forEach((key) => delete errors[key])
  const current = props.project
  if (current) {
    form.name = current.name
    form.purpose = current.purpose
    form.targetType = current.targetType
    form.targetYuan = current.targetAmount / 100
    form.startDate = current.startDate
    form.endDate = current.endDate
    form.budget = current.budget.length
      ? current.budget.map((row) => ({
          subject: row.subject,
          amountYuan: row.amount / 100,
          note: row.note ?? '',
        }))
      : [{ subject: '绝育手术', amountYuan: null, note: '' }]
    return
  }
  form.name = ''
  form.purpose = ''
  form.targetType = 'MIXED'
  form.targetYuan = null
  form.startDate = today()
  form.endDate = today(30)
  form.budget = [{ subject: '绝育手术', amountYuan: null, note: '' }]
}

watch(
  [() => props.modelValue, () => props.project],
  ([open]) => {
    if (open) reset()
  },
)

function addBudget(): void {
  if (form.budget.length >= 50) {
    ElMessage.warning('预算明细最多 50 项')
    return
  }
  form.budget.push({ subject: '', amountYuan: null, note: '' })
}

function removeBudget(index: number): void {
  if (form.budget.length <= 1) {
    ElMessage.warning('至少保留一项预算')
    return
  }
  form.budget.splice(index, 1)
}

function validate(): boolean {
  Object.keys(errors).forEach((key) => delete errors[key])
  if (!form.name.trim()) errors.name = '项目名称不能为空'
  if (!form.purpose.trim()) errors.purpose = '用途说明不能为空'
  if (!form.targetYuan || form.targetYuan <= 0) errors.targetAmount = '目标金额必须大于 0'
  if (!form.startDate || !form.endDate) errors.dates = '起止日期必填'
  if (form.startDate && form.endDate && form.endDate < form.startDate) {
    errors.dates = '截止日期不能早于开始日期'
  }
  if (!form.budget.length) errors.budget = '预算明细至少一项'
  for (const [index, row] of form.budget.entries()) {
    if (!row.subject.trim()) errors.budget = `第 ${index + 1} 行预算科目不能为空`
    if (!row.amountYuan || row.amountYuan <= 0) errors.budget = `第 ${index + 1} 行预算金额必须大于 0`
  }
  if (form.targetYuan && budgetTotalYuan.value > form.targetYuan) {
    errors.budget = '预算合计不能超过目标金额（后端校验 40101）'
  }
  return Object.keys(errors).length === 0
}

async function save(submit: boolean): Promise<void> {
  if (submitting.value || !validate()) return
  submitting.value = true
  try {
    const payload = {
      name: form.name.trim(),
      purpose: form.purpose.trim(),
      targetType: form.targetType,
      targetAmount: yuanToCents(form.targetYuan),
      startDate: form.startDate,
      endDate: form.endDate,
      budget: form.budget.map((row) => ({
        subject: row.subject.trim(),
        amount: yuanToCents(row.amountYuan),
        note: row.note.trim() || undefined,
      })),
      coverFileId: props.project?.coverFileId ?? undefined,
      submit,
    }
    const existing = props.project
    const result = existing
      ? await fundApi.updateProject(existing.projectNo, payload)
      : await fundApi.createProject(payload)
    ElMessage.success(
      existing
        ? submit
          ? '已提交审核'
          : '草稿已保存'
        : submit
          ? `项目已提交审核：${result.projectNo}`
          : `项目草稿已保存：${result.projectNo}`,
    )
    emit('created', result.projectNo)
    emit('update:modelValue', false)
  } catch (error) {
    if (error instanceof BizError) {
      for (const item of error.fieldErrors) errors[item.field] = item.reason
      ElMessage.error(error.message)
    } else {
      ElMessage.error('项目保存失败，请稍后重试')
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
    width="780px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="form-grid">
      <div class="field form-span-2">
        <label>项目名称 <b>*</b></label>
        <input v-model="form.name" class="input" maxlength="64" :class="{ invalid: errors.name }" />
        <div v-if="errors.name" class="field-error">{{ errors.name }}</div>
      </div>

      <div class="field form-span-2">
        <label>用途说明 <b>*</b></label>
        <textarea
          v-model="form.purpose"
          class="textarea"
          maxlength="2000"
          placeholder="说明资金用途、受益对象与执行计划"
          :class="{ invalid: errors.purpose }"
        />
        <div v-if="errors.purpose" class="field-error">{{ errors.purpose }}</div>
      </div>

      <div class="field">
        <label>关联对象 <b>*</b></label>
        <select v-model="form.targetType" class="select">
          <option v-for="item in TARGET_TYPE_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </div>

      <div class="field">
        <label>目标金额（元） <b>*</b></label>
        <input
          v-model.number="form.targetYuan"
          class="input"
          type="number"
          min="0.01"
          step="0.01"
          :class="{ invalid: errors.targetAmount }"
        />
        <div v-if="errors.targetAmount" class="field-error">{{ errors.targetAmount }}</div>
      </div>

      <div class="field">
        <label>开始日期 <b>*</b></label>
        <input v-model="form.startDate" class="input" type="date" />
      </div>

      <div class="field">
        <label>截止日期 <b>*</b></label>
        <input v-model="form.endDate" class="input" type="date" />
        <div v-if="errors.dates" class="field-error">{{ errors.dates }}</div>
      </div>
    </div>

    <div class="budget-head">
      <div>
        <h3>预算明细 <b>*</b></h3>
        <span class="muted">合计 {{ budgetTotalYuan.toFixed(2) }} 元 / 目标 {{ (form.targetYuan ?? 0).toFixed(2) }} 元</span>
      </div>
      <button class="btn sm ghost" type="button" @click="addBudget">添加科目</button>
    </div>

    <div v-if="errors.budget" class="field-error" style="margin-bottom: 6px">{{ errors.budget }}</div>

    <div class="budget-list">
      <div v-for="(row, index) in form.budget" :key="index" class="budget-row">
        <input v-model="row.subject" class="input" maxlength="32" placeholder="预算科目" />
        <input v-model.number="row.amountYuan" class="input amount" type="number" min="0.01" step="0.01" placeholder="金额（元）" />
        <input v-model="row.note" class="input" maxlength="255" placeholder="备注（可不填）" />
        <button class="btn sm ghost remove" type="button" @click="removeBudget(index)">删</button>
      </div>
    </div>

    <div class="notice" style="margin-top: 12px">
      <template v-if="editing">
        仅草稿或已驳回项目可编辑；“保存”后仍保持当前状态，驳回项目可再点“提交审核”重新送审。
      </template>
      <template v-else>
        “存草稿”不会进入审核；“提交审核”后状态为 PENDING_AUDIT，需动物管理员通过后才公开募捐。
      </template>
    </div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn ghost" type="button" :disabled="submitting" @click="save(false)">
        {{ submitting ? '提交中…' : editing ? '保存修改' : '存草稿' }}
      </button>
      <button class="btn" type="button" :disabled="submitting" @click="save(true)">
        {{ submitting ? '提交中…' : '提交审核' }}
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

.field label b,
.budget-head h3 b {
  color: var(--danger);
  font-weight: 500;
}

.budget-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
  padding-top: 14px;
  margin-bottom: 8px;
  border-top: 1px solid var(--line);
}

.budget-head h3 {
  margin-bottom: 2px;
}

.budget-head .muted {
  font-size: 11px;
}

.budget-list {
  display: grid;
  gap: 6px;
}

.budget-row {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr 1.4fr auto;
  gap: 6px;
  align-items: center;
}

.amount {
  min-width: 92px;
}

.remove {
  padding: 7px 8px;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .form-span-2 {
    grid-column: span 1;
  }

  .budget-row {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
