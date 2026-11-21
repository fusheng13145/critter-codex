<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as fundApi from '@/api/fund'
import { BizError } from '@/api/http'
import PhotoUploader from '@/components/PhotoUploader.vue'
import { money } from '@/utils/format'
import type { ProjectDetail } from '@/api/types'

/** 结项：必须无待复核支出；报告与结余去向必填，发布后公示页公开可读。 */
const props = defineProps<{
  modelValue: boolean
  project: ProjectDetail | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  settled: []
}>()

const form = reactive({
  reportText: '',
  balanceTo: '',
  attachments: [] as string[],
})
const submitting = ref(false)
const errorText = ref('')

const balance = computed(() =>
  props.project ? props.project.raisedAmount - props.project.spentAmount : 0,
)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.reportText = ''
    form.balanceTo = ''
    form.attachments = []
    errorText.value = ''
  },
)

async function submit(): Promise<void> {
  const project = props.project
  if (!project) return
  errorText.value = ''
  if (form.reportText.trim().length < 20) {
    errorText.value = '结项报告至少 20 个字'
    return
  }
  if (!form.balanceTo.trim()) {
    errorText.value = '请填写结余去向'
    return
  }
  submitting.value = true
  try {
    const result = await fundApi.settleProject(project.projectNo, {
      reportText: form.reportText.trim(),
      balanceTo: form.balanceTo.trim(),
      attachments: form.attachments,
    })
    ElMessage.success(`项目已结项公示：余额 ${money(result.balance)}`)
    emit('update:modelValue', false)
    emit('settled')
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '结项提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="提交结项公示"
    width="720px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="dialog-note">
      结项后项目进入 SETTLED，报告与收支合计对全体用户公开；存在待复核支出时无法结项。
    </div>

    <div class="settle-metrics">
      <div><span>已筹</span><b>{{ money(project?.raisedAmount ?? 0) }}</b></div>
      <div><span>已支出</span><b>{{ money(project?.spentAmount ?? 0) }}</b></div>
      <div><span>结余</span><b class="accent">{{ money(balance) }}</b></div>
    </div>

    <div class="field">
      <label>结项报告 <b>*</b></label>
      <textarea
        v-model="form.reportText"
        class="textarea"
        maxlength="10000"
        rows="6"
        placeholder="说明资金使用情况、救助成效、票据归档与后续计划（至少 20 字）"
      />
      <div class="counter">{{ form.reportText.trim().length }} / 10000</div>
    </div>

    <div class="field">
      <label>结余去向 <b>*</b></label>
      <input
        v-model="form.balanceTo"
        class="input"
        maxlength="255"
        placeholder="如：转入下一期校园流浪动物绝育专项"
      />
    </div>

    <div class="field">
      <label>结项附件</label>
      <PhotoUploader
        v-model="form.attachments"
        :max="10"
        biz-type="PROJECT_SETTLEMENT"
        hint="可上传结项报告、票据汇总等，最多 10 个，公示后公开可读"
      />
    </div>

    <div v-if="errorText" class="notice danger" style="margin-top: 12px">{{ errorText }}</div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '确认结项并公示' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.settle-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.settle-metrics div {
  padding: 10px 12px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm, 8px);
}

.settle-metrics span {
  display: block;
  color: var(--muted);
  font-size: 12px;
  margin-bottom: 4px;
}

.settle-metrics b {
  font-size: 16px;
}

.accent {
  color: var(--accent);
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}

.counter {
  text-align: right;
  color: var(--muted);
  font-size: 11px;
  margin-top: 4px;
}
</style>