<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as medicalApi from '@/api/medical'
import { BizError } from '@/api/http'
import PageHero from '@/components/PageHero.vue'
import { useAuthStore } from '@/stores/auth'
import type { ClinicView, VisitDetail } from '@/api/types'
import { dateTime, money } from '@/utils/format'

/** P09 医疗协作：合作医院与示例报价、管理员登记就诊、按就诊编号查询详情。 */

const auth = useAuthStore()
const router = useRouter()

const clinics = ref<ClinicView[]>([])
const loadingClinics = ref(false)
const visitNo = ref('')
const lookupLoading = ref(false)
const detail = ref<VisitDetail | null>(null)
const detailVisible = ref(false)

const registerVisible = ref(false)
const registering = ref(false)
const clinicVisible = ref(false)
const clinicSaving = ref(false)
const editVisible = ref(false)
const editSaving = ref(false)

const form = reactive({
  animalId: null as number | null,
  orderId: null as number | null,
  clinicId: null as number | null,
  visitedAt: null as Date | null,
  complaint: '',
  diagnosis: '',
  treatment: '',
  costYuan: 0,
  fileIdText: '',
  sourceNote: '',
})

const clinicForm = reactive({
  clinicName: '',
  contact: '',
  phone: '',
  address: '',
  priceNote: '',
})

const editForm = reactive({
  complaint: '',
  diagnosis: '',
  treatment: '',
  costYuan: 0,
  fileIdText: '',
  reason: '',
})

const detailClinic = computed(
  () => clinics.value.find((item) => item.clinicId === detail.value?.clinicId) ?? null,
)

async function loadClinics(): Promise<void> {
  loadingClinics.value = true
  try {
    clinics.value = await medicalApi.fetchClinics()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '合作医院加载失败')
  } finally {
    loadingClinics.value = false
  }
}

async function lookupVisit(): Promise<void> {
  const no = visitNo.value.trim()
  if (!no) {
    ElMessage.warning('请输入就诊编号，如 VS20261120001')
    return
  }
  lookupLoading.value = true
  try {
    detail.value = await medicalApi.fetchVisit(no)
    detailVisible.value = true
  } catch (error) {
    if (error instanceof BizError && error.code === 30410) {
      ElMessage.warning('未找到该就诊记录，请核对编号')
    } else {
      ElMessage.error(error instanceof BizError ? error.message : '就诊记录查询失败')
    }
  } finally {
    lookupLoading.value = false
  }
}

function openRegister(): void {
  resetForm()
  registerVisible.value = true
}

function resetForm(): void {
  form.animalId = null
  form.orderId = null
  form.clinicId = null
  form.visitedAt = new Date()
  form.complaint = ''
  form.diagnosis = ''
  form.treatment = ''
  form.costYuan = 0
  form.fileIdText = ''
  form.sourceNote = ''
}

async function submitRegister(): Promise<void> {
  if (!form.animalId || !form.clinicId || !form.visitedAt) {
    ElMessage.warning('动物 ID、合作医院与到院时间必填')
    return
  }
  if (!form.orderId && !form.sourceNote.trim()) {
    ElMessage.warning('无工单就诊必须备注来源，如“校园巡查发现”')
    return
  }
  if (form.costYuan === null || form.costYuan < 0) {
    ElMessage.warning('费用不能为负数')
    return
  }

  registering.value = true
  try {
    const result = await medicalApi.createVisit({
      animalId: form.animalId,
      orderId: form.orderId || null,
      clinicId: form.clinicId,
      visitedAt: toOffsetIso(form.visitedAt),
      complaint: form.complaint.trim() || undefined,
      diagnosis: form.diagnosis.trim() || undefined,
      treatment: form.treatment.trim() || undefined,
      cost: Math.round(form.costYuan * 100),
      fileIds: form.fileIdText
        .split(/[,，\s]+/)
        .map((item) => item.trim())
        .filter(Boolean),
      sourceNote: form.sourceNote.trim() || undefined,
    })
    registerVisible.value = false
    ElMessage.success(`已登记就诊 ${result.visitNo}`)
    visitNo.value = result.visitNo
    await lookupVisit()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '就诊登记失败，请稍后重试')
  } finally {
    registering.value = false
  }
}

