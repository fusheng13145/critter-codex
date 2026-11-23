<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as adoptionsApi from '@/api/adoptions'
import * as benefitApi from '@/api/adoptionBenefits'
import { BizError } from '@/api/http'
import PageHero from '@/components/PageHero.vue'
import AdoptionPaymentDialog from '@/components/AdoptionPaymentDialog.vue'
import { useAuthStore } from '@/stores/auth'
import type { AdoptionPlan, AdoptionSummary } from '@/api/types'
import type { VisitAdminView } from '@/api/p2types'
import {
  adoptionStatusLabel,
  adoptionStatusType,
  dateTime,
  money,
  periodLabel,
} from '@/utils/format'

/** P03 认养专区：学生申请与我的认养、管理员审核与缴费入账共用一页。 */

const auth = useAuthStore()
const router = useRouter()
const isAdmin = computed(() => auth.isAdmin)
const activeTab = ref(isAdmin.value ? 'audit' : 'mine')

const plans = ref<AdoptionPlan[]>([])
const loadingPlans = ref(false)

const adminRows = ref<AdoptionSummary[]>([])
const adminStatus = ref('PENDING_AUDIT')
const adminPage = reactive({ page: 1, size: 10, total: 0 })
const loadingAdmin = ref(false)

const visitRows = ref<VisitAdminView[]>([])
const loadingVisits = ref(false)

const mineRows = ref<AdoptionSummary[]>([])
const mineStatus = ref('')
const minePage = reactive({ page: 1, size: 10, total: 0 })
const loadingMine = ref(false)

const applyVisible = ref(false)
const applyForm = reactive({
  animalCode: '',
  planId: 0,
  contact: '',
  remark: '',
  promiseAccepted: false,
})
const applying = ref(false)

const auditVisible = ref(false)
const auditTarget = ref<AdoptionSummary | null>(null)
const auditForm = reactive({ decision: 'APPROVE' as 'APPROVE' | 'REJECT', amount: 0, reason: '' })
const auditing = ref(false)

const paymentVisible = ref(false)
const paymentTarget = ref<{ adoptionNo: string; amount: number } | null>(null)

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'PENDING_AUDIT', label: '待审核' },
  { value: 'PENDING_PAYMENT', label: '待缴费' },
  { value: 'ACTIVE', label: '生效中' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'TERMINATED', label: '已终止' },
]

async function loadPlans(): Promise<void> {
  loadingPlans.value = true
  try {
    plans.value = await adoptionsApi.fetchPlans()
    if (!applyForm.planId && plans.value.length) {
      applyForm.planId = plans.value[0].planId
    }
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '认养方案加载失败')
  } finally {
    loadingPlans.value = false
  }
}

async function loadAdmin(): Promise<void> {
  if (!isAdmin.value) return
  loadingAdmin.value = true
  try {
    const data = await adoptionsApi.fetchAdoptions({
      status: adminStatus.value || undefined,
      page: adminPage.page,
      size: adminPage.size,
    })
    adminRows.value = data.list
    adminPage.total = data.page.total
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '认养单列表加载失败')
  } finally {
    loadingAdmin.value = false
  }
}

async function loadMine(): Promise<void> {  loadingMine.value = true
  try {
    const data = await adoptionsApi.fetchMyAdoptions({
      status: mineStatus.value || undefined,
      page: minePage.page,
      size: minePage.size,
    })
    mineRows.value = data.list
    minePage.total = data.page.total
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '我的认养加载失败')
  } finally {
    loadingMine.value = false
  }
}

async function loadVisits(): Promise<void> {
  if (!isAdmin.value) return
  loadingVisits.value = true
  try {
    visitRows.value = await benefitApi.fetchPendingVisits(50)
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '探视预约加载失败')
  } finally {
    loadingVisits.value = false
  }
}

async function reviewVisit(row: VisitAdminView, decision: 'APPROVE' | 'REJECT'): Promise<void> {
  let reason = ''
  if (decision === 'REJECT') {
    try {
      const value = await ElMessageBox.prompt('驳回探视必须填写原因', '驳回预约', {
        inputPlaceholder: '不少于 5 个字',
      })
      reason = value.value?.trim() ?? ''
    } catch {
      return
    }
    if (reason.length < 5) {
      ElMessage.warning('驳回原因至少 5 个字')
      return
    }
  }
  try {
    await benefitApi.reviewAdoptionVisit(row.adoptionNo, row.id, {
      decision,
      reason: reason || undefined,
    })
    ElMessage.success(decision === 'APPROVE' ? '探视预约已通过' : '已驳回探视预约')
    await loadVisits()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '探视审核失败')
  }
}

function openApply(): void {
  applyForm.animalCode = ''
  applyForm.contact = ''
  applyForm.remark = ''
  applyForm.promiseAccepted = false
  applyForm.planId = plans.value[0]?.planId ?? 0
  applyVisible.value = true
}

