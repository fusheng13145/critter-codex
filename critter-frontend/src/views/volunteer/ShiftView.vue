<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api/shifts'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import type { AppealView, ShiftSignupView, ShiftView, SuspensionView } from '@/api/p2types'

const auth = useAuthStore()
const isAdmin = computed(() => auth.isAdmin)
const tab = ref('week')

const suspension = ref<SuspensionView | null>(null)
const rows = ref<ShiftView[]>([])
const loading = ref(false)
const weekStart = ref(toIso(mondayOf(new Date())))
const weekEnd = ref(toIso(addDays(mondayOf(new Date()), 6)))
const mineOnly = ref(false)

const createVisible = ref(false)
const creating = ref(false)
const form = reactive({
  title: '',
  pointId: undefined as number | undefined,
  shiftDate: '',
  startTime: '09:00:00',
  endTime: '11:00:00',
  capacity: 4,
})

const signupVisible = ref(false)
const signupLoading = ref(false)
const signupTarget = ref<ShiftView | null>(null)
const signups = ref<ShiftSignupView[]>([])

const appealVisible = ref(false)
const appealText = ref('')
const appealing = ref(false)
const appeals = ref<AppealView[]>([])
const appealsLoading = ref(false)

const weekLabel = computed(() => `${weekStart.value} ~ ${weekEnd.value}`)
const suspended = computed(() => suspension.value?.status === 'SUSPENDED')

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function toIso(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function mondayOf(date: Date): Date {
  const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const offset = (copy.getDay() + 6) % 7
  copy.setDate(copy.getDate() - offset)
  return copy
}

function addDays(date: Date, days: number): Date {
  const copy = new Date(date)
  copy.setDate(copy.getDate() + days)
  return copy
}

function shiftWeek(delta: number): void {
  weekStart.value = toIso(addDays(new Date(weekStart.value), delta * 7))
  weekEnd.value = toIso(addDays(new Date(weekEnd.value), delta * 7))
  loadWeek()
}

function timeText(value: string): string {
  return value?.slice(0, 5) ?? ''
}

function canSign(row: ShiftView): boolean {
  return !suspended.value && !row.signed && row.status === 'OPEN' && row.booked < row.capacity
}

async function loadWeek(): Promise<void> {
  loading.value = true
  try {
    rows.value = await api.fetchShifts({
      from: weekStart.value,
      to: weekEnd.value,
      mine: mineOnly.value,
    })
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '排班加载失败')
  } finally {
    loading.value = false
  }
}

async function loadSuspension(): Promise<void> {
  try {
    suspension.value = await api.fetchSuspension()
  } catch {
    suspension.value = null
  }
}

async function loadAppeals(): Promise<void> {
  appealsLoading.value = true
  try {
    if (isAdmin.value) {
      const data = await api.fetchAppeals({ status: 'PENDING', page: 1, size: 20 })
      appeals.value = data.list
    } else {
      appeals.value = await api.fetchMyAppeals()
    }
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '申诉列表加载失败')
  } finally {
    appealsLoading.value = false
  }
}

async function signup(row: ShiftView): Promise<void> {
  try {
    await api.signupShift(row.id)
    ElMessage.success('已报名该班次')
    await loadWeek()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '报名失败')
  }
}

async function checkin(row: ShiftView): Promise<void> {
  try {
    await api.checkinShift(row.id)
    ElMessage.success('已签到，连续缺勤已清零')
    await Promise.all([loadWeek(), loadSuspension()])
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '签到失败')
  }
}

function openCreate(): void {
  form.title = ''
  form.pointId = undefined
  form.shiftDate = weekStart.value
  form.startTime = '09:00:00'
  form.endTime = '11:00:00'
  form.capacity = 4
  createVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!form.title.trim() || !form.shiftDate) {
    ElMessage.warning('班次名称和日期必填')
    return
  }
  creating.value = true
  try {
    await api.createShift({
      title: form.title.trim(),
      pointId: form.pointId || undefined,
      shiftDate: form.shiftDate,
      startTime: form.startTime,
      endTime: form.endTime,
      capacity: form.capacity,
    })
    createVisible.value = false
    ElMessage.success('班次已发布')
    await loadWeek()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '班次创建失败')
  } finally {
    creating.value = false
  }
}

async function openSignups(row: ShiftView): Promise<void> {
  signupTarget.value = row
  signupVisible.value = true
  signupLoading.value = true
  try {
    signups.value = await api.fetchShiftSignups(row.id)
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '名单加载失败')
  } finally {
    signupLoading.value = false
  }
}

