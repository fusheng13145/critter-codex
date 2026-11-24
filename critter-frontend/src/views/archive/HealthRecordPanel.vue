<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import PanelCard from '@/components/PanelCard.vue'
import PageState from '@/components/PageState.vue'
import { dateOnly, dateTime } from '@/utils/format'
import { HEALTH_TYPE_OPTIONS, healthTypeLabel, toOffsetDateTime } from '@/utils/domain'
import type { HealthRecordView } from '@/api/types'

/** 健康与防疫记录：GET 登录可见，新增仅管理员。 */
const props = defineProps<{
  code: string
  editable: boolean
}>()

const records = ref<HealthRecordView[]>([])
const loading = ref(false)
const errorText = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const formErrors = reactive<Record<string, string>>({})

const form = reactive({
  type: 'VACCINE',
  recordedAt: '',
  result: '',
  nextDate: '',
})

async function load(): Promise<void> {
  if (!props.code) return
  loading.value = true
  errorText.value = ''
  try {
    records.value = await archiveApi.fetchHealthRecords(props.code)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '健康记录加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  form.type = 'VACCINE'
  form.recordedAt = new Date().toISOString().slice(0, 16)
  form.result = ''
  form.nextDate = ''
  Object.keys(formErrors).forEach((key) => delete formErrors[key])
  dialogVisible.value = true
}

function validate(): boolean {
  Object.keys(formErrors).forEach((key) => delete formErrors[key])
  if (!form.result.trim()) formErrors.result = '记录结果不能为空'
  return Object.keys(formErrors).length === 0
}

async function submit(): Promise<void> {
  if (submitting.value || !validate()) return
  submitting.value = true
  try {
    await archiveApi.createHealthRecord(props.code, {
      type: form.type,
      recordedAt: toOffsetDateTime(form.recordedAt),
      result: form.result.trim(),
      nextDate: form.nextDate || undefined,
    })
    ElMessage.success('健康记录已登记')
    dialogVisible.value = false
    await load()
  } catch (error) {
    if (error instanceof BizError) {
      for (const item of error.fieldErrors) formErrors[item.field] = item.reason
      ElMessage.error(error.message)
    } else {
      ElMessage.error('登记失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}

watch(() => props.code, load, { immediate: true })
</script>

<template>
  <PanelCard title="健康与防疫记录">
    <template #extra>
      <button v-if="editable" class="btn sm ghost" type="button" @click="openCreate">登记记录</button>
    </template>

    <PageState
      :loading="loading"
      :error="errorText"
      :empty="!records.length"
    >
      <ul class="list">
        <li v-for="record in records" :key="record.id">
          <span class="dot" :class="{ gray: !record.reminderDue }" />
          <span class="grow">
            <b>{{ record.typeLabel || healthTypeLabel(record.type) }}</b>
            <span class="record-result">{{ record.result }}</span>
            <span v-if="record.nextDate" class="muted record-next">
              下次 {{ dateOnly(record.nextDate) }}
              <span v-if="record.daysUntilNext !== null" class="muted">（{{ record.daysUntilNext }} 天）</span>
            </span>
          </span>
          <span v-if="record.reminderDue" class="tag warn">临期</span>
          <span class="time">{{ dateTime(record.recordedAt) }}</span>
        </li>
      </ul>
    </PageState>



    <el-dialog v-model="dialogVisible" title="登记健康记录" width="560px" append-to-body>
      <div class="field">
        <label>记录类型</label>
        <select v-model="form.type" class="select">
          <option v-for="item in HEALTH_TYPE_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </div>

      <div class="field">
        <label>发生时间</label>
        <input v-model="form.recordedAt" class="input" type="datetime-local" />
      </div>

      <div class="field">
        <label>记录结果 <b>*</b></label>
        <textarea
          v-model="form.result"
          class="textarea"
          maxlength="255"
          placeholder="例如：体重 3.2kg，精神良好"
          :class="{ invalid: formErrors.result }"
        />
        <div v-if="formErrors.result" class="field-error">{{ formErrors.result }}</div>
      </div>

      <div class="field">
        <label>下次计划日期</label>
        <input v-model="form.nextDate" class="input" type="date" />

      </div>

      <template #footer>
        <button class="btn ghost" type="button" @click="dialogVisible = false">取消</button>
        <button class="btn" type="button" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交记录' }}
        </button>
      </template>
    </el-dialog>
  </PanelCard>
</template>

<style scoped>
.record-result {
  margin-left: 8px;
  color: var(--ink);
}

.record-next {
  margin-left: 8px;
  font-size: 12px;
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}
</style>
