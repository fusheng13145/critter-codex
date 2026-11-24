<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as dictApi from '@/api/dict'
import { BizError } from '@/api/http'
import type { DictView } from '@/api/types'

/**
 * 后台 · 字典维护：新增 / 改显示名 / 排序 / 启停。
 * 页面不做本地缓存：改完立刻重新拉一次，前端下拉通过 /dicts 实时生效（验收项）。
 */

const rows = ref<DictView[]>([])
const loading = ref(false)
const errorText = ref('')
const typeFilter = ref('')
const keyword = ref('')

const createDialog = reactive({
  visible: false,
  type: '',
  key: '',
  label: '',
  sortNo: 99,
})

const types = computed(() => Array.from(new Set(rows.value.map((item) => item.type))).sort())

const page = ref(1)
const size = ref(15)

const filtered = computed(() =>
  rows.value.filter((item) => {
    const hitType = !typeFilter.value || item.type === typeFilter.value
    const hitKeyword =
      !keyword.value ||
      item.key.toLowerCase().includes(keyword.value.toLowerCase()) ||
      item.label.includes(keyword.value)
    return hitType && hitKeyword
  }),
)

const paged = computed(() => {
  const start = (page.value - 1) * size.value
  return filtered.value.slice(start, start + size.value)
})

function resetPage(): void {
  page.value = 1
}

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    rows.value = await dictApi.fetchDictsForAdmin()
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '字典加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  createDialog.visible = true
  createDialog.type = typeFilter.value || 'ANIMAL_COLOR'
  createDialog.key = ''
  createDialog.label = ''
  createDialog.sortNo = 99
}

async function submitCreate(): Promise<void> {
  if (!createDialog.type.trim() || !createDialog.key.trim() || !createDialog.label.trim()) {
    ElMessage.warning('类型、键、显示名都不能为空')
    return
  }
  try {
    await dictApi.createDict({
      type: createDialog.type.trim().toUpperCase(),
      key: createDialog.key.trim().toUpperCase(),
      label: createDialog.label.trim(),
      sortNo: createDialog.sortNo,
    })
    ElMessage.success('已新增，下拉框立即生效')
    createDialog.visible = false
    load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '新增失败')
  }
}

async function toggle(item: DictView): Promise<void> {
  try {
    await dictApi.updateDict(item.id, { enabled: !item.enabled })
    ElMessage.success(item.enabled ? '已停用，下拉不再出现' : '已启用')
    load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '操作失败')
  }
}

async function rename(item: DictView): Promise<void> {
  const next = window.prompt('修改显示名（留空取消）', item.label)
  if (next === null) return
  if (!next.trim()) {
    ElMessage.warning('显示名不能为空')
    return
  }
  try {
    await dictApi.updateDict(item.id, { label: next.trim() })
    ElMessage.success('已更新')
    load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '更新失败')
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <select v-model="typeFilter" class="select" style="min-width: 200px" @change="resetPage">
        <option value="">全部字典类型</option>
        <option v-for="type in types" :key="type" :value="type">{{ type }}</option>
      </select>
      <input v-model="keyword" class="input" style="min-width: 200px" placeholder="键 / 显示名" @input="resetPage" />
      <button class="btn" type="button" @click="openCreate">＋ 新增字典项</button>
      <span class="muted" style="font-size: 12px; margin-left: auto">
        共 {{ filtered.length }} 项
      </span>
    </div>

    <div v-if="errorText" class="notice" style="margin-bottom: 12px">{{ errorText }}</div>

    <el-table :data="paged" v-loading="loading" stripe>
      <el-table-column prop="type" label="类型" width="180" />
      <el-table-column prop="key" label="键" width="180" />
      <el-table-column prop="label" label="显示名" min-width="160" />
      <el-table-column prop="sortNo" label="排序" width="90" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <span class="tag" :class="{ gray: !row.enabled }">{{ row.enabled ? '启用' : '已停用' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <button class="btn sm ghost" type="button" @click="rename(row)">改名</button>
          <button class="btn sm ghost" type="button" style="margin-left: 6px" @click="toggle(row)">
            {{ row.enabled ? '停用' : '启用' }}
          </button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="filtered.length"
      :page-sizes="[15, 30, 60]"
      layout="total, sizes, prev, pager, next"
      @size-change="resetPage"
    />

    <el-dialog v-model="createDialog.visible" title="新增字典项" width="460px">
      <div class="field">
        <label>类型（大写字母 / 数字 / 下划线）</label>
        <input v-model="createDialog.type" class="input" placeholder="例如 ANIMAL_COLOR" />
      </div>
      <div class="field">
        <label>键（同类型内唯一）</label>
        <input v-model="createDialog.key" class="input" placeholder="例如 SMOKE" />
      </div>
      <div class="field">
        <label>显示名</label>
        <input v-model="createDialog.label" class="input" placeholder="例如 烟灰色" />
      </div>
      <div class="field">
        <label>排序（越小越靠前）</label>
        <input v-model.number="createDialog.sortNo" class="input" type="number" />
      </div>
      <template #footer>
        <button class="btn ghost" type="button" @click="createDialog.visible = false">取消</button>
        <button class="btn" type="button" style="margin-left: 8px" @click="submitCreate">保存</button>
      </template>
    </el-dialog>
  </div>
</template>
