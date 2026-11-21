<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import StatusTag from '@/components/StatusTag.vue'
import { money } from '@/utils/format'
import { projectStatusLabel, targetTypeLabel } from '@/utils/domain'
import type { ProjectSummary } from '@/api/types'

/** 项目卡片：进度、金额与剩余天数直接取接口字段，不在前端二次计算口径。 */
const props = defineProps<{
  project: ProjectSummary
}>()

const router = useRouter()
const progress = computed(() => Math.max(0, Math.min(100, props.project.progressPercent)))
</script>

<template>
  <article class="project-card">
    <div class="project-head">
      <div class="project-title">
        <div class="project-no mono">{{ project.projectNo }}</div>
        <h2>{{ project.name }}</h2>
      </div>
      <StatusTag :code="project.status" :label="projectStatusLabel(project.status)" />
    </div>

    <div class="project-tags">
      <span class="tag gray">{{ targetTypeLabel(project.targetType) }}</span>
      <span class="tag gray">{{ project.donationCount }} 笔已入账</span>
      <span class="tag gray">剩余 {{ project.daysRemaining }} 天</span>
    </div>

    <div class="progress-line">
      <div class="progress-label">
        <span>募捐进度</span>
        <b>{{ project.progressPercent }}%</b>
      </div>
      <div class="progress">
        <i :style="{ width: `${progress}%` }" />
      </div>
    </div>

    <div class="amount-grid">
      <div class="amount">
        <span>目标</span>
        <b>{{ money(project.targetAmount) }}</b>
      </div>
      <div class="amount">
        <span>已筹</span>
        <b class="accent">{{ money(project.raisedAmount) }}</b>
      </div>
      <div class="amount">
        <span>已支出</span>
        <b>{{ money(project.spentAmount) }}</b>
      </div>
    </div>

    <div class="project-foot">
      <span class="muted date">{{ project.startDate }} → {{ project.endDate }}</span>
      <button class="btn sm" type="button" @click="router.push(`/projects/${project.projectNo}`)">
        查看项目
      </button>
    </div>
  </article>
</template>

<style scoped>
.project-card {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: 0.15s;
}

.project-card:hover {
  border-color: var(--accent-2);
}

.project-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.project-no {
  font-size: 11px;
}

.project-title h2 {
  font-size: 18px;
  margin-top: 2px;
  line-height: 1.35;
}

.project-tags {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
}

.progress-line {
  margin-top: 2px;
}

.progress-label {
  display: flex;
  justify-content: space-between;
  color: var(--muted);
  font-size: 11px;
}

.progress-label b {
  color: var(--accent);
}

.amount-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 10px 12px;
  background: var(--chip);
  border-radius: var(--radius-sm);
}

.amount {
  min-width: 0;
}

.amount span {
  display: block;
  color: var(--muted);
  font-size: 11px;
}

.amount b {
  display: block;
  margin-top: 2px;
  font-size: 13px;
  white-space: nowrap;
}

.amount b.accent {
  color: var(--accent);
}

.project-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 2px;
}

.date {
  font-size: 11px;
}
</style>
