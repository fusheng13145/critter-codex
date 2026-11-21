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
import BarChart from '@/components/BarChart.vue'
import { actionLabel, moneyShort, relativeTime, statusLabel } from '@/utils/format'
import type { BiologyStats, EfficiencyStats, FundStats, LogItem } from '@/api/types'

/**
 * P01 工作台：数字全部来自 /stats/*（A 的统计接口），没有假图。
 * 资金与效率只对管理员开放，学生看到的是"我的身份 + 快捷入口"，这是权限而不是缺数据。
 */

const auth = useAuthStore()
const router = useRouter()

const biology = ref<BiologyStats | null>(null)
const fund = ref<FundStats | null>(null)
const efficiency = ref<EfficiencyStats | null>(null)
const logs = ref<LogItem[]>([])
const loading = ref(true)
const denied = ref('')

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

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

/** 待办数字为 0 时不再拼"0 个待审核"，避免读起来像缺数据。 */
const heroDesc = computed(() => {
  if (!auth.isAdmin) {
    return '你有上报、认养与留言的完整权限；需要治理类操作时请联系社团管理员。'
  }
  if (!efficiency.value) {
    return '正在读取待办与统计…'
  }
  const parts: string[] = []
  if (efficiency.value.reports.pending > 0) parts.push(`${efficiency.value.reports.pending} 条上报待受理`)
  if (efficiency.value.pendingAdoptions > 0) parts.push(`${efficiency.value.pendingAdoptions} 个认养申请待审核`)
  if (efficiency.value.pendingExpenses > 0) parts.push(`${efficiency.value.pendingExpenses} 笔支出待复核`)
  if (efficiency.value.orders.timeout > 0) parts.push(`${efficiency.value.orders.timeout} 单工单已超时`)
  if (parts.length === 0) {
    return '今天没有待办，主链路队列是空的。让每一只校园生灵都被看见。'
  }
  return `今天有 ${parts.join('、')}。让每一只校园生灵都被看见。`
})

const todos = computed(() => {
  if (!efficiency.value) return []
  const items: { text: string; tag: string; warn: boolean; to: string }[] = []
  if (efficiency.value.reports.pending > 0) {
    items.push({
      text: `上报待受理 ${efficiency.value.reports.pending} 条 · 24 小时内需响应`,
      tag: '待受理',
      warn: true,
      to: '/report',
    })
  }
  if (efficiency.value.orders.timeout > 0) {
    items.push({
      text: `救助工单超时 ${efficiency.value.orders.timeout} 单 · 需说明原因`,
      tag: '超时',
      warn: true,
      to: '/report',
    })
  }
  if (efficiency.value.pendingExpenses > 0) {
    items.push({
      text: `支出待复核 ${efficiency.value.pendingExpenses} 笔 · 超 500 元需双签`,
      tag: '复核',
      warn: false,
      to: '/fund',
    })
  }
  if (efficiency.value.pendingAdoptions > 0) {
    items.push({
      text: `认养申请待审核 ${efficiency.value.pendingAdoptions} 单`,
      tag: '待办',
      warn: false,
      to: '/adopt',
    })
  }
  if (items.length === 0) {
    items.push({ text: '暂无待办，主链路队列是空的', tag: '清空', warn: false, to: '/' })
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
    <PageHero :title="`${greeting}，${auth.displayName}`" :desc="heroDesc">
      <template #actions>
        <button v-if="auth.isAdmin" class="btn" type="button" @click="router.push('/admin')">处理待办</button>
        <button v-else class="btn" type="button" @click="router.push('/report')">我要上报</button>
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
      <MetricCard v-else label="群体 / 出现记录" :value="`${biology?.groupCount ?? 0} / ${biology?.occurrenceCount ?? 0}`" sub="三层口径分开统计" />
    </div>

    <div class="grid2 align-start">
      <PanelCard title="近半年新增档案" more="单位：只（个体）">
        <BarChart v-if="monthPoints.length" :points="monthPoints" :alt-from="3" />
        <div v-else class="muted" style="padding: 20px 0">暂无数据</div>
        <div class="footnote" style="text-align: left; margin-top: 10px">
          数据取自 <span class="mono">/stats/biology</span>，与档案列表同源；群体与出现记录不计入此图。
        </div>
      </PanelCard>

      <PanelCard v-if="auth.isAdmin" title="待办事项" :more="`共 ${todos.length} 项`">
        <ul class="list">
          <li v-for="item in todos" :key="item.text">
            <span class="dot" :class="{ gray: !item.warn }" />
            <span class="grow">{{ item.text }}</span>
            <span class="tag" :class="{ warn: item.warn }">{{ item.tag }}</span>
            <button class="btn sm ghost" type="button" @click="router.push(item.to)">前往</button>
          </li>
        </ul>
      </PanelCard>

      <PanelCard v-else title="我的身份与菜单" more="由后端权限矩阵下发">
        <ul class="list">
          <li v-for="menu in auth.menus" :key="menu.code">
            <span class="dot" />
            <span class="grow">{{ menu.title }}</span>
            <span class="tag gray">{{ menu.owner === 'A' ? '已实现' : `${menu.owner} 负责` }}</span>
          </li>
        </ul>
        <div class="notice" style="margin-top: 12px">
          角色不是单值：主角色 + 叠加身份。申请志愿者或认养人身份后，审核通过才会出现对应菜单。
        </div>
      </PanelCard>
    </div>

    <PanelCard
      v-if="auth.isSystemAdmin"
      title="后台动态"
      more="最近 5 条操作日志"
    >
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
          <span class="grow muted">暂无日志</span>
        </li>
      </ul>
      <div class="footnote" style="text-align: left; margin-top: 10px">
        任一档案变更、资金操作都能在这里查到操作人、时间与前后值。
      </div>
    </PanelCard>

    <PanelCard v-else title="近期状态分布" more="排除已离世">
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
</template>
