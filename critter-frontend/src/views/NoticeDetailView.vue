<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as noticesApi from '@/api/notices'
import { BizError } from '@/api/http'
import PageHero from '@/components/PageHero.vue'
import type { NoticeDetail } from '@/api/types'
import { dateTime, noticeCategoryLabel, noticeCategoryTag } from '@/utils/format'

/** 公告详情：公开正文、发布人与发布时间。 */

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const notice = ref<NoticeDetail | null>(null)

async function loadNotice(): Promise<void> {
  const id = Number(route.params.id)
  if (!Number.isInteger(id) || id <= 0) {
    ElMessage.error('公告编号非法')
    await router.replace('/notices')
    return
  }
  loading.value = true
  try {
    notice.value = await noticesApi.fetchNotice(id)
  } catch (error) {
    if (error instanceof BizError && error.code === 30410) {
      ElMessage.error('公告不存在或已下线')
    } else {
      ElMessage.error(error instanceof BizError ? error.message : '公告加载失败')
    }
  } finally {
    loading.value = false
  }
}

function backToList(): void {
  void router.push('/notices')
}

onMounted(() => {
  void loadNotice()
})
</script>

<template>
  <div>
    <PageHero title="公告详情">
      <template #actions>
        <el-button @click="backToList">返回公告列表</el-button>
      </template>
    </PageHero>

    <section v-loading="loading" class="notice-detail card">
      <template v-if="notice">
        <div class="notice-head">
          <div class="notice-tags">
            <el-tag v-if="notice.pinned" type="danger" effect="plain">置顶</el-tag>
            <el-tag :type="noticeCategoryTag(notice.category)" effect="plain">
              {{ noticeCategoryLabel(notice.category) }}
            </el-tag>
          </div>
          <h1>{{ notice.title }}</h1>
          <div class="notice-byline">
            <span class="mono">{{ notice.noticeNo }}</span>
            <span>{{ notice.authorName || '校园管理团队' }}</span>
            <span>{{ dateTime(notice.publishedAt) }}</span>
          </div>
        </div>

        <article class="notice-content">{{ notice.content }}</article>

      </template>

      <div v-else-if="!loading" class="notice-empty">
        <b>公告不可用</b>
        <span>公告不存在或已下线</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.notice-detail {
  max-width: 900px;
  margin: 0 auto;
  padding: 30px 36px 22px;
}

.notice-head {
  padding-bottom: 20px;
  border-bottom: 1px solid var(--line);
}

.notice-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.notice-head h1 {
  margin: 0 0 14px;
  font-size: 28px;
  line-height: 1.35;
}

.notice-byline {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  color: var(--muted);
  font-size: 12px;
}

.notice-content {
  min-height: 160px;
  padding: 24px 0;
  color: var(--ink);
  font-size: 15px;
  line-height: 1.95;
  white-space: pre-wrap;
  word-break: break-word;
}

.notice-foot {
  padding-top: 16px;
  border-top: 1px dashed var(--line);
  font-size: 12px;
}

.notice-empty {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  padding: 52px 16px;
  color: var(--muted);
  text-align: center;
}

.notice-empty b {
  color: var(--ink);
  font-size: 15px;
}

@media (max-width: 620px) {
  .notice-detail {
    padding: 22px 18px 18px;
  }

  .notice-head h1 {
    font-size: 22px;
  }
}
</style>
