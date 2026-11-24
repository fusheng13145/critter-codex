<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as adoptionsApi from '@/api/adoptions'
import { BizError } from '@/api/http'
import { uploadFile } from '@/api/file'
import { money } from '@/utils/format'

/** 缴费入账对话框：先上传凭证拿 fileId，再由管理员确认线下到账。 */
const props = defineProps<{
  modelValue: boolean
  adoption: { adoptionNo: string; amount: number } | null
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const form = reactive({
  paidAmount: 0,
  paidAt: nowLocal(),
  note: '',
})
const files = ref<File[]>([])
const submitting = ref(false)

watch(
  () => props.modelValue,
  (open) => {
    if (open && props.adoption) {
      form.paidAmount = props.adoption.amount
      form.paidAt = nowLocal()
      form.note = ''
      files.value = []
    }
  },
)

function nowLocal(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function toOffsetDateTime(value: string): string {
  if (!value) return ''
  const date = new Date(value)
  const offset = -date.getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const hh = String(Math.floor(Math.abs(offset) / 60)).padStart(2, '0')
  const mm = String(Math.abs(offset) % 60).padStart(2, '0')
  return `${value.length === 16 ? `${value}:00` : value}${sign}${hh}:${mm}`
}

function onFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  files.value = Array.from(input.files ?? [])
}

async function submit(): Promise<void> {
  if (!props.adoption || submitting.value) return
  if (!files.value.length) {
    ElMessage.warning('请先选择至少一张转账凭证')
    return
  }
  if (form.paidAmount <= 0) {
    ElMessage.warning('实收金额必须大于 0')
    return
  }
  submitting.value = true
  try {
    const voucherFileIds: string[] = []
    for (const file of files.value) {
      const uploaded = await uploadFile(file, 'ADOPTION_VOUCHER')
      voucherFileIds.push(uploaded.fileId)
    }
    const result = await adoptionsApi.confirmAdoptionPayment(props.adoption.adoptionNo, {
      paidAmount: form.paidAmount,
      paidAt: toOffsetDateTime(form.paidAt),
      voucherFileIds,
      note: form.note.trim() || undefined,
    })
    ElMessage.success(`入账成功，关系已生效，证书号 ${result.certificateNo}`)
    visible.value = false
    emit('success')
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '缴费入账失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="缴费入账" width="560px" destroy-on-close>
    <el-form label-width="92px">
      <el-form-item label="认养单号">
        <span class="mono">{{ adoption?.adoptionNo }}</span>
      </el-form-item>
      <el-form-item label="应收金额">
        <b>{{ money(adoption?.amount) }}</b>
      </el-form-item>
      <el-form-item label="实收金额（分）" required>
        <el-input-number v-model="form.paidAmount" :min="1" :step="100" style="width: 220px" />
        <span class="muted" style="margin-left: 8px">{{ money(form.paidAmount) }}</span>
      </el-form-item>
      <el-form-item label="到账时间" required>
        <el-date-picker
          v-model="form.paidAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm"
          placeholder="选择线下到账时间"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="转账凭证" required>
        <input type="file" multiple accept="image/*,.pdf" @change="onFiles" />
        <div class="muted" style="font-size: 12px; margin-top: 6px">
          已选 {{ files.length }} 个文件。
        </div>
      </el-form-item>
      <el-form-item label="差额说明">
        <el-input
          v-model="form.note"
          type="textarea"
          :rows="2"
          maxlength="255"
          show-word-limit
          placeholder="实收与应收不一致时必须填写差额原因，如手续费差额"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认入账</el-button>
    </template>
  </el-dialog>
</template>
