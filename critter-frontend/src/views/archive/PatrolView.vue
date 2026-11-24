<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PanelCard from '@/components/PanelCard.vue'
import PageState from '@/components/PageState.vue'
import PhotoUploader from '@/components/PhotoUploader.vue'
import { dateTime } from '@/utils/format'
import type { PatrolView, PointView } from '@/api/types'

/**
 * P02 巡查记录（FR-R-05）：登记巡查，必要时把一次巡查转成目击；
 * 转目击时若指定群体，会刷新该群体最近目击时间并合并点位。
 */
const auth = useAuthStore()
const points = ref<PointView[]>([])
const records = ref<PatrolView[]>([])
const total = ref(0)
const loading = ref(false)
const errorText = ref('')
const submitting = ref(false)
const uploading = ref(false)

const filter = reactive({ pointId: null as number | null, from: '', to: '' })
const form = reactive({
  pointId: null as number | null,
  patrolAt: nowLocal(),
  countEstimate: null as number | null,
  hasAnomaly: false,
  anomalyNote: '',
  fileIds: [] as string[],
})

const convertVisible = ref(false)
const converting = ref<PatrolView | null>(null)
const convertForm = reactive({ groupCode: '' })
const convertError = ref('')

const canSubmit = computed(() => Boolean(form.pointId) && Boolean(form.patrolAt))

function nowLocal(): string {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** datetime-local 没有时区，这里按浏览器本地时区补成 ISO-8601。 */
function toOffsetIso(local: string): string {
  const value = local.length === 16 ? `${local}:00` : local
  const date = new Date(value)
  const offsetMinutes = -date.getTimezoneOffset()
  const sign = offsetMinutes >= 0 ? '+' : '-'
  const hours = String(Math.floor(Math.abs(offsetMinutes) / 60)).padStart(2, '0')
  const minutes = String(Math.abs(offsetMinutes) % 60).padStart(2, '0')
  return `${value}${sign}${hours}:${minutes}`
}

function dateStart(value: string): string | undefined {
  return value ? `${value}T00:00:00+08:00` : undefined
}

function dateEnd(value: string): string | undefined {
  return value ? `${value}T23:59:59+08:00` : undefined
}

async function loadPoints(): Promise<void> {
  try {
    points.value = await archiveApi.fetchPoints()
  } catch {
    points.value = []
  }
}

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const page = await archiveApi.fetchPatrols({
      pointId: filter.pointId ?? undefined,
      from: dateStart(filter.from),
      to: dateEnd(filter.to),
      page: 1,
      size: 30,
    })
    records.value = page.list
    total.value = page.page.total
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '巡查记录加载失败'
  } finally {
    loading.value = false
  }
}

async function create(): Promise<void> {
  if (submitting.value || uploading.value) return
  if (!canSubmit.value) {
    ElMessage.warning('请选择点位和巡查时间')
    return
  }
  if (form.hasAnomaly && !form.anomalyNote.trim()) {
    ElMessage.warning('发现异常时请填写异常说明')
    return
  }
  submitting.value = true
  try {
    await archiveApi.createPatrol({
      pointId: form.pointId as number,
      patrolAt: toOffsetIso(form.patrolAt),
      countEstimate: form.countEstimate,
      hasAnomaly: form.hasAnomaly,
      anomalyNote: form.anomalyNote.trim() || null,
      fileIds: form.fileIds,
    })
    ElMessage.success('巡查已登记')
    form.anomalyNote = ''
    form.hasAnomaly = false
    form.countEstimate = null
    form.fileIds = []
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '巡查登记失败')
  } finally {
    submitting.value = false
  }
}

function openConvert(record: PatrolView): void {
  converting.value = record
  convertForm.groupCode = ''
  convertError.value = ''
  convertVisible.value = true
}

async function convert(): Promise<void> {
  if (!converting.value) return
  try {
    const result = await archiveApi.convertPatrol(
      converting.value.id,
      convertForm.groupCode.trim() || null,
    )
    ElMessage.success(`已转为目击记录 #${result.id}`)
    convertVisible.value = false
    await load()
  } catch (error) {
    convertError.value = error instanceof BizError ? error.message : '转目击失败'
  }
}

async function resetFilter(): Promise<void> {
  filter.pointId = null
  filter.from = ''
  filter.to = ''
  await load()
}

onMounted(() => {
  loadPoints()
  load()
})
</script>

