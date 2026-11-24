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
import SightingDialog from '@/views/archive/SightingDialog.vue'
import GroupMergeDialog from '@/views/archive/GroupMergeDialog.vue'
import GroupSplitDialog from '@/views/archive/GroupSplitDialog.vue'
import UnassignedSightingDialog from '@/views/archive/UnassignedSightingDialog.vue'
import SightingAssignDialog from '@/views/archive/SightingAssignDialog.vue'
import PanelCard from '@/components/PanelCard.vue'
import { dateOnly, dateTime } from '@/utils/format'
import { GROUP_STATUS_OPTIONS, groupStatusLabel } from '@/utils/domain'
import { fileContentUrl } from '@/api/file'
import type { GroupDetail, GroupSummary, PageData, PointView, SightingView, SpeciesView } from '@/api/types'

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
const editVisible = ref(false)
const sightingVisible = ref(false)
const mergeVisible = ref(false)
const splitVisible = ref(false)
const unassignedVisible = ref(false)
const assignVisible = ref(false)
const assigningSighting = ref<SightingView | null>(null)

const drawerVisible = ref(false)
const detail = ref<GroupDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const sightings = ref<SightingView[]>([])
const sightingsLoading = ref(false)
const pendingSightings = ref<SightingView[]>([])
const pendingLoading = ref(false)
const pendingError = ref('')
const groupsForAssign = ref<GroupSummary[]>([])

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

async function loadAssignGroups(): Promise<void> {
  try {
    const page = await archiveApi.fetchGroups({ page: 1, size: 50, status: 'ACTIVE' })
    groupsForAssign.value = page.list
  } catch {
    groupsForAssign.value = []
  }
}

async function loadPending(): Promise<void> {
  if (!auth.isAdmin) {
    pendingSightings.value = []
    return
  }
  pendingLoading.value = true
  pendingError.value = ''
  try {
    const page = await archiveApi.fetchSightings({ assignStatus: 'UNASSIGNED', page: 1, size: 20 })
    pendingSightings.value = page.list
  } catch (error) {
    pendingError.value = error instanceof BizError ? error.message : '待归属目击加载失败'
  } finally {
    pendingLoading.value = false
  }
}

async function openDetail(group: GroupSummary): Promise<void> {
  await openDetailByCode(group.groupCode)
}

