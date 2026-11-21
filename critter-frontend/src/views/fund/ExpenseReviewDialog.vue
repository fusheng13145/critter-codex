<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as fundApi from '@/api/fund'
import { BizError } from '@/api/http'
import AuthImage from '@/components/AuthImage.vue'
import { dateTime, money, statusLabel } from '@/utils/format'
import type { ExpenseSummary } from '@/api/types'

/**
 * 支出复核：一签通过小额支出；超过 500 元第一签后仍需第二名管理员再次确认。
 * 复核人是当前登录人，登记人自己不能签字。
 */
const props = defineProps<{
  modelValue: boolean
  expense: ExpenseSummary | null
  currentUserId: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const form = reactive({
  decision: 'APPROVE' as 'APPROVE' | 'REJECT',
  reason: '',
})
const submitting = ref(false)
const errorText = ref('')

const isSelf = computed(() => !!props.expense && props.expense.handlerId === props.currentUserId)
const isFirstSign = computed(() => props.expense?.status === 'PENDING_REVIEW' && (props.expense?.amount ?? 0) > 50_000)
const isSecondSign = computed(() => props.expense?.status === 'FIRST_APPROVED')

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.decision = 'APPROVE'
    form.reason = ''
    errorText.value = ''
  },
)

async function submit(): Promise<void> {
  if (!props.expense) return
  errorText.value = ''
  if (isSelf.value) {
    errorText.value = '登记人不能复核自己登记的支出'
    return
  }
  if (form.decision === 'REJECT' && form.reason.trim().length < 5) {
    errorText.value = '驳回原因至少 5 个字'
    return
  }
  submitting.value = true
  try {
    const result = await fundApi.reviewExpense(props.expense.expenseNo, {
      decision: form.decision,
      reason: form.reason.trim() || undefined,
    })
    ElMessage.success(result.message)
    emit('update:modelValue', false)
    emit('saved')
  } catch (error) {
    if (error instanceof BizError) {
      const data = error.data as { gap?: number } | null
      errorText.value = data?.gap
        ? `${error.message}（尚差 ${money(data.gap)}）`
        : error.message
    } else {
      errorText.value = '复核失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="复核支出"
    width="600px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="expense" class="review-head">
      <div>
        <div class="expense-no mono">{{ expense.expenseNo }}</div>
        <div class="muted small">
          {{ expense.projectNo }} · {{ expense.projectName }} · {{ dateTime(expense.occurredAt) }}
        </div>
      </div>
      <div class="amount">{{ money(expense.amount) }}</div>
    </div>

    <div v-if="expense" class="info-grid">
      <div class="info">
        <div class="k">关联动物</div>
        <div class="v">{{ expense.animalCode }} · {{ expense.animalName || '未命名' }}</div>
      </div>
      <div class="info">
        <div class="k">用途</div>
        <div class="v">{{ expense.purpose }}</div>
      </div>
      <div class="info">
        <div class="k">收款方</div>
        <div class="v">{{ expense.payee }}</div>
      </div>
      <div class="info">
        <div class="k">登记人</div>
        <div class="v">{{ expense.handlerName || `#${expense.handlerId}` }}</div>
      </div>
      <div class="info">
        <div class="k">当前状态</div>
        <div class="v">{{ statusLabel(expense.status) }}</div>
      </div>
      <div class="info" v-if="expense.reviewReason">
        <div class="k">已有意见</div>
        <div class="v">{{ expense.reviewReason }}</div>
      </div>
    </div>

    <div v-if="expense" class="voucher-block">
      <div class="voucher-title">票据（{{ expense.voucherFileIds.length }} 张）</div>
      <div class="voucher-row">
        <div v-for="fileId in expense.voucherFileIds" :key="fileId" class="voucher-item">
          <AuthImage :file-id="fileId" :alt="`票据 ${fileId}`" fit="cover" />
        </div>
      </div>
    </div>

    <div v-if="isSelf" class="notice danger">
      你是这笔支出的登记人，后端硬拦截自我复核；请转交另一名管理员。
    </div>
    <div v-else-if="isFirstSign" class="notice">
      金额超过 500.00 元。本次通过只完成第一签，还需要另一名管理员再次复核。
    </div>
    <div v-else-if="isSecondSign" class="notice">
      该支出已完成第一签。本次通过将正式计入项目已支出并扣减余额。
    </div>

    <template v-if="!isSelf">
      <div class="field">
        <label>复核决定</label>
        <div class="decision-row">
          <label class="decision" :class="{ active: form.decision === 'APPROVE' }">
            <input v-model="form.decision" type="radio" value="APPROVE" />
            通过
          </label>
          <label class="decision danger" :class="{ active: form.decision === 'REJECT' }">
            <input v-model="form.decision" type="radio" value="REJECT" />
            驳回
          </label>
        </div>
      </div>

      <div class="field" style="margin-top: 12px">
        <label>复核意见 <b v-if="form.decision === 'REJECT'">*</b></label>
        <textarea
          v-model="form.reason"
          class="textarea"
          maxlength="255"
          :placeholder="form.decision === 'REJECT' ? '请说明驳回原因，至少 5 个字' : '可选，例如票据已核验'"
        />
      </div>
    </template>

    <div v-if="errorText" class="notice danger" style="margin-top: 12px">{{ errorText }}</div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button v-if="!isSelf" class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : form.decision === 'REJECT' ? '确认驳回' : '确认复核' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.review-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--line);
}

.expense-no {
  font-size: 13px;
  font-weight: 600;
}

.amount {
  font-size: 20px;
  font-weight: 600;
  color: var(--brand);
}

.small {
  font-size: 11px;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}

.info .k {
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 3px;
}

.info .v {
  font-size: 13px;
}

.field {
  margin-top: 14px;
}

.field label {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--muted);
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}

.decision-row {
  display: flex;
  gap: 10px;
}

.decision {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 12px;
}

.decision.active {
  border-color: var(--brand);
  background: var(--hover);
  color: var(--brand);
}

.decision.danger.active {
  border-color: var(--danger);
  color: var(--danger);
}

.notice.danger {
  color: var(--danger);
}

.voucher-block {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}

.voucher-title {
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 6px;
}

.voucher-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.voucher-item {
  width: 78px;
  height: 78px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

@media (max-width: 720px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
