<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as interactionApi from '@/api/interactions'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PanelCard from '@/components/PanelCard.vue'
import { dateTime } from '@/utils/format'
import type {
  CommentView,
  InteractionBizType,
} from '@/api/types'

const props = defineProps<{
  bizType: InteractionBizType
  bizId: string
}>()

const router = useRouter()
const auth = useAuthStore()

const comments = ref<CommentView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const errorText = ref('')
const content = ref('')
const submitting = ref(false)

const replyVisible = ref(false)
const replyTarget = ref<CommentView | null>(null)
const replyContent = ref('')
const replyPublic = ref(true)
const replyBusy = ref(false)

const loggedIn = computed(() => auth.isLoggedIn)
const canReply = computed(() => auth.isAdmin)

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const data = await interactionApi.fetchComments({
      bizType: props.bizType,
      bizId: props.bizId,
      page: page.value,
      size: pageSize,
    })
    comments.value = data.list
    total.value = data.page.total
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '留言加载失败'
    comments.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function changePage(next: number): void {
  page.value = next
  load()
}

async function submit(): Promise<void> {
  if (!loggedIn.value) {
    ElMessage.warning('登录后才能发表留言')
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  const text = content.value.trim()
  if (!text) {
    ElMessage.warning('请先填写留言内容')
    return
  }
  submitting.value = true
  try {
    await interactionApi.createComment({
      bizType: props.bizType,
      bizId: props.bizId,
      content: text,
    })
    content.value = ''
    ElMessage.success('留言已提交')
    page.value = 1
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '留言提交失败')
  } finally {
    submitting.value = false
  }
}

function openReply(comment: CommentView): void {
  if (!canReply.value) return
  replyTarget.value = comment
  replyContent.value = comment.replyContent ?? ''
  replyPublic.value = true
  replyVisible.value = true
}

async function submitReply(): Promise<void> {
  const target = replyTarget.value
  if (!target) return
  const text = replyContent.value.trim()
  if (!text) {
    ElMessage.warning('请填写回复内容')
    return
  }
  replyBusy.value = true
  try {
    await interactionApi.replyComment(target.commentId, {
      replyContent: text,
      visible: replyPublic.value,
    })
    replyVisible.value = false
    ElMessage.success('回复已保存')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '回复保存失败')
  } finally {
    replyBusy.value = false
  }
}

onMounted(load)
</script>

<template>
  <PanelCard title="留言讨论" :more="`共 ${total} 条`">
    <div v-if="errorText" class="notice">{{ errorText }}</div>
    <div v-else-if="loading && !comments.length" class="muted empty-line">留言加载中…</div>
    <div v-else-if="!comments.length" class="muted empty-line">还没有留言，来说第一句吧</div>

    <ul v-else class="comment-list">
      <li v-for="item in comments" :key="item.commentId" class="comment-item">
        <div class="comment-head">
          <b>{{ item.authorName || '校园用户' }}</b>
          <span class="muted small">{{ dateTime(item.createdAt) }}</span>
          <span v-if="item.status === 'PENDING_AUDIT'" class="tag warning">待审核</span>
          <button
            v-if="canReply"
            class="btn ghost small-btn"
            type="button"
            @click="openReply(item)"
          >
            {{ item.replyContent ? '修改回复' : '回复' }}
          </button>
        </div>
        <p class="comment-body">{{ item.content }}</p>
        <div v-if="item.replyContent" class="comment-reply">
          <span class="tag gray">管理员回复</span>
          <span>{{ item.replyContent }}</span>
        </div>
      </li>
    </ul>

    <div v-if="total > pageSize" class="comment-pager">
      <el-pagination
        layout="prev, pager, next"
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        background
        @current-change="changePage"
      />
    </div>

    <div class="comment-form">
      <el-input
        v-model="content"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="聊聊这只动物或这个项目，请勿发布广告与联系方式"
      />
      <div class="comment-form-foot">
        <span class="muted small">留言将公开展示；含引流转账号等营销内容会先进入待审核。</span>
        <button class="btn" type="button" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : loggedIn ? '发表留言' : '登录后留言' }}
        </button>
      </div>
    </div>

    <div class="footnote" style="text-align: left">
      来源：GET /comments、POST /comments；管理员通过 PUT /comments/{id}/reply 回复，回复待审留言同时视为审核通过。
    </div>

    <el-dialog v-model="replyVisible" title="回复留言" width="520px" append-to-body>
      <div v-if="replyTarget" class="reply-quote">
        <b>{{ replyTarget.authorName || '校园用户' }}</b>：{{ replyTarget.content }}
      </div>
      <el-input
        v-model="replyContent"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="填写回复内容"
      />
      <div class="reply-switch">
        <el-switch v-model="replyPublic" />
        <span>{{ replyPublic ? '公开回复：所有访客可见' : '仅留言者可见：不在公开列表展示' }}</span>
      </div>
      <template #footer>
        <button class="btn ghost" type="button" @click="replyVisible = false">取消</button>
        <button class="btn" type="button" :disabled="replyBusy" @click="submitReply">
          {{ replyBusy ? '保存中…' : '保存回复' }}
        </button>
      </template>
    </el-dialog>
  </PanelCard>
</template>

<style scoped>
.comment-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.comment-item {
  padding: 14px 0;
  border-bottom: 1px dashed var(--line, #e5e7eb);
}

.comment-item:last-child {
  border-bottom: none;
}

.comment-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.comment-body {
  margin: 8px 0 0;
  white-space: pre-wrap;
  line-height: 1.65;
}

.comment-reply {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fafc;
  display: flex;
  gap: 8px;
  line-height: 1.6;
}

.small-btn {
  margin-left: auto;
  padding: 4px 10px;
  font-size: 12px;
}

.comment-pager {
  display: flex;
  justify-content: center;
  margin-top: 14px;
}

.comment-form {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--line, #e5e7eb);
}

.comment-form-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.reply-quote {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: var(--muted, #64748b);
}

.reply-switch {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  color: var(--muted, #64748b);
  font-size: 13px;
}
</style>
