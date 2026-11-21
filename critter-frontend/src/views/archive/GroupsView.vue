<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PageState from '@/components/PageState.vue'
import StatusTag from '@/components/StatusTag.vue'
import GroupCreateDialog from '@/views/archive/GroupCreateDialog.vue'
import { dateOnly, dateTime } from '@/utils/format'
import { GROUP_STATUS_OPTIONS, groupStatusLabel } from '@/utils/domain'
import type { GroupDetail, GroupSummary, PageData, PointView, SpeciesView } from '@/api/types'

/** P02 群体档案：规模以区间展示，详情用抽屉读取 GET /groups/{code}。 */

const auth = useAuthStore()

const query = reactive({
  keyword: '',
  speciesId: null as number | null,
  status: '',
  pointId: null as number | null,
  page: 1,
  size: 15,
})

const pageData = ref<PageData<GroupSummary>>({
  list: [],
  page: { page: 1, size: 15, total: 0, pages: 0 },
})
const species = ref<SpeciesView[]>([])
const points = ref<PointView[]>([])
const loading = ref(false)
const errorText = ref('')
const createVisible = ref(false)

const drawerVisible = ref(false)
const detail = ref<GroupDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

async function loadOptions(): Promise<void> {
  try {
    const [speciesData, pointData] = await Promise.all([archiveApi.fetchSpecies(), archiveApi.fetchPoints()])
    species.value = speciesData
    points.value = pointData
  } catch {
    species.value = []
    points.value = []
  }
}

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    pageData.value = await archiveApi.fetchGroups({
      keyword: query.keyword.trim() || undefined,
      speciesId: query.speciesId ?? undefined,
      status: query.status || undefined,
      pointId: query.pointId ?? undefined,
      page: query.page,
      size: query.size,
    })
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '群体列表加载失败'
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
  query.speciesId = null
  query.status = ''
  query.pointId = null
  query.page = 1
  load()
}

