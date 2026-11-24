<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PageState from '@/components/PageState.vue'
import StatusTag from '@/components/StatusTag.vue'
import AuthImage from '@/components/AuthImage.vue'
import AnimalCreateDialog from '@/views/archive/AnimalCreateDialog.vue'
import { dateOnly } from '@/utils/format'
import {
  ANIMAL_STATUS_OPTIONS,
  animalStatusLabel,
  categoryLabel,
  fileIdFromUrl,
  genderLabel,
} from '@/utils/domain'
import type { AnimalSummary, PageData, PointView } from '@/api/types'

/**
 * P02 档案中心 · 个体列表。
 * 列表接口匿名可读，但页面在登录壳内；建档按钮按角色显示，最终仍由后端拦一次。
 */

const router = useRouter()
const auth = useAuthStore()

const query = reactive({
  keyword: '',
  status: '',
  pointId: null as number | null,
  neutered: '',
  adopted: '',
  page: 1,
  size: 15,
})

const pageData = ref<PageData<AnimalSummary>>({
  list: [],
  page: { page: 1, size: 15, total: 0, pages: 0 },
})
const points = ref<PointView[]>([])
const loading = ref(false)
const errorText = ref('')
const createVisible = ref(false)

async function loadPoints(): Promise<void> {
  try {
    points.value = await archiveApi.fetchPoints()
  } catch {
    points.value = []
  }
}

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    pageData.value = await archiveApi.fetchAnimals({
      keyword: query.keyword.trim() || undefined,
      status: query.status || undefined,
      pointId: query.pointId ?? undefined,
      neutered: query.neutered === '' ? undefined : query.neutered === 'true',
      adopted: query.adopted === '' ? undefined : query.adopted === 'true',
      page: query.page,
      size: query.size,
    })
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '档案列表加载失败，请确认后端已启动'
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.page = 1
  load()
}

function reset(): void {
  query.keyword = ''
  query.status = ''
  query.pointId = null
  query.neutered = ''
  query.adopted = ''
  query.page = 1
  load()
}

function onCreated(code: string): void {
  ElMessage.success(`已创建 ${code}`)
  search()
}

function changePage(page: number): void {
  query.page = page
  load()
}

onMounted(() => {
  loadPoints()
  load()
})
</script>

<template>
  <div>
    <PageHero
      title="生物档案"
    >
      <template #actions>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <button v-if="auth.isAdmin" class="btn" type="button" @click="createVisible = true">新建档案</button>
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

    <div class="toolbar">
      <input
        v-model="query.keyword"
        class="input grow-input"
        placeholder="编号 / 昵称 / 识别特征"
        @keyup.enter="search"
      />
      <select v-model="query.status" class="select">
        <option value="">全部状态</option>
        <option v-for="item in ANIMAL_STATUS_OPTIONS" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
      <select v-model.number="query.pointId" class="select">
        <option :value="null">全部点位</option>
        <option v-for="point in points" :key="point.id" :value="point.id">{{ point.name }}</option>
      </select>
      <select v-model="query.neutered" class="select">
        <option value="">绝育不限</option>
        <option value="true">已绝育</option>
        <option value="false">未绝育</option>
      </select>
      <select v-model="query.adopted" class="select">
        <option value="">认养不限</option>
        <option value="true">已认养</option>
        <option value="false">未认养</option>
      </select>
      <button class="btn sm" type="button" @click="search">筛选</button>
      <button class="btn sm ghost" type="button" @click="reset">重置</button>
      <span class="muted result-count">共 {{ pageData.page.total }} 只 · 按更新时间倒序</span>
    </div>

    <PageState
      :loading="loading"
      :error="errorText"
      :empty="!pageData.list.length"
    >
      <div class="table-wrap">
        <table class="native">
          <thead>
            <tr>
              <th style="width: 58px">照片</th>
              <th>编号 / 昵称</th>
              <th>类别</th>
              <th>点位</th>
              <th>状态</th>
              <th>照护</th>
              <th>更新</th>
              <th style="width: 86px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="animal in pageData.list" :key="animal.code">
              <td>
                <div class="animal-thumb">
                  <AuthImage :file-id="fileIdFromUrl(animal.coverUrl)" :alt="animal.name ?? animal.code" />
                </div>
              </td>
              <td>
                <div class="animal-code mono">{{ animal.code }}</div>
                <div class="animal-name">{{ animal.name || '未命名' }}</div>
              </td>
              <td>
                <div>{{ categoryLabel(animal.category) }} · {{ genderLabel(animal.gender) }}</div>
                <div class="muted small">{{ animal.color }}{{ animal.estimatedAge !== null ? ` · ${animal.estimatedAge} 岁` : '' }}</div>
              </td>
              <td>{{ animal.point?.name ?? '—' }}</td>
              <td>
                <StatusTag :code="animal.status" :label="animalStatusLabel(animal.status)" />
              </td>
              <td>
                <div class="tag-row">
                  <span class="tag" :class="{ gray: !animal.neutered }">{{ animal.neutered ? '已绝育' : '未绝育' }}</span>
                  <span class="tag" :class="{ gray: !animal.adopted }">{{ animal.adopted ? '已认养' : '未认养' }}</span>
                </div>
              </td>
              <td class="muted small">{{ dateOnly(animal.updatedAt) }}</td>
              <td>
                <button class="btn sm link" type="button" @click="router.push(`/animals/${animal.code}`)">
                  查看
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <el-pagination
        v-if="pageData.page.total > 0"
        background
        layout="prev, pager, next"
        :current-page="pageData.page.page"
        :page-size="pageData.page.size"
        :total="pageData.page.total"
        @current-change="changePage"
      />
    </PageState>

    <AnimalCreateDialog v-model="createVisible" :points="points" @created="onCreated" />
  </div>
</template>

<style scoped>
.result-count {
  margin-left: auto;
  font-size: 12px;
}

.animal-thumb {
  width: 46px;
  height: 46px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  border: 1px solid var(--line);
}

.animal-code {
  font-size: 11px;
}

.animal-name {
  font-weight: 600;
  margin-top: 1px;
}

.small {
  font-size: 11px;
}

.tag-row {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
</style>
