<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import PhotoUploader from '@/components/PhotoUploader.vue'
import type { GroupDetail, PointView } from '@/api/types'

/** 群体目击登记：数量是估计值，照片可选，点位必须属于群体活动范围。 */
const props = defineProps<{
  modelValue: boolean
  group: GroupDetail | null
  points: PointView[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: []
}>()

const form = reactive({
  pointId: null as number | null,
  seenAt: localDateTime(new Date()),
  countEstimate: null as number | null,
  behavior: '',
  fileIds: [] as string[],
})
const submitting = ref(false)
const errorText = ref('')

const groupPoints = ref<PointView[]>([])

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const current = props.group
    groupPoints.value = current
      ? props.points.filter((point) => current.points.some((item) => item.id === point.id))
      : []
    form.pointId = groupPoints.value[0]?.id ?? null
    form.seenAt = localDateTime(new Date())
    form.countEstimate = null
    form.behavior = ''
    form.fileIds = []
    errorText.value = ''
  },
)

async function submit(): Promise<void> {
  const current = props.group
  if (!current) return
  errorText.value = ''
  if (!form.pointId) return void (errorText.value = '请选择目击点位')
  if (!form.seenAt) return void (errorText.value = '请选择目击时间')
  if (form.countEstimate !== null && form.countEstimate < 1) {
    errorText.value = '数量估计至少为 1'
    return
  }
  submitting.value = true
  try {
    await archiveApi.createSighting(current.groupCode, {
      pointId: form.pointId,
      seenAt: new Date(form.seenAt).toISOString(),
      countEstimate: form.countEstimate ?? undefined,
      behavior: form.behavior.trim() || undefined,
      fileIds: form.fileIds,
    })
    ElMessage.success('目击记录已登记')
    emit('update:modelValue', false)
    emit('created')
  } catch (error) {
    if (error instanceof BizError) {
      errorText.value = error.message
    } else {
      errorText.value = '目击登记失败，请稍后重试'
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
    title="登记群体目击"
    width="640px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="dialog-note">
      {{ group?.groupCode }} · {{ group?.species.commonName }}；数量为估计值，可留空表示“有活动但未计数”。
    </div>

    <div class="field">
      <label>目击点位 <b>*</b></label>
      <select v-model.number="form.pointId" class="select">
        <option :value="null" disabled>请选择点位</option>
        <option v-for="point in groupPoints" :key="point.id" :value="point.id">{{ point.name }}</option>
      </select>
      <div v-if="!groupPoints.length" class="field-error">该群体尚未登记活动点位，请先编辑群体补全点位</div>
    </div>

    <div class="grid-two">
      <div class="field">
        <label>目击时间 <b>*</b></label>
        <input v-model="form.seenAt" class="input" type="datetime-local" />
      </div>
      <div class="field">
        <label>数量估计</label>
        <input v-model.number="form.countEstimate" class="input" type="number" min="1" step="1" placeholder="可不填" />
      </div>
    </div>

    <div class="field">
      <label>行为描述</label>
      <input v-model="form.behavior" class="input" maxlength="255" placeholder="如：在草坪觅食、沿围墙移动" />
    </div>

    <div class="field">
      <label>现场照片</label>
      <PhotoUploader
        v-model="form.fileIds"
        :max="6"
        biz-type="REPORT_PHOTO"
        hint="可选，最多 6 张；用于证明目击事实"
      />
    </div>

    <div v-if="errorText" class="notice danger" style="margin-top: 12px">{{ errorText }}</div>

    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '登记目击' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.field label b {
  color: var(--danger);
  font-weight: 500;
}

.grid-two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
</style>