function openCreateClinic(): void {
  clinicForm.clinicName = ''
  clinicForm.contact = ''
  clinicForm.phone = ''
  clinicForm.address = ''
  clinicForm.priceNote = ''
  clinicVisible.value = true
}

async function submitClinic(): Promise<void> {
  if (!clinicForm.clinicName.trim()) {
    ElMessage.warning('医院名称必填')
    return
  }
  clinicSaving.value = true
  try {
    const created = await medicalApi.createClinic({
      clinicName: clinicForm.clinicName.trim(),
      contact: clinicForm.contact.trim() || undefined,
      phone: clinicForm.phone.trim() || undefined,
      address: clinicForm.address.trim() || undefined,
      priceNote: clinicForm.priceNote.trim() || undefined,
    })
    clinicVisible.value = false
    ElMessage.success(`已新增合作医院 ${created.clinicName}`)
    await loadClinics()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '新增合作医院失败')
  } finally {
    clinicSaving.value = false
  }
}

function openEditVisit(): void {
  if (!detail.value) {
    return
  }
  editForm.complaint = detail.value.complaint || ''
  editForm.diagnosis = detail.value.diagnosis || ''
  editForm.treatment = detail.value.treatment || ''
  editForm.costYuan = detail.value.cost / 100
  editForm.fileIdText = detail.value.fileIds.join(', ')
  editForm.reason = ''
  editVisible.value = true
}

async function submitEditVisit(): Promise<void> {
  if (!detail.value) {
    return
  }
  if (!editForm.reason.trim()) {
    ElMessage.warning('请填写纠错原因，便于审计留痕')
    return
  }
  if (editForm.costYuan === null || editForm.costYuan < 0) {
    ElMessage.warning('费用不能为负数')
    return
  }

  editSaving.value = true
  try {
    detail.value = await medicalApi.updateVisit(detail.value.visitNo, {
      complaint: editForm.complaint.trim() || undefined,
      diagnosis: editForm.diagnosis.trim() || undefined,
      treatment: editForm.treatment.trim() || undefined,
      cost: Math.round(editForm.costYuan * 100),
      fileIds: editForm.fileIdText
        .split(/[,，\s]+/)
        .map((item) => item.trim())
        .filter(Boolean),
      reason: editForm.reason.trim(),
    })
    editVisible.value = false
    ElMessage.success('就诊记录已更正，变更已写入审计日志')
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '就诊纠错失败')
  } finally {
    editSaving.value = false
  }
}

/** 后端 visitedAt 为 OffsetDateTime，提交时携带本机时区偏移。 */
function toOffsetIso(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  const offsetMinutes = -date.getTimezoneOffset()
  const sign = offsetMinutes >= 0 ? '+' : '-'
  const hours = pad(Math.floor(Math.abs(offsetMinutes) / 60))
  const minutes = pad(Math.abs(offsetMinutes) % 60)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
    + `${sign}${hours}:${minutes}`
}

onMounted(loadClinics)
</script>

