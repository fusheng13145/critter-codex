<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as archiveApi from '@/api/archive'
import { BizError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import PageHero from '@/components/PageHero.vue'
import SectionTabs from '@/components/SectionTabs.vue'
import PageState from '@/components/PageState.vue'
import PanelCard from '@/components/PanelCard.vue'
import type { PointAdminView } from '@/api/types'

/**
 * P02 点位维护：列表含停用点位和引用量。
 * 有引用的点位后端拒绝物理删除，只能停用；未被引用的才允许删除。
 */
const auth = useAuthStore()
const points = ref<PointAdminView[]>([])
const loading = ref(false)
const errorText = ref('')
const dialogVisible = ref(false)
const editing = ref<PointAdminView | null>(null)
const submitting = ref(false)
const formError = ref('')

const form = reactive({
  pointName: '',
  area: '',
  x: null as number | null,
  y: null as number | null,
  enabled: true,
  note: '',
})

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    points.value = await archiveApi.fetchAdminPoints()
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '点位列表加载失败'
  } finally {
    loading.value = false
  }
}

function openEdit(point: PointAdminView | null = null): void {
  editing.value = point
  form.pointName = point?.name ?? ''
  form.area = point?.area ?? ''
  form.x = point?.x ?? null
  form.y = point?.y ?? null
  form.enabled = point?.enabled ?? true
  form.note = point?.note ?? ''
  formError.value = ''
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (submitting.value) return
  if (!form.pointName.trim()) {
    ElMessage.warning('点位名称必填')
    return
  }
  const payload = {
    pointName: form.pointName.trim(),
    area: form.area.trim() || undefined,
    x: form.x,
    y: form.y,
    enabled: form.enabled,
    note: form.note.trim() || null,
  }
  submitting.value = true
  try {
    if (editing.value) {
      await archiveApi.updatePoint(editing.value.id, payload)
      ElMessage.success('点位已更新')
    } else {
      await archiveApi.createPoint(payload)
      ElMessage.success('点位已新增')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    formError.value = error instanceof BizError ? error.message : '保存失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

async function remove(point: PointAdminView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除点位「${point.name}」？该点位没有任何业务引用，删除后不可恢复。`,
      '删除点位',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await archiveApi.deletePoint(point.id)
    ElMessage.success('点位已删除')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '删除失败')
  }
}

function toggleEnabled(point: PointAdminView): void {
  openEdit(point)
}

onMounted(load)
</script>

<template>
  <div>
    <PageHero
      title="点位维护"
      desc="点位只停用不误删：有动物、群体、目击、上报或巡查引用时，后端返回 30413，只能把 enabled 置为 false。"
    >
      <template #actions>
        <button class="btn ghost" type="button" :disabled="loading" @click="load">刷新</button>
        <button v-if="auth.isAdmin" class="btn" type="button" @click="openEdit()">新增点位</button>
      </template>
    </PageHero>

    <SectionTabs
      :items="[
        { label: '个体档案', path: '/animals', matchPrefix: true },
        { label: '群体档案', path: '/groups' },
        { label: '物种名录', path: '/species' },
        { label: '点位维护', path: '/points' },
        { label: '巡查记录', path: '/patrols' },
      ]"
    />

    <div v-if="!auth.isAdmin" class="notice" style="margin-bottom: 16px">
      点位维护需要管理员身份；当前账号只能查看公开点位。
    </div>

    <PageState :loading="loading" :error="errorText" :empty="!points.length" empty-text="暂无点位">
      <PanelCard title="点位与引用" :more="`${points.length} 个`">
        <div class="table-wrap">
          <table class="native">
            <thead>
              <tr>
                <th>点位</th>
                <th>区域 / 坐标</th>
                <th>状态</th>
                <th>引用</th>
                <th>可删除</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="point in points" :key="point.id">
                <td>
                  <b>{{ point.name }}</b>
                  <div class="muted small mono">pointId · {{ point.id }}</div>
                  <div v-if="point.note" class="muted small">{{ point.note }}</div>
                </td>
                <td>
                  {{ point.area || '未填写' }}
                  <div class="muted small mono">
                    x {{ point.x ?? '—' }} · y {{ point.y ?? '—' }}
                  </div>
                </td>
                <td>
                  <span class="tag" :class="{ gray: !point.enabled }">{{ point.enabled ? '启用' : '停用' }}</span>
                </td>
                <td>
                  <div class="ref-line">动物 {{ point.animalCount }} · 群体 {{ point.groupCount }}</div>
                  <div class="ref-line">目击 {{ point.sightingCount }} · 出现 {{ point.occurrenceCount }}</div>
                  <div class="ref-line">上报 {{ point.reportCount }} · 巡查 {{ point.patrolCount }}</div>
                </td>
                <td>
                  <span class="tag" :class="{ gray: !point.deletable }">
                    {{ point.deletable ? '可删除' : `引用 ${point.referenceCount}` }}
                  </span>
                </td>
                <td>
                  <div class="row-actions">
                    <button class="btn sm ghost" type="button" @click="openEdit(point)">编辑</button>
                    <button class="btn sm ghost" type="button" @click="toggleEnabled(point)">
                      {{ point.enabled ? '停用' : '启用' }}
                    </button>
                    <button
                      class="btn sm ghost danger-text"
                      type="button"
                      :disabled="!point.deletable"
                      @click="remove(point)"
                    >
                      删除
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </PanelCard>
    </PageState>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑点位' : '新增点位'"
      width="620px"
      append-to-body
    >
      <div class="form-grid">
        <div class="field">
          <label>点位名称 <b>*</b></label>
          <input v-model="form.pointName" class="input" maxlength="64" placeholder="例如：图书馆北侧花坛" />
        </div>
        <div class="field">
          <label>所属区域</label>
          <input v-model="form.area" class="input" maxlength="32" placeholder="例如：教学区" />
        </div>
        <div class="field">
          <label>x 坐标（0–100）</label>
          <input v-model.number="form.x" class="input" type="number" min="0" max="100" step="0.1" />
        </div>
        <div class="field">
          <label>y 坐标（0–100）</label>
          <input v-model.number="form.y" class="input" type="number" min="0" max="100" step="0.1" />
        </div>
        <div class="field form-span-2">
          <label>启用状态</label>
          <label class="switch-line">
            <input v-model="form.enabled" type="checkbox" />
            启用后出现在上报与档案的公开点位下拉
          </label>
        </div>
        <div class="field form-span-2">
          <label>备注</label>
          <textarea v-model="form.note" class="textarea" maxlength="255" placeholder="可不填" />
        </div>
      </div>
      <div v-if="formError" class="notice">{{ formError }}</div>
      <template #footer>
        <button class="btn ghost" type="button" @click="dialogVisible = false">取消</button>
        <button class="btn" type="button" :disabled="submitting" @click="submit">
          {{ submitting ? '保存中…' : '保存' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ref-line {
  font-size: 11px;
  color: var(--muted);
  line-height: 1.6;
}

.row-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.danger-text {
  color: var(--danger);
}

.small {
  font-size: 11px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.form-span-2 {
  grid-column: span 2;
}

.field label b {
  color: var(--danger);
  font-weight: 500;
}

.switch-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--muted);
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .form-span-2 {
    grid-column: span 1;
  }
}
</style>
