<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import * as fundApi from '@/api/fund'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PanelCard from '@/components/PanelCard.vue'
import { dateTime, money, statusLabel } from '@/utils/format'
import type { LedgerResult, ProjectSummary } from '@/api/types'

/**
 * P04 资金台账：收入只取已入账捐赠，支出只取已复核支出；
 * 现有记录均由后端 GET /ledger 合成，不在前端拼假流水。
 */
const auth = useAuthStore()

function isoDate(offset = 0): string {
  const date = new Date()
  date.setDate(date.getDate() + offset)
  return date.toISOString().slice(0, 10)
}

const query = reactive({ from: isoDate(-365), to: isoDate(), projectNo: '' })
const typeFilter = ref('')
const data = ref<LedgerResult | null>(null)
const projects = ref<ProjectSummary[]>([])
const loading = ref(false)
const errorText = ref('')

const entries = computed(() => {
  const list = data.value?.entries ?? []
  return typeFilter.value ? list.filter((item) => item.type === typeFilter.value) : list
})

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    data.value = await fundApi.fetchLedger({
      from: query.from || undefined,
      to: query.to || undefined,
      projectNo: query.projectNo || undefined,
    })
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '资金台账加载失败'
    data.value = null
  } finally {
    loading.value = false
  }
}

async function loadProjects(): Promise<void> {
  try {
    const result = await fundApi.fetchProjects({ page: 1, size: 100 })
    projects.value = result.list
  } catch {
    projects.value = []
  }
}

function reset(): void {
  query.from = isoDate(-365)
  query.to = isoDate()
  query.projectNo = ''
  typeFilter.value = ''
  load()
}

onMounted(() => {
  loadProjects()
  load()
})
</script>

<template>
  <div>
    <PageHero title="资金台账" desc="逐笔核对每一笔入账与支出；合计恒等于已入账捐赠减已复核支出。">
      <template #actions>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新台账</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '募捐项目', path: '/projects', matchPrefix: true },
        { label: '资金台账', path: '/ledger' },
        { label: '支出复核', path: '/expenses', adminOnly: true },
      ]"
    />

    <div v-if="!auth.isAdmin" class="notice">资金台账含捐赠人展示名与支出详情，仅管理员可查看。</div>

    <template v-else>
      <div class="toolbar">
        <label class="date-label">统计区间</label>
        <input v-model="query.from" class="input date" type="date" />
        <span class="muted">至</span>
        <input v-model="query.to" class="input date" type="date" />
        <select v-model="query.projectNo" class="select project-select">
          <option value="">全部项目</option>
          <option v-for="project in projects" :key="project.projectNo" :value="project.projectNo">
            {{ project.projectNo }} · {{ project.name }}
          </option>
        </select>
        <button class="btn sm" type="button" :disabled="loading" @click="load">查询</button>
        <button class="btn sm ghost" type="button" :disabled="loading" @click="reset">近一年</button>
        <span class="muted result-count">口径：GET /ledger</span>
      </div>

      <div v-if="errorText" class="notice">{{ errorText }}</div>
      <div v-else-if="loading" class="notice">台账加载中…</div>

      <template v-else-if="data">
        <div class="metrics">
          <div class="metric">
            <div class="label">已入账捐赠</div>
            <div class="num accent">{{ money(data.summary.income) }}</div>
            <div class="sub">{{ data.summary.donationCount }} 笔 VERIFIED</div>
          </div>
          <div class="metric">
            <div class="label">已复核支出</div>
            <div class="num">{{ money(data.summary.expense) }}</div>
            <div class="sub">{{ data.summary.expenseCount }} 笔 APPROVED</div>
          </div>
          <div class="metric">
            <div class="label">账面余额</div>
            <div class="num">{{ money(data.summary.balance) }}</div>
            <div class="sub">收入 − 支出</div>
          </div>
          <div class="metric">
            <div class="label">逐笔流水</div>
            <div class="num">{{ data.entries.length }}</div>
            <div class="sub">当前筛选 {{ entries.length }} 笔</div>
          </div>
        </div>

        <PanelCard title="逐笔流水" :more="`${entries.length} 笔`">
          <div class="toolbar inner">
            <select v-model="typeFilter" class="select">
              <option value="">全部方向</option>
              <option value="INCOME">收入</option>
              <option value="EXPENSE">支出</option>
            </select>
            <span class="muted small">方向由后端按状态判定，前端不推断。</span>
          </div>

          <div v-if="!entries.length" class="muted empty-line">当前条件下没有流水</div>
          <div v-else class="table-wrap ledger-table">
            <table class="native">
              <thead>
                <tr>
                  <th>发生时间</th>
                  <th>方向</th>
                  <th>单号</th>
                  <th>项目</th>
                  <th>摘要</th>
                  <th>关联动物</th>
                  <th>金额</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="entry in entries" :key="`${entry.type}-${entry.refNo}`">
                  <td class="muted small">{{ dateTime(entry.occurredAt) }}</td>
                  <td>
                    <span class="tag" :class="entry.type === 'INCOME' ? 'income' : 'expense'">
                      {{ entry.type === 'INCOME' ? '收入' : '支出' }}
                    </span>
                  </td>
                  <td class="mono small">{{ entry.refNo }}</td>
                  <td>
                    <div class="mono small">{{ entry.projectNo }}</div>
                    <div class="muted small">{{ entry.projectName }}</div>
                  </td>
                  <td>
                    <div>{{ entry.title }}</div>
                    <div class="muted small">{{ entry.party }}</div>
                  </td>
                  <td class="mono small">{{ entry.animalCode || '—' }}</td>
                  <td class="amount" :class="entry.type === 'INCOME' ? 'plus' : 'minus'">
                    {{ entry.type === 'INCOME' ? '+' : '−' }}{{ money(entry.amount) }}
                  </td>
                  <td><span class="tag gray">{{ statusLabel(entry.status) }}</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </PanelCard>

        <div class="footnote" style="text-align: left">
          收入 = 已核对入账捐赠（VERIFIED）；支出 = 已复核支出（APPROVED）。待核对与待复核均不进入台账。
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.date-label {
  color: var(--muted);
  font-size: 12px;
}

.date {
  width: 150px;
}

.project-select {
  min-width: 220px;
}

.result-count {
  margin-left: auto;
  font-size: 11px;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.accent {
  color: var(--accent);
  font-weight: 600;
}

.inner {
  margin-bottom: 10px;
}

.ledger-table {
  box-shadow: none;
}

.tag.income {
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent);
}

.tag.expense {
  background: color-mix(in srgb, var(--danger) 12%, transparent);
  color: var(--danger);
}

.amount {
  white-space: nowrap;
  font-weight: 600;
}

.amount.plus {
  color: var(--accent);
}

.amount.minus {
  color: var(--danger);
}

.small {
  font-size: 11px;
}

.empty-line {
  padding: 22px 0;
  font-size: 12px;
  text-align: center;
}

@media (max-width: 1100px) {
  .metrics {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 720px) {
  .metrics {
    grid-template-columns: 1fr;
  }

  .result-count {
    margin-left: 0;
  }
}
</style>
