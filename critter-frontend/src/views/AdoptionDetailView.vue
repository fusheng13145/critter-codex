<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as adoptionsApi from '@/api/adoptions'
import { BizError } from '@/api/http'
import PageHero from '@/components/PageHero.vue'
import PanelCard from '@/components/PanelCard.vue'
import AdoptionPaymentDialog from '@/components/AdoptionPaymentDialog.vue'
import { useAuthStore } from '@/stores/auth'
import type { AdoptionDetail } from '@/api/types'
import {
  adoptionStatusLabel,
  adoptionStatusType,
  dateTime,
  money,
  periodLabel,
} from '@/utils/format'

/** 认养详情：状态、缴费流水、证书入口，管理员可继续入账 / 终止。 */

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const no = computed(() => String(route.params.no ?? ''))
const isAdmin = computed(() => auth.isAdmin)

const detail = ref<AdoptionDetail | null>(null)
const loading = ref(true)
const errorText = ref('')

const paymentVisible = ref(false)
const terminateVisible = ref(false)
const terminateForm = reactive({ reason: '' })
const terminating = ref(false)

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    detail.value = await adoptionsApi.fetchAdoptionDetail(no.value)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '认养详情加载失败'
  } finally {
    loading.value = false
  }
}

function openPayment(): void {
  if (!detail.value) return
  paymentVisible.value = true
}

function openTerminate(): void {
  terminateForm.reason = ''
  terminateVisible.value = true
}

async function submitTerminate(): Promise<void> {
  if (terminateForm.reason.trim().length < 5) {
    ElMessage.warning('终止原因不少于 5 字')
    return
  }
  terminating.value = true
  try {
    await adoptionsApi.terminateAdoption(no.value, terminateForm.reason.trim())
    terminateVisible.value = false
    ElMessage.success('认养关系已终止，动物已恢复为在校园')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '终止认养失败')
  } finally {
    terminating.value = false
  }
}

function openCertificate(): void {
  router.push(`/adopt/${no.value}/certificate`)
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero :title="`认养详情 ${no}`">
      <template #actions>
        <button class="btn ghost" type="button" @click="router.push('/adopt')">返回认养专区</button>
        <button
          v-if="detail && (detail.status === 'ACTIVE' || detail.status === 'TERMINATED')"
          class="btn"
          type="button"
          @click="openCertificate"
        >
          查看电子证书
        </button>
      </template>
    </PageHero>

    <div v-if="errorText" class="card">
      <el-empty :description="errorText" />
    </div>

    <div v-else v-loading="loading" class="grid2 align-start">
      <PanelCard title="认养关系">
        <template v-if="detail">
          <div class="status-line">
            <el-tag :type="adoptionStatusType(detail.status)" effect="dark">
              {{ adoptionStatusLabel(detail.status) }}
            </el-tag>
            <span class="muted">申请于 {{ dateTime(detail.createdAt) }}</span>
          </div>
          <ul class="list">
            <li><span class="dot" /><span class="grow">认养单号</span><b class="mono">{{ detail.adoptionNo }}</b></li>
            <li><span class="dot" /><span class="grow">申请人</span><b>{{ detail.userName }}</b></li>
            <li><span class="dot" /><span class="grow">联系方式</span><b>{{ detail.contactMasked }}</b></li>
            <li><span class="dot" /><span class="grow">动物</span><b>{{ detail.animalName || '未命名' }} · {{ detail.animalCode }}</b></li>
            <li><span class="dot" /><span class="grow">方案</span><b>{{ detail.planName }} · {{ periodLabel(detail.period) }}</b></li>
            <li><span class="dot" /><span class="grow">金额</span><b>{{ money(detail.amount) }}</b></li>
            <li><span class="dot" /><span class="grow">生效日期</span><b>{{ detail.startDate || '未生效' }}</b></li>
            <li><span class="dot" /><span class="grow">结束日期</span><b>{{ detail.endDate || '未生效' }}</b></li>
            <li v-if="detail.certificateNo">
              <span class="dot" /><span class="grow">证书编号</span><b class="mono">{{ detail.certificateNo }}</b>
            </li>
          </ul>
          <div v-if="detail.status === 'PENDING_PAYMENT' && detail.payDeadline" class="notice">
            缴费截止：{{ dateTime(detail.payDeadline) }}
          </div>

          <div v-if="isAdmin" class="actions">
            <el-button
              v-if="detail.status === 'PENDING_PAYMENT'"
              type="warning"
              @click="openPayment"
            >
              缴费入账
            </el-button>
            <el-button v-if="detail.status === 'ACTIVE'" type="danger" plain @click="openTerminate">
              终止认养
            </el-button>
          </div>
        </template>
      </PanelCard>

      <PanelCard title="缴费流水">
        <el-table v-if="detail?.payments.length" :data="detail.payments" size="small">
          <el-table-column prop="paymentNo" label="流水号" min-width="140" />
          <el-table-column label="实收" width="100">
            <template #default="{ row }">{{ money(row.paidAmount) }}</template>
          </el-table-column>
          <el-table-column label="到账时间" width="145">
            <template #default="{ row }">{{ dateTime(row.paidAt) }}</template>
          </el-table-column>
          <el-table-column label="凭证" width="90">
            <template #default="{ row }">{{ row.voucherFileIds.length }} 张</template>
          </el-table-column>
          <el-table-column prop="note" label="说明" min-width="120" />
        </el-table>
        <el-empty v-else description="尚未入账；认养关系不会生效" :image-size="72" />
      </PanelCard>
    </div>

    <AdoptionPaymentDialog
      v-model="paymentVisible"
      :adoption="detail ? { adoptionNo: detail.adoptionNo, amount: detail.amount } : null"
      @success="load"
    />

    <el-dialog v-model="terminateVisible" title="终止认养关系" width="480px">
      <el-form label-width="86px">
        <el-form-item label="终止原因" required>
          <el-input
            v-model="terminateForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="不少于 5 字，例如：认养人申请终止"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="terminateVisible = false">取消</el-button>
        <el-button type="danger" :loading="terminating" @click="submitTerminate">确认终止</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.status-line {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
}
</style>
