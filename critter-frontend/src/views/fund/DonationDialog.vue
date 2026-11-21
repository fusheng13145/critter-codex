<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as fundApi from '@/api/fund'
import { BizError } from '@/api/http'
import PhotoUploader from '@/components/PhotoUploader.vue'
import { yuanToCents } from '@/utils/domain'

/** 捐赠登记：登记 ≠ 入账，提交后只生成待核对单，项目公开进度不变。 */
const props = defineProps<{
  modelValue: boolean
  projectNo: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: []
}>()

const form = reactive({
  amountYuan: null as number | null,
  anonymous: false,
  message: '',
  vouchers: [] as string[],
})

const submitting = ref(false)
const uploaded = ref(false)
const errors = reactive<Record<string, string>>({})

function reset(): void {
  form.amountYuan = null
  form.anonymous = false
  form.message = ''
  form.vouchers = []
  uploaded.value = false
  Object.keys(errors).forEach((key) => delete errors[key])
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) reset()
  },
)

function quick(amount: number): void {
  form.amountYuan = amount
}

function validate(): boolean {
  Object.keys(errors).forEach((key) => delete errors[key])
  if (!form.amountYuan || form.amountYuan <= 0) errors.amount = '捐赠金额必须大于 0'
  if (!form.vouchers.length) errors.vouchers = '至少上传 1 张支付凭证'
  if (form.message.length > 200) errors.message = '留言最多 200 个字'
  if (uploaded.value) errors.vouchers = '附件仍在上传，请稍候'
  return Object.keys(errors).length === 0
}

async function submit(): Promise<void> {
  if (submitting.value || !validate()) return
  submitting.value = true
  try {
    const result = await fundApi.createDonation({
      projectNo: props.projectNo,
      amount: yuanToCents(form.amountYuan),
      anonymous: form.anonymous,
      message: form.message.trim() || undefined,
      voucherFileIds: form.vouchers,
    })
    ElMessage.success(`捐赠已登记：${result.donationNo}，待管理员核对后入账`)
    emit('created')
    emit('update:modelValue', false)
  } catch (error) {
    if (error instanceof BizError) {
      for (const item of error.fieldErrors) errors[item.field] = item.reason
      ElMessage.error(error.message)
    } else {
      ElMessage.error('捐赠登记失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="捐赠登记"
    width="620px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="project-line">
      <span class="muted">捐赠项目</span>
      <span class="mono">{{ projectNo }}</span>
    </div>

    <div class="field">
      <label>捐赠金额（元） <b>*</b></label>
      <input
        v-model.number="form.amountYuan"
        class="input"
        type="number"
        min="0.01"
        step="0.01"
        placeholder="请输入金额"
        :class="{ invalid: errors.amount }"
      />
      <div class="quick-row">
        <button v-for="amount in [50, 100, 200, 500]" :key="amount" class="btn sm ghost" type="button" @click="quick(amount)">
          ¥{{ amount }}
        </button>
      </div>
      <div v-if="errors.amount" class="field-error">{{ errors.amount }}</div>
    </div>

    <div class="field">
      <label>展示方式</label>
      <label class="switch-line">
        <input v-model="form.anonymous" type="checkbox" />
        <span>匿名捐赠（公开流水只显示“匿名爱心人士”）</span>
      </label>
    </div>

    <div class="field">
      <label>留言</label>
      <textarea v-model="form.message" class="textarea" maxlength="200" placeholder="可不填" />
      <div v-if="errors.message" class="field-error">{{ errors.message }}</div>
    </div>

    <div class="field">
      <label>支付凭证 <b>*</b></label>
      <PhotoUploader
        v-model="form.vouchers"
        :max="5"
        biz-type="GENERAL"
        accept="image/jpeg,image/png,image/webp,application/pdf"
        hint="支持图片或 PDF，最多 5 个附件"
        @uploading="uploaded = $event"
      />
      <div v-if="errors.vouchers" class="field-error">{{ errors.vouchers }}</div>
    </div>

    <div class="notice">
      登记只生成 PENDING_VERIFY 单据，公开“已筹金额”不会立即增加；管理员核对金额一致后才计入项目进度。
    </div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting || uploaded" @click="submit">
        {{ submitting ? '提交中…' : '提交捐赠登记' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.project-line {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 12px;
  margin-bottom: 14px;
  background: var(--chip);
  border-radius: var(--radius-sm);
  font-size: 12px;
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}

.quick-row {
  display: flex;
  gap: 6px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.switch-line {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 12px;
}

.switch-line input {
  accent-color: var(--accent);
}
</style>
