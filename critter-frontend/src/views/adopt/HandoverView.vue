<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api/handovers'
import { BizError } from '@/api/http'
import PageHero from '@/components/PageHero.vue'
import type { FollowupView, HandoverDetail, HandoverView } from '@/api/p2types'
import { dateTime } from '@/utils/format'

const rows = ref<HandoverView[]>([])
const loading = ref(false)
const page = reactive({ page: 1, size: 10, total: 0 })
const status = ref('')

const createVisible = ref(false)
const creating = ref(false)
const form = reactive({
  animalCode: '',
  adopterName: '',
  contact: '',
  guardianConsent: false,
  residenceNote: '',
})

const detailVisible = ref(false)
const detail = ref<HandoverDetail | null>(null)
const detailLoading = ref(false)

const completeVisible = ref(false)
const completing = ref(false)
const completeTarget = ref<FollowupView | null>(null)
const completeForm = reactive({ result: 'NORMAL' as 'NORMAL' | 'ABNORMAL', note: '' })

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'ACTIVE', label: '回访中' },
  { value: 'RETURNED', label: '已退回' },
  { value: 'CLOSED', label: '已结束' },
]

function progressText(row: HandoverView): string {
  return `${row.completedFollowups}/${row.totalFollowups}`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await api.fetchHandovers({
      status: status.value || undefined,
      page: page.page,
      size: page.size,
    })
    rows.value = data.list
    page.total = data.page.total
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '领养交接列表加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  form.animalCode = ''
  form.adopterName = ''
  form.contact = ''
  form.guardianConsent = false
  form.residenceNote = ''
  createVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!form.animalCode.trim() || !form.adopterName.trim() || !form.contact.trim()) {
    ElMessage.warning('动物编号、领养人和联系方式必填')
    return
  }
  if (!form.guardianConsent) {
    ElMessage.warning('缺少监护人同意材料，不能建立领养交接')
    return
  }
  if (form.residenceNote.trim().length < 5) {
    ElMessage.warning('居住说明至少 5 个字，作为领养材料的一部分')
    return
  }
  creating.value = true
  try {
    const created = await api.createHandover({
      animalCode: form.animalCode.trim(),
      adopterName: form.adopterName.trim(),
      contact: form.contact.trim(),
      guardianConsent: form.guardianConsent,
      residenceNote: form.residenceNote.trim(),
    })
    createVisible.value = false
    ElMessage.success(`已建立 ${created.handover.handoverNo}，自动生成 3 次回访计划`)
    await load()
    await openDetail(created.handover.handoverNo)
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '建立领养交接失败')
  } finally {
    creating.value = false
  }
}

async function openDetail(no: string): Promise<void> {
  detailLoading.value = true
  detailVisible.value = true
  try {
    detail.value = await api.fetchHandover(no)
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '回访详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function openComplete(row: FollowupView): void {
  completeTarget.value = row
  completeForm.result = 'NORMAL'
  completeForm.note = ''
  completeVisible.value = true
}

async function submitComplete(): Promise<void> {
  if (!detail.value || !completeTarget.value) return
  completing.value = true
  try {
    await api.completeFollowup(
      detail.value.handover.handoverNo,
      completeTarget.value.seqNo,
      { result: completeForm.result, note: completeForm.note || undefined },
    )
    completeVisible.value = false
    ElMessage.success('回访记录已提交')
    detail.value = await api.fetchHandover(detail.value.handover.handoverNo)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '回访提交失败')
  } finally {
    completing.value = false
  }
}

