<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as tnrApi from '@/api/tnr'
import { BizError } from '@/api/http'
import PageHero from '@/components/PageHero.vue'
import { useAuthStore } from '@/stores/auth'
import type { TnrStage, TnrTaskView } from '@/api/types'
import { dateTime } from '@/utils/format'

/**
 * P10 TNR 全链路看板：抓捕 → 转运 → 手术 → 放归 → 观察。
 * 看板登录可读；发起与推进仅管理员，末节点完成时后端同步动物绝育标记。
 */

const auth = useAuthStore()

const STAGES: TnrStage[] = ['CATCH', 'TRANSPORT', 'SURGERY', 'RELEASE', 'OBSERVE']
const STAGE_LABELS: Record<TnrStage, string> = {
  CATCH: '抓捕',
  TRANSPORT: '转运',
  SURGERY: '手术',
  RELEASE: '放归',
  OBSERVE: '观察',
}
const STAGE_TYPES: Record<TnrStage, 'success' | 'warning' | 'info' | 'primary' | 'danger'> = {
  CATCH: 'info',
  TRANSPORT: 'primary',
  SURGERY: 'warning',
  RELEASE: 'success',
  OBSERVE: 'success',
}

const tasks = ref<TnrTaskView[]>([])
const loading = ref(false)
const page = reactive({ page: 1, size: 20, total: 0 })
const filters = reactive({ stage: '' as TnrStage | '', animalCode: '' })

const detail = ref<TnrTaskView | null>(null)
const detailVisible = ref(false)

const createVisible = ref(false)
const creating = ref(false)
const advanceVisible = ref(false)
const advancing = ref(false)
const advancingTask = ref<TnrTaskView | null>(null)

const createForm = reactive({
  animalId: null as number | null,
  occurredAt: new Date() as Date | null,
  note: '',
  fileIdText: '',
})

const advanceForm = reactive({
  occurredAt: new Date() as Date | null,
  note: '',
  fileIdText: '',
})

const advancingStage = computed<TnrStage | null>(() => {
  if (!advancingTask.value) return null
  const index = STAGES.indexOf(advancingTask.value.stage)
  return index < 0 || index >= STAGES.length - 1 ? null : STAGES[index + 1]
})

const openCount = computed(() => tasks.value.filter((task) => task.stage !== 'OBSERVE').length)

async function loadTasks(reset = false): Promise<void> {
  if (reset) page.page = 1
  loading.value = true
  try {
    const result = await tnrApi.fetchTnrTasks({
      stage: filters.stage || undefined,
      animalCode: filters.animalCode.trim() || undefined,
      page: page.page,
      size: page.size,
    })
    tasks.value = result.list
    page.total = result.page.total
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : 'TNR 看板加载失败')
  } finally {
    loading.value = false
  }
}

function openDetail(task: TnrTaskView): void {
  detail.value = task
  detailVisible.value = true
}

function openCreate(): void {
  createForm.animalId = null
  createForm.occurredAt = new Date()
  createForm.note = ''
  createForm.fileIdText = ''
  createVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!createForm.animalId || !createForm.occurredAt) {
    ElMessage.warning('动物 ID 与节点时间必填')
    return
  }
  creating.value = true
  try {
    const created = await tnrApi.createTnr({
      animalId: createForm.animalId,
      occurredAt: toOffsetIso(createForm.occurredAt),
      note: createForm.note.trim() || undefined,
      fileIds: splitIds(createForm.fileIdText),
    })
    createVisible.value = false
    ElMessage.success(`已发起 TNR 任务 ${created.tnrNo}`)
    detail.value = created
    detailVisible.value = true
    await loadTasks(true)
  } catch (error) {
    if (error instanceof BizError && error.code === 30403) {
      ElMessage.warning('该动物已有未完成 TNR 任务，请先推进原任务')
    } else if (error instanceof BizError && error.code === 30410) {
      ElMessage.warning('动物或处理人不存在，请核对动物 ID')
    } else {
      ElMessage.error(error instanceof BizError ? error.message : 'TNR 任务发起失败')
    }
  } finally {
    creating.value = false
  }
}

function openAdvance(task: TnrTaskView): void {
  advancingTask.value = task
  advanceForm.occurredAt = new Date()
  advanceForm.note = ''
  advanceForm.fileIdText = ''
  advanceVisible.value = true
}