<template>
  <div>
    <PageHero
      title="医疗协作"
      desc="合作医院、就诊登记与处置留痕。费用按整数分入账；无工单就诊必须备注来源。"
    >
      <template #actions>
        <el-button @click="router.push('/tnr')">TNR 看板</el-button>
        <el-button @click="router.push('/medical/plans')">疫苗与用药计划</el-button>
        <el-button v-if="auth.isAdmin" type="primary" @click="openRegister">登记就诊</el-button>
      </template>
    </PageHero>

    <div class="card lookup-card">
      <div class="section-head">
        <div>
          <h2 class="section-title">查询就诊记录</h2>
          <p class="muted">凭就诊编号查看诊断、处置、费用与关联影像；无需工单也可查询。</p>
        </div>
      </div>
      <div class="lookup-row">
        <el-input
          v-model="visitNo"
          class="mono"
          clearable
          placeholder="VS20261120001"
          @keyup.enter="lookupVisit"
        />
        <el-button type="primary" :loading="lookupLoading" @click="lookupVisit">查询</el-button>
      </div>
    </div>

    <div class="card">
      <div class="section-head">
        <div>
          <h2 class="section-title">合作医院</h2>
          <p class="muted">报价为课程示例，仅作内部协作参考，不构成真实价格承诺。</p>
        </div>
        <div class="section-actions">
          <el-button v-if="auth.isAdmin" type="primary" plain @click="openCreateClinic">新增医院</el-button>
          <el-button text :loading="loadingClinics" @click="loadClinics">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loadingClinics" :data="clinics" stripe>
        <el-table-column prop="clinicName" label="医院" min-width="190" />
        <el-table-column label="联系人" min-width="120">
          <template #default="{ row }">{{ row.contact || '—' }}</template>
        </el-table-column>
        <el-table-column label="电话" min-width="130">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column label="地址" min-width="200">
          <template #default="{ row }">{{ row.address || '—' }}</template>
        </el-table-column>
        <el-table-column label="示例报价" min-width="230">
          <template #default="{ row }">{{ row.priceNote || '—' }}</template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="clinicVisible" title="新增合作医院" width="560px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="医院名称" required>
          <el-input v-model="clinicForm.clinicName" maxlength="64" show-word-limit placeholder="如校园东门合作医院（示例）" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="clinicForm.contact" maxlength="32" show-word-limit placeholder="可空" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="clinicForm.phone" maxlength="32" show-word-limit placeholder="可空" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="clinicForm.address" maxlength="255" show-word-limit placeholder="可空" />
        </el-form-item>
        <el-form-item label="报价备注">
          <el-input
            v-model="clinicForm.priceNote"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="如绝育 280 元、疫苗 60 元/针（示例价）"
          />
          <span class="field-hint">仅作内部协作参考，页面不构成真实价格承诺。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clinicVisible = false">取消</el-button>
        <el-button type="primary" :loading="clinicSaving" @click="submitClinic">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="registerVisible" title="登记就诊" width="640px" :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item label="动物 ID" required>
          <el-input-number v-model="form.animalId" :min="1" :controls="false" placeholder="档案主键" />
          <span class="field-hint">档案页由成员 B 实现，暂以档案 ID 关联。</span>
        </el-form-item>
        <el-form-item label="关联工单 ID">
          <el-input-number v-model="form.orderId" :min="1" :controls="false" placeholder="可空" />
          <span class="field-hint">填写后工单动物必须与就诊动物一致。</span>
        </el-form-item>
        <el-form-item label="合作医院" required>
          <el-select v-model="form.clinicId" filterable placeholder="选择合作医院" style="width: 100%">
            <el-option
              v-for="item in clinics"
              :key="item.clinicId"
              :label="item.clinicName"
              :value="item.clinicId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="到院时间" required>
          <el-date-picker
            v-model="form.visitedAt"
            type="datetime"
            placeholder="选择到院时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="主诉">
          <el-input v-model="form.complaint" maxlength="255" show-word-limit placeholder="如左前肢跛行" />
        </el-form-item>
        <el-form-item label="诊断">
          <el-input v-model="form.diagnosis" maxlength="255" show-word-limit placeholder="如软组织挫伤" />
        </el-form-item>
        <el-form-item label="处置">
          <el-input
            v-model="form.treatment"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="如清创包扎、补液观察"
          />
        </el-form-item>
        <el-form-item label="费用（元）">
          <el-input-number v-model="form.costYuan" :min="0" :precision="2" :step="10" />
          <span class="field-hint">提交时换算为整数分。</span>
        </el-form-item>
        <el-form-item label="影像编号">
          <el-input v-model="form.fileIdText" placeholder="多个编号用逗号分隔，可空" />
        </el-form-item>
        <el-form-item label="来源备注" :required="!form.orderId">
          <el-input
            v-model="form.sourceNote"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="无工单时必填，如校园巡查发现"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="registering" @click="submitRegister">登记</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="纠错就诊记录" width="620px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="仅更正主诉、诊断、处置、费用与影像；动物、医院与到院时间不可改，变更原因写入审计日志。"
        class="edit-alert"
      />
      <el-form label-width="100px">
        <el-form-item label="主诉">
          <el-input v-model="editForm.complaint" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="诊断">
          <el-input v-model="editForm.diagnosis" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="处置">
          <el-input v-model="editForm.treatment" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="费用（元）">
          <el-input-number v-model="editForm.costYuan" :min="0" :precision="2" :step="10" />
          <span class="field-hint">提交时换算为整数分。</span>
        </el-form-item>
        <el-form-item label="影像编号">
          <el-input v-model="editForm.fileIdText" placeholder="多个编号用逗号分隔，清空表示无影像" />
        </el-form-item>
        <el-form-item label="纠错原因" required>
          <el-input
            v-model="editForm.reason"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="如费用按实际票据更正、诊断复核"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="submitEditVisit">保存更正</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="就诊记录详情" size="480px">
      <template v-if="detail">
        <div class="detail-hero">
          <div class="muted">就诊编号</div>
          <div class="detail-no mono">{{ detail.visitNo }}</div>
          <div class="detail-animal">
            {{ detail.animalName || '未命名' }} · {{ detail.animalCode }}
          </div>
          <div v-if="auth.isAdmin" class="detail-actions">
            <el-button type="primary" plain size="small" @click="openEditVisit">纠错记录</el-button>
          </div>
        </div>

        <div class="detail-grid">
          <div class="detail-item">
            <span class="muted">合作医院</span>
            <strong>{{ detail.clinicName }}</strong>
          </div>
          <div class="detail-item">
            <span class="muted">到院时间</span>
            <strong>{{ dateTime(detail.visitedAt) }}</strong>
          </div>
          <div class="detail-item">
            <span class="muted">费用</span>
            <strong>{{ money(detail.cost) }}</strong>
          </div>
          <div class="detail-item">
            <span class="muted">关联工单</span>
            <strong class="mono">{{ detail.orderNo || '无工单' }}</strong>
          </div>
        </div>

        <div class="detail-block">
          <h3>主诉</h3>
          <p>{{ detail.complaint || '未填写' }}</p>
        </div>
        <div class="detail-block">
          <h3>诊断</h3>
          <p>{{ detail.diagnosis || '未填写' }}</p>
        </div>
        <div class="detail-block">
          <h3>处置</h3>
          <p>{{ detail.treatment || '未填写' }}</p>
        </div>
        <div class="detail-block">
          <h3>影像编号</h3>
          <div v-if="detail.fileIds.length" class="file-tags">
            <el-tag v-for="fileId in detail.fileIds" :key="fileId" effect="plain">{{ fileId }}</el-tag>
          </div>
          <p v-else class="muted">无影像关联</p>
          <p class="muted small">仅记录附件编号，公开访问仍需通过附件审核与授权。</p>
        </div>
        <div v-if="detail.sourceNote" class="detail-block">
          <h3>来源备注</h3>
          <p>{{ detail.sourceNote }}</p>
        </div>
        <div v-if="detailClinic" class="detail-block">
          <h3>医院联系方式</h3>
          <p>{{ detailClinic.contact || '—' }} · {{ detailClinic.phone || '—' }}</p>
          <p class="muted">{{ detailClinic.address || '—' }}</p>
        </div>
        <p class="muted small">登记于 {{ dateTime(detail.createdAt) }}</p>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.lookup-card {
  margin-bottom: 16px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.section-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.section-title {
  margin: 0 0 4px;
  font-size: 18px;
}

.lookup-row {
  display: flex;
  gap: 10px;
  max-width: 520px;
}

.field-hint {
  margin-left: 10px;
  color: #8792a2;
  font-size: 12px;
}

.detail-hero {
  padding: 16px;
  border-radius: 12px;
  background: #f4f7fb;
  margin-bottom: 18px;
}

.detail-no {
  font-size: 20px;
  font-weight: 700;
  margin: 4px 0;
}

.detail-animal {
  color: #4d5a6a;
}

.detail-actions {
  margin-top: 10px;
}

.edit-alert {
  margin-bottom: 14px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #e6ebf2;
  border-radius: 10px;
}

.detail-block {
  margin-bottom: 16px;
}

.detail-block h3 {
  margin: 0 0 6px;
  font-size: 14px;
  color: #334155;
}

.detail-block p {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
}

.file-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.small {
  font-size: 12px;
}

@media (max-width: 640px) {
  .lookup-row {
    flex-direction: column;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
