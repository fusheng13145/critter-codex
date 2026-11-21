<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as adoptionsApi from '@/api/adoptions'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import PanelCard from '@/components/PanelCard.vue'
import type { AdoptionPlan, AdoptionPlanPayload } from '@/api/types'
import { money, periodLabel } from '@/utils/format'

/** 认养方案配置：POST /plans、PUT /plans/{id}，仅管理员可见。 */

const auth = useAuthStore()
const router = useRouter()

const rows = ref<AdoptionPlan[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  planName: '',
  period: 'MONTH' as 'MONTH' | 'QUARTER',
  amountYuan: 30,
  benefits: '',
  enabled: true,
})

async function load(): Promise<void> {
  loading.value = true
  try {
    rows.value = await adoptionsApi.fetchPlans(true)
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '方案加载失败')
    rows.value = []
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  form.planName = ''
  form.period = 'MONTH'
  form.amountYuan = 30
  form.benefits = ''
  form.enabled = true
  dialogVisible.value = true
}

function openEdit(plan: AdoptionPlan): void {
  editingId.value = plan.planId
  form.planName = plan.planName
  form.period = plan.period
  form.amountYuan = plan.amount / 100
  form.benefits = plan.benefits ?? ''
  form.enabled = plan.enabled
  dialogVisible.value = true
}

function payload(enabled = form.enabled): AdoptionPlanPayload {
  return {
    planName: form.planName.trim(),
    period: form.period,
    amount: Math.round(form.amountYuan * 100),
    benefits: form.benefits.trim() || null,
    enabled,
  }
}

async function save(): Promise<void> {
  if (!form.planName.trim()) {
    ElMessage.warning('请填写方案名称')
    return
  }
  if (!(form.amountYuan > 0)) {
    ElMessage.warning('金额必须大于 0')
    return
  }
  saving.value = true
  try {
    if (editingId.value === null) {
      await adoptionsApi.createPlan(payload())
      ElMessage.success('方案已新增')
    } else {
      await adoptionsApi.updatePlan(editingId.value, payload())
      ElMessage.success('方案已保存')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '方案保存失败')
  } finally {
    saving.value = false
  }
}

async function toggle(plan: AdoptionPlan): Promise<void> {
  try {
    await adoptionsApi.updatePlan(plan.planId, {
      planName: plan.planName,
      period: plan.period,
      amount: plan.amount,
      benefits: plan.benefits,
      enabled: !plan.enabled,
    })
    ElMessage.success(plan.enabled ? '方案已停用' : '方案已启用')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '启停失败')
  }
}

onMounted(() => {
  if (!auth.isAdmin) {
    ElMessage.warning('仅管理员可配置认养方案')
    router.replace({ name: 'adopt' })
    return
  }
  load()
})
</script>

<template>
  <PageHero title="认养方案配置" desc="维护按月/按季方案的金额与权益；停用后公开认养页不再展示，历史认养单不受影响。">
    <template #actions>
      <button class="btn ghost" type="button" @click="router.push({ name: 'adopt' })">返回认养专区</button>
      <button class="btn" type="button" @click="openCreate">新增方案</button>
    </template>
  </PageHero>

  <PanelCard title="方案列表" :more="`共 ${rows.length} 个`">
    <div v-if="loading" class="muted">加载中…</div>
    <div v-else-if="!rows.length" class="muted">还没有方案，先新增一个月度方案</div>
    <el-table v-else :data="rows" stripe>
      <el-table-column prop="planName" label="方案名称" min-width="150" />
      <el-table-column label="周期" width="90">
        <template #default="{ row }">{{ periodLabel(row.period) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="110">
        <template #default="{ row }">{{ money(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="benefits" label="权益" min-width="220">
        <template #default="{ row }">{{ row.benefits || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <span class="tag" :class="row.enabled ? 'success' : 'gray'">
            {{ row.enabled ? '启用' : '停用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggle(row)">
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="footnote" style="text-align: left">
      金额按分存储；页面输入的是元。POST /plans 新增，PUT /plans/{id} 修改或启停。
    </div>
  </PanelCard>

  <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增认养方案' : '编辑认养方案'" width="520px">
    <el-form label-width="88px">
      <el-form-item label="方案名称">
        <el-input v-model="form.planName" maxlength="32" show-word-limit placeholder="例如：校园月度守护" />
      </el-form-item>
      <el-form-item label="周期">
        <el-radio-group v-model="form.period">
          <el-radio-button value="MONTH">按月</el-radio-button>
          <el-radio-button value="QUARTER">按季</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="金额（元）">
        <el-input-number v-model="form.amountYuan" :min="0.01" :precision="2" :step="10" style="width: 180px" />
      </el-form-item>
      <el-form-item label="权益说明">
        <el-input v-model="form.benefits" type="textarea" :rows="3" maxlength="255" show-word-limit />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" />
        <span class="muted" style="margin-left: 10px">停用后公开列表不再展示</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <button class="btn ghost" type="button" @click="dialogVisible = false">取消</button>
      <button class="btn" type="button" :disabled="saving" @click="save">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </template>
  </el-dialog>
</template>
