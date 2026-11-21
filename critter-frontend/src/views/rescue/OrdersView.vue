<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as rescueApi from '@/api/rescue'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PageState from '@/components/PageState.vue'
import StatusTag from '@/components/StatusTag.vue'
import PhotoGallery from '@/components/PhotoGallery.vue'
import { dateTime } from '@/utils/format'
import {
  ORDER_STATUS_OPTIONS,
  REPORT_TYPE_OPTIONS,
  orderStatusLabel,
  reportTypeLabel,
} from '@/utils/domain'
import type {
  OrderDetail,
  OrderStatusCode,
  OrderSummary,
  PageData,
} from '@/api/types'

/**
 * P06 工单看板（管理员）。
 * 状态流转由后端状态机裁决，前端只渲染 allowed，不自行推导“下一步”。
 */

const router = useRouter()
const auth = useAuthStore()

const query = reactive({
  status: '',
  type: '',
  overdue: '',
  page: 1,
  size: 10,
})

const pageData = ref<PageData<OrderSummary>>({
  list: [],
  page: { page: 1, size: 10, total: 0, pages: 0 },
})
const loading = ref(false)
const errorText = ref('')

const drawerVisible = ref(false)
const detail = ref<OrderDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

const transitionVisible = ref(false)
const target = ref<OrderStatusCode | null>(null)
const note = ref('')
const reason = ref('')
const transitionBusy = ref(false)
const transitionError = ref('')

const animalAdmin = auth.hasRole('ANIMAL_ADMIN')

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    pageData.value = await rescueApi.fetchOrders({
      status: query.status || undefined,
      type: query.type || undefined,
      overdue: query.overdue === '' ? undefined : query.overdue === 'true',
      page: query.page,
      size: query.size,
    })
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '工单列表加载失败'
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

