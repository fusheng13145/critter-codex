<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import ReportForm from '@/views/rescue/ReportForm.vue'
import ReportQueue from '@/views/rescue/ReportQueue.vue'
import type { PointView } from '@/api/types'

/**
 * P06 上报入口。
 * 学生只看到“提交上报”；管理员多一个“上报处理”页签，避免另开第三个路由。
 */

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const points = ref<PointView[]>([])
const pointsError = ref('')

const activeTab = computed(() => (auth.isAdmin && route.query.tab === 'queue' ? 'queue' : 'form'))

async function loadPoints(): Promise<void> {
  try {
    points.value = await archiveApi.fetchPoints()
  } catch (error) {
    pointsError.value = error instanceof BizError ? error.message : '点位加载失败，无法选点'
    points.value = []
  }
}

function switchTab(tab: 'form' | 'queue'): void {
  router.replace({ path: '/report', query: tab === 'queue' ? { tab: 'queue' } : {} })
}

onMounted(loadPoints)
</script>

<template>
  <div>
    <PageHero
      title="救助上报"
      desc="上报进入待受理队列，不等于已受理；受理后生成工单，工单流转与上报状态分开记录。"
    >
      <template #actions>
        <button class="btn ghost" type="button" @click="router.push('/report/mine')">我的上报</button>
        <button v-if="auth.isAdmin" class="btn ghost" type="button" @click="router.push('/orders')">工单看板</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '我要上报', path: '/report' },
        { label: '我的上报', path: '/report/mine' },
        { label: '工单看板', path: '/orders', adminOnly: true },
      ]"
    />

    <div v-if="auth.isAdmin" class="mode-switch">
      <button
        class="mode"
        :class="{ on: activeTab === 'form' }"
        type="button"
        @click="switchTab('form')"
      >
        提交上报
      </button>
      <button
        class="mode"
        :class="{ on: activeTab === 'queue' }"
        type="button"
        @click="switchTab('queue')"
      >
        上报处理
      </button>
    </div>

    <div v-if="pointsError" class="notice">{{ pointsError }}</div>

    <ReportQueue v-if="activeTab === 'queue'" />
    <ReportForm v-else :points="points" />

    <div class="footnote" style="text-align: left">
      接口：POST /api/v1/reports（登录）、GET /api/v1/reports（管理员）。
      五类枚举与点位来自后端字典/基础数据，页面不硬编码业务状态。
    </div>
  </div>
</template>

<style scoped>
.mode-switch {
  display: flex;
  gap: 4px;
  margin-bottom: 14px;
  padding: 4px;
  background: var(--chip);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  width: fit-content;
}

.mode {
  border: 0;
  background: transparent;
  padding: 7px 16px;
  border-radius: 6px;
  color: var(--muted);
  cursor: pointer;
  font-size: 12px;
}

.mode.on {
  background: var(--panel);
  color: var(--accent);
  font-weight: 600;
  box-shadow: var(--shadow);
}
</style>
