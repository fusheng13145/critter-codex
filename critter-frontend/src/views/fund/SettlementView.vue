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
import { useAuthStore } from '@/stores/auth'
import type { ProjectSettlementView } from '@/api/types'

/** 结项公示：公开只读，展示收支合计、结项报告、结余去向与附件。 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const projectNo = String(route.params.id ?? '')
const settlement = ref<ProjectSettlementView | null>(null)
const loading = ref(false)
const errorText = ref('')
const noteText = ref('')
const noteSaving = ref(false)
const noteError = ref('')

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

async function appendNote(): Promise<void> {
  if (noteSaving.value) return
  const note = noteText.value.trim()
  if (note.length < 5) {
    noteError.value = '追加说明至少 5 个字'
    return
  }
  if (note.length > 2000) {
    noteError.value = '追加说明最多 2000 个字'
    return
  }
  noteSaving.value = true
  noteError.value = ''
  try {
    settlement.value = await fundApi.appendSettlementNote(projectNo, note)
    noteText.value = ''
  } catch (error) {
    noteError.value = error instanceof BizError ? error.message : '追加说明提交失败'
  } finally {
    noteSaving.value = false
  }
}
</script>

<template>
  <div>
    <PageHero
      :title="settlement?.projectName || '结项公示'"
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

    <PageState :loading="loading" :error="errorText" :empty="!settlement">
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

        <PanelCard title="公示附件">
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



        <PanelCard
          title="结项后追加说明"
          style="margin-top: 16px"
        >
          <div v-if="!settlement.appendNotes.length" class="muted empty-line">
            暂无追加说明
          </div>
          <div v-else class="note-list">
            <div v-for="(item, index) in settlement.appendNotes" :key="index" class="note-item">
              <div class="note-head">
                <span class="mono">#{{ index + 1 }}</span>
                <span class="muted small">{{ dateTime(item.createdAt) }}</span>
                <span v-if="item.operatorId" class="muted small">操作人 {{ item.operatorId }}</span>
              </div>
              <div class="note-body">{{ item.note }}</div>
            </div>
          </div>

          <div v-if="auth.isAdmin" class="note-form">
            <textarea
              v-model="noteText"
              class="textarea"
              rows="3"
              maxlength="2000"
              placeholder="例如：2026-11 剩余善款已转用于校园投喂点维护，凭证见附件 3。"
            />
            <div class="note-actions">
              <span class="muted small">{{ noteText.length }} / 2000</span>
              <button class="btn sm" type="button" :disabled="noteSaving" @click="appendNote">
                {{ noteSaving ? '提交中…' : '追加说明' }}
              </button>
            </div>
            <div v-if="noteError" class="notice">{{ noteError }}</div>
          </div>
        </PanelCard>
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

.note-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.note-item {
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm, 8px);
  background: var(--panel);
}

.note-head {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 6px;
  font-size: 11px;
  color: var(--muted);
}

.note-body {
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 13px;
}

.note-form {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}

.note-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

@media (max-width: 900px) {
  .settle-grid {
    grid-template-columns: 1fr;
  }
}
</style>