async function openDetailByCode(code: string): Promise<void> {
  drawerVisible.value = true
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  sightings.value = []
  try {
    const row = await archiveApi.fetchGroup(code)
    detail.value = row
    await loadSightings(row.mergedIntoCode ?? row.groupCode)
  } catch (error) {
    detailError.value = error instanceof BizError ? error.message : '群体详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

async function loadSightings(code: string): Promise<void> {
  sightingsLoading.value = true
  try {
    const result = await archiveApi.fetchSightings({ groupCode: code, page: 1, size: 20 })
    sightings.value = result.list
  } catch {
    sightings.value = []
  } finally {
    sightingsLoading.value = false
  }
}

async function refreshDetail(): Promise<void> {
  const code = detail.value?.mergedIntoCode ?? detail.value?.groupCode
  if (!code) return
  try {
    const row = await archiveApi.fetchGroup(code)
    detail.value = row
    await loadSightings(row.mergedIntoCode ?? row.groupCode)
  } catch (error) {
    detailError.value = error instanceof BizError ? error.message : '群体详情加载失败'
  }
}

function onSightingCreated(): void {
  refreshDetail()
}

function onMaintenanceSaved(code: string): void {
  ElMessage.success(`群体维护完成：${code}`)
  load()
  loadAssignGroups()
  loadPending()
  openDetailByCode(code)
}

function openMergedTarget(): void {
  if (detail.value?.mergedIntoCode) {
    openDetailByCode(detail.value.mergedIntoCode)
  }
}

function openAssign(sighting: SightingView): void {
  assigningSighting.value = sighting
  assignVisible.value = true
}

function onSightingAssigned(): void {
  loadPending()
  loadAssignGroups()
}

function onCreated(groupCode: string): void {
  ElMessage.success(`已保存群体 ${groupCode}`)
  search()
  if (detail.value?.groupCode === groupCode) refreshDetail()
}

function changePage(page: number): void {
  query.page = page
  load()
}

onMounted(() => {
  loadOptions()
  load()
  loadPending()
  loadAssignGroups()
})
</script>

<template>
  <div>
    <PageHero
      title="群体档案"
    >
      <template #actions>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <button v-if="auth.isAdmin" class="btn ghost" type="button" @click="unassignedVisible = true">登记待归属目击</button>
        <button v-if="auth.isAdmin" class="btn" type="button" @click="createVisible = true">新建群体</button>
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

    <PanelCard
      v-if="auth.isAdmin"
      title="待归属目击"
      style="margin-top: 16px"
    >
      <div v-if="pendingLoading" class="muted line">正在读取待归属目击…</div>
      <div v-else-if="pendingError" class="notice">{{ pendingError }}</div>
      <div v-else-if="!pendingSightings.length" class="muted line">暂无待归属目击</div>
      <div v-else class="table-wrap">
        <table class="native">
          <thead>
            <tr>
              <th>ID</th>
              <th>点位</th>
              <th>目击时间</th>
              <th>数量 / 行为</th>
              <th>照片</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in pendingSightings" :key="item.id">
              <td class="mono">#{{ item.id }}</td>
              <td>{{ item.point?.name ?? '—' }}</td>
              <td>{{ dateTime(item.seenAt) }}</td>
              <td>
                <div>{{ item.countEstimate ? `约 ${item.countEstimate} 只` : '未记录' }}</div>
                <div v-if="item.behavior" class="muted small">{{ item.behavior }}</div>
              </td>
              <td>{{ item.fileIds.length }} 张</td>
              <td>
                <button class="btn sm" type="button" @click="openAssign(item)">指派群体</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

    </PanelCard>

    <GroupCreateDialog
      v-model="createVisible"
      :species="species"
      :points="points"
      @created="onCreated"
    />
    <GroupCreateDialog
      v-model="editVisible"
      :species="species"
      :points="points"
      :group="detail"
      @created="onCreated"
    />
    <SightingDialog
      v-model="sightingVisible"
      :group="detail"
      :points="points"
      @created="onSightingCreated"
    />
    <GroupMergeDialog
      v-model="mergeVisible"
      :group="detail"
      @saved="onMaintenanceSaved"
    />
    <GroupSplitDialog
      v-model="splitVisible"
      :group="detail"
      :points="points"
      :sightings="sightings"
      @saved="onMaintenanceSaved"
    />
    <UnassignedSightingDialog
      v-model="unassignedVisible"
      :points="points"
      @created="loadPending"
    />
    <SightingAssignDialog
      v-model="assignVisible"
      :sighting="assigningSighting"
      :groups="groupsForAssign"
      @saved="onSightingAssigned"
    />

    <el-drawer v-model="drawerVisible" title="群体详情" size="520px" append-to-body>
      <PageState :loading="detailLoading" :error="detailError" :empty="!detail">
        <div v-if="detail" class="drawer-body">
          <div class="drawer-title">
            <span class="mono">{{ detail.groupCode }}</span>
            <div class="drawer-actions">
              <StatusTag :code="detail.status" :label="groupStatusLabel(detail.status, detail.statusLabel)" />
              <button
                v-if="auth.isAdmin && detail.status !== 'GONE'"
                class="btn sm ghost"
                type="button"
                @click="editVisible = true"
              >编辑</button>
              <button
                v-if="auth.isAdmin && detail.status !== 'GONE'"
                class="btn sm"
                type="button"
                @click="sightingVisible = true"
              >登记目击</button>
              <button
                v-if="auth.isAdmin && detail.status !== 'GONE'"
                class="btn sm ghost"
                type="button"
                @click="mergeVisible = true"
              >合并</button>
              <button
                v-if="auth.isAdmin && detail.status !== 'GONE'"
                class="btn sm ghost"
                type="button"
                @click="splitVisible = true"
              >拆分</button>
            </div>
          </div>

          <div v-if="detail.mergedIntoCode" class="notice">
            该群体已合并到
            <button class="inline-link mono" type="button" @click="openMergedTarget">
              {{ detail.mergedIntoCode }}
            </button>
            （已合并）
          </div>
          <div v-if="detail.mergedFrom.length" class="lineage-line">
            由以下群体合并而来：
            <span v-for="oldCode in detail.mergedFrom" :key="oldCode" class="tag gray mono">{{ oldCode }}</span>
          </div>
          <div v-if="detail.splitFrom" class="lineage-line">
            由 <span class="mono">{{ detail.splitFrom }}</span> 拆分而来。
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

          <div class="block">
            <h3>目击记录 <span class="muted small">最近 {{ sightings.length }} 条</span></h3>
            <div v-if="sightingsLoading" class="muted line">正在读取目击记录…</div>
            <div v-else-if="!sightings.length" class="muted line">尚未登记目击记录</div>
            <ul v-else class="sighting-list">
              <li v-for="item in sightings" :key="item.id">
                <div class="sighting-head">
                  <b>{{ dateTime(item.seenAt) }}</b>
                  <span class="tag gray">{{ item.point?.name ?? '点位未知' }}</span>
                  <span v-if="item.countEstimate" class="count">约 {{ item.countEstimate }} 只</span>
                </div>
                <div v-if="item.behavior" class="muted small">{{ item.behavior }}</div>
                <div v-if="item.fileIds.length" class="sighting-files">
                  <a
                    v-for="(fileId, index) in item.fileIds"
                    :key="fileId"
                    :href="fileContentUrl(fileId)"
                    target="_blank"
                    rel="noopener"
                  >照片 {{ index + 1 }}</a>
                </div>
              </li>
            </ul>
          </div>

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

.drawer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sighting-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.sighting-list li {
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm, 8px);
  background: var(--panel);
}

.sighting-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
}

.sighting-head b {
  color: var(--ink);
  font-weight: 600;
}

.count {
  color: var(--accent);
  font-size: 12px;
}

.sighting-files {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.sighting-files a {
  color: var(--accent);
  font-size: 11px;
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

.inline-link {
  border: 0;
  background: none;
  padding: 0;
  color: var(--accent);
  cursor: pointer;
  font: inherit;
  border-bottom: 1px solid currentColor;
}

.lineage-line {
  font-size: 12px;
  color: var(--muted);
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
}
</style>