async function submitApply(): Promise<void> {
  if (!applyForm.animalCode.trim()) {
    ElMessage.warning('请填写动物编号，如 CAT-2026-031')
    return
  }
  if (!applyForm.planId) {
    ElMessage.warning('请选择认养方案')
    return
  }
  if (!applyForm.contact.trim()) {
    ElMessage.warning('请填写联系方式，仅管理员可见')
    return
  }
  if (!applyForm.promiseAccepted) {
    ElMessage.warning('请先阅读并勾选认养承诺')
    return
  }
  applying.value = true
  try {
    const result = await adoptionsApi.applyAdoption({
      animalCode: applyForm.animalCode.trim(),
      planId: applyForm.planId,
      promiseAccepted: applyForm.promiseAccepted,
      contact: applyForm.contact.trim(),
      remark: applyForm.remark.trim() || undefined,
    })
    applyVisible.value = false
    await ElMessageBox.alert(
      `认养单 ${result.adoptionNo} 已提交，状态为待审核。审核通过后请在七日内完成线下缴费。`,
      '申请已提交',
      { confirmButtonText: '知道了' },
    )
    await Promise.all([loadMine(), loadAdmin()])
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '提交认养申请失败')
  } finally {
    applying.value = false
  }
}

function openAudit(row: AdoptionSummary): void {
  auditTarget.value = row
  auditForm.decision = 'APPROVE'
  auditForm.amount = row.amount
  auditForm.reason = ''
  auditVisible.value = true
}

async function submitAudit(): Promise<void> {
  if (!auditTarget.value) return
  const reason = auditForm.reason.trim()
  if (auditForm.decision === 'REJECT' && reason.length < 5) {
    ElMessage.warning('驳回理由不少于 5 字')
    return
  }
  if (
    auditForm.decision === 'APPROVE'
    && auditForm.amount !== auditTarget.value.amount
    && reason.length < 5
  ) {
    ElMessage.warning('调整金额时必须填写不少于 5 字的说明')
    return
  }
  auditing.value = true
  try {
    const result = await adoptionsApi.auditAdoption(auditTarget.value.adoptionNo, {
      decision: auditForm.decision,
      reason: reason || undefined,
      amount: auditForm.decision === 'APPROVE' ? auditForm.amount : undefined,
    })
    auditVisible.value = false
    if (result.status === 'PENDING_PAYMENT') {
      ElMessage.success('审核通过，已转待缴费；认证入账后才会生效')
    } else {
      ElMessage.success('已驳回该申请')
    }
    await Promise.all([loadAdmin(), loadMine()])
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '审核失败，请稍后重试')
  } finally {
    auditing.value = false
  }
}

function openPayment(row: AdoptionSummary): void {
  paymentTarget.value = { adoptionNo: row.adoptionNo, amount: row.amount }
  paymentVisible.value = true
}

function openDetail(row: AdoptionSummary): void {
  router.push(`/adopt/${row.adoptionNo}`)
}

function onAdminPage(page: number): void {
  adminPage.page = page
  loadAdmin()
}

function onMinePage(page: number): void {
  minePage.page = page
  loadMine()
}

onMounted(async () => {
  await Promise.all([loadPlans(), loadMine(), loadAdmin(), loadVisits()])
})
</script>