function onPage(value: number): void {
  page.page = value
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero
      title="领养回访"
      desc="领养离校后按 7 / 30 / 90 天自动生成三次回访；监护人同意与居住说明缺一不可。"
    >
      <template #actions>
        <button class="btn" type="button" @click="openCreate">新建领养交接</button>
      </template>
    </PageHero>

    <div class="card">
      <div class="toolbar">
        <el-select v-model="status" style="width: 140px" @change="page.page = 1; load()">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <span class="muted">共 {{ page.total }} 份交接单</span>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="handoverNo" label="交接单号" min-width="150">
          <template #default="{ row }">
            <button class="link-btn mono" type="button" @click="openDetail(row.handoverNo)">
              {{ row.handoverNo }}
            </button>
          </template>
        </el-table-column>
        <el-table-column label="动物" min-width="120">
          <template #default="{ row }">{{ row.animalCode }}</template>
        </el-table-column>
        <el-table-column label="领养人" min-width="130">
          <template #default="{ row }">
            <div>{{ row.adopterName }}</div>
            <div class="muted" style="font-size: 12px">{{ row.contact }}</div>
          </template>
        </el-table-column>
        <el-table-column label="回访进度" width="110">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.completedFollowups < row.totalFollowups }">
              {{ progressText(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'RETURNED' ? 'warning' : 'info'">
              {{ row.status === 'ACTIVE' ? '回访中' : row.status === 'RETURNED' ? '已退回' : '已结束' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="领养时间" width="165">
          <template #default="{ row }">{{ dateTime(row.adoptedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row.handoverNo)">回访记录</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          layout="prev, pager, next"
          :current-page="page.page"
          :page-size="page.size"
          :total="page.total"
          @current-change="onPage"
        />
      </div>
    </div>

    <el-dialog v-model="createVisible" title="新建领养交接" width="520px">
      <div class="form-grid">
        <label>动物编号</label>
        <el-input v-model="form.animalCode" placeholder="CAT-2026-031" />
        <label>领养人</label>
        <el-input v-model="form.adopterName" maxlength="32" />
        <label>联系方式</label>
        <el-input v-model="form.contact" maxlength="32" />
        <label>监护人同意</label>
        <el-checkbox v-model="form.guardianConsent">已取得监护人书面同意</el-checkbox>
        <label>居住说明</label>
        <el-input
          v-model="form.residenceNote"
          type="textarea"
          :rows="3"
          maxlength="255"
          placeholder="居住环境、封窗、同住人同意等，不少于 5 字"
        />
      </div>
      <div class="notice" style="margin-top: 12px">
        材料不齐会返回 30409（领养材料不齐），不会生成回访计划。
      </div>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">建立并生成三次回访</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="回访记录" width="680px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="handover-head">
            <div>
              <strong>{{ detail.handover.animalCode }}</strong>
              <span class="muted"> · {{ detail.handover.adopterName }}</span>
            </div>
            <div class="muted">
              {{ detail.handover.handoverNo }} · 领养于 {{ dateTime(detail.handover.adoptedAt) }}
            </div>
          </div>
          <div class="residence">{{ detail.handover.residenceNote }}</div>
          <el-table :data="detail.followups" stripe>
            <el-table-column label="次序" width="70">
              <template #default="{ row }">第 {{ row.seqNo }} 次</template>
            </el-table-column>
            <el-table-column label="计划日期" width="120">
              <template #default="{ row }">
                <span :class="{ 'text-danger': row.overdue }">{{ row.dueDate }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'DONE' ? 'success' : row.overdue ? 'danger' : 'info'">
                  {{ row.status === 'DONE' ? '已完成' : row.overdue ? '已逾期' : '待回访' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                {{ row.result === 'NORMAL' ? '正常' : row.result === 'ABNORMAL' ? '异常' : '—' }}
              </template>
            </el-table-column>
            <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="openComplete(row)">
                  登记
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>

    <el-dialog v-model="completeVisible" title="登记回访" width="440px">
      <div class="form-grid">
        <label>回访结果</label>
        <el-radio-group v-model="completeForm.result">
          <el-radio value="NORMAL">正常</el-radio>
          <el-radio value="ABNORMAL">异常</el-radio>
        </el-radio-group>
        <label>备注</label>
        <el-input v-model="completeForm.note" type="textarea" :rows="3" maxlength="255" />
      </div>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="completing" @click="submitComplete">提交回访</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 12px;
  align-items: center;
}
.handover-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.residence {
  padding: 10px 12px;
  margin-bottom: 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-2);
  font-size: 13px;
}
.text-danger {
  color: var(--danger);
  font-weight: 600;
}
</style>
