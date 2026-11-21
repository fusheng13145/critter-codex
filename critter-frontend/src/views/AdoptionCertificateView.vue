<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as adoptionsApi from '@/api/adoptions'
import { BizError } from '@/api/http'
import type { AdoptionCertificate } from '@/api/types'
import { dateOnly } from '@/utils/format'

/** 电子认养证书：服务端签发证书号，页面负责可打印排版；浏览器“打印 → 另存为 PDF”。 */

const route = useRoute()
const router = useRouter()
const no = computed(() => String(route.params.no ?? ''))
const certificate = ref<AdoptionCertificate | null>(null)
const errorText = ref('')

async function load(): Promise<void> {
  try {
    certificate.value = await adoptionsApi.fetchAdoptionCertificate(no.value)
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '证书加载失败'
    ElMessage.error(errorText.value)
  }
}

onMounted(load)

function printCertificate(): void {
  window.print()
}
</script>

<template>
  <div class="certificate-page">
    <div class="cert-actions">
      <button class="btn ghost" type="button" @click="router.push(`/adopt/${no}`)">返回详情</button>
      <button class="btn" type="button" :disabled="!certificate" @click="printCertificate">打印 / 保存为 PDF</button>
    </div>

    <div v-if="errorText" class="card">
      <el-empty :description="errorText" />
    </div>

    <article v-else-if="certificate" class="certificate-sheet">
      <div class="corner top-left">青</div>
      <div class="corner bottom-right">檐</div>
      <header>
        <p class="org">江苏科技大学 · 青檐万灵册校园生物档案与救助平台</p>
        <h1>电 子 认 养 证 书</h1>
        <p class="en">CAMPUS ADOPTION CERTIFICATE</p>
      </header>

      <section class="body">
        <p>
          兹证明 <b>{{ certificate.adopterName }}</b> 于
          <b>{{ dateOnly(certificate.startDate) }}</b> 起，通过“青檐万灵册”认养
          <b>{{ certificate.animalName || '未命名' }}</b>（编号 <b>{{ certificate.animalCode }}</b>），
          认养方案为 <b>{{ certificate.planName }}</b>，有效期至
          <b>{{ dateOnly(certificate.endDate) }}</b>。
        </p>
        <p>
          愿每一次守护，都被温柔记录。本证书用于纪念人与校园生灵之间的照护关系，
          不构成动物所有权或交易凭证。
        </p>
      </section>

      <section class="meta">
        <div>
          <span>认养单号</span>
          <b>{{ certificate.adoptionNo }}</b>
        </div>
        <div>
          <span>证书编号</span>
          <b>{{ certificate.certificateNo }}</b>
        </div>
        <div>
          <span>签发日期</span>
          <b>{{ dateOnly(certificate.startDate) }}</b>
        </div>
      </section>

      <footer>
        <div class="seal">青檐<br />万灵册</div>
        <div class="signature">
          <p>青檐万灵册 · 认养管理专用章</p>
          <small>本证书可凭证书编号在系统内查询核验</small>
        </div>
      </footer>
    </article>
  </div>
</template>

<style scoped>
.certificate-page {
  max-width: 980px;
  margin: 0 auto;
}

.cert-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 16px;
}

.certificate-sheet {
  position: relative;
  overflow: hidden;
  min-height: 680px;
  padding: 64px 76px;
  background:
    radial-gradient(circle at 12% 10%, rgba(122, 169, 143, 0.16), transparent 32%),
    radial-gradient(circle at 90% 90%, rgba(47, 107, 87, 0.12), transparent 30%),
    #fcfdf9;
  border: 1px solid #c9d8cf;
  box-shadow: 0 14px 40px rgba(42, 80, 65, 0.12);
}

.certificate-sheet::before {
  content: '';
  position: absolute;
  inset: 18px;
  border: 2px solid #2f6b57;
  pointer-events: none;
}

.certificate-sheet::after {
  content: '';
  position: absolute;
  inset: 24px;
  border: 1px solid #a8c4b5;
  pointer-events: none;
}

header {
  text-align: center;
  position: relative;
  z-index: 1;
}

.org {
  color: #6d7f78;
  font-size: 12px;
  letter-spacing: 1px;
}

h1 {
  margin-top: 26px;
  font-size: 38px;
  letter-spacing: 12px;
  color: #244b3d;
}

.en {
  margin-top: 8px;
  font-size: 11px;
  letter-spacing: 4px;
  color: #8aa297;
}

.body {
  position: relative;
  z-index: 1;
  margin-top: 54px;
  font-size: 18px;
  line-height: 2.2;
  color: #31463d;
  text-indent: 2em;
}

.body b {
  color: #244b3d;
  border-bottom: 1px solid #9dbbae;
  padding: 0 4px;
}

.meta {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 54px;
  padding-top: 20px;
  border-top: 1px dashed #b9cec3;
}

.meta div {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta span {
  color: #7b8e85;
  font-size: 12px;
}

.meta b {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 13px;
  color: #2f6b57;
}

footer {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: 58px;
}

.seal {
  width: 112px;
  height: 112px;
  border: 4px double #a5423a;
  border-radius: 50%;
  color: #a5423a;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  line-height: 1.5;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 3px;
  transform: rotate(-8deg);
  opacity: 0.86;
}

.signature {
  text-align: right;
  color: #52685e;
}

.signature small {
  color: #8a9b93;
}

.corner {
  position: absolute;
  width: 54px;
  height: 54px;
  color: #2f6b57;
  opacity: 0.35;
  font-size: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.top-left {
  top: 28px;
  left: 30px;
}

.bottom-right {
  right: 30px;
  bottom: 28px;
}

@media print {
  :global(.sidebar),
  :global(.topbar) {
    display: none !important;
  }

  :global(.app),
  :global(.main),
  :global(.content) {
    display: block !important;
    background: #fff !important;
  }

  .certificate-page {
    max-width: none;
    margin: 0;
  }

  .cert-actions {
    display: none;
  }

  .certificate-sheet {
    min-height: 0;
    box-shadow: none;
    border: 0;
  }
}
</style>
