<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as fundApi from '@/api/fund'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PageState from '@/components/PageState.vue'
import ProjectCard from '@/views/fund/ProjectCard.vue'
import ProjectCreateDialog from '@/views/fund/ProjectCreateDialog.vue'
import { PROJECT_STATUS_OPTIONS, TARGET_TYPE_OPTIONS } from '@/utils/domain'
import type { PageData, ProjectSummary } from '@/api/types'

/** P04 资金 · 项目列表：公开进度来自已入账捐赠，募捐中项目才能继续登记。 */

const auth = useAuthStore()

const query = reactive({
  keyword: '',
  status: '',
  targetType: '',
  page: 1,
  size: 12,
})

const pageData = ref<PageData<ProjectSummary>>({
  list: [],
  page: { page: 1, size: 12, total: 0, pages: 0 },
})
const loading = ref(false)
const errorText = ref('')
const createVisible = ref(false)

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    pageData.value = await fundApi.fetchProjects({
      keyword: query.keyword.trim() || undefined,
      status: query.status || undefined,
      targetType: query.targetType || undefined,
      page: query.page,
      size: query.size,
    })
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '项目列表加载失败'
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
  query.targetType = ''
  query.page = 1
  load()
}

function onCreated(projectNo: string): void {
  ElMessage.success(`项目已创建：${projectNo}`)
  search()
}

function changePage(page: number): void {
  query.page = page
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero
      title="募捐项目"
    >
      <template #actions>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <button v-if="auth.hasRole('ANIMAL_ADMIN')" class="btn" type="button" @click="createVisible = true">发起项目</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '募捐项目', path: '/projects', matchPrefix: true },
        { label: '资金台账', path: '/ledger', adminOnly: true },
        { label: '支出复核', path: '/expenses', adminOnly: true },
      ]"
    />

    <div class="toolbar">
      <input
        v-model="query.keyword"
        class="input grow-input"
        placeholder="项目编号 / 名称"
        @keyup.enter="search"
      />
      <select v-model="query.status" class="select">
        <option value="">全部状态</option>
        <option v-for="item in PROJECT_STATUS_OPTIONS" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
      <select v-model="query.targetType" class="select">
        <option value="">全部对象</option>
        <option v-for="item in TARGET_TYPE_OPTIONS" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
      <button class="btn sm" type="button" @click="search">筛选</button>
      <button class="btn sm ghost" type="button" @click="reset">重置</button>
      <span class="muted result-count">共 {{ pageData.page.total }} 个项目</span>
    </div>

    <PageState
      :loading="loading"
      :error="errorText"
      :empty="!pageData.list.length"
    >
      <div class="project-grid">
        <ProjectCard v-for="project in pageData.list" :key="project.projectNo" :project="project" />
      </div>

      <el-pagination
        v-if="pageData.page.total > query.size"
        background
        layout="prev, pager, next"
        :current-page="pageData.page.page"
        :page-size="pageData.page.size"
        :total="pageData.page.total"
        @current-change="changePage"
      />
    </PageState>

    <ProjectCreateDialog v-model="createVisible" @created="onCreated" />
  </div>
</template>

<style scoped>
.result-count {
  margin-left: auto;
  font-size: 12px;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

@media (max-width: 1000px) {
  .project-grid {
    grid-template-columns: 1fr;
  }
}
</style>
