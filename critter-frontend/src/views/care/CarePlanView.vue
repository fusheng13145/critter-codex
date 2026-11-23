<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api/carePlans'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import type { CarePlanView } from '@/api/p2types'

const auth = useAuthStore()
const isAdmin = computed(() => auth.isAdmin)

const rows = ref<CarePlanView[]>([])
const overdue = ref<CarePlanView[]>([])
const loading = ref(false)
const page = reactive({ page: 1, size: 10, total: 0 })
const filters = reactive({ animalCode: '', type: '', status: '' })

const createVisible = ref(false)
const creating = ref(false)
const form = reactive({
  animalCode: '',
  visitId: undefined as number | undefined,
  type: 'VACCINE' as 'VACCINE' | 'MEDICATION',
  itemName: '',
  dosage: '',
  planDate: '',
  note: '',
})

const completeVisible = ref(false)
const completing = ref(false)
const completeTarget = ref<CarePlanView | null>(null)
const completeForm = reactive({ result: '', note: '' })

const typeLabel = (type: string) => (type === 'VACCINE' ? '疫苗' : '用药')
const statusLabel = (status: string) =>
  status === 'PLANNED' ? '待执行' : status === 'DONE' ? '已完成' : '已取消'

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await api.fetchCarePlans({
      animalCode: filters.animalCode.trim() || undefined,
      type: filters.type || undefined,
      status: filters.status || undefined,
      page: page.page,
      size: page.size,
    })
    rows.value = data.list
    page.total = data.page.total
    overdue.value = await api.fetchOverduePlans(3)
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '计划列表加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  form.animalCode = filters.animalCode
  form.visitId = undefined
  form.type = 'VACCINE'
  form.itemName = ''
  form.dosage = ''
  form.planDate = ''
  form.note = ''
  createVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!form.itemName.trim() || !form.planDate) {
    ElMessage.warning('计划项目和计划日期必填')
    return
  }
  if (!form.animalCode.trim() && !form.visitId) {
    ElMessage.warning('动物编号或就诊记录至少填一个')
    return
  }
  creating.value = true
  try {
    await api.createCarePlan({
      animalCode: form.animalCode.trim() || undefined,
      visitId: form.visitId || undefined,
      type: form.type,
      itemName: form.itemName.trim(),
      dosage: form.dosage.trim() || undefined,
      planDate: form.planDate,
      note: form.note.trim() || undefined,
    })
    createVisible.value = false
    ElMessage.success('计划已创建，到期前三天会推送提醒')
    page.page = 1
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '计划创建失败')
  } finally {
    creating.value = false
  }
}

function openComplete(row: CarePlanView): void {
  completeTarget.value = row
  completeForm.result = ''
  completeForm.note = ''
  completeVisible.value = true
}

async function submitComplete(): Promise<void> {
  if (!completeTarget.value) return
  if (!completeForm.result.trim()) {
    ElMessage.warning('请填写执行结果')
    return
  }
  completing.value = true
  try {
    await api.completeCarePlan(completeTarget.value.id, {
      result: completeForm.result.trim(),
      note: completeForm.note.trim() || undefined,
    })
    completeVisible.value = false
    ElMessage.success('计划已标记完成')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '计划完成失败')
  } finally {
    completing.value = false
  }
}

