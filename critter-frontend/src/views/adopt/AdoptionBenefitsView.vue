<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '@/api/adoptionBenefits'
import { BizError } from '@/api/http'
import PageHero from '@/components/PageHero.vue'
import type { AdoptionBenefitView, AdoptionVisitView } from '@/api/p2types'
import { dateTime } from '@/utils/format'

const route = useRoute()
const no = String(route.params.no ?? '')

const data = ref<AdoptionBenefitView | null>(null)
const loading = ref(true)
const denied = ref('')

const postForm = reactive({ title: '', content: '' })
const posting = ref(false)

const visitVisible = ref(false)
const booking = ref(false)
const visitForm = reactive({ visitDate: '', timeSlot: '09:00-11:00', note: '' })

const statusLabel = (status: string) =>
  status === 'PENDING' ? '待审核' : status === 'APPROVED' ? '已通过' : status === 'REJECTED' ? '已驳回' : '已取消'

async function load(): Promise<void> {
  loading.value = true
  denied.value = ''
  try {
    data.value = await api.fetchAdoptionBenefits(no)
  } catch (error) {
    if (error instanceof BizError && error.code === 20002) {
      denied.value = error.message
    } else {
      ElMessage.error(error instanceof BizError ? error.message : '专属权益加载失败')
    }
  } finally {
    loading.value = false
  }
}

async function submitPost(): Promise<void> {
  if (!postForm.title.trim() || !postForm.content.trim()) {
    ElMessage.warning('标题和正文必填')
    return
  }
  posting.value = true
  try {
    await api.createAdoptionPost(no, {
      title: postForm.title.trim(),
      content: postForm.content.trim(),
    })
    postForm.title = ''
    postForm.content = ''
    ElMessage.success('动态已发布')
    await load()
  } catch (error) {
    if (error instanceof BizError && error.code === 20002) {
      denied.value = error.message
    } else {
      ElMessage.error(error instanceof BizError ? error.message : '动态发布失败')
    }
  } finally {
    posting.value = false
  }
}

function openVisit(): void {
  visitForm.visitDate = ''
  visitForm.timeSlot = '09:00-11:00'
  visitForm.note = ''
  visitVisible.value = true
}

async function submitVisit(): Promise<void> {
  if (!visitForm.visitDate) {
    ElMessage.warning('请选择探视日期')
    return
  }
  booking.value = true
  try {
    await api.bookAdoptionVisit(no, {
      visitDate: visitForm.visitDate,
      timeSlot: visitForm.timeSlot.trim(),
      note: visitForm.note.trim() || undefined,
    })
    visitVisible.value = false
    ElMessage.success('探视预约已提交，等待管理员审核')
    await load()
  } catch (error) {
    if (error instanceof BizError && error.code === 20002) {
      denied.value = error.message
    } else {
      ElMessage.error(error instanceof BizError ? error.message : '预约失败')
    }
  } finally {
    booking.value = false
  }
}

