<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as rescueApi from '@/api/rescue'
import { BizError } from '@/api/http'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import StatusTag from '@/components/StatusTag.vue'
import PhotoGallery from '@/components/PhotoGallery.vue'
import { dateTime } from '@/utils/format'
import { reportStatusLabel, reportTypeLabel } from '@/utils/domain'
import type { ReportDetail, ReportSummary } from '@/api/types'

/**
 * 我的上报：走服务端 GET /reports/mine，不再依赖 localStorage 回执。
 * 详情按 id 读取，联系方式只对上报人本人返回。
 */
const router = useRouter()

const rows = ref<ReportSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const errorText = ref('')
const drawerVisible = ref(false)
const detail = ref<ReportDetail | null>(null)
const detailError = ref('')
const confirmBusy = ref(false)

function needsConfirm(row: ReportSummary): boolean {
  return row.status === 'CLOSED' && !row.invalid && !row.confirmedAt && Boolean(row.confirmDueAt)
}

async function confirmClose(): Promise<void> {
  if (!detail.value || confirmBusy.value) return
  confirmBusy.value = true
  try {
    const result = await rescueApi.confirmReportClose(detail.value.id)
    detail.value.confirmedAt = result.confirmedAt
    detail.value.confirmDueAt = null
    detail.value.autoConfirmed = result.autoConfirmed
    ElMessage.success('已确认结案')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '确认结案失败')
  } finally {
    confirmBusy.value = false
  }
}

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const result = await rescueApi.fetchMyReports({ page: page.value, size })
    rows.value = result.list
    total.value = result.page.total
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '我的上报加载失败'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function openDetail(row: ReportSummary): Promise<void> {
  drawerVisible.value = true
  detail.value = null
  detailError.value = ''
  try {
    detail.value = await rescueApi.fetchReport(row.id)
  } catch (error) {
    detailError.value = error instanceof BizError ? error.message : '上报详情加载失败'
  }
}

function changePage(next: number): void {
  const max = Math.max(1, Math.ceil(total.value / size))
  page.value = Math.min(Math.max(1, next), max)
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero title="我的上报">
      <template #actions>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <button class="btn" type="button" @click="router.push('/report')">新增上报</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '我要上报', path: '/report' },
        { label: '我的上报', path: '/report/mine' },
        { label: '工单看板', path: '/orders', adminOnly: true },
      ]"
    />

    <div v-if="errorText" class="notice">{{ errorText }}</div>
    <div v-else-if="loading" class="notice">上报列表加载中…</div>
    <div v-else-if="!rows.length" class="notice">你还没有提交过上报。</div>

    <template v-else>
      <div class="table-wrap">
        <table class="native">
          <thead>
            <tr>
              <th>上报编号</th>
              <th>类型</th>
              <th>点位</th>
              <th>描述</th>
              <th>状态</th>
              <th>受理截止</th>
              <th>提交时间</th>
              <th style="width: 74px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>
                <div class="mono">{{ row.reportNo }}</div>
                <div v-if="row.overdue" class="tag warn">已超受理时限</div>
                <div v-if="needsConfirm(row)" class="tag warn">待确认结案</div>
              </td>
              <td>{{ reportTypeLabel(row.type) }}</td>
              <td>{{ row.pointName || '—' }}</td>
              <td class="desc-cell">{{ row.description }}</td>
              <td><StatusTag :code="row.status" :label="reportStatusLabel(row.status)" /></td>
              <td class="muted small">{{ dateTime(row.deadline) }}</td>
              <td class="muted small">{{ dateTime(row.createdAt) }}</td>
              <td>
                <button class="btn sm link" type="button" @click="openDetail(row)">详情</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <button class="btn sm ghost" type="button" :disabled="page <= 1 || loading" @click="changePage(page - 1)">上一页</button>
        <span class="muted small">第 {{ page }} / {{ Math.max(1, Math.ceil(total / size)) }} 页 · 共 {{ total }} 条</span>
        <button
          class="btn sm ghost"
          type="button"
          :disabled="page >= Math.ceil(total / size) || loading"
          @click="changePage(page + 1)"
        >
          下一页
        </button>
      </div>
    </template>



    <el-drawer v-model="drawerVisible" title="上报详情" size="540px" append-to-body>
      <div v-if="detailError" class="notice">{{ detailError }}</div>
      <div v-else-if="!detail" class="muted">详情加载中…</div>
      <div v-else class="detail-body">
        <div class="detail-head">
          <div>
            <div class="mono">{{ detail.reportNo }}</div>
            <h2>{{ reportTypeLabel(detail.type) }} · {{ detail.pointName }}</h2>
          </div>
          <StatusTag :code="detail.status" :label="reportStatusLabel(detail.status)" />
        </div>

        <div v-if="detail.overdue" class="notice">已超过受理时限</div>

        <div v-if="detail.invalid" class="notice">
          已判定无效：{{ detail.invalidReason || '未填写原因' }}
        </div>
        <div v-else-if="needsConfirm(detail)" class="notice confirm-box">
          <div>
            <b>结案待确认</b><br />
            已于 {{ dateTime(detail.confirmedAt) }} 结案，待确认至     {{ dateTime(detail.confirmDueAt) }}，逾期自动确认。
          </div>
          <button class="btn sm" type="button" :disabled="confirmBusy" @click="confirmClose">
            {{ confirmBusy ? '提交中…' : '确认结案' }}
          </button>
        </div>
        <div v-else-if="detail.confirmedAt" class="notice">
          结案已确认于 {{ dateTime(detail.confirmedAt) }}{{ detail.autoConfirmed ? '（系统自动确认）' : '' }}。
        </div>

        <div class="block">
          <h3>情况描述</h3>
          <p>{{ detail.description }}</p>
        </div>

        <div class="block">
          <h3>现场照片</h3>
          <PhotoGallery :file-ids="detail.photos" />
        </div>

        <div class="block">
          <div class="info-grid single">
            <div class="info">
              <div class="k">关联档案</div>
              <div class="v mono">{{ detail.animalCode || '—' }}</div>
            </div>
            <div class="info">
              <div class="k">联系方式</div>
              <div class="v">{{ detail.contact || '—' }}</div>
            </div>
            <div class="info">
              <div class="k">提交时间</div>
              <div class="v">{{ dateTime(detail.createdAt) }}</div>
            </div>
            <div class="info">
              <div class="k">受理截止</div>
              <div class="v">{{ dateTime(detail.deadline) }}</div>
            </div>
            <div class="info">
              <div class="k">工单号</div>
              <div class="v mono">{{ detail.orderNo || '尚未受理' }}</div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.desc-cell {
  max-width: 240px;
  white-space: normal;
  font-size: 12px;
  line-height: 1.5;
}

.small {
  font-size: 11px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 12px;
}

.detail-body {
  display: grid;
  gap: 16px;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.detail-head h2 {
  margin-top: 4px;
  font-size: 19px;
}

.block h3 {
  margin-bottom: 6px;
}

.block p {
  color: var(--ink);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.info-grid.single {
  grid-template-columns: 1fr 1fr;
  margin-top: 0;
}

.confirm-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  line-height: 1.8;
}
</style>
