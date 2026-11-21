<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as rescueApi from '@/api/rescue'
import { BizError } from '@/api/http'
import CampusPointMap from '@/components/CampusPointMap.vue'
import PhotoUploader from '@/components/PhotoUploader.vue'
import { REPORT_TYPE_OPTIONS } from '@/utils/domain'
import { dateTime } from '@/utils/format'
import type { CreatedReport, PointView } from '@/api/types'

/**
 * P06 上报表单。
 * 后端硬约束：五类枚举、点位存在、至少 1 张照片、描述 ≥10 字。
 * 提交成功后由服务端 /reports/mine 提供实时列表，本页只展示本次回执。
 */
const props = defineProps<{
  points: PointView[]
}>()

const router = useRouter()

const form = reactive({
  type: 'INJURED',
  pointId: null as number | null,
  photos: [] as string[],
  description: '',
  animalCode: '',
  contact: '',
})

const submitting = ref(false)
const uploaded = ref(false)
const errors = reactive<Record<string, string>>({})
const lastCreated = ref<CreatedReport | null>(null)
const lastPointName = ref('')

function resetForm(): void {
  form.type = 'INJURED'
  form.pointId = null
  form.photos = []
  form.description = ''
  form.animalCode = ''
  form.contact = ''
  uploaded.value = false
  Object.keys(errors).forEach((key) => delete errors[key])
  lastCreated.value = null
  lastPointName.value = ''
}

function validate(): boolean {
  Object.keys(errors).forEach((key) => delete errors[key])
  if (!form.type) errors.type = '请选择上报类型'
  if (!form.pointId) errors.pointId = '请选择事发点位'
  if (form.photos.length < 1) errors.photos = '至少上传 1 张现场照片'
  if (form.photos.length > 3) errors.photos = '最多上传 3 张照片'
  if (form.description.trim().length < 10) errors.description = '描述至少 10 个字'
  if (form.description.length > 500) errors.description = '描述最多 500 个字'
  if (uploaded.value) errors.photos = '照片仍在上传，请稍候'
  return Object.keys(errors).length === 0
}

async function submit(): Promise<void> {
  if (submitting.value || !validate()) return
  submitting.value = true
  try {
    const point = props.points.find((item) => item.id === form.pointId)
    const result = await rescueApi.createReport({
      type: form.type,
      pointId: form.pointId as number,
      photos: form.photos,
      description: form.description.trim(),
      animalCode: form.animalCode.trim() || undefined,
      contact: form.contact.trim() || undefined,
    })
    lastCreated.value = result
    lastPointName.value = point?.name ?? ''
    ElMessage.success(`上报已提交：${result.reportNo}`)
  } catch (error) {
    if (error instanceof BizError) {
      for (const item of error.fieldErrors) errors[item.field] = item.reason
      ElMessage.error(error.message)
    } else {
      ElMessage.error('上报提交失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-if="lastCreated" class="success-card">
    <div class="success-mark">已提交</div>
    <h2>{{ lastCreated.reportNo }}</h2>
    <div class="success-meta">
      <span>状态：待受理</span>
      <span>点位：{{ lastPointName || '—' }}</span>
      <span>受理截止：{{ dateTime(lastCreated.deadline) }}</span>
    </div>
    <div v-if="lastCreated.duplicated" class="notice">
      与 {{ lastCreated.duplicateOf }} 可能重复：系统仍会生成新工单，请勿重复催促。
    </div>
    <div class="success-actions">
      <button class="btn" type="button" @click="router.push('/report/mine')">查看我的上报</button>
      <button class="btn ghost" type="button" @click="resetForm">再报一条</button>
    </div>
    <div class="footnote" style="text-align: left">
      提交只代表进入待受理队列，不代表已受理或已救助；可在“我的上报”查看本机回执。
    </div>
  </div>

  <div v-else class="form-shell">
    <div class="form-grid">
      <div class="field form-span-2">
        <label>上报类型 <b>*</b></label>
        <div class="type-grid">
          <button
            v-for="item in REPORT_TYPE_OPTIONS"
            :key="item.value"
            class="type-option"
            :class="{ on: form.type === item.value }"
            type="button"
            @click="form.type = item.value"
          >
            {{ item.label }}
          </button>
        </div>
        <div v-if="errors.type" class="field-error">{{ errors.type }}</div>
      </div>

      <div class="field form-span-2">
        <label>事发点位 <b>*</b></label>
        <CampusPointMap v-model="form.pointId" :points="points" />
        <div v-if="errors.pointId" class="field-error">{{ errors.pointId }}</div>
      </div>

      <div class="field form-span-2">
        <label>现场照片 <b>*</b></label>
        <PhotoUploader
          v-model="form.photos"
          :max="3"
          biz-type="REPORT_PHOTO"
          hint="至少 1 张，最多 3 张，单张不超过 5MB"
          @uploading="uploaded = $event"
        />
        <div v-if="errors.photos" class="field-error">{{ errors.photos }}</div>
      </div>

      <div class="field form-span-2">
        <label>情况描述 <b>*</b></label>
        <textarea
          v-model="form.description"
          class="textarea"
          maxlength="500"
          placeholder="请描述动物状态、受伤部位、周围环境、是否需要紧急救助等（至少 10 个字）"
          :class="{ invalid: errors.description }"
        />
        <div class="desc-meta">
          <span v-if="errors.description" class="field-error">{{ errors.description }}</span>
          <span class="muted count">{{ form.description.length }} / 500</span>
        </div>
      </div>

      <div class="field">
        <label>关联档案编号</label>
        <input
          v-model="form.animalCode"
          class="input mono"
          maxlength="20"
          placeholder="例如 CAT-2026-031，可不填"
        />
      </div>

      <div class="field">
        <label>联系方式</label>
        <input v-model="form.contact" class="input" maxlength="40" placeholder="手机号 / 微信号，可不填" />
      </div>
    </div>

    <div class="notice">
      照片和描述会影响受理优先级；恶意上报、重复占用工单会被记录。提交后请在“我的上报”保留回执。
    </div>

    <div class="form-actions">
      <button class="btn ghost" type="button" @click="resetForm">清空</button>
      <button class="btn" type="button" :disabled="submitting || uploaded" @click="submit">
        {{ submitting ? '提交中…' : '提交上报' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.form-shell {
  display: grid;
  gap: 14px;
}

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

.type-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.type-option {
  border: 1px solid var(--line);
  background: var(--panel);
  border-radius: 999px;
  padding: 7px 16px;
  color: var(--muted);
  cursor: pointer;
  font-size: 13px;
}

.type-option:hover,
.type-option.on {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.desc-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.count {
  font-size: 11px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.success-card {
  min-height: 280px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  padding: 36px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.success-mark {
  padding: 4px 10px;
  background: var(--accent-soft);
  color: var(--accent);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.success-card h2 {
  margin: 12px 0 8px;
  font-size: 26px;
  letter-spacing: 1px;
}

.success-meta {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  color: var(--muted);
  font-size: 12px;
}

.success-actions {
  display: flex;
  gap: 8px;
  margin-top: 20px;
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
