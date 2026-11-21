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
import AnimalEditDialog from '@/views/archive/AnimalEditDialog.vue'
import { dateOnly, dateTime } from '@/utils/format'
import {
  animalStatusLabel,
  categoryLabel,
  fileIdFromUrl,
  genderLabel,
} from '@/utils/domain'
import type { AnimalChangeLogView, AnimalDetail, PointView } from '@/api/types'

/**
 * P02 档案详情：公开字段 + 登录可见健康记录 + 管理员可见变更历史。
 * 后端详情接口未返回 allowed 状态迁移，所以本页不伪造“下一步状态”按钮。
 */

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const code = String(route.params.id ?? '')
const animal = ref<AnimalDetail | null>(null)
const points = ref<PointView[]>([])
const history = ref<AnimalChangeLogView[]>([])
const historyError = ref('')
const loading = ref(false)
const errorText = ref('')
const editVisible = ref(false)

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
      :desc="animal ? `编号 ${animal.code} · ${categoryLabel(animal.category)} · ${animal.point?.name ?? '点位待补'}` : errorText ? '档案读取失败，请返回列表或刷新重试。' : '正在读取档案…'"
    >
      <template #actions>
        <button class="btn ghost" type="button" @click="router.push('/animals')">返回列表</button>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <button v-if="auth.isAdmin && animal" class="btn" type="button" @click="editVisible = true">编辑档案</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '个体档案', path: '/animals', matchPrefix: true },
        { label: '群体档案', path: '/groups' },
        { label: '物种名录', path: '/species' },
      ]"
    />

    <PageState :loading="loading" :error="errorText" :empty="!animal" empty-text="档案不存在或已被合并">
      <template v-if="animal">
        <div class="detail-top">
          <PanelCard title="档案概览" :more="`${animal.code}`">
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

          <PanelCard title="照护坐标" more="字段只展示，不生成推断">
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
            <div class="footnote" style="text-align: left">
              状态迁移接口尚未在 AnimalController 开放，本页只展示当前状态，不臆造 allowed 列表。
            </div>
          </PanelCard>
        </div>

        <HealthRecordPanel :code="animal.code" :editable="auth.isAdmin" />

        <PanelCard v-if="auth.isAdmin" title="字段级变更历史" :more="`共 ${history.length} 条`">
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
          <div class="footnote" style="text-align: left">
            来源：GET /animals/{code}/history，仅 ANIMAL_ADMIN / SYSTEM_ADMIN 可见。
          </div>
        </PanelCard>
      </template>
    </PageState>

    <AnimalEditDialog v-model="editVisible" :animal="animal" :points="points" @saved="afterSaved" />
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

.empty-line {
  padding: 12px 0;
  font-size: 12px;
}

.history-table {
  box-shadow: none;
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