async function markAbsent(item: ShiftSignupView): Promise<void> {
  if (!signupTarget.value) return
  try {
    await ElMessageBox.confirm(
      `确认把 ${item.userName} 记为缺勤？连续三次会暂停接单。`,
      '缺勤确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    const result = await api.markShiftAbsent(signupTarget.value.id, item.userId)
    ElMessage.success(result.message)
    signups.value = await api.fetchShiftSignups(signupTarget.value.id)
    await loadWeek()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '缺勤登记失败')
  }
}

function openAppeal(): void {
  appealText.value = ''
  appealVisible.value = true
}

async function submitAppeal(): Promise<void> {
  if (appealText.value.trim().length < 10) {
    ElMessage.warning('申诉理由至少 10 个字')
    return
  }
  appealing.value = true
  try {
    await api.submitAppeal(appealText.value.trim())
    appealVisible.value = false
    ElMessage.success('申诉已提交，等待管理员审核')
    await Promise.all([loadSuspension(), loadAppeals()])
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '申诉提交失败')
  } finally {
    appealing.value = false
  }
}

async function reviewAppeal(row: AppealView, decision: 'APPROVE' | 'REJECT'): Promise<void> {
  let reason = ''
  if (decision === 'REJECT') {
    try {
      const value = await ElMessageBox.prompt('驳回必须填写原因', '驳回申诉', {
        inputPlaceholder: '不少于 5 个字',
      })
      reason = value.value?.trim() ?? ''
    } catch {
      return
    }
    if (reason.length < 5) {
      ElMessage.warning('驳回原因至少 5 个字')
      return
    }
  }
  try {
    await api.reviewAppeal(row.id, { decision, reason: reason || undefined })
    ElMessage.success(decision === 'APPROVE' ? '已恢复接单资格' : '已驳回申诉')
    await loadAppeals()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '申诉审核失败')
  }
}

async function closeShift(row: ShiftView): Promise<void> {
  try {
    await api.closeShift(row.id, 'CLOSED')
    ElMessage.success('班次已关闭')
    await loadWeek()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '关闭失败')
  }
}

onMounted(async () => {
  await Promise.all([loadWeek(), loadSuspension(), loadAppeals()])
})
</script>

