<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as fileApi from '@/api/file'
import type { FileAuditItem } from '@/api/file'
import { BizError } from '@/api/http'
import { dateTime, statusLabel } from '@/utils/format'

/**
 * 后台 · 附件审核（P10 的第五个页签）。
 * 结论审查指出：PUT /files/{fileId}/audit 是 A 的约定端点里唯一没有前端入口的一个；
 * 这里补上工作台：先审后发的照片（ANIMAL_PUBLIC/PROJECT_COVER）在这里上架或下架。
 */

const rows = ref<FileAuditItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const loading = ref(false)
const auditStatusFilter = ref('PENDING')
const bizTypeFilter = ref('')
const errorText = ref('')

const BIZ_TYPES = [
  'ANIMAL_PUBLIC', 'PROJECT_COVER', 'PROJECT_SETTLEMENT', 'EXPENSE_VOUCHER',
  'ADOPTION_VOUCHER', 'REPORT_PHOTO', 'VISIT_PHOTO', 'AVATAR', 'GENERAL',
]

const BizTypeLabels: Record<string, string> = {
  ANIMAL_PUBLIC: '公开档案照片',
  PROJECT_COVER: '项目封面',
  PROJECT_SETTLEMENT: '结项公示附件',
  EXPENSE_VOUCHER: '支出票据',
  ADOPTION_VOUCHER: '认养缴费凭证',
  REPORT_PHOTO: '上报照片',
  VISIT_PHOTO: '就诊照片',
  AVATAR: '头像',
  GENERAL: '普通附件',
}

const isImage = computed(() => (row: FileAuditItem) => row.mime.startsWith('image/'))

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const data = await fileApi.fetchFilesForAudit({
      page: page.value,
      size: size.value,
      auditStatus: auditStatusFilter.value || undefined,
      bizType: bizTypeFilter.value || undefined,
    })
    rows.value = data.list
    total.value = data.page.total
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '附件列表加载失败'
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  load()
}

async function audit(item: FileAuditItem, decision: 'PASS' | 'REJECT'): Promise<void> {
  try {
    await fileApi.auditFile(item.fileId, decision)
    ElMessage.success(decision === 'PASS' ? '已通过，公开页立即可见' : '已驳回，公开页已下架')
    load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '审核失败')
  }
}

function sizeLabel(size: number): string {
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  if (size >= 1024) return `${Math.round(size / 1024)} KB`
  return `${size} B`
}

function bizTypeLabel(type: string): string {
  return BizTypeLabels[type] ?? type
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <select v-model="auditStatusFilter" class="select" style="min-width: 140px" @change="search">
        <option value="">全部状态</option>
        <option value="PENDING">待审核</option>
        <option value="PASSED">已通过</option>
        <option value="REJECTED">已驳回</option>
      </select>
      <select v-model="bizTypeFilter" class="select" style="min-width: 170px" @change="search">
        <option value="">全部业务类型</option>
        <option v-for="type in BIZ_TYPES" :key="type" :value="type">{{ bizTypeLabel(type) }}</option>
      </select>
      <button class="btn" type="button" @click="search">检索</button>
      <span class="muted" style="font-size: 12px; margin-left: auto">
        公开照片与项目封面"先审后发"；通过后立即可见，驳回立即下架
      </span>
    </div>

    <div v-if="errorText" class="notice" style="margin-bottom: 12px">{{ errorText }}</div>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column label="预览" width="96">
        <template #default="{ row }">
          <img
            v-if="isImage(row)"
            :src="fileApi.fileContentUrl(row.fileId)"
            class="thumb"
            :alt="row.name"
          />
          <span v-else class="tag gray">PDF</span>
        </template>
      </el-table-column>
      <el-table-column label="文件" min-width="200">
        <template #default="{ row }">
          {{ row.name }}<br />
          <span class="mono">{{ row.fileId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="业务类型" width="130">
        <template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template>
      </el-table-column>
      <el-table-column label="大小" width="90">
        <template #default="{ row }">{{ sizeLabel(row.size) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span class="tag" :class="{ warn: row.auditStatus === 'PENDING', gray: row.auditStatus === 'REJECTED' }">
            {{ statusLabel(row.auditStatus) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="上传时间" width="160">
        <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <template v-if="row.auditStatus !== 'PASSED'">
            <button class="btn sm" type="button" @click="audit(row, 'PASS')">通过</button>
          </template>
          <template v-if="row.auditStatus !== 'REJECTED'">
            <button class="btn sm ghost" type="button" style="margin-left: 6px" @click="audit(row, 'REJECT')">驳回</button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[12, 24, 48]"
      layout="total, sizes, prev, pager, next"
      @current-change="load"
      @size-change="search"
    />
  </div>
</template>

<style scoped>
.thumb {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 9px;
  border: 1px solid var(--line);
  display: block;
  background: var(--chip);
}
</style>