<template>
  <div>
    <PageHero title="认养专区" desc="申请 → 审核 → 缴费入账 → 生效。审核通过只转待缴费，动物标记只在入账事务中变更。">
      <template #actions>
        <button v-if="isAdmin" class="btn ghost" type="button" @click="router.push({ name: 'plan-manage' })">方案管理</button>
        <button v-if="isAdmin" class="btn ghost" type="button" @click="router.push({ name: 'handovers' })">领养回访</button>
        <button class="btn" type="button" @click="openApply">提交认养申请</button>
      </template>
    </PageHero>

    <div class="card">
      <el-tabs v-model="activeTab">
        <el-tab-pane v-if="isAdmin" label="认养审核" name="audit">
          <div class="toolbar">
            <el-select v-model="adminStatus" style="width: 150px" @change="adminPage.page = 1; loadAdmin()">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <span class="muted">共 {{ adminPage.total }} 单</span>
            <el-button :loading="loadingAdmin" @click="loadAdmin">刷新</el-button>
          </div>

          <el-table v-loading="loadingAdmin" :data="adminRows" stripe>
            <el-table-column prop="adoptionNo" label="认养单号" min-width="150">
              <template #default="{ row }">
                <button class="link-btn mono" type="button" @click="openDetail(row)">{{ row.adoptionNo }}</button>
              </template>
            </el-table-column>
            <el-table-column label="申请人" min-width="120">
              <template #default="{ row }">
                <div>{{ row.userName }}</div>
                <div class="muted" style="font-size: 12px">{{ row.contactMasked }}</div>
              </template>
            </el-table-column>
            <el-table-column label="动物 / 方案" min-width="160">
              <template #default="{ row }">
                <div>{{ row.animalName || '未命名' }} · {{ row.animalCode }}</div>
                <div class="muted" style="font-size: 12px">{{ row.planName }} · {{ periodLabel(row.period) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="100">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="adoptionStatusType(row.status)" effect="light">
                  {{ adoptionStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="申请时间" width="150">
              <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING_AUDIT'" size="small" type="primary" @click="openAudit(row)">
                  审核
                </el-button>
                <el-button
                  v-if="row.status === 'PENDING_PAYMENT'"
                  size="small"
                  type="warning"
                  @click="openPayment(row)"
                >
                  缴费入账
                </el-button>
                <el-button size="small" @click="openDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              layout="prev, pager, next"
              :current-page="adminPage.page"
              :page-size="adminPage.size"
              :total="adminPage.total"
              @current-change="onAdminPage"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="isAdmin" label="探视预约" name="visits">
          <div class="toolbar">
            <span class="muted">待审核的探视预约共 {{ visitRows.length }} 条</span>
            <el-button :loading="loadingVisits" @click="loadVisits">刷新</el-button>
          </div>
          <el-table v-loading="loadingVisits" :data="visitRows" stripe>
            <el-table-column prop="adoptionNo" label="认养单号" min-width="150" />
            <el-table-column label="动物" min-width="110">
              <template #default="{ row }">{{ row.animalCode }}</template>
            </el-table-column>
            <el-table-column prop="adopterName" label="认养人" width="110" />
            <el-table-column prop="visitDate" label="日期" width="110" />
            <el-table-column prop="timeSlot" label="时段" width="110" />
            <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="reviewVisit(row, 'APPROVE')">通过</el-button>
                <el-button size="small" @click="reviewVisit(row, 'REJECT')">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="我的认养" name="mine">
          <div class="toolbar">
            <el-select v-model="mineStatus" style="width: 150px" @change="minePage.page = 1; loadMine()">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <span class="muted">共 {{ minePage.total }} 单</span>
            <el-button :loading="loadingMine" @click="loadMine">刷新</el-button>
          </div>

          <el-table v-loading="loadingMine" :data="mineRows" stripe>
            <el-table-column prop="adoptionNo" label="认养单号" min-width="150">
              <template #default="{ row }">
                <button class="link-btn mono" type="button" @click="openDetail(row)">{{ row.adoptionNo }}</button>
              </template>
            </el-table-column>
            <el-table-column label="动物" min-width="140">
              <template #default="{ row }">{{ row.animalName || '未命名' }} · {{ row.animalCode }}</template>
            </el-table-column>
            <el-table-column prop="planName" label="方案" min-width="120" />
            <el-table-column label="金额" width="100">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="adoptionStatusType(row.status)" effect="light">
                  {{ adoptionStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="起止日期" width="190">
              <template #default="{ row }">
                {{ row.startDate || '—' }} 至 {{ row.endDate || '—' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openDetail(row)">查看详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              layout="prev, pager, next"
              :current-page="minePage.page"
              :page-size="minePage.size"
              :total="minePage.total"
              @current-change="onMinePage"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="applyVisible" title="提交认养申请" width="560px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="动物编号" required>
          <el-input v-model="applyForm.animalCode" placeholder="例如 CAT-2026-031" />
        </el-form-item>
        <el-form-item label="认养方案" required>
          <el-select v-model="applyForm.planId" :loading="loadingPlans" style="width: 100%">
            <el-option
              v-for="plan in plans"
              :key="plan.planId"
              :label="`${plan.planName} · ${periodLabel(plan.period)} · ${money(plan.amount)}`"
              :value="plan.planId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="联系方式" required>
          <el-input v-model="applyForm.contact" placeholder="手机号或微信号，仅管理员可见" maxlength="32" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="applyForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="applyForm.promiseAccepted">
            我已阅读并承诺不弃养、按时反馈近况，接受定期回访
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="auditVisible" title="审核认养申请" width="520px" destroy-on-close>
      <el-form label-width="82px">
        <el-form-item label="认养单号">
          <span class="mono">{{ auditTarget?.adoptionNo }}</span>
        </el-form-item>
        <el-form-item label="审核结论">
          <el-radio-group v-model="auditForm.decision">
            <el-radio-button value="APPROVE">通过</el-radio-button>
            <el-radio-button value="REJECT">驳回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="auditForm.decision === 'APPROVE'" label="审核金额（分）">
          <el-input-number v-model="auditForm.amount" :min="1" :step="100" />
          <span class="muted" style="margin-left: 8px">{{ money(auditForm.amount) }}</span>
        </el-form-item>
        <el-form-item :label="auditForm.decision === 'REJECT' ? '驳回理由' : '审核说明'">
          <el-input
            v-model="auditForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            :placeholder="auditForm.decision === 'REJECT'
              ? '不少于 5 字，将展示给申请人'
              : '调整金额时必填，不少于 5 字'"
          />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon>
        通过只转“待缴费”，不会立即生效；必须再完成缴费入账。
      </el-alert>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="auditing" @click="submitAudit">提交审核</el-button>
      </template>
    </el-dialog>

    <AdoptionPaymentDialog
      v-model="paymentVisible"
      :adoption="paymentTarget"
      @success="loadAdmin(); loadMine()"
    />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
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
</style>
