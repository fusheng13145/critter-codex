<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as statsApi from '@/api/stats'
import { BizError } from '@/api/http'
import PageHero from '@/components/PageHero.vue'
import MetricCard from '@/components/MetricCard.vue'
import PanelCard from '@/components/PanelCard.vue'
import BarChart from '@/components/BarChart.vue'
import { money, percent, statusLabel } from '@/utils/format'
import type { BiologyStats, EfficiencyStats, FundStats } from '@/api/types'

/**
 * P09 数据统计：生物 / 资金 / 效率三块。
 * 口径红线：资金块展示 balance，接口侧已自检 raised − spent = balance，前端不做二次换算。
 */

const biology = ref<BiologyStats | null>(null)
const fund = ref<FundStats | null>(null)
const efficiency = ref<EfficiencyStats | null>(null)
const loading = ref(true)
const errorText = ref('')

const from = ref('')
const to = ref('')

const statusSlices = computed(() => (biology.value?.byStatus ?? []).filter((item) => item.count > 0))

const fundPoints = computed(() =>
  (fund.value?.byMonth ?? []).map((item) => ({
    label: item.month.slice(5) + '月',
    value: item.raisedAmount,
  })),
)

const reportClosedRate = computed(() => {
  const reports = efficiency.value?.reports
  if (!reports || reports.total === 0) return 0
  return Math.round((reports.closed / reports.total) * 1000) / 10
})

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    biology.value = await statsApi.fetchBiology()
    const params = { from: from.value || undefined, to: to.value || undefined }
    const [fundData, efficiencyData] = await Promise.all([
      statsApi.fetchFund(params),
      statsApi.fetchEfficiency(params),
    ])
    fund.value = fundData
    efficiency.value = efficiencyData
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '统计加载失败，请确认后端已启动'
  } finally {
    loading.value = false
  }
}

function resetRange(): void {
  from.value = ''
  to.value = ''
  load()
}

