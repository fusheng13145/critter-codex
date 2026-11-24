<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import * as fundApi from '@/api/fund'
import * as dictApi from '@/api/dict'
import { BizError } from '@/api/http'
import PhotoUploader from '@/components/PhotoUploader.vue'
import { money } from '@/utils/format'
import type { AnimalSummary, ProjectSummary } from '@/api/types'

/**
 * 支出登记：必须挂项目和动物，票据至少一张；金额按元输入，提交时换算为整数分。
 */
const props = defineProps<{
  modelValue: boolean
  projects: ProjectSummary[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const form = reactive({
  projectNo: '',
  animalCode: '',
  animalKeyword: '',
  purpose: '',
  amountYuan: null as number | null,
  payee: '',
  occurredAt: localDateTime(new Date()),
  voucherFileIds: [] as string[],
})

const animalOptions = ref<AnimalSummary[]>([])
const purposeOptions = ref<string[]>([])
const searching = ref(false)
const submitting = ref(false)
const errorText = ref('')

const cents = computed(() => (form.amountYuan === null ? 0 : Math.round(form.amountYuan * 100)))
const animalLabel = computed(() => {
  const hit = animalOptions.value.find((item) => item.code === form.animalCode)
  return hit ? `${hit.code} · ${hit.name || '未命名'}` : form.animalCode
})

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.projectNo = props.projects[0]?.projectNo ?? ''
    form.animalCode = ''
    form.animalKeyword = ''
    form.purpose = purposeOptions.value[0] ?? ''
    form.amountYuan = null
    form.payee = ''
    form.occurredAt = localDateTime(new Date())
    form.voucherFileIds = []
    errorText.value = ''
    animalOptions.value = []
    loadPurposes()
  },
)

async function loadPurposes(): Promise<void> {
  try {
    const bundle = await dictApi.fetchDicts(['EXPENSE_PURPOSE'])
    purposeOptions.value = (bundle.dicts.EXPENSE_PURPOSE ?? []).map((item) => item.label)
    if (!form.purpose) form.purpose = purposeOptions.value[0] ?? ''
  } catch {
    purposeOptions.value = []
  }
}

async function searchAnimals(): Promise<void> {
  if (!form.animalKeyword.trim()) {
    animalOptions.value = []
    return
  }
  searching.value = true
  try {
    const result = await archiveApi.fetchAnimals({ keyword: form.animalKeyword.trim(), page: 1, size: 10 })
    animalOptions.value = result.list
  } catch {
    animalOptions.value = []
  } finally {
    searching.value = false
  }
}

function onAnimalChange(): void {
  const hit = animalOptions.value.find((item) => item.code === form.animalCode)
  if (hit && !form.animalKeyword) form.animalKeyword = hit.code
}

async function submit(): Promise<void> {
  errorText.value = ''
  if (!form.projectNo) return void (errorText.value = '请选择项目')
  if (!form.animalCode) return void (errorText.value = '请选择关联动物')
  if (!form.purpose) return void (errorText.value = '请选择支出用途')
  if (cents.value <= 0) return void (errorText.value = '支出金额必须大于 0')
  if (!form.payee.trim()) return void (errorText.value = '请填写收款方')
  if (!form.occurredAt) return void (errorText.value = '请选择发生时间')
  if (!form.voucherFileIds.length) return void (errorText.value = '请上传至少 1 张票据')

  submitting.value = true
  try {
    const created = await fundApi.createExpense({
      projectNo: form.projectNo,
      animalCode: form.animalCode,
      purpose: form.purpose,
      amount: cents.value,
      payee: form.payee.trim(),
      occurredAt: new Date(form.occurredAt).toISOString(),
      voucherFileIds: form.voucherFileIds,
    })
    ElMessage.success(`${created.expenseNo} 已登记，${created.note}`)
    emit('update:modelValue', false)
    emit('saved')
  } catch (error) {
    if (error instanceof BizError) {
      const data = error.data as { gap?: number } | null
      errorText.value = data?.gap
        ? `${error.message}（尚差 ${money(data.gap)}）`
        : error.message
    } else {
      errorText.value = '支出登记失败，请稍后重试'
    }
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
    title="登记支出"
    width="720px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >

    <div class="form-grid">
      <div class="field form-span-2">
        <label>关联项目 <b>*</b></label>
        <select v-model="form.projectNo" class="select">
          <option value="" disabled>选择项目</option>
          <option v-for="project in projects" :key="project.projectNo" :value="project.projectNo">
            {{ project.projectNo }} · {{ project.name }}（余额 {{ money(project.raisedAmount - project.spentAmount) }}）
          </option>
        </select>
      </div>

      <div class="field">
        <label>搜索动物 <b>*</b></label>
        <input
          v-model="form.animalKeyword"
          class="input"
          placeholder="输入编号 / 昵称后回车"
          @keyup.enter="searchAnimals"
        />
      </div>
      <div class="field">
        <label>关联动物</label>
        <select v-model="form.animalCode" class="select" @change="onAnimalChange">
          <option value="" disabled>请选择动物</option>
          <option v-for="animal in animalOptions" :key="animal.code" :value="animal.code">
            {{ animal.code }} · {{ animal.name || '未命名' }} · {{ animal.point?.name ?? '点位未知' }}
          </option>
        </select>
        <div v-if="searching" class="field-hint">搜索中…</div>
        <div v-else-if="form.animalCode" class="field-hint">已选：{{ animalLabel }}</div>
      </div>

      <div class="field">
        <label>支出用途 <b>*</b></label>
        <select v-model="form.purpose" class="select">
          <option value="" disabled>请选择用途</option>
          <option v-for="purpose in purposeOptions" :key="purpose" :value="purpose">{{ purpose }}</option>
        </select>
      </div>
      <div class="field">
        <label>金额（元） <b>*</b></label>
        <input v-model.number="form.amountYuan" class="input" type="number" min="0.01" step="0.01" />
        <div class="field-hint">按整数分提交：{{ cents }} 分</div>
      </div>

      <div class="field">
        <label>收款方 <b>*</b></label>
        <input v-model="form.payee" class="input" maxlength="64" placeholder="医院或个人" />
      </div>
      <div class="field">
        <label>发生时间 <b>*</b></label>
        <input v-model="form.occurredAt" class="input" type="datetime-local" />
      </div>

      <div class="field form-span-2">
        <label>票据 <b>*</b></label>
        <PhotoUploader
          v-model="form.voucherFileIds"
          :max="10"
          biz-type="EXPENSE_VOUCHER"
        />
      </div>
    </div>

    <div class="double-rule">
      <b>双签规则</b>
      <span></span>
    </div>

    <div v-if="errorText" class="notice danger" style="margin-top: 12px">{{ errorText }}</div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '登记并送审' }}
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

.field-hint {
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted);
}

.dialog-note {
  margin-bottom: 14px;
  padding: 9px 12px;
  background: var(--chip);
  border-radius: var(--radius-sm);
  color: var(--muted);
  font-size: 12px;
}

.double-rule {
  margin-top: 14px;
  padding: 10px 12px;
  border-left: 3px solid var(--warning, #d97706);
  background: var(--hover);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  font-size: 12px;
}

.double-rule b {
  display: block;
  margin-bottom: 3px;
}

.double-rule span {
  color: var(--muted);
}

.notice.danger {
  color: var(--danger);
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
