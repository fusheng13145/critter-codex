<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import * as statsApi from '@/api/stats'
import * as fundApi from '@/api/fund'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PanelCard from '@/components/PanelCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import ExpenseCreateDialog from '@/views/fund/ExpenseCreateDialog.vue'
import ExpenseReviewDialog from '@/views/fund/ExpenseReviewDialog.vue'
import { dateTime, money, statusLabel } from '@/utils/format'
import type { EfficiencyStats, ExpenseSummary, ProjectSummary } from '@/api/types'

/**
 * P04 支出登记与复核：列表、筛选、登记、复核全部走真实接口。
 * 复核仅 ANIMAL_ADMIN；SYSTEM_ADMIN 不参与业务审核。
 */
const auth = useAuthStore()
const canReview = computed(() => auth.roles.includes('ANIMAL_ADMIN'))

const rows = ref<ExpenseSummary[]>([])
const projects = ref<ProjectSummary[]>([])
const efficiency = ref<EfficiencyStats | null>(null)
const total = ref(0)
const page = ref(1)
const size = 20
const statusFilter = ref('')
const projectFilter = ref('')
const loading = ref(false)
const errorText = ref('')
const createVisible = ref(false)
const reviewVisible = ref(false)
const current = ref<ExpenseSummary | null>(null)

const pageAmount = computed(() => rows.value.reduce((sum, row) => sum + row.amount, 0))
const pendingCount = computed(() => efficiency.value?.pendingExpenses ?? 0)
const currentUserId = computed(() => auth.user?.id ?? null)