<template>
  <div>
    <PageHero
      title="巡查记录"
    >
      <template #actions>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '个体档案', path: '/animals', matchPrefix: true },
        { label: '群体档案', path: '/groups' },
        { label: '物种名录', path: '/species' },
        { label: '点位维护', path: '/points' },
        { label: '巡查记录', path: '/patrols' },
      ]"
    />

    <div v-if="!auth.isAdmin" class="notice" style="margin-bottom: 16px">
      需动物管理员权限
    </div>

    <div class="patrol-layout">
      <PanelCard title="登记巡查">
        <div class="form-grid">
          <div class="field">
            <label>巡查点位 <b>*</b></label>
            <select v-model.number="form.pointId" class="select">
              <option :value="null" disabled>请选择点位</option>
              <option v-for="point in points" :key="point.id" :value="point.id">
                {{ point.name }} · {{ point.area }}
              </option>
            </select>
          </div>
          <div class="field">
            <label>巡查时间 <b>*</b></label>
            <input v-model="form.patrolAt" class="input" type="datetime-local" />
          </div>
          <div class="field">
            <label>目测数量</label>
            <input v-model.number="form.countEstimate" class="input" type="number" min="1" placeholder="可空" />
          </div>
          <div class="field">
            <label>异常</label>
            <label class="switch-line">
              <input v-model="form.hasAnomaly" type="checkbox" />
              发现受伤、异常聚集等情况
            </label>
          </div>
          <div v-if="form.hasAnomaly" class="field form-span-2">
            <label>异常说明 <b>*</b></label>
            <textarea v-model="form.anomalyNote" class="textarea" maxlength="255" placeholder="说明异常表现与建议处置" />
          </div>
          <div class="field form-span-2">
            <label>现场照片</label>
            <PhotoUploader v-model="form.fileIds" :max="6" biz-type="PATROL" @uploading="uploading = $event" />
          </div>
        </div>
        <button class="btn" type="button" :disabled="submitting || uploading || !canSubmit" @click="create">
          {{ submitting ? '登记中…' : '登记巡查' }}
        </button>
      </PanelCard>

      <div>
        <div class="toolbar">
          <select v-model.number="filter.pointId" class="select">
            <option :value="null">全部点位</option>
            <option v-for="point in points" :key="point.id" :value="point.id">{{ point.name }}</option>
          </select>
          <input v-model="filter.from" class="input" type="date" />
          <span class="muted">至</span>
          <input v-model="filter.to" class="input" type="date" />
          <button class="btn sm" type="button" @click="load">筛选</button>
          <button class="btn sm ghost" type="button" @click="resetFilter">重置</button>
        </div>

        <PageState :loading="loading" :error="errorText" :empty="!records.length">
          <PanelCard title="巡查流水">
            <div class="table-wrap">
              <table class="native">
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>点位</th>
                    <th>数量 / 异常</th>
                    <th>照片</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="record in records" :key="record.id">
                    <td>{{ dateTime(record.patrolAt) }}</td>
                    <td>{{ record.point?.name ?? '—' }}</td>
                    <td>
                      <div>{{ record.countEstimate ?? '未记录' }} 只</div>
                      <span class="tag" :class="{ gray: !record.hasAnomaly }">
                        {{ record.hasAnomaly ? '发现异常' : '正常' }}
                      </span>
                      <div v-if="record.anomalyNote" class="muted small">{{ record.anomalyNote }}</div>
                    </td>
                    <td>{{ record.fileIds.length }} 张</td>
                    <td>
                      <span v-if="record.convertedSightingId" class="tag">已转目击 #{{ record.convertedSightingId }}</span>
                      <span v-else class="tag gray">未转目击</span>
                    </td>
                    <td>
                      <button
                        class="btn sm ghost"
                        type="button"
                        :disabled="Boolean(record.convertedSightingId) || !auth.isAdmin"
                        @click="openConvert(record)"
                      >
                        转目击
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </PanelCard>
        </PageState>
      </div>
    </div>

    <el-dialog v-model="convertVisible" title="巡查转目击" width="520px" append-to-body>
      <div class="dialog-note">
        巡查 #{{ converting?.id }} · {{ converting?.point?.name }} · {{ dateTime(converting?.patrolAt) }}
      </div>
      <div class="field">
        <label>目标群体编号（可空）</label>
        <input v-model="convertForm.groupCode" class="input mono" placeholder="例如 GRP-PASSER-2026-0007" />
      </div>
      <div v-if="convertError" class="notice">{{ convertError }}</div>
      <template #footer>
        <button class="btn ghost" type="button" @click="convertVisible = false">取消</button>
        <button class="btn" type="button" @click="convert">确认转目击</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.patrol-layout {
  display: grid;
  grid-template-columns: 380px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}

.form-span-2 {
  grid-column: span 2;
}

.switch-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--muted);
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}

.dialog-note {
  margin-bottom: 14px;
  padding: 9px 12px;
  background: var(--chip);
  border-radius: var(--radius-sm);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.small {
  font-size: 11px;
}

@media (max-width: 1100px) {
  .patrol-layout {
    grid-template-columns: 1fr;
  }
}
</style>