function exportStats(): void {
  ElMessage.info('导出为三期功能：GET /stats/export 走异步任务，超过 5 万行不在接口内同步生成')
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero title="数据统计" desc="生物、资金、效率三块口径分开统计；资金只计已入账捐赠与已复核支出。">
      <template #actions>
        <button class="btn ghost" type="button" @click="exportStats">导出报表（三期）</button>
        <button class="btn" type="button" :disabled="loading" @click="load">刷新</button>
      </template>
    </PageHero>

    <div v-if="errorText" class="notice" style="margin-bottom: 16px">{{ errorText }}</div>

    <div class="toolbar">
      <label class="muted" style="font-size: 12px">统计区间</label>
      <input v-model="from" class="input" type="date" style="min-width: 160px" />
      <span class="muted">至</span>
      <input v-model="to" class="input" type="date" style="min-width: 160px" />
      <button class="btn ghost sm" type="button" @click="load">应用</button>
      <button class="btn ghost sm" type="button" @click="resetRange">重置</button>
      <span class="muted" style="font-size: 12px; margin-left: auto">
        生物总览固定看近 6 个月；资金与效率按所选区间
      </span>
    </div>

    <div class="metrics">
      <MetricCard label="在册个体" :value="String(biology?.total ?? 0)" sub="已离世" :sub-strong="String(biology?.deceased ?? 0)" />
      <MetricCard label="绝育率" :value="percent(biology?.neuteredRate)" :sub="`已绝育 ${biology?.neuteredCount ?? 0} 只`" />
      <MetricCard label="认养覆盖率" :value="percent(biology?.adoptionCoverage)" :sub="`生效认养 ${biology?.activeAdoptions ?? 0} 只`" />
      <MetricCard
        label="项目结余"
        :value="money(fund?.summary.balanceAmount)"
        sub="已筹 / 已支出"
        :sub-strong="`${money(fund?.summary.raisedAmount)} / ${money(fund?.summary.spentAmount)}`"
      />
    </div>

    <div class="grid2 align-start">
      <PanelCard title="档案状态分布" :more="`${biology?.groupCount ?? 0} 个群体 · ${biology?.occurrenceCount ?? 0} 条出现记录`">
        <div v-if="statusSlices.length" style="display: grid; gap: 10px">
          <div v-for="slice in statusSlices" :key="slice.status">
            <div style="display: flex; justify-content: space-between; font-size: 12px; color: var(--muted)">
              <span>{{ statusLabel(slice.status) }}</span>
              <span>{{ slice.count }} 只</span>
            </div>
            <div class="progress">
              <i :style="{ width: `${Math.round((slice.count / Math.max(1, biology?.total ?? 1)) * 100)}%` }" />
            </div>
          </div>
        </div>
        <div v-else class="muted">暂无档案数据</div>
        <div class="footnote" style="text-align: left; margin-top: 12px">
          来源：<span class="mono">v_animal_overview</span>（排除软删与已离世）；群体规模区间
          {{ biology?.groupSizeMin ?? 0 }}—{{ biology?.groupSizeMax ?? 0 }}。
        </div>
      </PanelCard>

      <PanelCard title="近半年新增档案" more="单位：只">
        <BarChart v-if="fundPoints.length >= 0 && (biology?.months.length ?? 0) > 0"
          :points="(biology?.months ?? []).map((item) => ({ label: `${Number(item.month.slice(5))}月`, value: item.newAnimals }))"
          :alt-from="3"
        />
      </PanelCard>
    </div>

    <PanelCard title="资金月度流水" more="只计已入账捐赠与已复核支出">
      <div v-if="fundPoints.length" class="grid2" style="margin-bottom: 0">
        <BarChart :points="fundPoints" :alt-from="3" />
        <table class="native" style="min-width: 0">
          <thead>
            <tr>
              <th>月份</th>
              <th>入账</th>
              <th>支出</th>
              <th>净额</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in fund?.byMonth ?? []" :key="row.month">
              <td>{{ row.month }}</td>
              <td>+ {{ money(row.raisedAmount) }}</td>
              <td>− {{ money(row.spentAmount) }}</td>
              <td>{{ money(row.raisedAmount - row.spentAmount) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="muted">所选区间内没有资金流水</div>
      <div class="footnote" style="text-align: left; margin-top: 12px">
        来源：<span class="mono">v_project_fund</span>，结余恒等于"已筹 − 已支出"；接口自检不通过会返回 50001 而不是矛盾数字。
      </div>
    </PanelCard>

    <PanelCard title="项目资金明细" more="点击项目编号可到资金页（B 域）">
      <table class="native">
        <thead>
          <tr>
            <th>项目</th>
            <th>状态</th>
            <th>目标</th>
            <th>已筹</th>
            <th>已支出</th>
            <th>结余</th>
            <th>进度</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="project in fund?.byProject ?? []" :key="project.no">
            <td>
              <b>{{ project.name }}</b><br />
              <span class="mono">{{ project.no }}</span>
            </td>
            <td><span class="tag" :class="{ gray: project.status === 'SETTLED' }">{{ statusLabel(project.status) }}</span></td>
            <td>{{ money(project.targetAmount) }}</td>
            <td>{{ money(project.raisedAmount) }}</td>
            <td>{{ money(project.spentAmount) }}</td>
            <td>{{ money(project.balanceAmount) }}</td>
            <td>
              <div class="progress" style="min-width: 90px">
                <i :style="{ width: `${Math.min(100, Math.round(project.progress))}%` }" />
              </div>
              <span class="muted" style="font-size: 11px">{{ percent(project.progress) }}</span>
            </td>
          </tr>
          <tr v-if="!(fund?.byProject ?? []).length">
            <td colspan="7" class="muted">暂无项目</td>
          </tr>
        </tbody>
      </table>
    </PanelCard>

    <div class="grid2" style="margin-top: 16px">
      <PanelCard title="上报与工单效率">
        <div class="metrics" style="grid-template-columns: repeat(2, 1fr); margin-bottom: 0">
          <MetricCard label="上报总数" :value="String(efficiency?.reports.total ?? 0)" :sub="`待受理 ${efficiency?.reports.pending ?? 0}`" />
          <MetricCard label="上报结案率" :value="percent(reportClosedRate)" :sub="`已关闭 ${efficiency?.reports.closed ?? 0}`" />
          <MetricCard label="工单平均受理" :value="`${efficiency?.orders.avgAcceptMinutes ?? 0} 分钟`" sub="从上报到受理" />
          <MetricCard label="工单超时" :value="String(efficiency?.orders.timeout ?? 0)" sub="需说明原因" />
        </div>
      </PanelCard>

      <PanelCard title="口径说明">
        <ul class="list">
          <li><span class="dot" /><span class="grow">在册数 = 未软删且未离世的个体，群体不并入计算</span></li>
          <li><span class="dot gray" /><span class="grow">已筹只计 <span class="mono">status=VERIFIED</span> 的捐赠，登记未入账不计</span></li>
          <li><span class="dot gray" /><span class="grow">已支出只计 <span class="mono">status=APPROVED</span> 的支出，待复核不计</span></li>
          <li><span class="dot gray" /><span class="grow">认养覆盖率取 animal.adopted，与列表筛选同源</span></li>
        </ul>
      </PanelCard>
    </div>
  </div>
</template>
