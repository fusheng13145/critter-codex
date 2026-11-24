<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as fundApi from '@/api/fund'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PanelCard from '@/components/PanelCard.vue'
import PageState from '@/components/PageState.vue'
import StatusTag from '@/components/StatusTag.vue'
import DonationPanel from '@/views/fund/DonationPanel.vue'
import DonationDialog from '@/views/fund/DonationDialog.vue'
import ProjectAuditDialog from '@/views/fund/ProjectAuditDialog.vue'
import ProjectCreateDialog from '@/views/fund/ProjectCreateDialog.vue'
import SettlementCreateDialog from '@/views/fund/SettlementCreateDialog.vue'
import CommentPanel from '@/components/CommentPanel.vue'
import FollowButton from '@/components/FollowButton.vue'
import { dateTime, money } from '@/utils/format'
import { projectStatusLabel, targetTypeLabel } from '@/utils/domain'
import type { ProjectDetail } from '@/api/types'

/**
 * P04 项目详情。
 * 管理员：提交审核 / 审核通过或驳回 / 捐赠核对。
 * 所有人：查看预算与已入账流水；仅募捐中项目开放登记捐赠。
 */

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const projectNo = String(route.params.id ?? '')
const project = ref<ProjectDetail | null>(null)
const loading = ref(false)
const errorText = ref('')
const actionBusy = ref(false)
const auditVisible = ref(false)
const donationVisible = ref(false)
const editVisible = ref(false)
const settleVisible = ref(false)

const animalAdmin = computed(() => auth.hasRole('ANIMAL_ADMIN'))
const balance = computed(() => (project.value ? project.value.raisedAmount - project.value.spentAmount : 0))
const canDonate = computed(() => project.value?.status === 'FUNDING')
const canEdit = computed(() =>
  Boolean(project.value && (project.value.status === 'DRAFT' || project.value.status === 'REJECTED')),
)
const canSettle = computed(() => {
  const current = project.value
  if (!current || !animalAdmin.value) return false
  if (current.status === 'SETTLED') return false
  if (current.status === 'PENDING_SETTLE') return true
  if (current.status !== 'FUNDING') return false
  const reachedTarget = current.raisedAmount >= current.targetAmount
  const deadlineReached = current.endDate <= new Date().toISOString().slice(0, 10)
  return reachedTarget || deadlineReached
})
const canAudit = computed(() =>
  Boolean(project.value?.allowedActions.includes('APPROVE') || project.value?.allowedActions.includes('REJECT')),
)
const canSubmit = computed(() =>
  Boolean(project.value?.allowedActions.includes('SUBMIT') || project.value?.allowedActions.includes('RESUBMIT')),
)

async function load(): Promise<void> {
  if (!projectNo) return
  loading.value = true
  errorText.value = ''
  try {
    project.value = await fundApi.fetchProject(projectNo)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '项目详情加载失败'
    project.value = null
  } finally {
    loading.value = false
  }
}

async function submitForAudit(): Promise<void> {
  const current = project.value
  if (!current || actionBusy.value) return
  try {
    await ElMessageBox.confirm(
      '提交后项目进入 PENDING_AUDIT，管理员审核通过才会公开募捐。确认提交？',
      '提交审核',
      { confirmButtonText: '提交审核', cancelButtonText: '再检查一下' },
    )
  } catch {
    return
  }
  actionBusy.value = true
  try {
    await fundApi.updateProject(current.projectNo, {
      name: current.name,
      purpose: current.purpose,
      targetType: current.targetType,
      targetAmount: current.targetAmount,
      startDate: current.startDate,
      endDate: current.endDate,
      budget: current.budget.map((item) => ({
        subject: item.subject,
        amount: item.amount,
        note: item.note ?? undefined,
      })),
      coverFileId: current.coverFileId ?? undefined,
      submit: true,
    })
    ElMessage.success('已提交审核')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '提交失败，请稍后重试')
  } finally {
    actionBusy.value = false
  }
}

async function afterChange(): Promise<void> {
  await load()
}

function openEdit(): void {
  if (!canEdit.value) return
  editVisible.value = true
}