<template>
  <div>
    <PageHero
      title="志愿排班"
      desc="按周发布班次，报名、签到与缺勤都落在同一张排班表；连续三次缺勤自动暂停接单，可申诉恢复。"
    >
      <template #actions>
        <button v-if="isAdmin" class="btn" type="button" @click="openCreate">发布班次</button>
        <button class="btn ghost" type="button" @click="$router.push('/tasks')">任务广场</button>
      </template>
    </PageHero>

    <div v-if="suspension" class="suspension" :class="{ warn: suspended }">
      <div>
        <strong>{{ suspended ? '接单已暂停' : '接单正常' }}</strong>
        <span class="muted" style="margin-left: 10px">
          连续缺勤 {{ suspension.absenceStreak }} 次
        </span>
      </div>
      <div class="suspension-right">
        <span v-if="suspension.pendingAppeal" class="tag warn">申诉审核中</span>
        <button
          v-else-if="suspension.canAppeal"
          class="btn sm"
          type="button"
          @click="openAppeal"
        >
          提交申诉
        </button>
        <span v-else class="muted">连续三次缺勤才会暂停接单</span>
      </div>
    </div>

    <div class="card">
      <el-tabs v-model="tab">
        <el-tab-pane label="周排班" name="week">
          <div class="toolbar">
            <button class="btn sm ghost" type="button" @click="shiftWeek(-1)">上一周</button>
            <button class="btn sm ghost" type="button" @click="shiftWeek(0)">本周</button>
            <button class="btn sm ghost" type="button" @click="shiftWeek(1)">下一周</button>
            <span class="mono">{{ weekLabel }}</span>
            <el-switch v-model="mineOnly" active-text="只看我的班" @change="loadWeek" />
            <span class="muted">共 {{ rows.length }} 个班次</span>
            <el-button :loading="loading" @click="loadWeek">刷新</el-button>
          </div>

          <el-table v-loading="loading" :data="rows" stripe>
            <el-table-column prop="shiftNo" label="班次号" min-width="140" />
            <el-table-column label="日期 / 时间" min-width="170">
              <template #default="{ row }">
                <div>{{ row.shiftDate }}</div>
                <div class="muted" style="font-size: 12px">
                  {{ timeText(row.startTime) }}–{{ timeText(row.endTime) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="班次" min-width="130" />
            <el-table-column label="名额" width="90">
              <template #default="{ row }">{{ row.booked }}/{{ row.capacity }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'OPEN' ? 'success' : 'info'">
                  {{ row.status === 'OPEN' ? '可报名' : row.status === 'CLOSED' ? '已关闭' : '已取消' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="我的出勤" width="110">
              <template #default="{ row }">
                <span v-if="row.absent" class="text-danger">缺勤</span>
                <span v-else-if="row.checkedIn">已签到</span>
                <span v-else-if="row.signed" class="muted">已报名</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="!row.signed"
                  size="small"
                  type="primary"
                  :disabled="!canSign(row)"
                  @click="signup(row)"
                >
                  {{ suspended ? '暂停接单' : '报名' }}
                </el-button>
                <el-button
                  v-else-if="row.signed && !row.checkedIn && !row.absent"
                  size="small"
                  type="success"
                  @click="checkin(row)"
                >
                  签到
                </el-button>
                <el-button v-if="isAdmin" size="small" @click="openSignups(row)">名单</el-button>
                <el-button v-if="isAdmin && row.status === 'OPEN'" size="small" @click="closeShift(row)">
                  关闭
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="footnote" style="text-align: left; margin-top: 10px">
            暂停状态下来自任务的报名也会被后端拦截；排班页只是把按钮置灰，真正校验在服务端。
          </div>
        </el-tab-pane>

        <el-tab-pane :label="isAdmin ? '缺勤与申诉' : '我的申诉'" name="appeals">
          <div class="toolbar">
            <span class="muted">{{ isAdmin ? '待审核申诉' : '我的申诉记录' }}</span>
            <el-button :loading="appealsLoading" @click="loadAppeals">刷新</el-button>
          </div>
          <el-table v-loading="appealsLoading" :data="appeals" stripe>
            <el-table-column v-if="isAdmin" prop="userName" label="志愿者" width="120" />
            <el-table-column prop="reason" label="申诉理由" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'">
                  {{ row.status === 'APPROVED' ? '已通过' : row.status === 'REJECTED' ? '已驳回' : '待审核' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reviewReason" label="审核意见" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="170" v-if="isAdmin">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING'">
                  <el-button size="small" type="primary" @click="reviewAppeal(row, 'APPROVE')">通过</el-button>
                  <el-button size="small" @click="reviewAppeal(row, 'REJECT')">驳回</el-button>
                </template>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="createVisible" title="发布班次" width="500px">
      <div class="form-grid">
        <label>班次名称</label>
        <el-input v-model="form.title" maxlength="64" placeholder="如 猫舍晚班" />
        <label>日期</label>
        <el-date-picker v-model="form.shiftDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        <label>开始时间</label>
        <el-time-picker v-model="form.startTime" value-format="HH:mm:ss" style="width: 100%" />
        <label>结束时间</label>
        <el-time-picker v-model="form.endTime" value-format="HH:mm:ss" style="width: 100%" />
        <label>名额</label>
        <el-input-number v-model="form.capacity" :min="1" :max="100" />
        <label>点位 ID</label>
        <el-input-number v-model="form.pointId" :min="1" :controls="false" style="width: 100%" />
      </div>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="signupVisible" title="班次签到名单" width="620px">
      <div v-loading="signupLoading">
        <el-table :data="signups" stripe>
          <el-table-column prop="userName" label="志愿者" width="120" />
          <el-table-column label="报名时间" width="160">
            <template #default="{ row }">{{ row.signedAt?.replace('T', ' ').slice(0, 16) }}</template>
          </el-table-column>
          <el-table-column label="签到" width="90">
            <template #default="{ row }">{{ row.checkinAt ? '已签到' : '未签到' }}</template>
          </el-table-column>
          <el-table-column label="缺勤" width="80">
            <template #default="{ row }">
              <span v-if="row.absent" class="text-danger">缺勤</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button
                v-if="!row.absent && !row.checkinAt"
                size="small"
                type="danger"
                plain
                @click="markAbsent(row)"
              >
                记缺勤
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <el-dialog v-model="appealVisible" title="申诉恢复接单" width="460px">
      <el-input
        v-model="appealText"
        type="textarea"
        :rows="4"
        maxlength="255"
        placeholder="说明缺勤原因与后续改进，至少 10 字"
      />
      <template #footer>
        <el-button @click="appealVisible = false">取消</el-button>
        <el-button type="primary" :loading="appealing" @click="submitAppeal">提交申诉</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.suspension {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  margin-bottom: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-2);
}
.suspension.warn {
  border-color: #f3b7b7;
  background: #fff7f7;
}
.suspension-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.form-grid {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 12px;
  align-items: center;
}
.text-danger {
  color: var(--danger);
  font-weight: 600;
}
</style>