async function openDetail(row: OrderSummary): Promise<void> {
  drawerVisible.value = true
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    detail.value = await rescueApi.fetchOrder(row.orderNo)
  } catch (error) {
    detailError.value = error instanceof BizError ? error.message : '工单详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

function requiredHint(code: OrderStatusCode): { noteRequired: boolean; reasonRequired: boolean; hint: string } {
  if (code === 'TO_REVISIT') {
    return { noteRequired: true, reasonRequired: false, hint: '处置完成必须填写处置说明' }
  }
  if (code === 'CLOSED' && detail.value?.status === 'PROCESSING') {
    return { noteRequired: false, reasonRequired: true, hint: '处置中直接结案必须填写无法处置原因' }
  }
  if (code === 'PROCESSING' && detail.value?.status === 'TO_REVISIT') {
    return { noteRequired: true, reasonRequired: false, hint: '回访发现异常，必须填写说明' }
  }
  return { noteRequired: false, reasonRequired: false, hint: '可选填备注' }
}

function openTransition(code: OrderStatusCode): void {
  target.value = code
  note.value = ''
  reason.value = ''
  transitionError.value = ''
  transitionVisible.value = true
}

async function submitTransition(): Promise<void> {
  if (!detail.value || !target.value || transitionBusy.value) return
  const rule = requiredHint(target.value)
  if (rule.noteRequired && !note.value.trim()) {
    transitionError.value = '本次流转必须填写说明'
    return
  }
  if (rule.reasonRequired && reason.value.trim().length < 5) {
    transitionError.value = '结案原因至少 5 个字'
    return
  }
  transitionBusy.value = true
  transitionError.value = ''
  try {
    await rescueApi.transitionOrder(detail.value.orderNo, {
      targetStatus: target.value,
      note: note.value.trim() || undefined,
      reason: reason.value.trim() || undefined,
      handlerId: auth.user?.id,
    })
    ElMessage.success(`工单已流转到 ${orderStatusLabel(target.value)}`)
    transitionVisible.value = false
    await openDetail(detail.value)
    await load()
  } catch (error) {
    transitionError.value = error instanceof BizError ? error.message : '流转失败，请稍后重试'
  } finally {
    transitionBusy.value = false
  }
}

onMounted(() => {
  if (auth.isAdmin) load()
})
</script>

<template>
  <div>
    <PageHero
      title="工单看板"
      desc="工单状态由状态机驱动：待受理 → 处置中 → 待回访 / 已结案；超时只作为风险标记，不篡改业务状态。"
    >
      <template #actions>
        <button class="btn ghost" type="button" @click="router.push('/report')">上报处理</button>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '我要上报', path: '/report' },
        { label: '我的上报', path: '/report/mine' },
        { label: '工单看板', path: '/orders', adminOnly: true },
      ]"
    />

    <div v-if="!auth.isAdmin" class="notice">工单看板仅 ANIMAL_ADMIN / SYSTEM_ADMIN 可见。</div>

    <template v-else>
      <div class="toolbar">
        <select v-model="query.status" class="select" @change="search">
          <option value="">全部状态</option>
          <option v-for="item in ORDER_STATUS_OPTIONS" :key="item.value" :value="item.value">
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
        <span class="muted result-count">共 {{ pageData.page.total }} 张工单</span>
      </div>

      <PageState
        :loading="loading"
        :error="errorText"
        :empty="!pageData.list.length"
        empty-text="当前筛选下没有工单"
      >
        <div class="table-wrap">
          <table class="native">
            <thead>
              <tr>
                <th>工单号</th>
                <th>上报</th>
                <th>类型</th>
                <th>点位</th>
                <th>状态</th>
                <th>受理人</th>
                <th>受理时间</th>
                <th>截止时间</th>
                <th style="width: 74px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in pageData.list" :key="row.orderNo">
                <td>
                  <div class="mono strong">{{ row.orderNo }}</div>
                  <div v-if="row.overdue" class="tag danger tiny">超时</div>
                </td>
                <td class="mono small">{{ row.reportNo }}</td>
                <td>{{ reportTypeLabel(row.type) }}</td>
                <td>{{ row.pointName }}</td>
                <td><StatusTag :code="row.status" :label="orderStatusLabel(row.status)" /></td>
                <td class="mono small">{{ row.handlerId ?? '—' }}</td>
                <td class="muted small">{{ row.acceptedAt ? dateTime(row.acceptedAt) : '—' }}</td>
                <td class="muted small">{{ dateTime(row.deadlineAt) }}</td>
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
        来源：GET /orders、GET /orders/{no}；状态迁移 PUT /orders/{no}/status 只允许 ANIMAL_ADMIN。
      </div>
    </template>

    <el-drawer v-model="drawerVisible" title="工单详情" size="580px" append-to-body>
      <PageState :loading="detailLoading" :error="detailError" :empty="!detail" empty-text="工单不存在">
        <div v-if="detail" class="drawer-body">
          <div class="drawer-head">
            <div>
              <div class="mono">{{ detail.orderNo }}</div>
              <h2>{{ reportTypeLabel(detail.type) }} · {{ detail.pointName }}</h2>
            </div>
            <div class="head-tags">
              <StatusTag :code="detail.status" :label="orderStatusLabel(detail.status)" />
              <span v-if="detail.overdue" class="tag danger">超时</span>
            </div>
          </div>

          <div class="block">
            <h3>工单信息</h3>
            <div class="info-grid single">
              <div class="info">
                <div class="k">来源上报</div>
                <div class="v mono">{{ detail.reportNo }}</div>
              </div>
              <div class="info">
                <div class="k">关联档案</div>
                <div class="v mono">{{ detail.animalCode || '—' }}</div>
              </div>
              <div class="info">
                <div class="k">受理人</div>
                <div class="v mono">{{ detail.handlerId ?? '—' }}</div>
              </div>
              <div class="info">
                <div class="k">受理时间</div>
                <div class="v">{{ detail.acceptedAt ? dateTime(detail.acceptedAt) : '—' }}</div>
              </div>
              <div class="info">
                <div class="k">截止时间</div>
                <div class="v">{{ dateTime(detail.deadlineAt) }}</div>
              </div>
              <div class="info">
                <div class="k">创建时间</div>
                <div class="v">{{ dateTime(detail.createdAt) }}</div>
              </div>
            </div>
          </div>

          <div class="block">
            <h3>情况描述</h3>
            <p class="desc-text">{{ detail.description }}</p>
          </div>

          <div class="block">
            <h3>现场照片</h3>
            <PhotoGallery :file-ids="detail.photos" empty-text="无现场照片" />
          </div>

          <div v-if="detail.disposeNote || detail.closeReason" class="block">
            <h3>处置记录</h3>
            <p v-if="detail.disposeNote" class="desc-text">处置说明：{{ detail.disposeNote }}</p>
            <p v-if="detail.closeReason" class="desc-text">结案原因：{{ detail.closeReason }}</p>
          </div>

          <div class="block">
            <h3>流转时间线</h3>
            <div v-if="!detail.timeline.length" class="muted">暂无流转记录</div>
            <ol v-else class="timeline">
              <li v-for="(item, index) in detail.timeline" :key="`${item.at}-${index}`">
                <span class="timeline-dot" :class="{ danger: item.result === 'FAIL' }" />
                <div class="timeline-body">
                  <div class="timeline-head">
                    <b>{{ item.actionLabel }}</b>
                    <span class="muted">{{ dateTime(item.at) }}</span>
                  </div>
                  <div class="muted timeline-meta">
                    {{ item.actorRole || '—' }} · #{{ item.actorId ?? '—' }}
                    <span v-if="item.fromStatus"> · {{ orderStatusLabel(item.fromStatus) }} → {{ orderStatusLabel(item.toStatus) }}</span>
                  </div>
                  <div v-if="item.note" class="timeline-note">{{ item.note }}</div>
                </div>
              </li>
            </ol>
          </div>

          <div v-if="animalAdmin && detail.allowed.length" class="drawer-actions">
            <span class="muted action-label">可流转到</span>
            <button
              v-for="code in detail.allowed"
              :key="code"
              class="btn sm"
              type="button"
              @click="openTransition(code)"
            >
              {{ orderStatusLabel(code) }}
            </button>
          </div>
          <div v-else-if="!animalAdmin" class="footnote" style="text-align: left">
            SYSTEM_ADMIN 可查看工单，但状态迁移只允许 ANIMAL_ADMIN。
          </div>
        </div>
      </PageState>
    </el-drawer>

    <el-dialog v-model="transitionVisible" title="流转工单" width="520px" append-to-body>
      <div v-if="target" class="transition-head">
        <span>目标状态</span>
        <StatusTag :code="target" :label="orderStatusLabel(target)" />
      </div>

      <div class="field">
        <label>处置说明 {{ requiredHint(target ?? 'PROCESSING').noteRequired ? '（必填）' : '（可选）' }}</label>
        <textarea v-model="note" class="textarea" maxlength="255" placeholder="说明处置动作、结果或回访情况" />
      </div>

      <div class="field">
        <label>结案原因 {{ requiredHint(target ?? 'PROCESSING').reasonRequired ? '（必填，至少 5 字）' : '（可选）' }}</label>
        <textarea v-model="reason" class="textarea" maxlength="255" placeholder="无法处置、误报等情况在此说明" />
      </div>

      <div class="footnote" style="text-align: left">{{ requiredHint(target ?? 'PROCESSING').hint }}</div>
      <div v-if="transitionError" class="field-error">{{ transitionError }}</div>

      <template #footer>
        <button class="btn ghost" type="button" @click="transitionVisible = false">取消</button>
        <button class="btn" type="button" :disabled="transitionBusy" @click="submitTransition">
          {{ transitionBusy ? '流转中…' : '确认流转' }}
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

.small {
  font-size: 11px;
}

.tiny {
  font-size: 10px;
  margin-top: 3px;
}

.strong {
  color: var(--ink);
  font-weight: 600;
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

.head-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
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

.timeline {
  list-style: none;
  margin: 0;
  padding: 0 0 0 4px;
  display: grid;
  gap: 12px;
}

.timeline li {
  display: flex;
  gap: 10px;
}

.timeline-dot {
  flex: 0 0 9px;
  width: 9px;
  height: 9px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--accent-2);
  box-shadow: 0 0 0 2px var(--accent-soft);
}

.timeline-dot.danger {
  background: var(--danger);
  box-shadow: 0 0 0 2px var(--danger-soft);
}

.timeline-body {
  min-width: 0;
  flex: 1;
}

.timeline-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
}

.timeline-meta {
  font-size: 11px;
  margin-top: 2px;
}

.timeline-note {
  margin-top: 4px;
  padding: 6px 8px;
  background: var(--chip);
  border-radius: 6px;
  font-size: 11px;
  color: var(--ink);
}

.drawer-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}

.action-label {
  font-size: 11px;
}

.transition-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--muted);
  font-size: 12px;
}
</style>
