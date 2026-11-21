<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as fundApi from '@/api/fund'
import { BizError } from '@/api/http'
import PanelCard from '@/components/PanelCard.vue'
import PageState from '@/components/PageState.vue'
import StatusTag from '@/components/StatusTag.vue'
import PhotoGallery from '@/components/PhotoGallery.vue'
import { dateTime, money } from '@/utils/format'
import {
  DONATION_STATUS_OPTIONS,
  centsToYuan,
  donationStatusLabel,
  yuanToCents,
} from '@/utils/domain'
import type { DonationSummary, PageData } from '@/api/types'

/**
 * 管理员捐赠核对面板（T3.1）。
 * 单笔核对允许填写实收金额，金额不一致时后端转 ABNORMAL 并保持项目进度；
 * 批量核对只能按登记金额入账，逐笔独立事务，允许部分成功。
 */
const props = defineProps<{
  projectNo: string
}>()

const emit = defineEmits<{
  changed: []
}>()

const query = reactive({ status: 'PENDING_VERIFY', page: 1, size: 10 })
const pageData = ref<PageData<DonationSummary>>({
  list: [],
  page: { page: 1, size: 10, total: 0, pages: 0 },
})
const loading = ref(false)
const errorText = ref('')
const selected = ref<string[]>([])

const confirming = ref<DonationSummary | null>(null)
const actualYuan = ref<number | null>(null)
const note = ref('')
const confirmVisible = ref(false)
const confirmBusy = ref(false)
const confirmError = ref('')

const batchBusy = ref(false)
const batchReport = ref<string[]>([])

const voucherPreview = ref<string[]>([])
const voucherVisible = ref(false)

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  selected.value = []
  try {
    pageData.value = await fundApi.fetchDonations({
      projectNo: props.projectNo,
      status: query.status || undefined,
      page: query.page,
      size: query.size,
    })
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '捐赠流水加载失败'
  } finally {
    loading.value = false
  }
}

function setStatus(value: string): void {
  query.status = value
  query.page = 1
  load()
}

function onStatusChange(event: Event): void {
  setStatus((event.target as HTMLSelectElement).value)
}

function changePage(page: number): void {
  query.page = page
  load()
}

function isPending(row: DonationSummary): boolean {
  return row.status === 'PENDING_VERIFY'
}

function toggle(id: string): void {
  selected.value = selected.value.includes(id)
    ? selected.value.filter((item) => item !== id)
    : [...selected.value, id]
}

function toggleAll(): void {
  const pendingIds = pageData.value.list.filter(isPending).map((row) => row.donationNo)
  selected.value = selected.value.length === pendingIds.length ? [] : pendingIds
}

function openConfirm(row: DonationSummary): void {
  confirming.value = row
  actualYuan.value = centsToYuan(row.amount)
  note.value = ''
  confirmError.value = ''
  confirmVisible.value = true
}

async function submitConfirm(): Promise<void> {
  if (!confirming.value || confirmBusy.value) return
  if (!actualYuan.value || actualYuan.value <= 0) {
    confirmError.value = '实收金额必须大于 0'
    return
  }
  confirmBusy.value = true
  confirmError.value = ''
  try {
    const result = await fundApi.confirmDonation(confirming.value.donationNo, {
      amount: yuanToCents(actualYuan.value),
      note: note.value.trim() || undefined,
    })
    if (result.status === 'VERIFIED') {
      ElMessage.success(`已入账：${money(result.amount)}，项目进度 ${result.progress?.progressPercent ?? 0}%`)
    } else {
      ElMessage.warning(result.note || '实收金额与登记金额不一致，已转为凭证异常')
    }
    confirmVisible.value = false
    await load()
    emit('changed')
  } catch (error) {
    confirmError.value = error instanceof BizError ? error.message : '核对失败，请稍后重试'
  } finally {
    confirmBusy.value = false
  }
}

async function batchConfirm(): Promise<void> {
  if (!selected.value.length || batchBusy.value) return
  batchBusy.value = true
  batchReport.value = []
  try {
    const result = await fundApi.confirmDonations(selected.value)
    batchReport.value = [
      `成功 ${result.success} 笔`,
      ...result.failed.map((item) => `${item.id}：${item.message}（${item.code}）`),
    ]
    if (result.failed.length) {
      ElMessage.warning(`批量核对部分成功：成功 ${result.success} 笔，失败 ${result.failed.length} 笔`)
    } else {
      ElMessage.success(`批量核对完成：${result.success} 笔已入账`)
    }
    await load()
    emit('changed')
  } catch (error) {
    batchReport.value = [error instanceof BizError ? error.message : '批量核对失败，请稍后重试']
    ElMessage.error(batchReport.value[0])
  } finally {
    batchBusy.value = false
  }
}

function openVouchers(ids: string[]): void {
  voucherPreview.value = ids
  voucherVisible.value = ids.length > 0
}

watch(
  () => props.projectNo,
  () => {
    query.page = 1
    load()
  },
)

onMounted(load)
</script>

