<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as rescueApi from '@/api/rescue'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageState from '@/components/PageState.vue'
import StatusTag from '@/components/StatusTag.vue'
import PhotoGallery from '@/components/PhotoGallery.vue'
import { dateTime } from '@/utils/format'
import {
  REPORT_STATUS_OPTIONS,
  REPORT_TYPE_OPTIONS,
  reportStatusLabel,
  reportTypeLabel,
} from '@/utils/domain'
import type { PageData, ReportDetail, ReportSummary } from '@/api/types'

/**
 * P06 上报处理队列（管理员）。
 * 列表与详情只读；受理生成工单，关闭必须写理由。
 * 受理/关闭仅 ANIMAL_ADMIN 可执行，SYSTEM_ADMIN 只能看列表与详情。
 */

const auth = useAuthStore()

const query = reactive({
  status: 'PENDING',
  type: '',
  overdue: '',
  page: 1,
  size: 10,
})

const pageData = ref<PageData<ReportSummary>>({
  list: [],
  page: { page: 1, size: 10, total: 0, pages: 0 },
})
const loading = ref(false)
const errorText = ref('')

const drawerVisible = ref(false)
const detail = ref<ReportDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

const acceptVisible = ref(false)
const acceptBusy = ref(false)
const acceptError = ref('')
const acceptedOrderNo = ref('')
const acceptForm = reactive({ animalCode: '', note: '' })

const closeVisible = ref(false)
const closeBusy = ref(false)
const closeError = ref('')
const closeReason = ref('')

const animalAdmin = auth.hasRole('ANIMAL_ADMIN')

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    pageData.value = await rescueApi.fetchReports({
      status: query.status || undefined,
      type: query.type || undefined,
      overdue: query.overdue === '' ? undefined : query.overdue === 'true',
      page: query.page,
      size: query.size,
    })
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '上报队列加载失败'
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.page = 1
  load()
}

function changePage(page: number): void {
  query.page = page
  load()
}