function openSettlement(): void {
  router.push(`/projects/${projectNo}/settlement`)
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero
      :title="project?.name || '项目详情'"
    >
      <template #actions>
        <button class="btn ghost" type="button" @click="router.push('/projects')">返回列表</button>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <FollowButton v-if="project" biz-type="PROJECT" :biz-id="project.projectNo" label="关注项目进度" />
        <button
          v-if="animalAdmin && canEdit"
          class="btn ghost"
          type="button"
          @click="openEdit"
        >
          编辑项目
        </button>
        <button
          v-if="animalAdmin && canSubmit"
          class="btn ghost"
          type="button"
          :disabled="actionBusy"
          @click="submitForAudit"
        >
          {{ actionBusy ? '提交中…' : '提交审核' }}
        </button>
        <button v-if="animalAdmin && canAudit" class="btn" type="button" @click="auditVisible = true">
          审核项目
        </button>
        <button v-if="canSettle" class="btn" type="button" @click="settleVisible = true">
          提交结项
        </button>
        <button v-if="project?.status === 'SETTLED'" class="btn" type="button" @click="openSettlement">
          查看结项公示
        </button>
        <button v-if="canDonate" class="btn" type="button" @click="donationVisible = true">
          我要捐赠
        </button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '募捐项目', path: '/projects', matchPrefix: true },
        { label: '资金台账', path: '/ledger', adminOnly: true },
        { label: '支出复核', path: '/expenses', adminOnly: true },
      ]"
    />

    <PageState :loading="loading" :error="errorText" :empty="!project">
      <template v-if="project">
        <div class="project-status-line">
          <StatusTag :code="project.status" :label="projectStatusLabel(project.status)" />
          <span class="muted">已入账 {{ project.donationCount }} 笔 · 剩余 {{ project.daysRemaining }} 天</span>
          <span v-if="project.auditReason" class="tag danger">审核意见：{{ project.auditReason }}</span>
        </div>

        <div class="metrics">
          <div class="metric">
            <div class="label">目标金额</div>
            <div class="num">{{ money(project.targetAmount) }}</div>
            <div class="sub">预算合计 {{ money(project.budgetTotal) }}</div>
          </div>
          <div class="metric">
            <div class="label">已筹金额</div>
            <div class="num accent">{{ money(project.raisedAmount) }}</div>
            <div class="sub">{{ project.progressPercent }}% · 只含已核对入账</div>
          </div>
          <div class="metric">
            <div class="label">已支出</div>
            <div class="num">{{ money(project.spentAmount) }}</div>
            <div class="sub">只含已复核支出</div>
          </div>
          <div class="metric">
            <div class="label">可用余额</div>
            <div class="num">{{ money(balance) }}</div>
            <div class="sub">已筹 − 已支出</div>
          </div>
        </div>

        <div class="progress-card">
          <div class="progress-label">
            <span>募捐进度</span>
            <b>{{ project.progressPercent }}%</b>
          </div>
          <div class="progress">
            <i :style="{ width: `${Math.min(100, project.progressPercent)}%` }" />
          </div>
        </div>

        <div class="detail-grid">
          <PanelCard title="项目说明">
            <p class="purpose">{{ project.purpose }}</p>
            <div class="info-grid single">
              <div class="info">
                <div class="k">关联对象</div>
                <div class="v">{{ targetTypeLabel(project.targetType) }}</div>
              </div>
              <div class="info">
                <div class="k">周期</div>
                <div class="v">{{ project.startDate }} → {{ project.endDate }}</div>
              </div>
              <div class="info">
                <div class="k">创建时间</div>
                <div class="v">{{ dateTime(project.createdAt) }}</div>
              </div>
              <div class="info">
                <div class="k">审核人</div>
                <div class="v">{{ project.auditorId ?? '尚未审核' }}</div>
              </div>
            </div>
          </PanelCard>

          <PanelCard title="预算明细">
            <div v-if="!project.budget.length" class="muted empty-line">未登记预算</div>
            <div v-else class="table-wrap budget-table">
              <table class="native">
                <thead>
                  <tr>
                    <th>科目</th>
                    <th>金额</th>
                    <th>备注</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in project.budget" :key="item.subject">
                    <td>{{ item.subject }}</td>
                    <td>{{ money(item.amount) }}</td>
                    <td class="muted">{{ item.note || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="budget-total">
              <span>预算合计</span>
              <b>{{ money(project.budgetTotal) }}</b>
            </div>
          </PanelCard>
        </div>

        <PanelCard title="已入账捐赠">
          <div v-if="!project.donations.length" class="muted empty-line">暂无已入账捐赠</div>
          <ul v-else class="list">
            <li v-for="donation in project.donations" :key="donation.donationNo">
              <span class="dot" />
              <span class="grow">
                <b>{{ donation.donorDisplay }}</b>
                <span v-if="donation.message" class="muted"> · {{ donation.message }}</span>
              </span>
              <span class="donation-amount">{{ money(donation.amount) }}</span>
              <span class="time">{{ donation.verifiedAt ? dateTime(donation.verifiedAt) : '—' }}</span>
            </li>
          </ul>

        </PanelCard>

        <CommentPanel biz-type="PROJECT" :biz-id="project.projectNo" />

        <DonationPanel v-if="animalAdmin" :project-no="project.projectNo" @changed="afterChange" />
      </template>
    </PageState>

    <ProjectAuditDialog
      v-model="auditVisible"
      :project-no="projectNo"
      @audited="afterChange"
    />
    <DonationDialog
      v-model="donationVisible"
      :project-no="projectNo"
      @created="afterChange"
    />
    <ProjectCreateDialog
      v-model="editVisible"
      :project="project"
      @created="afterChange"
    />
    <SettlementCreateDialog
      v-model="settleVisible"
      :project="project"
      @settled="afterChange"
    />
  </div>
</template>

<style scoped>
.project-status-line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.project-status-line .muted {
  font-size: 12px;
}

.accent {
  color: var(--accent);
}

.progress-card {
  padding: 14px 18px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  margin-bottom: 16px;
}

.progress-label {
  display: flex;
  justify-content: space-between;
  color: var(--muted);
  font-size: 12px;
  margin-bottom: 6px;
}

.progress-label b {
  color: var(--accent);
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 16px;
  align-items: start;
}

.purpose {
  color: var(--ink);
  line-height: 1.8;
  white-space: pre-wrap;
}

.info-grid.single {
  grid-template-columns: 1fr 1fr;
  margin-top: 14px;
}

.budget-table {
  box-shadow: none;
}

.budget-table table.native {
  min-width: 0;
}

.budget-total {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
}

.budget-total b {
  color: var(--accent);
}

.empty-line {
  padding: 8px 0;
  font-size: 12px;
}

.donation-amount {
  color: var(--accent);
  font-weight: 600;
  white-space: nowrap;
}

@media (max-width: 1000px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
