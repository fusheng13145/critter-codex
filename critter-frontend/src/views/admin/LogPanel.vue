<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as logApi from '@/api/log'
import { BizError } from '@/api/http'
import { ACTION_LABELS, actionLabel, dateTime, roleLabel } from '@/utils/format'
import type { LogItem } from '@/api/types'

/**
 * 后台 · 操作日志：按人、动作、对象、时间段检索；
 * 点行展开 before/after JSON，用于回答"这条档案谁改的、改成什么"。
 */

const rows = ref<LogItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const errorText = ref('')

const filters = reactive({
  action: '',
  actorId: '',
  result: '',
  bizId: '',
  from: '',
  to: '',
})

const detail = ref<LogItem | null>(null)
const detailVisible = ref(false)

const actionOptions = Object.keys(ACTION_LABELS)

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const data = await logApi.fetchLogs({
      page: page.value,
      size: size.value,
      action: filters.action || undefined,
      actorId: filters.actorId ? Number(filters.actorId) : undefined,
      result: filters.result || undefined,
      bizId: filters.bizId || undefined,
      from: filters.from || undefined,
      to: filters.to || undefined,
    })
    rows.value = data.list
    total.value = data.page.total
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '日志加载失败'
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  load()
}

function reset(): void {
  filters.action = ''
  filters.actorId = ''
  filters.result = ''
  filters.bizId = ''
  filters.from = ''
  filters.to = ''
  page.value = 1
  load()
}

function pretty(json: string | null): string {
  if (!json) return '—'
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

function openDetail(row: LogItem): void {
  detail.value = row
  detailVisible.value = true
}

function copyRequestId(row: LogItem): void {
  if (!row.requestId) return
  navigator.clipboard
    ?.writeText(row.requestId)
    .then(() => ElMessage.success('已复制 requestId，可在后端日志中定位'))
    .catch(() => ElMessage.warning(`requestId：${row.requestId}`))
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <select v-model="filters.action" class="select" style="min-width: 132px">
        <option value="">全部动作</option>
        <option v-for="code in actionOptions" :key="code" :value="code">{{ actionLabel(code) }}</option>
      </select>
      <select v-model="filters.result" class="select" style="min-width: 112px">
        <option value="">全部结果</option>
        <option value="SUCCESS">成功</option>
        <option value="FAIL">失败</option>
      </select>
      <input v-model="filters.actorId" class="input" style="min-width: 108px" placeholder="操作人 ID" />
      <input v-model="filters.bizId" class="input" style="min-width: 140px" placeholder="对象编号（如学号）" />
      <input v-model="filters.from" class="input" type="date" style="min-width: 138px" />
      <span class="muted">至</span>
      <input v-model="filters.to" class="input" type="date" style="min-width: 138px" />
      <button class="btn" type="button" @click="search">检索</button>
      <button class="btn ghost" type="button" @click="reset">重置</button>
    </div>

    <div v-if="errorText" class="notice" style="margin-bottom: 12px">{{ errorText }}</div>

    <el-table :data="rows" v-loading="loading" size="default" stripe @row-click="openDetail">
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作人" width="150">
        <template #default="{ row }">
          #{{ row.actorId }}<br />
          <span class="muted" style="font-size: 11px">{{ roleLabel(row.actorRole ?? '') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="动作" width="130">
        <template #default="{ row }">
          <span class="tag" :class="{ gray: row.result !== 'SUCCESS' }">{{ actionLabel(row.action) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="对象" min-width="160">
        <template #default="{ row }">
          {{ row.bizType ?? '—' }}<br />
          <span class="mono">{{ row.bizId ?? '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="100">
        <template #default="{ row }">
          <span class="tag" :class="{ danger: row.result !== 'SUCCESS', gray: row.result === 'SUCCESS' }">
            {{ row.result === 'SUCCESS' ? '成功' : `失败 ${row.failCode ?? ''}` }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="来源 IP" width="130" />
      <el-table-column label="追踪号" width="130">
        <template #default="{ row }">
          <button class="btn link" type="button" @click.stop="copyRequestId(row)">复制</button>
        </template>
      </el-table-column>
      <el-table-column label="变更" width="90">
        <template #default="{ row }">
          <button class="btn link" type="button" @click.stop="openDetail(row)">查看</button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="load"
      @size-change="search"
    />

    <el-dialog v-model="detailVisible" title="日志详情" width="640px">
      <div v-if="detail">
        <div class="info-grid" style="grid-template-columns: repeat(2, 1fr); margin-bottom: 14px">
          <div class="info"><div class="k">动作</div><div class="v">{{ actionLabel(detail.action) }}</div></div>
          <div class="info"><div class="k">时间</div><div class="v">{{ dateTime(detail.createdAt) }}</div></div>
          <div class="info"><div class="k">操作人</div><div class="v">#{{ detail.actorId }} {{ roleLabel(detail.actorRole ?? '') }}</div></div>
          <div class="info"><div class="k">来源 IP</div><div class="v">{{ detail.ip ?? '—' }}</div></div>
        </div>
        <h3 style="margin-bottom: 6px">变更前</h3>
        <pre class="json">{{ pretty(detail.beforeJson) }}</pre>
        <h3 style="margin: 14px 0 6px">变更后</h3>
        <pre class="json">{{ pretty(detail.afterJson) }}</pre>

      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.json {
  background: var(--chip);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  font-size: 12px;
  max-height: 180px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
