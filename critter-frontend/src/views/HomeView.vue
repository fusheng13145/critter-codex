<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as statsApi from '@/api/stats'
import * as logApi from '@/api/log'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import MetricCard from '@/components/MetricCard.vue'
import PanelCard from '@/components/PanelCard.vue'
import PageState from '@/components/PageState.vue'
import BarChart from '@/components/BarChart.vue'
import { actionLabel, moneyShort, relativeTime, statusLabel } from '@/utils/format'
import type { BiologyStats, EfficiencyStats, FundStats, LogItem } from '@/api/types'

/**
 * P01 工作台：数字全部来自 /stats/*，没有假图。
 * 资金与待办只对管理员开放，学生看到的是自己的统计面，这是权限差异而不是缺数据。
 */

const auth = useAuthStore()
const router = useRouter()

const biology = ref<BiologyStats | null>(null)
const fund = ref<FundStats | null>(null)
const efficiency = ref<EfficiencyStats | null>(null)
const logs = ref<LogItem[]>([])
const loading = ref(true)
const denied = ref('')

const monthPoints = computed(() =>
  (biology.value?.months ?? []).map((item) => ({
    label: `${Number(item.month.slice(5))}月`,
    value: item.newAnimals,
  })),
)

const currentMonthNew = computed(() => biology.value?.months.at(-1)?.newAnimals ?? 0)

const today = new Date()
const monthRaised = computed(() => {
  const prefix = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`
  const row = fund.value?.byMonth.find((item) => item.month === prefix)
  return row?.raisedAmount ?? 0
})

/** 待办只给"哪一类、多少件、去哪处理"，判定依据留在对应列表页。 */
interface Todo {
  label: string
  count: number
  to: string
  urgent: boolean
}

const todos = computed<Todo[]>(() => {
  if (!efficiency.value) return []
  const items: Todo[] = []
  if (efficiency.value.reports.pending > 0) {
    items.push({ label: '上报待受理', count: efficiency.value.reports.pending, to: '/report', urgent: true })
  }
  if (efficiency.value.orders.timeout > 0) {
    items.push({ label: '救助工单超时', count: efficiency.value.orders.timeout, to: '/report', urgent: true })
  }
  if (efficiency.value.pendingExpenses > 0) {
    items.push({ label: '支出待复核', count: efficiency.value.pendingExpenses, to: '/fund', urgent: false })
  }
  if (efficiency.value.pendingAdoptions > 0) {
    items.push({ label: '认养待审核', count: efficiency.value.pendingAdoptions, to: '/adopt', urgent: false })
  }
  return items
})

async function load(): Promise<void> {
  loading.value = true
  denied.value = ''
  try {
    biology.value = await statsApi.fetchBiology()
  } catch (error) {
    denied.value = error instanceof BizError ? error.message : '统计数据加载失败'
  }

  if (auth.isAdmin) {
    try {
      const [fundData, efficiencyData] = await Promise.all([
        statsApi.fetchFund(),
        statsApi.fetchEfficiency(),
      ])
      fund.value = fundData
      efficiency.value = efficiencyData
    } catch (error) {
      denied.value = error instanceof BizError ? error.message : '资金统计加载失败'
    }
  }

  if (auth.isSystemAdmin) {
    try {
      const page = await logApi.fetchLogs({ page: 1, size: 5 })
      logs.value = page.list
    } catch {
      logs.value = []
    }
  }
  loading.value = false
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero title="工作台">
      <template #actions>
        <button v-if="auth.isAdmin" class="btn" type="button" @click="router.push('/admin')">处理待办</button>
        <button v-else class="btn" type="button" @click="router.push('/report')">上报</button>
      </template>
    </PageHero>

    <div v-if="denied" class="notice" style="margin-bottom: 16px">{{ denied }}</div>

    <div class="metrics">
      <MetricCard
        label="在册生物"
        :value="loading ? '—' : String(biology?.total ?? 0)"
        sub="本月新增"
        :sub-strong="loading ? '' : `+${currentMonthNew}`"
      />
      <MetricCard
        label="已绝育"
        :value="loading ? '—' : String(biology?.neuteredCount ?? 0)"
        sub="覆盖率"
        :sub-strong="loading ? '' : `${biology?.neuteredRate ?? 0}%`"
      />
      <MetricCard
        label="生效认养"
        :value="loading ? '—' : String(biology?.activeAdoptions ?? 0)"
        sub="覆盖率"
        :sub-strong="loading ? '' : `${biology?.adoptionCoverage ?? 0}%`"
      />
      <MetricCard
        v-if="auth.isAdmin"
        label="本月入账"
        :value="loading ? '—' : moneyShort(monthRaised)"
        sub="已支出"
        :sub-strong="moneyShort(fund?.summary.spentAmount ?? 0)"
      />
      <MetricCard v-else label="群体 / 出现记录" :value="`${biology?.groupCount ?? 0} / ${biology?.occurrenceCount ?? 0}`" />
    </div>

    <div class="grid2 align-start">
      <PanelCard title="近半年新增档案">
        <BarChart v-if="monthPoints.length" :points="monthPoints" :alt-from="3" />
        <PageState v-else :empty="true" />
      </PanelCard>

      <PanelCard v-if="auth.isAdmin" title="待办">
        <PageState :empty="todos.length === 0">
          <ul class="list">
            <li v-for="item in todos" :key="item.label">
              <span class="dot" :class="{ gray: !item.urgent }" />
              <span class="grow">{{ item.label }}</span>
              <b class="count">{{ item.count }}</b>
              <button class="btn sm ghost" type="button" @click="router.push(item.to)">处理</button>
            </li>
          </ul>
        </PageState>
      </PanelCard>

      <PanelCard v-else title="状态分布">
        <div class="grid3" style="margin: 0">
          <div v-for="slice in (biology?.byStatus ?? []).filter((item) => item.count > 0)" :key="slice.status">
            <div style="display: flex; justify-content: space-between; font-size: 12px; color: var(--muted)">
              <span>{{ statusLabel(slice.status) }}</span>
              <span>{{ slice.count }}</span>
            </div>
            <div class="progress">
              <i :style="{ width: `${Math.round((slice.count / Math.max(1, biology?.total ?? 1)) * 100)}%` }" />
            </div>
          </div>
        </div>
      </PanelCard>
    </div>

    <PanelCard v-if="auth.isSystemAdmin" title="操作日志">
      <ul class="list">
        <li v-for="item in logs" :key="item.id">
          <span class="dot" :class="{ gray: item.result !== 'SUCCESS' }" />
          <span class="grow">
            {{ actionLabel(item.action) }} · {{ item.bizType }}/{{ item.bizId ?? '—' }}
            <span v-if="item.result !== 'SUCCESS'" class="tag danger" style="margin-left: 6px">
              失败 {{ item.failCode }}
            </span>
          </span>
          <span class="time">{{ relativeTime(item.createdAt) }}</span>
        </li>
        <li v-if="!logs.length">
          <span class="dot gray" />
          <span class="grow muted">—</span>
        </li>
      </ul>
    </PanelCard>
  </div>
</template>