async function submitAdvance(): Promise<void> {
  if (!advancingTask.value || !advancingStage.value || !advanceForm.occurredAt) {
    ElMessage.warning('节点时间必填')
    return
  }
  const fileIds = splitIds(advanceForm.fileIdText)
  if (advancingStage.value === 'SURGERY' && fileIds.length === 0) {
    ElMessage.warning('手术节点至少需要一张照片编号')
    return
  }

  advancing.value = true
  try {
    const updated = await tnrApi.advanceTnr(advancingTask.value.tnrNo, {
      stage: advancingStage.value,
      occurredAt: toOffsetIso(advanceForm.occurredAt),
      note: advanceForm.note.trim() || undefined,
      fileIds,
    })
    advanceVisible.value = false
    detail.value = updated
    detailVisible.value = true
    ElMessage.success(
      updated.stage === 'OBSERVE'
        ? `TNR ${updated.tnrNo} 已完成，动物绝育标记已同步`
        : `已推进至${STAGE_LABELS[updated.stage]}节点`,
    )
    await loadTasks(true)
  } catch (error) {
    if (error instanceof BizError && error.code === 30402) {
      ElMessage.warning('只能推进到当前节点的下一节点，已完成任务不可再推进')
    } else if (error instanceof BizError && error.code === 30601) {
      ElMessage.warning('手术节点至少需要一张照片编号')
    } else if (error instanceof BizError && error.code === 30301) {
      ElMessage.warning(error.message || '节点时间必须晚于上一节点')
    } else {
      ElMessage.error(error instanceof BizError ? error.message : 'TNR 节点推进失败')
    }
  } finally {
    advancing.value = false
  }
}

