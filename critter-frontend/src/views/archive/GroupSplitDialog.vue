<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import type { GroupDetail, PointView, SightingView } from '@/api/types'

/** 群体拆分：源群体缩小范围，新群体使用 new* 字段；可选迁移源群体目击。 */
const props = defineProps<{
  modelValue: boolean
  group: GroupDetail | null
  points: PointView[]
  sightings: SightingView[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [newCode: string]
}>()

const form = reactive({
  newFeatures: '',
  newMinSize: 1,
  newMaxSize: 2,
  newSizeBasis: '',
  pointIds: [] as number[],
  sourceMinSize: 1,
  sourceMaxSize: 2,
  sourceSizeBasis: '',
  moveSightingIds: '',
  reason: '',
})
const submitting = ref(false)
const errorText = ref('')

watch(
  () => props.modelValue,
  (open) => {
    if (!open || !props.group) return
    form.newFeatures = `${props.group.features}（拆出子群）`
    form.newMinSize = 1
    form.newMaxSize = Math.max(2, Math.min(props.group.maxSize, 3))
    form.newSizeBasis = props.group.sizeBasis
    form.pointIds = props.group.points.map((point) => point.id)
    form.sourceMinSize = props.group.minSize
    form.sourceMaxSize = props.group.maxSize
    form.sourceSizeBasis = props.group.sizeBasis
    form.moveSightingIds = ''
    form.reason = ''
    errorText.value = ''
  },
)

function togglePoint(id: number): void {
  const index = form.pointIds.indexOf(id)
  if (index >= 0) form.pointIds.splice(index, 1)
  else form.pointIds.push(id)
}

async function submit(): Promise<void> {
  if (!props.group || submitting.value) return
  if (!form.newFeatures.trim()) {
    ElMessage.warning('请填写新群体识别特征')
    return
  }
  if (!form.newSizeBasis.trim() || !form.sourceSizeBasis.trim()) {
    ElMessage.warning('源群体与新群体的计数依据都要填写')
    return
  }
  if (form.newMinSize < 1 || form.newMaxSize < form.newMinSize
    || form.sourceMinSize < 1 || form.sourceMaxSize < form.sourceMinSize) {
    ElMessage.warning('规模区间需满足 1 ≤ min ≤ max')
    return
  }
  if (!form.pointIds.length) {
    ElMessage.warning('新群体至少选择一个活动点位')
    return
  }
  if (form.reason.trim().length < 5) {
    ElMessage.warning('拆分原因至少 5 个字')
    return
  }
  const movedIds = form.moveSightingIds
    .split(/[\s,，;；]+/)
    .map((item) => Number(item.trim()))
    .filter((id) => Number.isInteger(id) && id > 0)
  submitting.value = true
  try {
    const result = await archiveApi.splitGroup(props.group.groupCode, {
      newFeatures: form.newFeatures.trim(),
      newMinSize: form.newMinSize,
      newMaxSize: form.newMaxSize,
      newSizeBasis: form.newSizeBasis.trim(),
      pointIds: form.pointIds,
      sourceMinSize: form.sourceMinSize,
      sourceMaxSize: form.sourceMaxSize,
      sourceSizeBasis: form.sourceSizeBasis.trim(),
      moveSightingIds: movedIds.length ? movedIds : undefined,
      reason: form.reason.trim(),
    })
    ElMessage.success(`已拆出新群体 ${result.newCode}，迁移目击 ${result.movedSightings} 条`)
    emit('saved', result.newCode)
    emit('update:modelValue', false)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '群体拆分失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="拆分群体"
    width="760px"
    append-to-body
    @update:model-value="emit('update:modelValue', false)"
  >
    <div class="dialog-note">
      源群体 <span class="mono">{{ group?.groupCode }}</span> 保留并缩小范围，新群体自动发新编号；可选把源群体的某几条目击迁给新群体。
    </div>
    <div class="form-grid">
      <div class="field form-span-2">
        <label>新群体识别特征 <b>*</b></label>
        <textarea v-model="form.newFeatures" class="textarea" maxlength="255" />
      </div>
      <div class="field">
        <label>新群体规模下限 <b>*</b></label>
        <input v-model.number="form.newMinSize" class="input" type="number" min="1" />
      </div>
      <div class="field">
        <label>新群体规模上限 <b>*</b></label>
        <input v-model.number="form.newMaxSize" class="input" type="number" min="1" />
      </div>
      <div class="field form-span-2">
        <label>新群体计数依据 <b>*</b></label>
        <input v-model="form.newSizeBasis" class="input" maxlength="64" />
      </div>
      <div class="field">
        <label>源群体规模下限 <b>*</b></label>
        <input v-model.number="form.sourceMinSize" class="input" type="number" min="1" />
      </div>
      <div class="field">
        <label>源群体规模上限 <b>*</b></label>
        <input v-model.number="form.sourceMaxSize" class="input" type="number" min="1" />
      </div>
      <div class="field form-span-2">
        <label>源群体计数依据 <b>*</b></label>
        <input v-model="form.sourceSizeBasis" class="input" maxlength="64" />
      </div>
      <div class="field form-span-2">
        <label>新群体活动点位 <b>*</b></label>
        <div class="check-grid">
          <label v-for="point in points" :key="point.id" class="check-item">
            <input
              type="checkbox"
              :checked="form.pointIds.includes(point.id)"
              @change="togglePoint(point.id)"
            />
            {{ point.name }}
          </label>
        </div>
      </div>
      <div class="field form-span-2">
        <label>迁移目击 ID（可选）</label>
        <input v-model="form.moveSightingIds" class="input mono" placeholder="例如 31, 32；只能填当前群体已下载的目击" />
        <div class="muted small" style="margin-top: 4px">
          当前群体最近目击：
          <span v-for="item in sightings" :key="item.id" class="mono sighting-id">{{ item.id }}</span>
          <span v-if="!sightings.length">暂无</span>
        </div>
      </div>
      <div class="field form-span-2">
        <label>拆分原因 <b>*</b></label>
        <input v-model="form.reason" class="input" maxlength="255" placeholder="至少 5 个字" />
      </div>
    </div>
    <div v-if="errorText" class="notice">{{ errorText }}</div>
    <template #footer>
      <button class="btn ghost" type="button" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '拆分中…' : '确认拆分' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-note {
  margin-bottom: 14px;
  padding: 9px 12px;
  background: var(--chip);
  border-radius: var(--radius-sm);
  color: var(--muted);
  font-size: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}

.form-span-2 {
  grid-column: span 2;
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}

.check-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
}

.check-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ink);
}

.sighting-id {
  margin-right: 8px;
  color: var(--accent);
}

.small {
  font-size: 11px;
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
