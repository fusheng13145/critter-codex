<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as noticesApi from '@/api/notices'
import { BizError } from '@/api/http'
import PageHero from '@/components/PageHero.vue'
import { useAuthStore } from '@/stores/auth'
import type { NoticeCategory, NoticeItem } from '@/api/types'
import { dateTime, noticeCategoryLabel, noticeCategoryTag } from '@/utils/format'

/** P09 公告：公开列表按分类与置顶排序；管理员可在同一页发布。 */

const router = useRouter()
const auth = useAuthStore()

const rows = ref<NoticeItem[]>([])
const loading = ref(false)
const publishing = ref(false)
const category = ref('')
const page = reactive({ page: 1, size: 10, total: 0 })

const createVisible = ref(false)
const form = reactive({
  category: 'PROGRESS' as NoticeCategory,
  title: '',
  content: '',
  pinned: false,
})

const tabs = [
  { value: '', label: '全部公告' },
  { value: 'PROGRESS', label: '进展' },
  { value: 'PUBLICITY', label: '公示' },
  { value: 'SCIENCE', label: '科普' },
]

const pinnedCount = computed(() => rows.value.filter((item) => item.pinned).length)

async function loadNotices(): Promise<void> {
  loading.value = true
  try {
    const data = await noticesApi.fetchNotices({
      category: category.value || undefined,
      page: page.page,
      size: page.size,
    })
    rows.value = data.list
    page.total = data.page.total
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '公告列表加载失败')
  } finally {
    loading.value = false
  }
}

function selectCategory(value: string): void {
  category.value = value
  page.page = 1
  void loadNotices()
}

function changeSize(): void {
  page.page = 1
  void loadNotices()
}

function openNotice(row: NoticeItem): void {
  void router.push(`/notices/${row.noticeId}`)
}

function openCreate(): void {
  form.category = 'PROGRESS'
  form.title = ''
  form.content = ''
  form.pinned = false
  createVisible.value = true
}

async function submitCreate(): Promise<void> {
  const title = form.title.trim()
  const content = form.content.trim()
  if (!title) {
    ElMessage.warning('请填写公告标题')
    return
  }
  if (!content) {
    ElMessage.warning('请填写公告正文')
    return
  }
  publishing.value = true
  try {
    const created = await noticesApi.publishNotice({
      category: form.category,
      title,
      content,
      pinned: form.pinned,
    })
    ElMessage.success(`公告 ${created.noticeNo} 已发布`)
    createVisible.value = false
    page.page = 1
    await loadNotices()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '公告发布失败')
  } finally {
    publishing.value = false
  }
}

onMounted(() => {
  void loadNotices()
})
</script>

<template>
  <div>
    <PageHero title="校园公告">
      <template #actions>
        <el-tag type="warning" effect="plain">置顶 {{ pinnedCount }} 条</el-tag>
        <el-button :loading="loading" @click="loadNotices">刷新</el-button>
        <el-button v-if="auth.isAdmin" type="primary" @click="openCreate">发布公告</el-button>
      </template>
    </PageHero>

    <div class="notice-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        type="button"
        class="notice-tab"
        :class="{ active: category === tab.value }"
        @click="selectCategory(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <section v-loading="loading" class="notice-list">
      <button
        v-for="row in rows"
        :key="row.noticeId"
        type="button"
        class="notice-card"
        :class="{ pinned: row.pinned }"
        @click="openNotice(row)"
      >
        <div class="notice-meta">
          <el-tag v-if="row.pinned" type="danger" size="small" effect="plain">置顶</el-tag>
          <el-tag :type="noticeCategoryTag(row.category)" size="small" effect="plain">
            {{ noticeCategoryLabel(row.category) }}
          </el-tag>
          <span class="mono">{{ row.noticeNo }}</span>
          <span class="muted">{{ dateTime(row.publishedAt) }}</span>
        </div>
        <h2>{{ row.title }}</h2>
        <span class="read-more">查看公告详情 →</span>
      </button>

      <div v-if="!loading && !rows.length" class="empty">
        <b>暂无公告</b>
        <span></span>
      </div>
    </section>

    <div class="pager">
      <el-pagination
        v-model:current-page="page.page"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadNotices"
        @size-change="changeSize"
      />
    </div>

    <el-dialog v-model="createVisible" title="发布公告" width="620px">
      <el-form label-width="72px">
        <el-form-item label="分类" required>
          <el-radio-group v-model="form.category">
            <el-radio-button value="PROGRESS">进展</el-radio-button>
            <el-radio-button value="PUBLICITY">公示</el-radio-button>
            <el-radio-button value="SCIENCE">科普</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="128" show-word-limit placeholder="不超过 128 字" />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="9"
            maxlength="20000"
            show-word-limit
            placeholder="支持纯文本分段，发布后立即生效"
          />
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="form.pinned" />
          <span class="muted" style="margin-left: 10px"></span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="submitCreate">确认发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.notice-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.notice-tab {
  padding: 8px 18px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--panel);
  color: var(--muted);
  cursor: pointer;
  transition: border-color 0.16s ease, color 0.16s ease;
}

.notice-tab:hover,
.notice-tab.active {
  border-color: var(--accent);
  color: var(--accent);
  font-weight: 600;
}

.notice-list {
  display: grid;
  gap: 12px;
  min-height: 120px;
}

.notice-card {
  display: block;
  width: 100%;
  padding: 18px 20px;
  border: 1px solid var(--line);
  border-left: 4px solid transparent;
  border-radius: var(--radius);
  background: var(--panel);
  box-shadow: var(--shadow);
  text-align: left;
  cursor: pointer;
  transition: transform 0.16s ease, border-color 0.16s ease;
}

.notice-card:hover {
  transform: translateY(-2px);
  border-color: var(--accent);
}

.notice-card.pinned {
  border-left-color: #c97920;
  background: #fffdf7;
}

.notice-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 11px;
}

.notice-card h2 {
  margin: 0 0 12px;
  font-size: 19px;
  line-height: 1.4;
}

.read-more {
  color: var(--accent);
  font-size: 12px;
}

.empty {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  padding: 46px 16px;
  border: 1px dashed var(--line);
  border-radius: var(--radius);
  color: var(--muted);
  text-align: center;
}

.empty b {
  color: var(--ink);
  font-size: 15px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

@media (max-width: 620px) {
  .notice-tabs {
    flex-wrap: wrap;
  }

  .notice-card {
    padding: 15px 16px;
  }
}
</style>