function splitIds(text: string): string[] {
  return text
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

/** 后端 occurredAt 为 OffsetDateTime，提交时携带本机时区偏移。 */
function toOffsetIso(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  const offsetMinutes = -date.getTimezoneOffset()
  const sign = offsetMinutes >= 0 ? '+' : '-'
  const hours = pad(Math.floor(Math.abs(offsetMinutes) / 60))
  const minutes = pad(Math.abs(offsetMinutes) % 60)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
    + `${sign}${hours}:${minutes}`
}

function stageLabel(stage: TnrStage): string {
  return STAGE_LABELS[stage] ?? stage
}

function stageType(stage: TnrStage): 'success' | 'warning' | 'info' | 'primary' | 'danger' {
  return STAGE_TYPES[stage] ?? 'info'
}

onMounted(() => loadTasks())
</script>

<template>
  <div>
    <PageHero
      title="TNR 全链路"
      desc="校园流浪动物抓捕、转运、绝育手术、放归与观察五个节点闭环留痕；末节点完成自动写入绝育标记。"
    >
      <template #actions>
        <el-button v-if="auth.isAdmin" type="primary" @click="openCreate">发起 TNR</el-button>
      </template>
    </PageHero>

    <div class="board-summary">
      <div class="summary-item">
        <span class="muted">当前筛选</span>
        <strong>{{ page.total }} 个任务</strong>
      </div>
      <div class="summary-item">
        <span class="muted">本页未完成</span>
        <strong>{{ openCount }}</strong>
      </div>
      <div class="summary-flow">
        <span
          v-for="(stage, index) in STAGES"
          :key="stage"
          class="flow-step"
        >
          <el-tag :type="stageType(stage)" effect="plain" size="small">{{ STAGE_LABELS[stage] }}</el-tag>
          <span v-if="index < STAGES.length - 1" class="flow-arrow">→</span>
        </span>
      </div>
    </div>

    <div class="card">
      <div class="section-head">
        <div>
          <h2 class="section-title">任务看板</h2>
          <p class="muted">按阶段与动物编号筛选；登录用户均可查看，发起与推进仅管理员。</p>
        </div>
        <el-button text :loading="loading" @click="loadTasks()">刷新</el-button>
      </div>

      <div class="filter-row">
        <el-select
          v-model="filters.stage"
          clearable
          placeholder="全部阶段"
          style="width: 160px"
          @change="loadTasks(true)"
        >
          <el-option v-for="stage in STAGES" :key="stage" :label="STAGE_LABELS[stage]" :value="stage" />
        </el-select>
        <el-input
          v-model="filters.animalCode"
          clearable
          placeholder="动物编号，如 CAT-2026-031"
          style="max-width: 260px"
          @keyup.enter="loadTasks(true)"
          @clear="loadTasks(true)"
        />
        <el-button type="primary" plain @click="loadTasks(true)">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="tasks" stripe class="task-table">
        <el-table-column label="任务编号" min-width="160">
          <template #default="{ row }">
            <button class="link-button mono" @click="openDetail(row)">{{ row.tnrNo }}</button>
          </template>
        </el-table-column>
        <el-table-column label="动物" min-width="160">
          <template #default="{ row }">
            <div>{{ row.animalName || '未命名' }}</div>
            <div class="muted small mono">{{ row.animalCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="当前节点" width="110">
          <template #default="{ row }">
          <el-tag :type="stageType(row.stage)" effect="light">{{ stageLabel(row.stage) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间线" width="110">
          <template #default="{ row }">
            <span class="muted">{{ row.nodes.length }} / 5</span>
          </template>
        </el-table-column>
        <el-table-column label="责任人" min-width="110">
          <template #default="{ row }">{{ row.handlerName || `#${row.handlerId}` }}</template>
        </el-table-column>
        <el-table-column label="最近节点时间" min-width="150">
          <template #default="{ row }">{{ dateTime(row.occurredAt) }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="180">
          <template #default="{ row }">
            <span class="ellipsis">{{ row.note || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="auth.isAdmin && row.stage !== 'OBSERVE'"
              link
              type="success"
              @click="openAdvance(row)"
            >推进</el-button>
            <el-tag v-else-if="row.stage === 'OBSERVE'" type="success" effect="plain" size="small">已完成</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-row">
        <el-pagination
          v-model:current-page="page.page"
          v-model:page-size="page.size"
          :page-sizes="[10, 20, 50]"
          :total="page.total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadTasks()"
          @size-change="loadTasks(true)"
        />
      </div>
    </div>

    <el-dialog v-model="createVisible" title="发起 TNR 任务" width="560px" :close-on-click-modal="false">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="首节点固定为抓捕；同一动物存在未完成 TNR 任务时不能重复发起。"
        class="dialog-alert"
      />
      <el-form label-width="100px">
        <el-form-item label="动物 ID" required>
          <el-input-number v-model="createForm.animalId" :min="1" :controls="false" placeholder="档案主键" />
          <span class="field-hint">暂以档案 ID 关联，可在档案详情查看。</span>
        </el-form-item>
        <el-form-item label="抓捕时间" required>
          <el-date-picker v-model="createForm.occurredAt" type="datetime" style="width: 100%" />
        </el-form-item>
        <el-form-item label="现场备注">
          <el-input
            v-model="createForm.note"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="如校园东区抓捕，装入诱捕笼"
          />
        </el-form-item>
        <el-form-item label="照片编号">
          <el-input v-model="createForm.fileIdText" placeholder="多个编号用逗号分隔，可空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">发起任务</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="advanceVisible"
      :title="advancingStage ? `推进至「${STAGE_LABELS[advancingStage]}」` : '推进节点'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-alert
        v-if="advancingStage === 'SURGERY'"
        type="warning"
        :closable="false"
        show-icon
        title="手术节点必须至少上传一张照片编号，作为绝育手术留痕。"
        class="dialog-alert"
      />
      <el-alert
        v-else-if="advancingStage === 'OBSERVE'"
        type="success"
        :closable="false"
        show-icon
        title="观察节点完成后任务闭环，动物绝育标记与绝育日期将同步更新。"
        class="dialog-alert"
      />
      <el-form label-width="100px">
        <el-form-item label="任务编号">
          <span class="mono">{{ advancingTask?.tnrNo }}</span>
          <span class="field-hint">当前节点 {{ advancingTask ? stageLabel(advancingTask.stage) : '—' }}</span>
        </el-form-item>
        <el-form-item label="节点时间" required>
          <el-date-picker v-model="advanceForm.occurredAt" type="datetime" style="width: 100%" />
          <span class="field-hint">必须晚于上一节点时间。</span>
        </el-form-item>
        <el-form-item label="节点备注">
          <el-input
            v-model="advanceForm.note"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="如完成绝育手术、送回校园原点位"
          />
        </el-form-item>
        <el-form-item label="照片编号" :required="advancingStage === 'SURGERY'">
          <el-input v-model="advanceForm.fileIdText" placeholder="多个编号用逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="advanceVisible = false">取消</el-button>
        <el-button type="primary" :loading="advancing" @click="submitAdvance">确认推进</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="TNR 任务详情" size="520px">
      <template v-if="detail">
        <div class="detail-hero">
          <div class="muted">任务编号</div>
          <div class="detail-no mono">{{ detail.tnrNo }}</div>
          <div class="detail-animal">
            {{ detail.animalName || '未命名' }} · <span class="mono">{{ detail.animalCode }}</span>
          </div>
          <div class="detail-tags">
            <el-tag :type="stageType(detail.stage)">{{ stageLabel(detail.stage) }}</el-tag>
            <el-tag v-if="detail.stage === 'OBSERVE'" type="success" effect="plain">已完成</el-tag>
          </div>
          <div v-if="auth.isAdmin && detail.stage !== 'OBSERVE'" class="detail-actions">
            <el-button type="primary" plain size="small" @click="openAdvance(detail)">推进下一节点</el-button>
          </div>
        </div>

        <div class="detail-grid">
          <div class="detail-item">
            <span class="muted">责任人</span>
            <strong>{{ detail.handlerName || `#${detail.handlerId}` }}</strong>
          </div>
          <div class="detail-item">
            <span class="muted">最近节点时间</span>
            <strong>{{ dateTime(detail.occurredAt) }}</strong>
          </div>
          <div class="detail-item">
            <span class="muted">节点进度</span>
            <strong>{{ detail.nodes.length }} / 5</strong>
          </div>
          <div class="detail-item">
            <span class="muted">发起时间</span>
            <strong>{{ dateTime(detail.createdAt) }}</strong>
          </div>
        </div>

        <div v-if="detail.note" class="detail-block">
          <h3>最近节点备注</h3>
          <p>{{ detail.note }}</p>
        </div>

        <div class="detail-block">
          <h3>节点时间线</h3>
          <el-timeline>
            <el-timeline-item
              v-for="node in detail.nodes"
              :key="node.stage"
              :timestamp="dateTime(node.occurredAt)"
              placement="top"
              :color="node.stage === 'OBSERVE' ? '#3f8f6b' : '#7b8aa0'"
            >
              <div class="node-head">
                <strong>{{ stageLabel(node.stage) }}</strong>
                <span class="muted small">{{ node.handlerName || `#${node.handlerId}` }}</span>
              </div>
              <p v-if="node.note" class="node-note">{{ node.note }}</p>
              <div v-if="node.fileIds.length" class="file-tags">
                <el-tag v-for="fileId in node.fileIds" :key="fileId" effect="plain" size="small">{{ fileId }}</el-tag>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.board-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 18px;
  padding: 14px 18px;
  margin-bottom: 16px;
  background: #f4f8f6;
  border: 1px solid #e2ede7;
  border-radius: 12px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-flow {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.flow-step {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.flow-arrow {
  color: #9aa7b5;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.section-title {
  margin: 0 0 4px;
  font-size: 18px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.task-table {
  width: 100%;
}

.link-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: #2f6f57;
  cursor: pointer;
  text-align: left;
}

.link-button:hover {
  text-decoration: underline;
}

.pager-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.dialog-alert {
  margin-bottom: 14px;
}

.field-hint {
  margin-left: 10px;
  color: #8792a2;
  font-size: 12px;
}

.detail-hero {
  padding: 16px;
  margin-bottom: 18px;
  background: #f4f7fb;
  border-radius: 12px;
}

.detail-no {
  margin: 4px 0;
  font-size: 20px;
  font-weight: 700;
}

.detail-animal {
  color: #4d5a6a;
}

.detail-tags {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.detail-actions {
  margin-top: 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #e6ebf2;
  border-radius: 10px;
}

.detail-block {
  margin-bottom: 18px;
}

.detail-block h3 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #334155;
}

.detail-block p {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
}

.node-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.node-note {
  margin: 6px 0;
  color: #52606f;
}

.file-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}

.small {
  font-size: 12px;
}

.ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

@media (max-width: 720px) {
  .summary-flow {
    margin-left: 0;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