async function load(): Promise<void> {
  if (!canReview.value) return
  loading.value = true
  errorText.value = ''
  try {
    const [expenses, stats] = await Promise.all([
      fundApi.fetchExpenses({
        status: statusFilter.value || undefined,
        projectNo: projectFilter.value || undefined,
        page: page.value,
        size,
      }),
      statsApi.fetchEfficiency().catch(() => null),
    ])
    rows.value = expenses.list
    total.value = expenses.page.total
    efficiency.value = stats
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '支出列表加载失败'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadProjects(): Promise<void> {
  try {
    const result = await fundApi.fetchProjects({ status: 'FUNDING,PENDING_SETTLE', page: 1, size: 100 })
    projects.value = result.list
  } catch {
    projects.value = []
  }
}

function applyFilter(): void {
  page.value = 1
  load()
}

function resetFilter(): void {
  statusFilter.value = ''
  projectFilter.value = ''
  page.value = 1
  load()
}

function openReview(row: ExpenseSummary): void {
  current.value = row
  reviewVisible.value = true
}

function changePage(next: number): void {
  const max = Math.max(1, Math.ceil(total.value / size))
  page.value = Math.min(Math.max(1, next), max)
  load()
}

async function afterSaved(): Promise<void> {
  await load()
  await loadProjects()
}

onMounted(() => {
  if (!canReview.value) return
  loadProjects()
  load()
})
</script>

<template>
  <div>
    <PageHero title="支出复核" desc="支出登记后只进入待复核；复核通过才扣减项目余额，超 500 元必须两签。">
      <template #actions>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <button v-if="canReview" class="btn" type="button" @click="createVisible = true">登记支出</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '募捐项目', path: '/projects', matchPrefix: true },
        { label: '资金台账', path: '/ledger' },
        { label: '支出复核', path: '/expenses', adminOnly: true },
      ]"
    />

    <div v-if="!canReview" class="notice">
      支出登记与复核仅 ANIMAL_ADMIN 可操作；SYSTEM_ADMIN 不参与业务审核。
    </div>

    <template v-else>
      <div class="metrics">
        <div class="metric">
          <div class="label">待复核 / 待二签</div>
          <div class="num">{{ pendingCount }}</div>
          <div class="sub">来源 /stats/efficiency</div>
        </div>
        <div class="metric">
          <div class="label">当前筛选笔数</div>
          <div class="num">{{ total }}</div>
          <div class="sub">全部状态含已复核与已驳回</div>
        </div>
        <div class="metric">
          <div class="label">本页金额</div>
          <div class="num">{{ money(pageAmount) }}</div>
          <div class="sub">仅当前 20 笔合计</div>
        </div>
      </div>

      <PanelCard title="支出列表" :more="`共 ${total} 笔`">
        <div class="toolbar">
          <select v-model="statusFilter" class="select">
            <option value="">全部状态</option>
            <option value="PENDING_REVIEW">待复核</option>
            <option value="FIRST_APPROVED">待二签</option>
            <option value="APPROVED">已复核</option>
            <option value="REJECTED">已驳回</option>
          </select>
          <select v-model="projectFilter" class="select grow-input">
            <option value="">全部项目</option>
            <option v-for="project in projects" :key="project.projectNo" :value="project.projectNo">
              {{ project.projectNo }} · {{ project.name }}
            </option>
          </select>
          <button class="btn sm" type="button" :disabled="loading" @click="applyFilter">筛选</button>
          <button class="btn sm ghost" type="button" :disabled="loading" @click="resetFilter">重置</button>
        </div>

        <div v-if="errorText" class="notice">{{ errorText }}</div>
        <div v-else-if="loading" class="muted empty-line">支出列表加载中…</div>
        <div v-else-if="!rows.length" class="muted empty-line">当前筛选没有支出记录</div>
        <div v-else class="table-wrap">
          <table class="native">
            <thead>
              <tr>
                <th>支出单号</th>
                <th>项目 / 动物</th>
                <th>用途</th>
                <th>金额</th>
                <th>收款方</th>
                <th>状态</th>
                <th>登记人</th>
                <th>发生时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.expenseNo">
                <td class="mono">{{ row.expenseNo }}</td>
                <td>
                  <div>{{ row.projectNo }}</div>
                  <div class="muted small">{{ row.animalCode }} · {{ row.animalName || '未命名' }}</div>
                </td>
                <td>{{ row.purpose }}</td>
                <td class="amount">{{ money(row.amount) }}</td>
                <td>{{ row.payee }}</td>
                <td>
                  <StatusTag :code="row.status" :label="statusLabel(row.status)" />
                  <div v-if="row.reviewReason" class="muted small reason">{{ row.reviewReason }}</div>
                </td>
                <td>
                  <div>{{ row.handlerName || `#${row.handlerId}` }}</div>
                  <div v-if="row.handlerId === currentUserId" class="tag gray">本人登记</div>
                </td>
                <td class="muted small">{{ dateTime(row.occurredAt) }}</td>
                <td>
                  <button
                    v-if="row.status === 'PENDING_REVIEW' || row.status === 'FIRST_APPROVED'"
                    class="btn sm ghost"
                    type="button"
                    @click="openReview(row)"
                  >
                    复核
                  </button>
                  <span v-else class="muted small">已处理</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pager">
          <button class="btn sm ghost" type="button" :disabled="page <= 1 || loading" @click="changePage(page - 1)">上一页</button>
          <span class="muted small">第 {{ page }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span>
          <button
            class="btn sm ghost"
            type="button"
            :disabled="page >= Math.ceil(total / size) || loading"
            @click="changePage(page + 1)"
          >
            下一页
          </button>
        </div>

        <div class="footnote" style="text-align: left">
          来源：GET /expenses。金额为整数分；待复核不进入公开进度，复核通过才计入 spentAmount。
        </div>
      </PanelCard>
    </template>

    <ExpenseCreateDialog v-model="createVisible" :projects="projects" @saved="afterSaved" />
    <ExpenseReviewDialog
      v-model="reviewVisible"
      :expense="current"
      :current-user-id="currentUserId"
      @saved="afterSaved"
    />
  </div>
</template>

<style scoped>
.metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.amount {
  white-space: nowrap;
  font-weight: 600;
  color: var(--brand);
}

.reason {
  margin-top: 4px;
  max-width: 220px;
  white-space: normal;
}

.empty-line {
  padding: 20px 0;
  font-size: 12px;
  text-align: center;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 12px;
}

@media (max-width: 1000px) {
  .metrics {
    grid-template-columns: 1fr;
  }
}
</style>
