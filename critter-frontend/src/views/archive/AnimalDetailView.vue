<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PanelCard from '@/components/PanelCard.vue'
import PageState from '@/components/PageState.vue'
import StatusTag from '@/components/StatusTag.vue'
import AuthImage from '@/components/AuthImage.vue'
import HealthRecordPanel from '@/views/archive/HealthRecordPanel.vue'
import AnimalVisitPanel from '@/views/archive/AnimalVisitPanel.vue'
import AnimalEditDialog from '@/views/archive/AnimalEditDialog.vue'
import AnimalStatusDialog from '@/views/archive/AnimalStatusDialog.vue'
import AnimalMergeDialog from '@/views/archive/AnimalMergeDialog.vue'
import AnimalQrDialog from '@/views/archive/AnimalQrDialog.vue'
import CommentPanel from '@/components/CommentPanel.vue'
import FollowButton from '@/components/FollowButton.vue'
import { dateOnly, dateTime } from '@/utils/format'
import {
  animalStatusLabel,
  categoryLabel,
  fileIdFromUrl,
  genderLabel,
} from '@/utils/domain'
import type { AnimalChangeLogView, AnimalDetail, AnimalStatusLogView, PointView } from '@/api/types'

/**
 * P02 档案详情：公开字段 + 登录可见健康记录 + 管理员可见状态迁移、字段变更历史。
 */

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const code = String(route.params.id ?? '')
const animal = ref<AnimalDetail | null>(null)
const points = ref<PointView[]>([])
const history = ref<AnimalChangeLogView[]>([])
const historyError = ref('')
const statusHistory = ref<AnimalStatusLogView[]>([])
const statusHistoryError = ref('')
const loading = ref(false)
const errorText = ref('')
const editVisible = ref(false)
const statusVisible = ref(false)
const mergeVisible = ref(false)
const qrVisible = ref(false)

async function load(): Promise<void> {
  if (!code) return
  loading.value = true
  errorText.value = ''
  try {
    animal.value = await archiveApi.fetchAnimal(code)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '档案详情加载失败'
    animal.value = null
    loading.value = false
    return
  }
  loading.value = false

  if (auth.isAdmin) {
    try {
      history.value = await archiveApi.fetchAnimalHistory(code)
      historyError.value = ''
    } catch (error) {
      historyError.value = error instanceof BizError ? error.message : '变更历史加载失败'
    }
  }
  if (auth.hasRole('ANIMAL_ADMIN')) {
    try {
      statusHistory.value = await archiveApi.fetchAnimalStatusHistory(code)
      statusHistoryError.value = ''
    } catch (error) {
      statusHistoryError.value = error instanceof BizError ? error.message : '状态时间线加载失败'
    }
  }
}

async function loadPoints(): Promise<void> {
  try {
    points.value = await archiveApi.fetchPoints()
  } catch {
    points.value = []
  }
}

async function afterSaved(): Promise<void> {
  await load()
}

onMounted(() => {
  loadPoints()
  load()
})
</script>

