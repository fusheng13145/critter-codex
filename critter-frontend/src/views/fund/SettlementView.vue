<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as fundApi from '@/api/fund'
import { BizError } from '@/api/http'
import { fileContentUrl } from '@/api/file'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PanelCard from '@/components/PanelCard.vue'
import PageState from '@/components/PageState.vue'
import { dateTime, money } from '@/utils/format'
import type { ProjectSettlementView } from '@/api/types'

/** 结项公示：公开只读，展示收支合计、结项报告、结余去向与附件。 */
const route = useRoute()
const router = useRouter()
const projectNo = String(route.params.id ?? '')
const settlement = ref<ProjectSettlementView | null>(null)
const loading = ref(false)
const errorText = ref('')

const balancePositive = computed(() => (settlement.value?.balance ?? 0) >= 0)

async function load(): Promise<void> {
  if (!projectNo) return
  loading.value = true
  errorText.value = ''
  try {
    settlement.value = await fundApi.fetchProjectSettlement(projectNo)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '结项公示加载失败'
    settlement.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero
      :title="settlement?.projectName || '结项公示'"
      :desc="settlement
        ? `${settlement.projectNo} · 发布于 ${dateTime(settlement.publishedAt)}`
        : errorText || '正在读取结项公示…'"
    >
      <template #actions>
        <button class="btn ghost" type="button" @click="router.push(`/projects/${projectNo}`)">返回项目</button>
        <button class="btn ghost" type="button" @click="router.push('/projects')">项目列表</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '募捐项目', path: '/projects' },
        { label: '资金台账', path: '/ledger', adminOnly: true },
        { label: '支出复核', path: '/expenses', adminOnly: true },
      ]"
    />

    <PageState :loading="loading" :error="errorText" :empty="!settlement" empty-text="项目尚未发布结项公示">
      <template v-if="settlement">
        <div class="metrics">
          <div class="metric">
            <div class="label">已筹金额</div>
            <div class="num accent">{{ money(settlement.totalRaised) }}</div>
            <div class="sub">只含已核对入账捐赠</div>
          </div>
          <div class="metric">
            <div class="label">已复核支出</div>
            <div class="num">{{ money(settlement.totalSpent) }}</div>
            <div class="sub">每笔均已通过复核</div>
          </div>
          <div class="metric">
            <div class="label">结余</div>
            <div class="num" :class="{ danger: !balancePositive }">{{ money(settlement.balance) }}</div>
            <div class="sub">已筹 − 已支出</div>
          </div>
        </div>

        <div class="settle-grid">
          <PanelCard title="结项报告">
            <p class="report-text">{{ settlement.reportText }}</p>
          </PanelCard>

          <PanelCard title="结余去向">
            <p class="balance-to">{{ settlement.balanceTo }}</p>
            <div class="info">
              <div class="k">公示时间</div>
              <div class="v">{{ dateTime(settlement.publishedAt) }}</div>
            </div>
          </PanelCard>
        </div>

        <PanelCard title="公示附件" :more="`${settlement.attachments.length} 个`">
          <div v-if="!settlement.attachments.length" class="muted empty-line">未上传附件</div>
          <div v-else class="attachment-list">
            <a
              v-for="(fileId, index) in settlement.attachments"
              :key="fileId"
              class="attachment"
              :href="fileContentUrl(fileId)"
              target="_blank"
              rel="noopener"
            >
              附件 {{ index + 1 }}
              <span class="mono">{{ fileId }}</span>
            </a>
          </div>
        </PanelCard>

        <div class="footnote">
          结项公示与资金台账同源：收入取已核对捐赠，支出取已复核支出；公开接受全校监督。
        </div>
      </template>
    </PageState>
  </div>
</template>

<style scoped>
.accent {
  color: var(--accent);
}

.danger {
  color: var(--danger);
}

.settle-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 16px;
  align-items: start;
}

.report-text {
  white-space: pre-wrap;
  line-height: 1.9;
  color: var(--ink);
}

.balance-to {
  color: var(--ink);
  line-height: 1.8;
  font-weight: 600;
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.attachment {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm, 8px);
  color: var(--ink);
  text-decoration: none;
  background: var(--panel);
}

.attachment:hover {
  border-color: var(--accent);
}

.attachment .mono {
  color: var(--muted);
  font-size: 11px;
}

.empty-line {
  padding: 8px 0;
  font-size: 12px;
}

@media (max-width: 900px) {
  .settle-grid {
    grid-template-columns: 1fr;
  }
}
</style>
