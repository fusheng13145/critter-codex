<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHero from '@/components/PageHero.vue'
import PanelCard from '@/components/PanelCard.vue'
import { menuTitle } from '@/utils/menus'

/**
 * B/C 页面的统一占位页。
 *
 * 为什么不是空白页：路由、侧栏、面包屑、权限与配色都是共享资产，
 * 先落占位能保证三人并行开发时"点哪都不 404、风格不变形"；
 * B/C 接手时只把它们指向自己的视图组件，路由与权限不用改。
 */

const route = useRoute()
const router = useRouter()

const HINTS: Record<string, { domain: string; endpoints: string[]; note: string }> = {
  archive: {
    domain: '档案与救助域（D2）',
    endpoints: [
      'GET /animals · 列表筛选（keyword/status/pointId/neutered/adopted）',
      'POST /animals · 建档（编号由服务端生成，前端不许传 code）',
      'GET /animals/{code} · 详情（按角色裁剪字段）',
      'PUT /animals/{code}/status · 状态迁移（非法时回 allowed 列表）',
    ],
    note: '列表默认按 updatedAt 倒序；状态按钮请用接口返回的 allowed 决定禁用，不要在前端硬编码迁移表。',
  },
  report: {
    domain: '档案与救助域（D2）',
    endpoints: [
      'POST /reports · 提交上报（点位选点 + 至少 1 张照片）',
      'GET /reports · 上报列表（按状态与时限）',
      'POST /reports/{id}/accept · 受理转工单',
      'GET /orders · 工单看板（待受理、超时高亮）',
    ],
    note: '同一点位 24 小时内重复上报会返回 duplicated 与可合并单号；照片先用 POST /files 拿 fileId。',
  },
  fund: {
    domain: '档案与资金域（D2）',
    endpoints: [
      'GET /projects · 项目列表（含进度）',
      'GET /projects/{no} · 项目详情与捐赠流水（脱敏）',
      'POST /donations · 捐赠登记（登记 ≠ 入账）',
      'POST /expenses · 支出登记（余额行锁）',
      'GET /ledger · 台账（按项目与月份）',
    ],
    note: '金额字段一律整数分（Amount 结尾）；支出超 500 元需双签，复核人不得为登记人。',
  },
  adopt: {
    domain: '认养与医疗互动域（D3）',
    endpoints: [
      'GET /plans · 认养方案',
      'POST /adoptions · 提交认养申请',
      'GET /adoptions/mine · 我的认养',
      'PUT /adoptions/{no}/audit · 审核（转待缴费）',
      'PUT /adoptions/{no}/payment · 缴费入账（关系生效）',
    ],
    note: '审核通过只是"待缴费"，ACTIVE 只能由入账动作触发；同一动物唯一生效关系由数据库唯一索引兜底。',
  },
  medical: {
    domain: '认养与医疗互动域（D3）',
    endpoints: [
      'GET /visits/{no} · 就诊详情',
      'POST /visits · 登记就诊',
      'GET /tnr · TNR 看板',
      'PUT /tnr/{no}/node · 推进节点（不可跳节点）',
    ],
    note: '绝育任务缺术前照片返回 30601；工单状态非法返回 30602。',
  },
  volunteer: {
    domain: '认养与医疗互动域（D3）',
    endpoints: [
      'GET /tasks · 任务大厅',
      'POST /tasks · 发布任务',
      'POST /tasks/{id}/join · 报名接单',
      'POST /tasks/{id}/checkin · 到场打卡（照片）',
      'GET /tasks/mine · 我的任务与服务时长',
    ],
    note: '名额满后第二人报名应失败；打卡照片同样走两步上传。',
  },
  content: {
    domain: '认养与医疗互动域（D3）',
    endpoints: [
      'GET /notices · 公告列表',
      'GET /messages · 消息中心（四类）',
      'PUT /messages/read · 标记已读（支持批量）',
      'GET /messages/unread · 未读分组',
    ],
    note: '消息保留 180 天；四类消息：认养到期、募捐进度、工单进展、审批结果。',
  },
}

const menuCode = computed(() => (route.meta.menu as string | undefined) ?? '')
const owner = computed(() => (route.meta.owner as string | undefined) ?? 'B')
const hint = computed(() => HINTS[menuCode.value])
const title = computed(() => menuTitle(menuCode.value))
</script>

<template>
  <div>
    <PageHero :title="title" :desc="`该页面组由成员 ${owner} 负责实现；路由、菜单权限与视觉风格已经就位，接上视图即可。`">
      <template #actions>
        <button class="btn ghost" type="button" @click="router.push('/')">回工作台</button>
      </template>
    </PageHero>

    <div class="grid2">
      <PanelCard title="对接契约" :more="hint?.domain ?? '见接口设计说明书'">
        <ul class="list">
          <li v-for="endpoint in hint?.endpoints ?? []" :key="endpoint">
            <span class="dot" />
            <span class="grow mono" style="color: var(--ink)">{{ endpoint }}</span>
          </li>
        </ul>
        <div class="notice" style="margin-top: 12px">{{ hint?.note }}</div>
      </PanelCard>

      <PanelCard title="共享资产（已由 A 就绪）">
        <ul class="list">
          <li><span class="dot" /><span class="grow">设计令牌：<span class="mono">src/styles/theme.css</span>（颜色/圆角/阴影/卡片/表格/标签）</span></li>
          <li><span class="dot gray" /><span class="grow">请求层：<span class="mono">src/api/http.ts</span>（统一解包、401 静默刷新、BizError）</span></li>
          <li><span class="dot gray" /><span class="grow">会话与菜单：<span class="mono">useAuthStore()</span>，菜单由后端下发</span></li>
          <li><span class="dot gray" /><span class="grow">鉴权：<span class="mono">Authorization: Bearer</span> 自动注入，匿名接口无需处理</span></li>
          <li><span class="dot gray" /><span class="grow">字典：<span class="mono">fetchDicts(['ANIMAL_COLOR', ...])</span>，表单下拉直接用</span></li>
          <li><span class="dot gray" /><span class="grow">附件：<span class="mono">uploadFile(file, bizType)</span> 返回 fileId 与 contentUrl</span></li>
        </ul>
        <div class="footnote" style="text-align: left">
          约定：页面不要自己写 axios，不要写死菜单；接口错误统一 `error instanceof BizError` 后按 code 提示。
        </div>
      </PanelCard>
    </div>
  </div>
</template>
