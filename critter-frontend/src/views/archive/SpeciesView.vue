<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PageState from '@/components/PageState.vue'
import PanelCard from '@/components/PanelCard.vue'
import { dateOnly } from '@/utils/format'
import { SPECIES_CATEGORY_OPTIONS, groupStatusLabel, occurrenceScaleLabel } from '@/utils/domain'
import type {
  GroupSummary,
  OccurrenceView,
  SpeciesView as SpeciesItem,
} from '@/api/types'

/**
 * P02 物种名录。
 * 物种接口返回全量名录；详情抽屉再按 speciesId 拉关联群体与出现记录，
 * 不在前端根据名称做任何保护等级推断。
 */

const query = reactive({ keyword: '', category: '' })
const species = ref<SpeciesItem[]>([])
const loading = ref(false)
const errorText = ref('')

const drawerVisible = ref(false)
const selected = ref<SpeciesItem | null>(null)
const groups = ref<GroupSummary[]>([])
const occurrences = ref<OccurrenceView[]>([])
const relationLoading = ref(false)
const relationError = ref('')

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    species.value = await archiveApi.fetchSpecies({
      keyword: query.keyword.trim() || undefined,
      category: query.category || undefined,
    })
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '物种名录加载失败'
  } finally {
    loading.value = false
  }
}

async function openSpecies(item: SpeciesItem): Promise<void> {
  selected.value = item
  drawerVisible.value = true
  groups.value = []
  occurrences.value = []
  relationError.value = ''
  relationLoading.value = true
  try {
    const [groupPage, occurrencePage] = await Promise.all([
      archiveApi.fetchGroups({ speciesId: item.id, page: 1, size: 10 }),
      archiveApi.fetchOccurrences({ speciesId: item.id, page: 1, size: 10 }),
    ])
    groups.value = groupPage.list
    occurrences.value = occurrencePage.list
  } catch (error) {
    relationError.value = error instanceof BizError ? error.message : '关联记录加载失败'
  } finally {
    relationLoading.value = false
  }
}

function reset(): void {
  query.keyword = ''
  query.category = ''
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero
      title="物种名录"
      desc="保护级别只展示名录登记值，不做保护等级推断；群体与出现记录按 speciesId 关联。"
    >
      <template #actions>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新名录</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '个体档案', path: '/animals', matchPrefix: true },
        { label: '群体档案', path: '/groups' },
        { label: '物种名录', path: '/species' },
      ]"
    />

    <div class="toolbar">
      <input
        v-model="query.keyword"
        class="input grow-input"
        placeholder="中文名 / 学名"
        @keyup.enter="load"
      />
      <select v-model="query.category" class="select">
        <option value="">全部类群</option>
        <option v-for="item in SPECIES_CATEGORY_OPTIONS" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
      <button class="btn sm" type="button" @click="load">检索</button>
      <button class="btn sm ghost" type="button" @click="reset">重置</button>
      <span class="muted result-count">共 {{ species.length }} 种</span>
    </div>

    <PageState :loading="loading" :error="errorText" :empty="!species.length" empty-text="没有匹配的物种">
      <div class="species-grid">
        <button
          v-for="item in species"
          :key="item.id"
          class="species-card"
          type="button"
          @click="openSpecies(item)"
        >
          <div class="species-top">
            <div>
              <div class="species-name">{{ item.commonName }}</div>
              <div class="species-scientific">{{ item.scientificName }}</div>
            </div>
            <span class="tag gray">{{ item.category }}</span>
          </div>
          <div class="species-meta">
            <span class="muted">保护级别</span>
            <b>{{ item.protectionLevel }}</b>
          </div>
          <p class="species-note">{{ item.note || '暂无备注' }}</p>
          <div class="species-foot">
            <span class="mono">speciesId · {{ item.id }}</span>
            <span class="link-text">查看关联记录 →</span>
          </div>
        </button>
      </div>
    </PageState>

    <el-drawer v-model="drawerVisible" title="物种关联记录" size="560px" append-to-body>
      <div v-if="selected" class="drawer-head">
        <div>
          <h2>{{ selected.commonName }}</h2>
          <div class="scientific">{{ selected.scientificName }}</div>
        </div>
        <div class="tag-row">
          <span class="tag gray">{{ selected.category }}</span>
          <span class="tag">{{ selected.protectionLevel }}</span>
        </div>
      </div>

      <div v-if="selected?.note" class="notice" style="margin-bottom: 16px">{{ selected.note }}</div>

      <PageState
        :loading="relationLoading"
        :error="relationError"
        empty-text="暂无关联群体与出现记录"
      >
        <PanelCard title="关联群体" :more="`${groups.length} 个`">
          <div v-if="!groups.length" class="muted empty-line">暂无按该物种登记的群体</div>
          <ul v-else class="list">
            <li v-for="group in groups" :key="group.groupCode">
              <span class="dot" />
              <span class="grow">
                <span class="mono">{{ group.groupCode }}</span>
                <span class="group-feature">{{ group.features }}</span>
              </span>
              <span class="tag gray">{{ group.minSize }}—{{ group.maxSize }}</span>
              <span class="tag" :class="{ gray: group.status !== 'ACTIVE' }">
                {{ groupStatusLabel(group.status, group.statusLabel) }}
              </span>
            </li>
          </ul>
        </PanelCard>

        <PanelCard title="最近出现记录" :more="`${occurrences.length} 条`">
          <div v-if="!occurrences.length" class="muted empty-line">暂无出现记录</div>
          <ul v-else class="list">
            <li v-for="occurrence in occurrences" :key="occurrence.id">
              <span class="dot gray" />
              <span class="grow">
                <b>{{ occurrence.point?.name ?? '点位未记录' }}</b>
                <span class="muted"> · {{ occurrence.habitat || '生境未填写' }}</span>
              </span>
              <span class="tag gray">{{ occurrenceScaleLabel(occurrence.scale) }}</span>
              <span class="time">{{ dateOnly(occurrence.occurredAt) }}</span>
            </li>
          </ul>
        </PanelCard>

        <div class="footnote" style="text-align: left">
          关联条件：GET /groups?speciesId=… 与 GET /occurrences?speciesId=…，均只读。
        </div>
      </PageState>
    </el-drawer>
  </div>
</template>

<style scoped>
.result-count {
  margin-left: auto;
  font-size: 12px;
}

.species-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.species-card {
  text-align: left;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--panel);
  box-shadow: var(--shadow);
  padding: 16px 18px;
  cursor: pointer;
  transition: 0.15s;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.species-card:hover {
  border-color: var(--accent-2);
  transform: translateY(-2px);
}

.species-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.species-name {
  font-size: 17px;
  font-weight: 600;
}

.species-scientific {
  margin-top: 2px;
  color: var(--muted);
  font-size: 11px;
  font-style: italic;
}

.species-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
}

.species-meta b {
  color: var(--accent);
}

.species-note {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
  min-height: 38px;
}

.species-foot {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  font-size: 11px;
}

.link-text {
  color: var(--accent);
}

.drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}

.drawer-head h2 {
  font-size: 24px;
}

.scientific {
  color: var(--muted);
  font-size: 12px;
  font-style: italic;
}

.tag-row {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
}

.group-feature {
  margin-left: 8px;
  color: var(--muted);
  font-size: 12px;
}

.empty-line {
  padding: 10px 0;
  font-size: 12px;
}

@media (max-width: 1200px) {
  .species-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 700px) {
  .species-grid {
    grid-template-columns: 1fr;
  }
}
</style>