async function openDetail(group: GroupSummary): Promise<void> {
  drawerVisible.value = true
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    detail.value = await archiveApi.fetchGroup(group.groupCode)
  } catch (error) {
    detailError.value = error instanceof BizError ? error.message : '群体详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

function onCreated(groupCode: string): void {
  ElMessage.success(`已创建群体 ${groupCode}`)
  search()
}

function changePage(page: number): void {
  query.page = page
  load()
}

onMounted(() => {
  loadOptions()
  load()
})
</script>

<template>
  <div>
    <PageHero
      title="群体档案"
      desc="群体与个体物理分离；规模用闭区间与计数依据表达，不把估算值写成精确只数。"
    >
      <template #actions>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <button v-if="auth.isAdmin" class="btn" type="button" @click="createVisible = true">新建群体</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '个体档案', path: '/animals', matchPrefix: true },
        { label: '群体档案', path: '/groups' },
        { label: '物种名录', path: '/species' },
      ]"
    />

    <div class="toolbar">
      <input
        v-model="query.keyword"
        class="input grow-input"
        placeholder="群体编号 / 识别特征"
        @keyup.enter="search"
      />
      <select v-model.number="query.speciesId" class="select">
        <option :value="null">全部物种</option>
        <option v-for="item in species" :key="item.id" :value="item.id">{{ item.commonName }}</option>
      </select>
      <select v-model="query.status" class="select">
        <option value="">全部状态</option>
        <option v-for="item in GROUP_STATUS_OPTIONS" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
      <select v-model.number="query.pointId" class="select">
        <option :value="null">全部点位</option>
        <option v-for="point in points" :key="point.id" :value="point.id">{{ point.name }}</option>
      </select>
      <button class="btn sm" type="button" @click="search">筛选</button>
      <button class="btn sm ghost" type="button" @click="reset">重置</button>
      <span class="muted result-count">共 {{ pageData.page.total }} 个群体</span>
    </div>

    <PageState
      :loading="loading"
      :error="errorText"
      :empty="!pageData.list.length"
      empty-text="当前筛选条件下没有群体档案"
    >
      <div class="table-wrap">
        <table class="native">
          <thead>
            <tr>
              <th>群体编号</th>
              <th>物种</th>
              <th>识别特征</th>
              <th>规模区间</th>
              <th>状态</th>
              <th>活动点位</th>
              <th>最近目击</th>
              <th style="width: 76px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="group in pageData.list" :key="group.groupCode">
              <td class="mono strong">{{ group.groupCode }}</td>
              <td>
                <div>{{ group.species.commonName }}</div>
                <div class="muted small">{{ group.species.category }}</div>
              </td>
              <td class="features-cell">{{ group.features }}</td>
              <td>
                <b>{{ group.minSize }}—{{ group.maxSize }}</b>
                <div class="muted small">{{ group.sizeBasis }}</div>
              </td>
              <td>
                <StatusTag :code="group.status" :label="groupStatusLabel(group.status, group.statusLabel)" />
              </td>
              <td>
                <div class="tag-row">
                  <span v-for="point in group.points" :key="point.id" class="tag gray">{{ point.name }}</span>
                </div>
              </td>
              <td class="muted small">{{ group.lastSeenAt ? dateOnly(group.lastSeenAt) : '未记录' }}</td>
              <td>
                <button class="btn sm link" type="button" @click="openDetail(group)">详情</button>
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

    <GroupCreateDialog
      v-model="createVisible"
      :species="species"
      :points="points"
      @created="onCreated"
    />

    <el-drawer v-model="drawerVisible" title="群体详情" size="520px" append-to-body>
      <PageState :loading="detailLoading" :error="detailError" :empty="!detail" empty-text="群体不存在">
        <div v-if="detail" class="drawer-body">
          <div class="drawer-title">
            <span class="mono">{{ detail.groupCode }}</span>
            <StatusTag :code="detail.status" :label="groupStatusLabel(detail.status, detail.statusLabel)" />
          </div>
          <div class="info-grid single">
            <div class="info">
              <div class="k">物种</div>
              <div class="v">{{ detail.species.commonName }}</div>
            </div>
            <div class="info">
              <div class="k">类群</div>
              <div class="v">{{ detail.species.category }}</div>
            </div>
            <div class="info">
              <div class="k">规模区间</div>
              <div class="v">{{ detail.minSize }}—{{ detail.maxSize }}</div>
            </div>
            <div class="info">
              <div class="k">计数依据</div>
              <div class="v">{{ detail.sizeBasis }}</div>
            </div>
          </div>

          <div class="block">
            <h3>识别特征</h3>
            <p>{{ detail.features }}</p>
          </div>

          <div class="block">
            <h3>活动点位</h3>
            <div class="tag-row">
              <span v-for="point in detail.points" :key="point.id" class="tag">{{ point.name }}</span>
              <span v-if="!detail.points.length" class="muted">未登记点位</span>
            </div>
          </div>

          <div class="block">
            <h3>时间</h3>
            <div class="muted line">最近目击：{{ detail.lastSeenAt ? dateTime(detail.lastSeenAt) : '未记录' }}</div>
            <div class="muted line">建档：{{ dateTime(detail.createdAt) }}</div>
            <div class="muted line">更新：{{ dateTime(detail.updatedAt) }}</div>
          </div>

          <div class="notice">群体不参与认养，也不能直接建个体档案；需要个体化时走群体拆分流程。</div>
        </div>
      </PageState>
    </el-drawer>
  </div>
</template>

<style scoped>
.result-count {
  margin-left: auto;
  font-size: 12px;
}

.features-cell {
  max-width: 240px;
  white-space: normal;
  line-height: 1.5;
}

.strong {
  font-weight: 600;
  color: var(--ink);
}

.small {
  font-size: 11px;
}

.tag-row {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.drawer-body {
  display: grid;
  gap: 16px;
}

.drawer-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  font-weight: 600;
}

.info-grid.single {
  grid-template-columns: 1fr 1fr;
  margin-top: 0;
}

.block h3 {
  margin-bottom: 6px;
}

.block p {
  color: var(--ink);
  font-size: 13px;
}

.line {
  font-size: 12px;
  line-height: 1.8;
}
</style>
