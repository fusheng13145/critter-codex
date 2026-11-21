<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as statsApi from '@/api/stats'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PageState from '@/components/PageState.vue'
import PanelCard from '@/components/PanelCard.vue'
import type { EfficiencyStats } from '@/api/types'

/**
 * P04 支出复核。
 * 后端 T3.2/T3.3 尚未实现，本页只读取 /stats/efficiency 的 pendingExpenses 做真实待办计数，
 * 不编造支出列表；列表结构先按接口设计说明书预留。
 */

const auth = useAuthStore()
const data = ref<EfficiencyStats | null>(null)
const loading = ref(false)
const errorText = ref('')

async function load(): Promise<void> {
  if (!auth.isAdmin) return
  loading.value = true
  errorText.value = ''
  try {
    data.value = await statsApi.fetchEfficiency()
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '待复核数量加载失败'
    data.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero
      title="支出复核"
      desc="支出登记、凭证复核与入账需要 T3.2/T3.3 接口；当前只提供真实待办计数与页面骨架。"
    >
      <template #actions>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新计数</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '募捐项目', path: '/projects', matchPrefix: true },
        { label: '资金台账', path: '/ledger', adminOnly: true },
        { label: '支出复核', path: '/expenses', adminOnly: true },
      ]"
    />

    <div v-if="!auth.isAdmin" class="notice">支出复核仅 ANIMAL_ADMIN / SYSTEM_ADMIN 可见。</div>

    <template v-else>
      <PageState :loading="loading" :error="errorText" empty-text="暂无待复核数据">
        <div class="metrics">
          <div class="metric">
            <div class="label">待复核支出</div>
            <div class="num">{{ data?.pendingExpenses ?? 0 }}</div>
            <div class="sub">来源 /stats/efficiency，仅计数</div>
          </div>
          <div class="metric">
            <div class="label">支出登记接口</div>
            <div class="num">T3.2</div>
            <div class="sub">POST /expenses · 待实现</div>
          </div>
          <div class="metric">
            <div class="label">复核入账接口</div>
            <div class="num">T3.3</div>
            <div class="sub">PUT /expenses/{no}/verify · 待实现</div>
          </div>
        </div>

        <div class="expense-grid">
          <PanelCard title="支出列表（骨架）" more="接口未开放">
            <div class="toolbar">
              <input class="input grow-input" placeholder="单号 / 项目 / 科目" disabled />
              <select class="select" disabled>
                <option>全部状态</option>
                <option>PENDING_VERIFY</option>
                <option>VERIFIED</option>
                <option>ABNORMAL</option>
              </select>
              <button class="btn sm" type="button" disabled>筛选</button>
              <button class="btn sm" type="button" disabled>登记支出</button>
            </div>

            <div class="table-wrap skeleton-table">
              <table class="native">
                <thead>
                  <tr>
                    <th>支出单号</th>
                    <th>项目</th>
                    <th>科目</th>
                    <th>金额</th>
                    <th>凭证</th>
                    <th>状态</th>
                    <th>登记人</th>
                    <th>时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td colspan="8" class="skeleton-cell">
                      等待 <span class="mono">T3.2 / T3.3</span> 提供支出列表接口后接入；此处不放置假数据。
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="footnote" style="text-align: left">
              已确认契约：金额为整数分、凭证必填、支出只能挂在已入账项目余额内、复核通过才计入 spentAmount。
            </div>
          </PanelCard>

          <PanelCard title="待确认的展示口径">
            <div class="rule-list">
              <div class="rule">
                <b>支出与项目余额</b>
                <span>支出金额不得超过项目已筹余额；待复核支出不进入公开进度。</span>
              </div>
              <div class="rule">
                <b>凭证异常</b>
                <span>金额或凭证不一致时转 ABNORMAL，保持原余额并写审计日志。</span>
              </div>
              <div class="rule">
                <b>批量复核</b>
                <span>逐笔独立事务，部分成功不回滚；失败项要逐条回显错误码。</span>
              </div>
              <div class="rule">
                <b>台账联动</b>
                <span>T3.5 逐笔台账需要同时记录捐赠入账与支出复核，本页不提前拼装。</span>
              </div>
            </div>
          </PanelCard>
        </div>
      </PageState>
    </template>
  </div>
</template>

<style scoped>
.expense-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(280px, 1fr);
  gap: 16px;
  align-items: start;
}

.skeleton-table {
  box-shadow: none;
}

.skeleton-cell {
  padding: 32px;
  text-align: center;
  color: var(--muted);
  font-size: 12px;
  background: var(--chip);
}

.rule-list {
  display: grid;
  gap: 10px;
}

.rule {
  padding: 10px 12px;
  border-left: 3px solid var(--accent-2);
  background: var(--hover);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

.rule b {
  display: block;
  margin-bottom: 3px;
  font-size: 12px;
}

.rule span {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

@media (max-width: 1000px) {
  .expense-grid {
    grid-template-columns: 1fr;
  }
}
</style>
