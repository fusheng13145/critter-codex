<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as messagesApi from '@/api/messages'
import { BizError } from '@/api/http'
import PageHero from '@/components/PageHero.vue'
import type { MessageItem, MessageUnreadCount } from '@/api/types'
import { messageTypeLabel, messageTypeTag, relativeTime } from '@/utils/format'

/** P08 消息中心：四类未读数、类型与已读筛选、批量已读、按业务对象跳转。 */

const router = useRouter()

const rows = ref<MessageItem[]>([])
const loading = ref(false)
const marking = ref(false)
const selected = ref<MessageItem[]>([])
const type = ref('')
const readFilter = ref('')
const page = reactive({ page: 1, size: 10, total: 0 })

const counts = ref<MessageUnreadCount>({
  adoptionExpiry: 0,
  donationProgress: 0,
  orderProgress: 0,
  approvalResult: 0,
})

const typeOptions = [
  { value: '', label: '全部类型' },
  { value: 'APPROVAL_RESULT', label: '审批结果' },
  { value: 'ADOPTION_EXPIRY', label: '认养到期' },
  { value: 'DONATION_PROGRESS', label: '募捐进度' },
  { value: 'ORDER_PROGRESS', label: '工单进展' },
]

const cards = computed(() => [
  {
    type: 'APPROVAL_RESULT',
    label: '审批结果',
    desc: '认养审核、缴费与终止',
    count: counts.value.approvalResult,
    icon: '✓',
    tone: 'green',
  },
  {
    type: 'ADOPTION_EXPIRY',
    label: '认养到期',
    desc: '到期前提醒与释放',
    count: counts.value.adoptionExpiry,
    icon: '◷',
    tone: 'amber',
  },
  {
    type: 'DONATION_PROGRESS',
    label: '募捐进度',
    desc: '捐赠入账与项目进展',
    count: counts.value.donationProgress,
    icon: '¥',
    tone: 'blue',
  },
  {
    type: 'ORDER_PROGRESS',
    label: '工单进展',
    desc: '上报受理与处置反馈',
    count: counts.value.orderProgress,
    icon: '↗',
    tone: 'slate',
  },
])

const unreadTotal = computed(
  () =>
    counts.value.approvalResult +
    counts.value.adoptionExpiry +
    counts.value.donationProgress +
    counts.value.orderProgress,
)

async function loadMessages(): Promise<void> {
  loading.value = true
  try {
    const data = await messagesApi.fetchMessages({
      type: type.value || undefined,
      readFlag: readFilter.value === '' ? undefined : readFilter.value === 'true',
      page: page.page,
      size: page.size,
    })
    rows.value = data.list
    page.total = data.page.total
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '消息列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCounts(): Promise<void> {
  try {
    counts.value = await messagesApi.fetchUnreadCounts()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '未读数加载失败')
  }
}

function selectType(value: string): void {
  type.value = type.value === value ? '' : value
  page.page = 1
  void loadMessages()
}

function changeFilter(): void {
  page.page = 1
  void loadMessages()
}

function changeSize(): void {
  page.page = 1
  void loadMessages()
}

function refresh(): void {
  void loadMessages()
  void loadCounts()
}

function rowClassName({ row }: { row: MessageItem }): string {
  return row.readFlag ? '' : 'unread-row'
}

function handleSelectionChange(selection: MessageItem[]): void {
  selected.value = selection
}

async function markSelected(): Promise<void> {
  if (!selected.value.length) {
    ElMessage.warning('请先选择要标记的消息')
    return
  }
  marking.value = true
  try {
    const result = await messagesApi.markMessagesRead(selected.value.map((item) => item.id))
    if (result.failed.length) {
      ElMessage.warning(`已读 ${result.success} 条，${result.failed.length} 条失败`)
    } else {
      ElMessage.success(`已将 ${result.success} 条消息标记为已读`)
    }
    selected.value = []
    await Promise.all([loadMessages(), loadCounts()])
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '标记已读失败')
  } finally {
    marking.value = false
  }
}

async function markOne(row: MessageItem): Promise<boolean> {
  if (row.readFlag) return true
  try {
    const result = await messagesApi.markMessagesRead([row.id])
    if (result.failed.length) {
      ElMessage.warning(result.failed[0]?.message ?? '标记已读失败')
      return false
    }
    row.readFlag = true
    await loadCounts()
    return true
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '标记已读失败')
    return false
  }
}

