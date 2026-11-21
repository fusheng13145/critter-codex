<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as taskApi from '@/api/tasks'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import PanelCard from '@/components/PanelCard.vue'
import PhotoUploader from '@/components/PhotoUploader.vue'
import type {
  TaskStatus,
  TaskType,
  TaskView,
  VolunteerTaskView,
} from '@/api/types'
import { dateTime } from '@/utils/format'

/** D3 P1 志愿任务：列表、发布、报名、打卡、我的任务。 */

const auth = useAuthStore()

const activeTab = ref<'board' | 'mine'>('board')
const rows = ref<TaskView[]>([])
const total = ref(0)
const page = reactive({ page: 1, size: 10 })
const loading = ref(false)
const errorText = ref('')
const statusFilter = ref<TaskStatus | ''>('OPEN')
const typeFilter = ref<TaskType | ''>('')

const my = ref<VolunteerTaskView | null>(null)
const myLoading = ref(false)

const createVisible = ref(false)
const saving = ref(false)
const form = reactive({
  title: '',
  type: 'FEED' as TaskType,
  pointId: 0,
  startAt: '',
  quota: 3,
  durationHours: 2,
  description: '',
})

const checkinVisible = ref(false)
const checkinTarget = ref<TaskView | null>(null)
const photos = ref<string[]>([])
const checkinBusy = ref(false)

const isAdmin = computed(() => auth.isAdmin)
const canVolunteer = computed(() => auth.hasRole('VOLUNTEER'))

const typeOptions: Array<{ value: TaskType; label: string }> = [
  { value: 'FEED', label: '投喂' },
  { value: 'CLEAN', label: '清洁' },
  { value: 'RESCUE', label: '救助协助' },
  { value: 'PUBLICITY', label: '宣传' },
  { value: 'TNR', label: 'TNR 协助' },
  { value: 'OTHER', label: '其他' },
]

const statusOptions: Array<{ value: TaskStatus | ''; label: string }> = [
  { value: '', label: '全部状态' },
  { value: 'OPEN', label: '报名中' },
  { value: 'FULL', label: '已满额' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'FINISHED', label: '已完成' },
]

function typeLabel(type: TaskType): string {
  return typeOptions.find((item) => item.value === type)?.label ?? type
}

function statusLabel(status: TaskStatus): string {
  return statusOptions.find((item) => item.value === status)?.label ?? status
}

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const data = await taskApi.fetchTasks({
      status: statusFilter.value,
      type: typeFilter.value,
      page: page.page,
      size: page.size,
    })
    rows.value = data.list
    total.value = data.page.total
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '任务加载失败'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadMine(): Promise<void> {
  if (!canVolunteer.value) {
    ElMessage.warning('仅志愿者可查看我的任务')
    return
  }
  myLoading.value = true
  try {
    my.value = await taskApi.fetchMyTasks({ page: 1, size: 20 })
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '我的任务加载失败')
    my.value = null
  } finally {
    myLoading.value = false
  }
}

async function join(task: TaskView): Promise<void> {
  try {
    await taskApi.joinTask(task.taskId)
    ElMessage.success('报名成功，请按时间到场打卡')
    await load()
    if (my.value) await loadMine()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '报名失败')
  }
}

function openCheckin(task: TaskView): void {
  checkinTarget.value = task
  photos.value = []
  checkinVisible.value = true
}

async function submitCheckin(): Promise<void> {
  const target = checkinTarget.value
  if (!target) return
  const photo = photos.value[0]
  if (!photo) {
    ElMessage.warning('请先上传打卡照片')
    return
  }
  checkinBusy.value = true
  try {
    const result = await taskApi.checkinTask(target.taskId, photo)
    ElMessage.success(`打卡成功，本次 ${result.hours} 小时，累计 ${result.totalHours} 小时`)
    checkinVisible.value = false
    await load()
    if (my.value) await loadMine()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '打卡失败')
  } finally {
    checkinBusy.value = false
  }
}

