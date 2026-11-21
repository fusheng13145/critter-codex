<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import * as statsApi from '@/api/stats'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PageState from '@/components/PageState.vue'
import PanelCard from '@/components/PanelCard.vue'
import { money } from '@/utils/format'
import { projectStatusLabel } from '@/utils/domain'
import type { FundStats } from '@/api/types'

/**
 * P04 资金台账（汇总预览）。
 * 真正的逐笔借贷流水需要 T3.5 的 /ledger 接口；本页只展示 /stats/fund 的真实汇总，
 * 不拿项目列表拼假流水。
 */

const auth = useAuthStore()

function isoDate(offset = 0): string {
  const date = new Date()
  date.setDate(date.getDate() + offset)
  return date.toISOString().slice(0, 10)
}

const query = reactive({ from: isoDate(-365), to: isoDate() })
const data = ref<FundStats | null>(null)
const loading = ref(false)
const errorText = ref('')

async function load(): Promise<void> {
  if (!auth.isAdmin) return
  loading.value = true
  errorText.value = ''
  try {
    data.value = await statsApi.fetchFund({
      from: query.from || undefined,
      to: query.to || undefined,
    })
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '资金汇总加载失败'
    data.value = null
  } finally {
    loading.value = false
  }
}

function reset(): void {
  query.from = isoDate(-365)
  query.to = isoDate()
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero
      title="资金台账"
      desc="当前展示项目维度的资金汇总；逐笔流水、凭证与借贷方向等待 T3.5 台账接口接入。"
    >
      <template #actions>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新汇总</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '募捐项目', path: '/projects', matchPrefix: true },
        { label: '资金台账', path: '/ledger', adminOnly: true },
        { label: '支出复核', path: '/expenses', adminOnly: true },
      ]"
    />

    <div v-if="!auth.isAdmin" class="notice">资金台账仅 ANIMAL_ADMIN / SYSTEM_ADMIN 可见。</div>

    <template v-else>
      <div class="toolbar">
        <label class="date-label">统计区间</label>
        <input v-model="query.from" class="input date" type="date" />
        <span class="muted">至</span>
        <input v-model="query.to" class="input date" type="date" />
        <button class="btn sm" type="button" @click="load">查询</button>
        <button class="btn sm ghost" type="button" @click="reset">近一年</button>
        <span class="muted result-count">口径：/stats/fund</span>
      </div>

      <PageState :loading="loading" :error="errorText" :empty="!data" empty-text="区间内暂无资金数据">
        <template v-if="data">
          <div class="metrics">
            <div class="metric">
              <div class="label">已筹金额</div>
              <div class="num accent">{{ money(data.summary.raisedAmount) }}</div>
              <div class="sub">{{ data.summary.donationCount }} 笔已入账捐赠</div>
            </div>
            <div class="metric">
              <div class="label">已支出</div>
              <div class="num">{{ money(data.summary.spentAmount) }}</div>
              <div class="sub">{{ data.summary.expenseCount }} 笔已复核支出</div>
            </div>
            <div class="metric">
              <div class="label">账面余额</div>
              <div class="num">{{ money(data.summary.balanceAmount) }}</div>
              <div class="sub">已筹 − 已支出</div>
            </div>
            <div class="metric">
              <div class="label">未结项项目</div>
              <div class="num">{{ data.unsettled.length }}</div>
              <div class="sub">待结项或已结束未结算</div>
            </div>
          </div>

          <div class="ledger-grid">
            <PanelCard title="分项目汇总" :more="`${data.byProject.length} 个项目`">
              <div v-if="!data.byProject.length" class="muted empty-line">区间内没有项目</div>
              <div v-else class="table-wrap ledger-table">
                <table class="native">
                  <thead>
                    <tr>
                      <th>项目</th>
                      <th>状态</th>
                      <th>目标</th>
                      <th>已筹</th>
                      <th>已支出</th>
                      <th>余额</th>
                      <th>进度</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="project in data.byProject" :key="project.no">
                      <td>
                        <div class="mono small">{{ project.no }}</div>
                        <div class="project-name">{{ project.name }}</div>
                      </td>
                      <td><span class="tag gray">{{ projectStatusLabel(project.status) }}</span></td>
                      <td>{{ money(project.targetAmount) }}</td>
                      <td class="accent">{{ money(project.raisedAmount) }}</td>
                      <td>{{ money(project.spentAmount) }}</td>
                      <td>{{ money(project.balanceAmount) }}</td>
                      <td>{{ project.progress }}%</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </PanelCard>

            <PanelCard title="月度收支" :more="`${data.byMonth.length} 个月`">
              <div v-if="!data.byMonth.length" class="muted empty-line">区间内没有月度数据</div>
              <div v-else class="month-list">
                <div v-for="month in data.byMonth" :key="month.month" class="month-row">
                  <span class="mono month">{{ month.month }}</span>
                  <div class="month-values">
                    <span class="accent">收 {{ money(month.raisedAmount) }}</span>
                    <span>支 {{ money(month.spentAmount) }}</span>
                  </div>
                </div>
              </div>
            </PanelCard>
          </div>

          <PanelCard title="逐笔流水" more="T3.5 待接入">
            <div class="planned">
              <div>
                <h3>接口尚未开放</h3>
                <p>后端暂未提供 <span class="mono">GET /api/v1/ledger</span>，本页不会用项目汇总伪造逐笔流水。</p>
              </div>
              <div class="planned-fields">
                <span>流水号</span>
                <span>发生日期</span>
                <span>项目</span>
                <span>借贷方向</span>
                <span>金额</span>
                <span>凭证</span>
                <span>关联单据</span>
                <span>操作人</span>
              </div>
            </div>
          </PanelCard>
        </template>
      </PageState>
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

.result-count {
  margin-left: auto;
  font-size: 11px;
}

.accent {
  color: var(--accent);
  font-weight: 600;
}

.ledger-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 16px;
  align-items: start;
}

.ledger-table {
  box-shadow: none;
}

.small {
  font-size: 11px;
}

.project-name {
  font-weight: 600;
  margin-top: 1px;
}

.empty-line {
  padding: 8px 0;
  font-size: 12px;
}

.month-list {
  display: grid;
  gap: 6px;
}

.month-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px dashed var(--line);
  font-size: 12px;
}

.month-row:last-child {
  border-bottom: 0;
}

.month-values {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.month-values .accent {
  font-weight: 600;
}

.month {
  color: var(--muted);
}

.planned {
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  gap: 20px;
  align-items: start;
}

.planned h3 {
  margin-bottom: 5px;
}

.planned p {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.planned-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.planned-fields span {
  padding: 4px 8px;
  border: 1px dashed var(--line);
  border-radius: 999px;
  color: var(--muted);
  font-size: 11px;
}

@media (max-width: 1000px) {
  .ledger-grid,
  .planned {
    grid-template-columns: 1fr;
  }
}
</style>
