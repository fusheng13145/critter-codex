<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as medicalApi from '@/api/medical'
import { BizError } from '@/api/http'
import PanelCard from '@/components/PanelCard.vue'
import type { VisitDetail } from '@/api/types'
import { dateTime, money } from '@/utils/format'

/** 档案详情内嵌的就诊记录：按动物编号反查，不伪造“无记录”之外的结论。 */

const props = defineProps<{ code: string }>()

const visits = ref<VisitDetail[]>([])
const loading = ref(false)
const errorText = ref('')

async function loadVisits(): Promise<void> {
  if (!props.code) return
  loading.value = true
  errorText.value = ''
  try {
    visits.value = await medicalApi.fetchAnimalVisits(props.code)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '就诊记录加载失败'
    visits.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadVisits()
})
</script>

<template>
  <PanelCard title="就诊记录" :more="loading ? '读取中…' : `共 ${visits.length} 次`">
    <div v-if="errorText" class="notice">{{ errorText }}</div>
    <div v-else-if="!loading && !visits.length" class="muted empty-line">
      暂无就诊记录。管理员登记就诊后会在此显示。
    </div>
    <div v-else class="table-wrap visit-table">
      <table class="native">
        <thead>
          <tr>
            <th>就诊编号</th>
            <th>到院时间</th>
            <th>医院</th>
            <th>诊断</th>
            <th>处置</th>
            <th>费用</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in visits" :key="item.visitNo">
            <td class="mono">{{ item.visitNo }}</td>
            <td class="muted small">{{ dateTime(item.visitedAt) }}</td>
            <td>{{ item.clinicName }}</td>
            <td>{{ item.diagnosis || item.complaint || '—' }}</td>
            <td>{{ item.treatment || '—' }}</td>
            <td class="mono">{{ money(item.cost) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="footnote" style="text-align: left">
      来源：GET /visits?animalCode=...，按到院时间倒序；无工单就诊的来源备注见医疗协作页。
    </div>
  </PanelCard>
</template>

<style scoped>
.visit-table {
  box-shadow: none;
}

.empty-line {
  padding: 12px 0;
  font-size: 12px;
}

.small {
  font-size: 11px;
}
</style>