async function cancelPlan(row: CarePlanView): Promise<void> {
  try {
    await api.cancelCarePlan(row.id, '管理员取消')
    ElMessage.success('计划已取消')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '取消失败')
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
      title="疫苗与用药计划"
      desc="计划独立于健康记录：有到期日、执行结果与状态。逾期未执行标红，并在列表顶部固定展示三项。"
    >
      <template #actions>
        <button v-if="isAdmin" class="btn" type="button" @click="openCreate">新建计划</button>
      </template>
    </PageHero>

    <div v-if="overdue.length" class="overdue-strip">
      <div class="overdue-title">逾期未执行 · 置顶 {{ overdue.length }} 项</div>
      <div class="overdue-list">
        <div v-for="item in overdue" :key="item.id" class="overdue-item">
          <span class="tag danger">{{ typeLabel(item.type) }}</span>
          <span class="mono">{{ item.animalCode }}</span>
          <span>{{ item.itemName }}</span>
          <span class="date">{{ item.planDate }} · 逾期</span>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input
          v-model="filters.animalCode"
          placeholder="动物编号"
          clearable
          style="width: 150px"
          @change="page.page = 1; load()"
        />
        <el-select v-model="filters.type" style="width: 120px" @change="page.page = 1; load()">
          <el-option label="全部类型" value="" />
          <el-option label="疫苗" value="VACCINE" />
          <el-option label="用药" value="MEDICATION" />
        </el-select>
        <el-select v-model="filters.status" style="width: 130px" @change="page.page = 1; load()">
          <el-option label="全部状态" value="" />
          <el-option label="待执行" value="PLANNED" />
          <el-option label="已完成" value="DONE" />
          <el-option label="已取消" value="CANCELED" />
        </el-select>
        <span class="muted">共 {{ page.total }} 条计划</span>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="planNo" label="计划号" min-width="140" />
        <el-table-column label="动物" width="110">
          <template #default="{ row }">{{ row.animalCode }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.type === 'VACCINE' ? 'success' : 'warning'" effect="light">
              {{ typeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="itemName" label="项目" min-width="130" show-overflow-tooltip />
        <el-table-column prop="dosage" label="剂量" width="100" />
        <el-table-column label="计划日期" width="120">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.overdue }">{{ row.planDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'DONE' ? 'success' : row.overdue ? 'danger' : 'info'">
              {{ row.overdue ? '已逾期' : statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="执行结果" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <template v-if="isAdmin && row.status === 'PLANNED'">
              <el-button size="small" type="primary" @click="openComplete(row)">完成</el-button>
              <el-button size="small" @click="cancelPlan(row)">取消</el-button>
            </template>
            <span v-else class="muted">—</span>
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

    <el-dialog v-model="createVisible" title="新建疫苗 / 用药计划" width="520px">
      <div class="form-grid">
        <label>动物编号</label>
        <el-input v-model="form.animalCode" placeholder="CAT-2026-031" />
        <label>来源就诊 ID</label>
        <el-input-number v-model="form.visitId" :min="1" :controls="false" style="width: 100%" />
        <label>类型</label>
        <el-radio-group v-model="form.type">
          <el-radio value="VACCINE">疫苗</el-radio>
          <el-radio value="MEDICATION">用药</el-radio>
        </el-radio-group>
        <label>项目</label>
        <el-input v-model="form.itemName" maxlength="64" placeholder="如 狂犬疫苗 / 驱虫药" />
        <label>剂量</label>
        <el-input v-model="form.dosage" maxlength="64" placeholder="如 1 支 / 0.5 ml" />
        <label>计划日期</label>
        <el-date-picker
          v-model="form.planDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          style="width: 100%"
        />
        <label>备注</label>
        <el-input v-model="form.note" type="textarea" :rows="3" maxlength="255" />
      </div>
      <div class="notice" style="margin-top: 12px">
        填就诊 ID 时，后端会按该次就诊的动物自动补全动物编号；两者不一致会返回参数错误。
      </div>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建计划</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="completeVisible" title="标记计划完成" width="440px">
      <div class="form-grid">
        <label>执行结果</label>
        <el-input v-model="completeForm.result" maxlength="255" placeholder="如 已完成接种，无不良反应" />
        <label>备注</label>
        <el-input v-model="completeForm.note" type="textarea" :rows="3" maxlength="255" />
      </div>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="completing" @click="submitComplete">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.overdue-strip {
  border: 1px solid #f3b7b7;
  background: #fff7f7;
  border-radius: 12px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.overdue-title {
  color: var(--danger);
  font-weight: 700;
  font-size: 13px;
  margin-bottom: 8px;
}
.overdue-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
}
.overdue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.overdue-item .date {
  color: var(--danger);
}
.form-grid {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 12px;
  align-items: center;
}
.text-danger {
  color: var(--danger);
  font-weight: 600;
}
</style>