async function cancelVisit(row: AdoptionVisitView): Promise<void> {
  try {
    await api.cancelAdoptionVisit(no, row.id)
    ElMessage.success('预约已取消')
    await load()
  } catch (error) {
    if (error instanceof BizError && error.code === 20002) {
      denied.value = error.message
    } else {
      ElMessage.error(error instanceof BizError ? error.message : '取消失败')
    }
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <PageHero
      title="认养人专属页"
      desc="专属动态、探视预约与电子证书只对生效中的认养人开放；关系终止后服务端直接返回 403。"
    >
      <template #actions>
        <button class="btn ghost" type="button" @click="$router.push(`/adopt/${no}`)">返回认养详情</button>
        <button class="btn" type="button" @click="$router.push(`/adopt/${no}/certificate`)">电子证书</button>
      </template>
    </PageHero>

    <div v-if="denied" class="denied">
      <h3>专属权益已失效</h3>
      <p>{{ denied }}</p>
      <p class="muted">认养关系终止或到期后，专属动态与探视预约不可再访问。</p>
    </div>

    <template v-else-if="data">
      <div class="benefit-head">
        <div>
          <strong>{{ data.animalName || '未命名' }}</strong>
          <span class="mono" style="margin-left: 8px">{{ data.animalCode }}</span>
        </div>
        <div class="muted">
          认养期 {{ data.startDate || '—' }} ~ {{ data.endDate || '—' }}
        </div>
      </div>

      <div class="grid2 align-start">
        <div class="card">
          <h3 class="section-title">专属动态</h3>
          <div class="post-form">
            <el-input v-model="postForm.title" maxlength="128" placeholder="动态标题" />
            <el-input
              v-model="postForm.content"
              type="textarea"
              :rows="3"
              maxlength="2000"
              placeholder="记录它今天的状态、趣事或需要关注的变化"
            />
            <div class="post-actions">
              <span class="muted">仅认养人本人可见</span>
              <el-button type="primary" :loading="posting" @click="submitPost">发布动态</el-button>
            </div>
          </div>

          <div v-if="data.posts.length" class="post-list">
            <article v-for="post in data.posts" :key="post.id" class="post-item">
              <div class="post-title">
                <strong>{{ post.title }}</strong>
                <span class="muted">{{ dateTime(post.createdAt) }}</span>
              </div>
              <p>{{ post.content }}</p>
              <div class="muted" style="font-size: 12px">发布人：{{ post.authorName || '认养人' }}</div>
            </article>
          </div>
          <div v-else class="empty">还没有专属动态，发布第一条记录它今天的样子。</div>
        </div>

        <div class="card">
          <div class="section-head">
            <h3 class="section-title">探视预约</h3>
            <el-button size="small" type="primary" @click="openVisit">预约探视</el-button>
          </div>
          <el-table :data="data.visits" stripe>
            <el-table-column prop="visitDate" label="日期" width="110" />
            <el-table-column prop="timeSlot" label="时段" width="110" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'info'">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reviewReason" label="审核意见" min-width="120" show-overflow-tooltip />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'PENDING' || row.status === 'APPROVED'"
                  size="small"
                  @click="cancelVisit(row)"
                >
                  取消
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!data.visits.length" class="empty">暂无预约。预约审核通过后可按时间到校探视。</div>
        </div>
      </div>
    </template>

    <el-dialog v-model="visitVisible" title="预约探视" width="460px">
      <div class="form-grid">
        <label>探视日期</label>
        <el-date-picker
          v-model="visitForm.visitDate"
          type="date"
          value-format="YYYY-MM-DD"
          :disabled-date="(date: Date) => date.getTime() < Date.now() - 86400000"
          style="width: 100%"
        />
        <label>时段</label>
        <el-input v-model="visitForm.timeSlot" maxlength="16" placeholder="09:00-11:00" />
        <label>备注</label>
        <el-input v-model="visitForm.note" type="textarea" :rows="3" maxlength="255" />
      </div>
      <template #footer>
        <el-button @click="visitVisible = false">取消</el-button>
        <el-button type="primary" :loading="booking" @click="submitVisit">提交预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.benefit-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  margin-bottom: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-2);
}
.denied {
  padding: 28px 24px;
  border: 1px solid #f3b7b7;
  border-radius: 12px;
  background: #fff7f7;
}
.denied h3 {
  margin: 0 0 8px;
  color: var(--danger);
}
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.section-title {
  margin: 0 0 12px;
  font-size: 15px;
}
.post-form {
  display: grid;
  gap: 10px;
  padding-bottom: 16px;
  border-bottom: 1px dashed var(--line);
}
.post-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.post-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}
.post-item {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
}
.post-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.post-item p {
  margin: 0 0 8px;
  white-space: pre-wrap;
}
.empty {
  padding: 24px 0;
  color: var(--muted);
  text-align: center;
  font-size: 13px;
}
.form-grid {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 12px;
  align-items: center;
}
</style>