<template>
  <PanelCard title="捐赠核对（管理员）" :more="`待核对优先 · 共 ${pageData.page.total} 笔`">
    <div class="toolbar">
      <select :value="query.status" class="select" @change="onStatusChange">
        <option value="">全部状态</option>
        <option v-for="item in DONATION_STATUS_OPTIONS" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
      <button class="btn sm ghost" type="button" :disabled="loading" @click="load">刷新</button>
      <button
        class="btn sm"
        type="button"
        :disabled="!selected.length || batchBusy"
        @click="batchConfirm"
      >
        {{ batchBusy ? '核对中…' : `批量按登记金额入账（${selected.length}）` }}
      </button>
      <span class="muted batch-hint">批量只按登记金额入账；金额不符请逐笔核对。</span>
    </div>

    <div v-if="batchReport.length" class="notice batch-report">
      <div v-for="line in batchReport" :key="line">{{ line }}</div>
    </div>

    <PageState
      :loading="loading"
      :error="errorText"
      :empty="!pageData.list.length"
      empty-text="当前筛选下没有捐赠流水"
    >
      <div class="table-wrap">
        <table class="native">
          <thead>
            <tr>
              <th style="width: 42px">
                <input
                  type="checkbox"
                  :checked="selected.length === pageData.list.filter(isPending).length && selected.length > 0"
                  :disabled="!pageData.list.some(isPending)"
                  @change="toggleAll"
                />
              </th>
              <th>捐赠单号</th>
              <th>捐赠人</th>
              <th>金额</th>
              <th>留言</th>
              <th>凭证</th>
              <th>状态</th>
              <th>登记时间</th>
              <th style="width: 86px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in pageData.list" :key="row.donationNo">
              <td>
                <input
                  type="checkbox"
                  :checked="selected.includes(row.donationNo)"
                  :disabled="!isPending(row)"
                  @change="toggle(row.donationNo)"
                />
              </td>
              <td class="mono">{{ row.donationNo }}</td>
              <td>
                <div>{{ row.donorDisplay }}</div>
                <div v-if="row.anonymous" class="muted small">匿名</div>
              </td>
              <td><b>{{ money(row.amount) }}</b></td>
              <td class="message-cell">{{ row.message || '—' }}</td>
              <td>
                <button
                  v-if="row.voucherFileIds.length"
                  class="btn sm link"
                  type="button"
                  @click="openVouchers(row.voucherFileIds)"
                >
                  {{ row.voucherFileIds.length }} 个
                </button>
                <span v-else class="muted">—</span>
              </td>
              <td>
                <StatusTag :code="row.status" :label="donationStatusLabel(row.status)" />
              </td>
              <td class="muted small">{{ dateTime(row.createdAt) }}</td>
              <td>
                <button
                  v-if="isPending(row)"
                  class="btn sm link"
                  type="button"
                  @click="openConfirm(row)"
                >
                  核对
                </button>
                <span v-else class="muted small">{{ row.verifiedAt ? dateTime(row.verifiedAt) : '—' }}</span>
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
      口径：登记 ≠ 入账；只有 VERIFIED 才递增项目 raisedAmount。ABNORMAL 保持原进度，实收金额写入审计日志。
    </div>

    <el-dialog v-model="confirmVisible" title="核对捐赠入账" width="540px" append-to-body>
      <div v-if="confirming" class="confirm-body">
        <div class="mono">{{ confirming.donationNo }}</div>
        <div class="confirm-row"><span>登记金额</span><b>{{ money(confirming.amount) }}</b></div>
        <div class="field">
          <label>实收金额（元）</label>
          <input v-model.number="actualYuan" class="input" type="number" min="0.01" step="0.01" />
        </div>
        <div class="field">
          <label>核对备注</label>
          <input v-model="note" class="input" maxlength="255" placeholder="金额不一致时必须说明原因" />
        </div>
        <div v-if="confirmError" class="field-error">{{ confirmError }}</div>
        <div class="notice">实收金额与登记金额不一致时转 ABNORMAL，不计入项目进度。</div>
      </div>

      <template #footer>
        <button class="btn ghost" type="button" @click="confirmVisible = false">取消</button>
        <button class="btn" type="button" :disabled="confirmBusy" @click="submitConfirm">
          {{ confirmBusy ? '核对中…' : '确认核对' }}
        </button>
      </template>
    </el-dialog>

    <el-dialog v-model="voucherVisible" title="捐赠凭证" width="720px" append-to-body>
      <PhotoGallery :file-ids="voucherPreview" empty-text="未上传凭证" />
    </el-dialog>
  </PanelCard>
</template>

<style scoped>
.batch-hint {
  font-size: 11px;
}

.batch-report {
  margin-bottom: 12px;
}

.message-cell {
  max-width: 180px;
  white-space: normal;
  font-size: 12px;
  line-height: 1.5;
}

.small {
  font-size: 11px;
}

.confirm-body {
  display: grid;
  gap: 10px;
}

.confirm-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--chip);
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.confirm-row b {
  color: var(--accent);
}
</style>