<template>
  <div>
    <PageHero
      :title="animal?.name || code || '档案详情'"
    >
      <template #actions>
        <button class="btn ghost" type="button" @click="router.push('/animals')">返回列表</button>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <FollowButton v-if="animal" biz-type="ANIMAL" :biz-id="animal.code" label="关注这只动物" />
        <button v-if="auth.isAdmin && animal" class="btn ghost" type="button" @click="editVisible = true">编辑档案</button>
        <button v-if="auth.isAdmin && animal" class="btn ghost" type="button" @click="mergeVisible = true">合并重复档案</button>
        <button v-if="auth.isAdmin && animal" class="btn ghost" type="button" @click="qrVisible = true">二维码贴牌</button>
        <button
          v-if="auth.hasRole('ANIMAL_ADMIN') && animal && animal.allowedStatuses.length"
          class="btn"
          type="button"
          @click="statusVisible = true"
        >
          状态迁移
        </button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '个体档案', path: '/animals', matchPrefix: true },
        { label: '群体档案', path: '/groups' },
        { label: '物种名录', path: '/species' },
        { label: '点位维护', path: '/points' },
        { label: '巡查记录', path: '/patrols' },
      ]"
    />

    <PageState :loading="loading" :error="errorText" :empty="!animal">
      <template v-if="animal">
        <div v-if="code !== animal.code" class="notice" style="margin-bottom: 14px">
          编号 <span class="mono">{{ code }}</span> 已并入，当前为主档案
          <span class="mono">{{ animal.code }}</span>。
        </div>

        <div class="detail-top">
          <PanelCard title="档案概览">
            <div class="profile">
              <div class="profile-cover">
                <AuthImage :file-id="fileIdFromUrl(animal.coverUrl)" :alt="animal.name ?? animal.code" fit="cover" />
              </div>
              <div class="profile-main">
                <div class="profile-title">
                  <h2>{{ animal.name || '未命名个体' }}</h2>
                  <StatusTag :code="animal.status" :label="animalStatusLabel(animal.status)" />
                </div>
                <div class="tag-row">
                  <span class="tag gray">{{ categoryLabel(animal.category) }}</span>
                  <span class="tag gray">{{ genderLabel(animal.gender) }}</span>
                  <span class="tag gray">{{ animal.color }}</span>
                  <span class="tag" :class="{ gray: !animal.neutered }">{{ animal.neutered ? '已绝育' : '未绝育' }}</span>
                  <span class="tag" :class="{ gray: !animal.adopted }">{{ animal.adopted ? '已认养' : '未认养' }}</span>
                </div>
                <p class="features">{{ animal.features }}</p>
                <div class="meta-line muted">
                  建档 {{ dateTime(animal.createdAt) }} · 最近更新 {{ dateTime(animal.updatedAt) }}
                </div>
              </div>
            </div>
          </PanelCard>

          <PanelCard title="照护坐标">
            <div class="info-grid single">
              <div class="info">
                <div class="k">常驻点位</div>
                <div class="v">{{ animal.point?.name ?? '—' }}</div>
              </div>
              <div class="info">
                <div class="k">估计年龄</div>
                <div class="v">{{ animal.estimatedAge !== null ? `${animal.estimatedAge} 岁` : '未知' }}</div>
              </div>
              <div class="info">
                <div class="k">绝育日期</div>
                <div class="v">{{ animal.neuteredAt ? dateOnly(animal.neuteredAt) : '未登记' }}</div>
              </div>
              <div class="info">
                <div class="k">认养状态</div>
                <div class="v">{{ animal.adopted ? '已有生效认养' : '未认养' }}</div>
              </div>
            </div>
            <div v-if="animal.internalNote" class="notice" style="margin-top: 14px">
              内部备注（仅管理员可见）：{{ animal.internalNote }}
            </div>
            <div v-if="animal.mergedCodes.length" class="merge-line">
              <div class="k">已并入的旧编号</div>
              <div class="tag-row" style="margin: 6px 0 0">
                <span v-for="oldCode in animal.mergedCodes" :key="oldCode" class="tag gray mono">{{ oldCode }}</span>
              </div>
            </div>
          </PanelCard>
        </div>

        <HealthRecordPanel :code="animal.code" :editable="auth.isAdmin" />

        <AnimalVisitPanel :code="animal.code" />

        <CommentPanel biz-type="ANIMAL" :biz-id="animal.code" />

        <PanelCard v-if="auth.hasRole('ANIMAL_ADMIN')" title="状态时间线">
          <div v-if="statusHistoryError" class="notice">{{ statusHistoryError }}</div>
          <div v-else-if="!statusHistory.length" class="muted empty-line">暂无状态迁移记录</div>
          <div v-else class="status-timeline">
            <div v-for="(item, index) in statusHistory" :key="`${item.occurredAt}-${index}`" class="status-node">
              <span class="status-dot" />
              <div class="status-body">
                <div class="status-line">
                  <b>{{ item.fromLabel }}</b>
                  <span class="arrow">→</span>
                  <b>{{ item.toLabel }}</b>
                  <span class="tag gray">{{ item.eventLabel }}</span>
                </div>
                <div class="status-reason">{{ item.reason }}</div>
                <div class="muted small">{{ dateTime(item.occurredAt) }} · {{ item.event }}</div>
              </div>
            </div>
          </div>

        </PanelCard>

        <PanelCard v-if="auth.isAdmin" title="字段级变更历史">
          <div v-if="historyError" class="notice">{{ historyError }}</div>
          <div v-else-if="!history.length" class="muted empty-line">暂无变更记录</div>
          <div v-else class="table-wrap history-table">
            <table class="native">
              <thead>
                <tr>
                  <th>字段</th>
                  <th>变更前</th>
                  <th>变更后</th>
                  <th>原因</th>
                  <th>操作人</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in history" :key="`${item.field}-${item.createdAt}-${index}`">
                  <td class="mono">{{ item.field }}</td>
                  <td class="muted">{{ item.beforeValue ?? '—' }}</td>
                  <td>{{ item.afterValue ?? '—' }}</td>
                  <td>{{ item.reason }}</td>
                  <td class="mono">{{ item.operatorId ?? '—' }}</td>
                  <td class="muted small">{{ dateTime(item.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

        </PanelCard>
      </template>
    </PageState>

    <AnimalEditDialog v-model="editVisible" :animal="animal" :points="points" @saved="afterSaved" />
    <AnimalStatusDialog v-model="statusVisible" :animal="animal" @saved="afterSaved" />
    <AnimalMergeDialog v-model="mergeVisible" :animal="animal" @saved="afterSaved" />
    <AnimalQrDialog v-model="qrVisible" :animal="animal" />
  </div>
</template>

<style scoped>
.detail-top {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
  align-items: start;
}

.profile {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}

.profile-cover {
  flex: 0 0 132px;
  width: 132px;
  height: 132px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  border: 1px solid var(--line);
}

.profile-main {
  min-width: 0;
  flex: 1;
}

.profile-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.profile-title h2 {
  font-size: 22px;
}

.tag-row {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.features {
  color: var(--ink);
  font-size: 13px;
}

.meta-line {
  font-size: 11px;
  margin-top: 10px;
}

.info-grid.single {
  grid-template-columns: 1fr 1fr;
  margin-top: 0;
}

.merge-line {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}

.merge-line .k {
  font-size: 12px;
  color: var(--muted);
}

.empty-line {
  padding: 12px 0;
  font-size: 12px;
}

.history-table {
  box-shadow: none;
}

.status-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding-left: 4px;
}

.status-node {
  display: flex;
  gap: 12px;
  position: relative;
  padding-bottom: 16px;
}

.status-node:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 5px;
  top: 16px;
  bottom: 0;
  width: 1px;
  background: var(--line);
}

.status-dot {
  width: 11px;
  height: 11px;
  margin-top: 4px;
  border-radius: 50%;
  background: var(--brand);
  flex: 0 0 auto;
}

.status-body {
  min-width: 0;
}

.status-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 13px;
}

.status-line .arrow {
  color: var(--brand);
}

.status-reason {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink);
}

.small {
  font-size: 11px;
}

@media (max-width: 1000px) {
  .detail-top {
    grid-template-columns: 1fr;
  }

  .profile {
    flex-direction: column;
  }

  .profile-cover {
    width: 100%;
    height: 200px;
  }
}
</style>