async function openDetail(row: ReportSummary): Promise<void> {
  drawerVisible.value = true
  detail.value = null
  detailError.value = ''
  acceptedOrderNo.value = ''
  detailLoading.value = true
  try {
    detail.value = await rescueApi.fetchReport(row.id)
  } catch (error) {
    detailError.value = error instanceof BizError ? error.message : '上报详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

function openAccept(): void {
  acceptForm.animalCode = detail.value?.animalCode ?? ''
  acceptForm.note = ''
  acceptError.value = ''
  acceptedOrderNo.value = ''
  acceptVisible.value = true
}

async function submitAccept(): Promise<void> {
  if (!detail.value || acceptBusy.value) return
  acceptBusy.value = true
  acceptError.value = ''
  try {
    const result = await rescueApi.acceptReport(detail.value.id, {
      handlerId: auth.user?.id,
      animalCode: acceptForm.animalCode.trim() || undefined,
      note: acceptForm.note.trim() || undefined,
    })
    acceptedOrderNo.value = result.orderNo
    if (detail.value) {
      detail.value = {
        ...detail.value,
        status: result.reportStatus,
        orderNo: result.orderNo,
        animalCode: acceptForm.animalCode.trim() || detail.value.animalCode,
        deadline: result.deadline || detail.value.deadline,
      }
    }
    ElMessage.success(`已受理，生成工单 ${result.orderNo}`)
    acceptVisible.value = false
    await load()
  } catch (error) {
    acceptError.value = error instanceof BizError ? error.message : '受理失败，请稍后重试'
  } finally {
    acceptBusy.value = false
  }
}

function openClose(): void {
  closeReason.value = ''
  closeError.value = ''
  closeVisible.value = true
}

async function submitClose(): Promise<void> {
  if (!detail.value || closeBusy.value) return
  if (closeReason.value.trim().length < 5) {
    closeError.value = '关闭理由至少 5 个字'
    return
  }
  closeBusy.value = true
  closeError.value = ''
  try {
    await rescueApi.closeReport(detail.value.id, closeReason.value.trim())
    ElMessage.success('上报已关闭')
    closeVisible.value = false
    await openDetail(detail.value)
    await load()
  } catch (error) {
    closeError.value = error instanceof BizError ? error.message : '关闭失败，请稍后重试'
  } finally {
    closeBusy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <select v-model="query.status" class="select" @change="search">
        <option value="">全部状态</option>
        <option v-for="item in REPORT_STATUS_OPTIONS" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
      <select v-model="query.type" class="select" @change="search">
        <option value="">全部类型</option>
        <option v-for="item in REPORT_TYPE_OPTIONS" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
      <select v-model="query.overdue" class="select" @change="search">
        <option value="">超时不限</option>
        <option value="true">仅超时</option>
        <option value="false">未超时</option>
      </select>
      <button class="btn sm ghost" type="button" :disabled="loading" @click="load">刷新</button>
      <span class="muted result-count">共 {{ pageData.page.total }} 条 · 默认只看待受理</span>
    </div>

    <PageState
      :loading="loading"
      :error="errorText"
      :empty="!pageData.list.length"
      empty-text="当前筛选下没有上报"
    >
      <div class="table-wrap">
        <table class="native">
          <thead>
            <tr>
              <th>上报编号</th>
              <th>类型</th>
              <th>点位</th>
              <th>描述</th>
              <th>照片</th>
              <th>状态</th>
              <th>受理截止</th>
              <th style="width: 74px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in pageData.list" :key="row.id">
              <td>
                <div class="mono">{{ row.reportNo }}</div>
                <div class="muted small">#{{ row.id }}</div>
              </td>
              <td>{{ reportTypeLabel(row.type) }}</td>
              <td>{{ row.pointName }}</td>
              <td class="desc-cell">{{ row.description }}</td>
              <td>{{ row.photos.length }} 张</td>
              <td>
                <div class="status-cell">
                  <StatusTag :code="row.status" :label="reportStatusLabel(row.status)" />
                  <span v-if="row.overdue" class="tag danger">超时</span>
                </div>
              </td>
              <td class="muted small">{{ dateTime(row.deadline) }}</td>
              <td>
                <button class="btn sm link" type="button" @click="openDetail(row)">详情</button>
              </td>
            </tr>
          </tbody>
        </table>
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

    <div class="footnote" style="text-align: left">
      来源：GET /reports?status=…；列表与详情对 ANIMAL_ADMIN / SYSTEM_ADMIN 开放，受理与关闭只允许 ANIMAL_ADMIN。
    </div>

    <el-drawer v-model="drawerVisible" title="上报详情" size="560px" append-to-body>
      <PageState :loading="detailLoading" :error="detailError" :empty="!detail" empty-text="上报不存在">
        <div v-if="detail" class="drawer-body">
          <div class="drawer-head">
            <div>
              <div class="mono">{{ detail.reportNo }}</div>
              <h2>{{ reportTypeLabel(detail.type) }} · {{ detail.pointName }}</h2>
            </div>
            <StatusTag :code="detail.status" :label="reportStatusLabel(detail.status)" />
          </div>

          <div v-if="detail.overdue" class="notice danger">已超过受理时限，请优先处置。</div>
          <div v-if="acceptedOrderNo" class="notice">
            已生成工单 <span class="mono">{{ acceptedOrderNo }}</span>，可在工单看板继续流转。
          </div>

          <div class="block">
            <h3>情况描述</h3>
            <p class="desc-text">{{ detail.description }}</p>
          </div>

          <div class="block">
            <h3>现场照片</h3>
            <PhotoGallery :file-ids="detail.photos" empty-text="无现场照片" />
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
                <div class="k">上报时间</div>
                <div class="v">{{ dateTime(detail.createdAt) }}</div>
              </div>
              <div class="info">
                <div class="k">受理截止</div>
                <div class="v">{{ dateTime(detail.deadline) }}</div>
              </div>
              <div class="info">
                <div class="k">上报人</div>
                <div class="v mono">{{ detail.reporterId }}</div>
              </div>
              <div class="info">
                <div class="k">关联工单</div>
                <div class="v mono">{{ detail.orderNo || '尚未受理' }}</div>
              </div>
            </div>
          </div>

          <div v-if="animalAdmin" class="drawer-actions">
            <button
              v-if="detail.status === 'PENDING'"
              class="btn"
              type="button"
              @click="openAccept"
            >
              受理并生成工单
            </button>
            <button
              v-if="detail.status === 'PROCESSING'"
              class="btn ghost"
              type="button"
              @click="openClose"
            >
              关闭上报
            </button>
          </div>
          <div v-else class="footnote" style="text-align: left">
            SYSTEM_ADMIN 可查看队列，但受理/关闭需要 ANIMAL_ADMIN。
          </div>
        </div>
      </PageState>
    </el-drawer>

    <el-dialog v-model="acceptVisible" title="受理上报" width="520px" append-to-body>
      <div class="field">
        <label>关联档案编号</label>
        <input v-model="acceptForm.animalCode" class="input mono" maxlength="20" placeholder="例如 CAT-2026-031，可不填" />
      </div>
      <div class="field">
        <label>受理备注</label>
        <textarea v-model="acceptForm.note" class="textarea" maxlength="255" placeholder="例如：已联系志愿者，预计 30 分钟到场" />
      </div>
      <div v-if="acceptError" class="field-error">{{ acceptError }}</div>
      <div class="notice">受理后上报转 PROCESSING，并生成 PENDING 工单；工单另行流转。</div>
      <template #footer>
        <button class="btn ghost" type="button" @click="acceptVisible = false">取消</button>
        <button class="btn" type="button" :disabled="acceptBusy" @click="submitAccept">
          {{ acceptBusy ? '受理中…' : '确认受理' }}
        </button>
      </template>
    </el-dialog>

    <el-dialog v-model="closeVisible" title="关闭上报" width="520px" append-to-body>
      <div class="field">
        <label>关闭理由 <b>*</b></label>
        <textarea
          v-model="closeReason"
          class="textarea"
          maxlength="255"
          placeholder="例如：现场复核未发现受伤动物，已与上报人确认"
          :class="{ invalid: closeError }"
        />
        <div v-if="closeError" class="field-error">{{ closeError }}</div>
      </div>
      <div class="notice">关闭仅适用于已受理上报；若已有工单，应先在工单看板结案。</div>
      <template #footer>
        <button class="btn ghost" type="button" @click="closeVisible = false">取消</button>
        <button class="btn" type="button" :disabled="closeBusy" @click="submitClose">
          {{ closeBusy ? '关闭中…' : '确认关闭' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.result-count {
  margin-left: auto;
  font-size: 11px;
}

.desc-cell {
  max-width: 230px;
  white-space: normal;
  font-size: 12px;
  line-height: 1.5;
}

.small {
  font-size: 11px;
}

.status-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  align-items: flex-start;
}

.drawer-body {
  display: grid;
  gap: 16px;
}

.drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.drawer-head h2 {
  margin-top: 4px;
  font-size: 19px;
}

.block h3 {
  margin-bottom: 6px;
}

.desc-text {
  color: var(--ink);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.info-grid.single {
  grid-template-columns: 1fr 1fr;
  margin-top: 0;
}

.drawer-actions {
  display: flex;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
}

.notice.danger {
  background: var(--danger-soft);
  color: var(--danger);
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}
</style>
