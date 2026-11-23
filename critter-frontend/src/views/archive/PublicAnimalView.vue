<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import AuthImage from '@/components/AuthImage.vue'
import { categoryLabel, fileIdFromUrl, genderLabel, animalStatusLabel } from '@/utils/domain'
import { dateOnly } from '@/utils/format'
import type { AnimalDetail } from '@/api/types'

/**
 * P02 扫码公开页：/p/:code。无需登录，只展示公开档案字段；
 * 旧编号会由后端解析到主档，这里提示已合并。
 */
const route = useRoute()
const code = computed(() => String(route.params.code ?? ''))
const animal = ref<AnimalDetail | null>(null)
const loading = ref(true)
const errorText = ref('')

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    animal.value = await archiveApi.fetchAnimal(code.value)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '公开档案加载失败'
    animal.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="public-shell">
    <header class="public-head">
      <div class="public-brand">
        <span class="public-mark">青</span>
        <div>
          <b>青檐万灵册</b>
          <small>校园生物档案与救助平台</small>
        </div>
      </div>
      <router-link class="public-link" to="/">进入平台</router-link>
    </header>

    <main class="public-main">
      <div v-if="loading" class="public-state">正在读取档案…</div>
      <div v-else-if="errorText" class="public-state error">{{ errorText }}</div>
      <article v-else-if="animal" class="public-card">
        <div class="public-cover">
          <AuthImage :file-id="fileIdFromUrl(animal.coverUrl)" :alt="animal.name ?? animal.code" fit="cover" />
        </div>
        <div class="public-body">
          <div v-if="code !== animal.code" class="public-notice">
            编号 <span class="mono">{{ code }}</span> 已并入主档案 <span class="mono">{{ animal.code }}</span>。
          </div>
          <p class="public-kicker">CAMPUS CRITTER · 公开档案</p>
          <h1>{{ animal.name || '未命名个体' }}</h1>
          <div class="public-tags">
            <span>{{ animalStatusLabel(animal.status) }}</span>
            <span>{{ categoryLabel(animal.category) }}</span>
            <span>{{ genderLabel(animal.gender) }}</span>
            <span>{{ animal.color }}</span>
            <span v-if="animal.neutered">已绝育</span>
            <span v-if="animal.adopted">已认养</span>
          </div>
          <div class="public-meta">
            <div><span>档案编号</span><b class="mono">{{ animal.code }}</b></div>
            <div><span>常驻点位</span><b>{{ animal.point?.name ?? '待补充' }}</b></div>
            <div><span>估计年龄</span><b>{{ animal.estimatedAge !== null ? `${animal.estimatedAge} 岁` : '未知' }}</b></div>
            <div><span>建档日期</span><b>{{ dateOnly(animal.createdAt) }}</b></div>
          </div>
          <p class="public-features">{{ animal.features }}</p>
          <div v-if="animal.mergedCodes.length" class="public-merged">
            已并入旧编号：
            <span v-for="oldCode in animal.mergedCodes" :key="oldCode" class="mono">{{ oldCode }}</span>
          </div>
        </div>
      </article>
      <p class="public-foot">扫码查看 · 档案由校园志愿者与管理员共同维护</p>
    </main>
  </div>
</template>

<style scoped>
.public-shell {
  min-height: 100vh;
  background: radial-gradient(circle at 12% 10%, #e8f1ec 0, #f4f2ea 44%, #eef4f3 100%);
  color: #1f3338;
}

.public-head {
  max-width: 920px;
  margin: 0 auto;
  padding: 24px 22px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.public-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.public-brand div {
  display: flex;
  flex-direction: column;
}

.public-brand small {
  color: #6b7f83;
  font-size: 11px;
}

.public-mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #2d5a4e;
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
}

.public-link {
  font-size: 12px;
  color: #2d5a4e;
  border-bottom: 1px solid rgba(45, 90, 78, 0.3);
}

.public-main {
  max-width: 920px;
  margin: 0 auto;
  padding: 34px 22px 48px;
}

.public-state {
  padding: 60px 0;
  text-align: center;
  color: #6b7f83;
}

.public-state.error {
  color: #a54a3a;
}

.public-card {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 0;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(45, 90, 78, 0.13);
  border-radius: 22px;
  overflow: hidden;
  box-shadow: 0 24px 60px rgba(38, 70, 61, 0.13);
  backdrop-filter: blur(8px);
}

.public-cover {
  min-height: 360px;
  background: #e8eee9;
}

.public-body {
  padding: 34px 32px;
}

.public-kicker {
  margin: 0 0 8px;
  color: #2d5a4e;
  font-size: 11px;
  letter-spacing: 0.16em;
}

.public-body h1 {
  margin: 0 0 14px;
  font-size: 30px;
  letter-spacing: 0.02em;
}

.public-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 22px;
}

.public-tags span {
  padding: 4px 10px;
  border-radius: 999px;
  background: #edf3ee;
  color: #2d5a4e;
  font-size: 12px;
}

.public-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 18px;
  padding: 16px 0;
  border-top: 1px dashed rgba(45, 90, 78, 0.2);
  border-bottom: 1px dashed rgba(45, 90, 78, 0.2);
}

.public-meta div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 12px;
}

.public-meta span {
  color: #708286;
}

.public-meta b {
  font-size: 13px;
  font-weight: 600;
}

.public-features {
  margin: 18px 0 0;
  font-size: 14px;
  line-height: 1.9;
  color: #33484d;
}

.public-notice {
  margin-bottom: 16px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f4efe2;
  color: #7a6538;
  font-size: 12px;
}

.public-merged {
  margin-top: 16px;
  color: #708286;
  font-size: 12px;
}

.public-merged span {
  margin-left: 6px;
}

.public-foot {
  margin-top: 22px;
  text-align: center;
  color: #8a999b;
  font-size: 11px;
  letter-spacing: 0.08em;
}

@media (max-width: 760px) {
  .public-card {
    grid-template-columns: 1fr;
  }

  .public-cover {
    min-height: 240px;
  }

  .public-body {
    padding: 24px 20px;
  }

  .public-body h1 {
    font-size: 24px;
  }

  .public-meta {
    grid-template-columns: 1fr;
  }
}
</style>