function openCreate(): void {
  form.title = ''
  form.type = 'FEED'
  form.pointId = 0
  form.startAt = ''
  form.quota = 3
  form.durationHours = 2
  form.description = ''
  createVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!form.title.trim() || !form.startAt) {
    ElMessage.warning('请填写任务标题与开始时间')
    return
  }
  saving.value = true
  try {
    await taskApi.createTask({
      title: form.title.trim(),
      type: form.type,
      pointId: form.pointId > 0 ? form.pointId : null,
      startAt: form.startAt,
      quota: form.quota,
      durationHours: form.durationHours,
      description: form.description.trim() || null,
    })
    ElMessage.success('任务已发布')
    createVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '任务发布失败')
  } finally {
    saving.value = false
  }
}

function changePage(next: number): void {
  page.page = next
  load()
}

onMounted(load)
</script>

<template>
  <PageHero title="志愿任务" desc="管理员发布任务，活跃志愿者报名；到场拍照打卡后按任务时长累计服务时长。">
    <template #actions>
      <button v-if="isAdmin" class="btn" type="button" @click="openCreate">发布任务</button>
      <button v-if="canVolunteer" class="btn ghost" type="button" @click="activeTab = 'mine'; loadMine()">
        我的任务
      </button>
    </template>
  </PageHero>

  <div class="card">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="任务广场" name="board">
        <div class="toolbar">
          <el-select v-model="statusFilter" style="width: 140px" @change="page.page = 1; load()">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="typeFilter" style="width: 150px" @change="page.page = 1; load()">
            <el-option label="全部类型" value="" />
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <span class="muted">共 {{ total }} 个任务</span>
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>

        <div v-if="errorText" class="notice">{{ errorText }}</div>
        <div v-else-if="loading && !rows.length" class="muted">任务加载中…</div>
        <div v-else-if="!rows.length" class="muted">当前没有符合条件的任务</div>

        <div v-else class="task-grid">
          <PanelCard v-for="task in rows" :key="task.taskId" :title="task.title" :more="task.taskNo">
            <div class="task-meta">
              <span class="tag">{{ typeLabel(task.type) }}</span>
              <span class="tag" :class="task.status === 'OPEN' ? 'success' : task.status === 'FULL' ? 'warning' : 'gray'">
                {{ statusLabel(task.status) }}
              </span>
              <span class="muted small">{{ dateTime(task.startAt) }}</span>
            </div>
            <p class="task-desc">{{ task.description || '暂无补充说明' }}</p>
            <div class="task-facts">
              <span>地点：{{ task.pointName || '待定' }}</span>
              <span>名额：{{ task.joined }} / {{ task.quota }}</span>
              <span>时长：{{ task.durationHours }} 小时</span>
            </div>
            <div class="task-actions">
              <button
                v-if="canVolunteer && !task.joinedByMe && task.status === 'OPEN' && task.joined < task.quota"
                class="btn"
                type="button"
                @click="join(task)"
              >
                报名
              </button>
              <button
                v-else-if="task.joinedByMe && !task.checkinAt"
                class="btn"
                type="button"
                @click="openCheckin(task)"
              >
                到场打卡
              </button>
              <span v-else-if="task.joinedByMe" class="tag success">已打卡</span>
              <span v-else-if="task.status === 'FULL'" class="muted small">名额已满</span>
            </div>
          </PanelCard>
        </div>

        <div v-if="total > page.size" class="pager">
          <el-pagination
            layout="prev, pager, next"
            :current-page="page.page"
            :page-size="page.size"
            :total="total"
            background
            @current-change="changePage"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="canVolunteer" label="我的任务" name="mine">
        <div v-if="myLoading" class="muted">加载中…</div>
        <template v-else-if="my">
          <div class="mine-summary">
            <div><b>{{ my.totalHours }}</b><span>累计服务时长（小时）</span></div>
            <div><b>{{ my.total }}</b><span>报名任务</span></div>
            <div><b>{{ my.volunteerStatus === 'ACTIVE' ? '已通过' : my.volunteerStatus }}</b><span>志愿者身份</span></div>
          </div>
          <el-table :data="my.tasks" stripe>
            <el-table-column prop="taskNo" label="任务编号" width="170" />
            <el-table-column prop="title" label="任务" min-width="180" />
            <el-table-column label="开始时间" width="170">
              <template #default="{ row }">{{ dateTime(row.startAt) }}</template>
            </el-table-column>
            <el-table-column label="报名时间" width="170">
              <template #default="{ row }">{{ dateTime(row.signedAt) }}</template>
            </el-table-column>
            <el-table-column label="打卡" width="110">
              <template #default="{ row }">
                <span class="tag" :class="row.checkedIn ? 'success' : 'gray'">
                  {{ row.checkedIn ? '已打卡' : '未打卡' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="时长" width="90">
              <template #default="{ row }">{{ row.hours }}h</template>
            </el-table-column>
          </el-table>
        </template>
        <div v-else class="muted">还没有报名记录</div>
      </el-tab-pane>
    </el-tabs>

    <div class="footnote" style="text-align: left">
      来源：GET /tasks、POST /tasks、POST /tasks/{id}/join、POST /tasks/{id}/checkin、GET /tasks/mine。
    </div>
  </div>

  <el-dialog v-model="createVisible" title="发布志愿任务" width="560px">
    <el-form label-width="92px">
      <el-form-item label="任务标题">
        <el-input v-model="form.title" maxlength="64" show-word-limit />
      </el-form-item>
      <el-form-item label="任务类型">
        <el-select v-model="form.type" style="width: 100%">
          <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="点位 ID">
        <el-input-number v-model="form.pointId" :min="0" style="width: 180px" />
        <span class="muted small" style="margin-left: 8px">0 表示不指定点位</span>
      </el-form-item>
      <el-form-item label="开始时间">
        <el-date-picker
          v-model="form.startAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="选择开始时间"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="名额">
        <el-input-number v-model="form.quota" :min="1" :max="100" style="width: 160px" />
      </el-form-item>
      <el-form-item label="服务时长">
        <el-input-number v-model="form.durationHours" :min="0.5" :max="12" :step="0.5" :precision="1" style="width: 160px" />
        <span class="muted small" style="margin-left: 8px">小时</span>
      </el-form-item>
      <el-form-item label="补充说明">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <button class="btn ghost" type="button" @click="createVisible = false">取消</button>
      <button class="btn" type="button" :disabled="saving" @click="submitCreate">
        {{ saving ? '发布中…' : '发布' }}
      </button>
    </template>
  </el-dialog>

  <el-dialog v-model="checkinVisible" title="到场打卡" width="480px">
    <p class="muted" style="margin-top: 0">上传一张现场照片，系统按任务时长累计服务时长。</p>
    <PhotoUploader v-model="photos" :max="1" biz-type="TASK_CHECKIN" hint="单张不超过 5MB" />
    <template #footer>
      <button class="btn ghost" type="button" @click="checkinVisible = false">取消</button>
      <button class="btn" type="button" :disabled="checkinBusy" @click="submitCheckin">
        {{ checkinBusy ? '提交中…' : '确认打卡' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.task-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.task-meta,
.task-facts,
.task-actions,
.mine-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.task-meta {
  margin-bottom: 8px;
}

.task-desc {
  margin: 0 0 10px;
  color: var(--muted, #64748b);
  line-height: 1.6;
  min-height: 3em;
}

.task-facts {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--muted, #64748b);
}

.task-actions {
  padding-top: 10px;
  border-top: 1px dashed var(--line, #e5e7eb);
}

.mine-summary {
  gap: 42px;
  margin: 6px 0 18px;
}

.mine-summary div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mine-summary b {
  font-size: 22px;
}

.mine-summary span {
  color: var(--muted, #64748b);
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
