<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PageState from '@/components/PageState.vue'
import StatusTag from '@/components/StatusTag.vue'
import PhotoGallery from '@/components/PhotoGallery.vue'
import { dateTime } from '@/utils/format'
import { reportStatusLabel, reportTypeLabel } from '@/utils/domain'
import {
  clearReportReceipts,
  loadReportReceipts,
  type ReportReceipt,
} from '@/utils/reportReceipts'

/**
 * “我的上报”当前只能展示本机回执。
 * 原因：后端未提供 /reports/mine，创建响应也不返回数据库 id，前端无法按 reportNo 反查。
 * 页面明确标注本地回执，不把回执状态伪装成后端实时状态。
 */

const router = useRouter()
const auth = useAuthStore()

const receipts = ref<ReportReceipt[]>([])
const drawerVisible = ref(false)
const selected = ref<ReportReceipt | null>(null)

function owner(): string {
  return auth.user?.userNo ?? 'guest'
}

function load(): void {
  receipts.value = loadReportReceipts(owner())
}

function openDetail(receipt: ReportReceipt): void {
  selected.value = receipt
  drawerVisible.value = true
}

async function clearAll(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '只会清空这台电脑上的上报回执，不影响后端上报记录。确认清空？',
      '清空本机回执',
      { confirmButtonText: '清空', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  clearReportReceipts(owner())
  receipts.value = []
  ElMessage.success('本机回执已清空')
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero
      title="我的上报"
      desc="当前后端尚未开放上报人自查列表，本页展示浏览器本地回执；正式状态以管理员受理/关闭记录为准。"
    >
      <template #actions>
        <button class="btn" type="button" @click="router.push('/report')">新增上报</button>
        <button v-if="receipts.length" class="btn ghost" type="button" @click="clearAll">清空本机回执</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '我要上报', path: '/report' },
        { label: '我的上报', path: '/report/mine' },
        { label: '工单看板', path: '/orders', adminOnly: true },
      ]"
    />

    <div class="notice">
      本页不是后端实时列表：<span class="mono">GET /reports/mine</span> 尚未实现，且创建响应只返回
      <span class="mono">reportNo</span>、不返回 id。下列记录保存在当前浏览器，换设备或清缓存后会消失。
    </div>

    <PageState :empty="!receipts.length" empty-text="这台设备还没有上报回执">
      <div class="table-wrap">
        <table class="native">
          <thead>
            <tr>
              <th>上报编号</th>
              <th>类型</th>
              <th>点位</th>
              <th>描述</th>
              <th>提交状态（快照）</th>
              <th>受理截止</th>
              <th>提交时间</th>
              <th style="width: 74px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="receipt in receipts" :key="receipt.reportNo">
              <td>
                <div class="mono">{{ receipt.reportNo }}</div>
                <div v-if="receipt.duplicated" class="tag warn">疑似重复</div>
              </td>
              <td>{{ reportTypeLabel(receipt.type) }}</td>
              <td>{{ receipt.pointName || '—' }}</td>
              <td class="desc-cell">{{ receipt.description }}</td>
              <td>
                <StatusTag :code="receipt.status" :label="reportStatusLabel(receipt.status)" />
              </td>
              <td class="muted small">{{ dateTime(receipt.deadline) }}</td>
              <td class="muted small">{{ dateTime(receipt.createdAt) }}</td>
              <td>
                <button class="btn sm link" type="button" @click="openDetail(receipt)">回执</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </PageState>

    <div class="footnote" style="text-align: left">
      待确认项：后端补 <span class="mono">GET /reports/mine</span> 或在创建响应返回 id 后，
      本页应替换为真实服务端列表，并移除 localStorage 回执。
    </div>

    <el-drawer v-model="drawerVisible" title="上报回执" size="520px" append-to-body>
      <div v-if="selected" class="receipt-body">
        <div class="receipt-head">
          <div>
            <div class="mono">{{ selected.reportNo }}</div>
            <h2>{{ reportTypeLabel(selected.type) }} · {{ selected.pointName }}</h2>
          </div>
          <StatusTag :code="selected.status" :label="reportStatusLabel(selected.status)" />
        </div>

        <div v-if="selected.duplicated" class="notice">
          提交时被后端标记为疑似重复，原单：<span class="mono">{{ selected.duplicateOf || '—' }}</span>
        </div>

        <div class="block">
          <h3>情况描述</h3>
          <p>{{ selected.description }}</p>
        </div>

        <div class="block">
          <h3>现场照片</h3>
          <PhotoGallery :file-ids="selected.photos" empty-text="无照片" />
        </div>

        <div class="block">
          <div class="info-grid single">
            <div class="info">
              <div class="k">关联档案</div>
              <div class="v mono">{{ selected.animalCode || '—' }}</div>
            </div>
            <div class="info">
              <div class="k">联系方式</div>
              <div class="v">{{ selected.contact || '—' }}</div>
            </div>
            <div class="info">
              <div class="k">提交时间</div>
              <div class="v">{{ dateTime(selected.createdAt) }}</div>
            </div>
            <div class="info">
              <div class="k">受理截止</div>
              <div class="v">{{ dateTime(selected.deadline) }}</div>
            </div>
          </div>
        </div>

        <div class="notice">状态为提交时的快照，不代表当前受理进度。</div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.desc-cell {
  max-width: 240px;
  white-space: normal;
  font-size: 12px;
  line-height: 1.5;
}

.small {
  font-size: 11px;
}

.receipt-body {
  display: grid;
  gap: 16px;
}

.receipt-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.receipt-head h2 {
  margin-top: 4px;
  font-size: 19px;
}

.block h3 {
  margin-bottom: 6px;
}

.block p {
  color: var(--ink);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.info-grid.single {
  grid-template-columns: 1fr 1fr;
  margin-top: 0;
}
</style>