async function openMessage(row: MessageItem): Promise<void> {
  if (!(await markOne(row))) return
  if (row.bizType === 'ADOPTION' && row.bizId) {
    await router.push(`/adopt/${row.bizId}`)
    return
  }
  if (row.type === 'DONATION_PROGRESS') {
    await router.push('/fund')
    return
  }
  if (row.type === 'ORDER_PROGRESS') {
    await router.push('/report')
    return
  }
  await loadMessages()
}

onMounted(async () => {
  await Promise.all([loadMessages(), loadCounts()])
})
</script>

<template>
  <div>
    <PageHero title="消息中心" desc="审批结果、认养到期、募捐进度与工单进展，四类消息在此汇总。">
      <template #actions>
        <el-tag type="warning" effect="plain">未读 {{ unreadTotal }} 条</el-tag>
        <el-button @click="router.push('/notices')">校园公告</el-button>
        <el-button :loading="loading" @click="refresh">刷新</el-button>
      </template>
    </PageHero>

    <div class="message-grid">
      <button
        v-for="card in cards"
        :key="card.type"
        class="message-card"
        :class="[card.tone, { active: type === card.type }]"
        type="button"
        @click="selectType(card.type)"
      >
        <span class="message-icon">{{ card.icon }}</span>
        <span class="message-copy">
          <b>{{ card.label }}</b>
          <small>{{ card.desc }}</small>
        </span>
        <strong>{{ card.count }}</strong>
      </button>
    </div>

    <section class="card">
      <div class="card-head">
        <div class="toolbar">
          <el-select v-model="type" style="width: 150px" @change="changeFilter">
            <el-option
              v-for="item in typeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-select v-model="readFilter" style="width: 130px" @change="changeFilter">
            <el-option label="全部消息" value="" />
            <el-option label="仅未读" value="false" />
            <el-option label="仅已读" value="true" />
          </el-select>
          <span class="muted">共 {{ page.total }} 条</span>
        </div>
        <div class="head-actions">
          <el-button :disabled="!selected.length" :loading="marking" @click="markSelected">
            标记已读{{ selected.length ? `（${selected.length}）` : '' }}
          </el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        :row-class-name="rowClassName"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column label="类型" width="112">
          <template #default="{ row }">
            <el-tag :type="messageTypeTag(row.type)" effect="plain">
              {{ messageTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="消息" min-width="260">
          <template #default="{ row }">
            <button class="message-title" :class="{ unread: !row.readFlag }" type="button" @click="openMessage(row)">
              <span v-if="!row.readFlag" class="dot" />
              {{ row.title }}
            </button>
            <div v-if="row.bizId" class="biz-no mono">{{ row.bizType }} / {{ row.bizId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.readFlag ? 'info' : 'warning'" size="small" effect="plain">
              {{ row.readFlag ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">
            <span :title="row.createdAt">{{ relativeTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="86" fixed="right">
          <template #default="{ row }">
            <button class="link-btn" type="button" @click="openMessage(row)">查看</button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page.page"
          v-model:page-size="page.size"
          :total="page.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadMessages"
          @size-change="changeSize"
        />
      </div>

      <el-alert type="info" :closable="false" show-icon>
        消息保留 180 天。审批结果与认养关系消息可直接跳转到认养单详情。
      </el-alert>
    </section>
  </div>
</template>

<style scoped>
.message-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.message-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 94px;
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--panel);
  box-shadow: var(--shadow);
  text-align: left;
  cursor: pointer;
  transition: transform 0.16s ease, border-color 0.16s ease;
}

.message-card:hover,
.message-card.active {
  transform: translateY(-2px);
  border-color: var(--accent);
}

.message-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 18px;
  flex: 0 0 auto;
}

.message-card.amber .message-icon {
  background: #fbf1dc;
  color: #9a6a13;
}

.message-card.blue .message-icon {
  background: #e6effa;
  color: #2c5f96;
}

.message-card.slate .message-icon {
  background: #eef1f3;
  color: #53636b;
}

.message-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.message-copy b {
  font-size: 14px;
}

.message-copy small {
  color: var(--muted);
  font-size: 11px;
}

.message-card strong {
  font-size: 26px;
  font-weight: 600;
  color: var(--accent);
}

.toolbar,
.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.message-title {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.message-title:hover {
  color: var(--accent);
}

.message-title.unread {
  font-weight: 600;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #c97920;
  flex: 0 0 auto;
}

.biz-no {
  margin-top: 4px;
  font-size: 11px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin: 14px 0;
}

.link-btn {
  border: 0;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
  padding: 0;
}

.link-btn:hover {
  text-decoration: underline;
}

:deep(.unread-row) {
  background: #fffdf7;
}

@media (max-width: 980px) {
  .message-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .message-grid {
    grid-template-columns: 1fr;
  }

  .card-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
