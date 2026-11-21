<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as roleApi from '@/api/role'
import { BizError } from '@/api/http'
import { menuTitle } from '@/utils/menus'
import type { PermissionDiff, RoleMatrix, RolesResponse } from '@/api/types'

/**
 * 后台 · 角色与权限。
 * 展示的是"服务端当前生效的矩阵"（GET /roles），前端不另存一份：
 * 这样评审时看到的页面就是真正的鉴权依据，不会出现"页面说有权限、接口却 403"。
 *
 * V7 起矩阵落在 sys_role_permission，可在本页在线改：
 * 先"预览差异"（preview=true 不落库），确认后再保存；
 * 服务端会拦住"把系统管理员锁在门外"的操作。
 */

const data = ref<RolesResponse | null>(null)
const loading = ref(false)
const errorText = ref('')

const selectedRoleCode = ref('')
const selectedMenus = ref<string[]>([])
const selectedApis = ref<string[]>([])
const diff = ref<PermissionDiff | null>(null)
const previewing = ref(false)
const saving = ref(false)

const editableRoles = computed(() => data.value?.matrix ?? [])
const currentMatrix = computed<RoleMatrix | null>(
  () => editableRoles.value.find((item) => item.roleCode === selectedRoleCode.value) ?? null,
)

/** 接口权限清单：以 sys_permission 的 API 类型为准，并补上该角色当前独有的码，避免漏显示。 */
const apiOptions = computed(() => {
  const known = (data.value?.permissions ?? []).filter((item) => item.type === 'API').map((item) => item.permCode)
  const extra = (currentMatrix.value?.apis ?? []).filter((code) => !known.includes(code))
  return [...known, ...extra].sort()
})

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    data.value = await roleApi.fetchRoles()
    if (!selectedRoleCode.value && data.value.matrix.length) {
      selectRole(data.value.matrix[0].roleCode)
    } else if (selectedRoleCode.value) {
      selectRole(selectedRoleCode.value)
    }
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '角色清单加载失败'
  } finally {
    loading.value = false
  }
}

function selectRole(roleCode: string): void {
  selectedRoleCode.value = roleCode
  const matrix = editableRoles.value.find((item) => item.roleCode === roleCode)
  selectedMenus.value = [...(matrix?.menus ?? [])]
  selectedApis.value = [...(matrix?.apis ?? [])]
  diff.value = null
}

function roleIdOf(roleCode: string): number | null {
  const role = (data.value?.roles ?? []).find((item) => item.roleCode === roleCode)
  return role ? role.id : null
}

function changed(): boolean {
  if (!currentMatrix.value) return false
  const sameMenus = [...selectedMenus.value].sort().join(',') === [...currentMatrix.value.menus].sort().join(',')
  const sameApis = [...selectedApis.value].sort().join(',') === [...currentMatrix.value.apis].sort().join(',')
  return !(sameMenus && sameApis)
}

async function preview(): Promise<void> {
  const roleId = roleIdOf(selectedRoleCode.value)
  if (roleId === null) return
  previewing.value = true
  errorText.value = ''
  try {
    diff.value = await roleApi.updateRolePermissions(roleId, {
      menus: selectedMenus.value,
      apis: selectedApis.value,
      preview: true,
    })
    if (isEmptyDiff(diff.value)) {
      ElMessage.info('与当前配置相比没有变化')
    }
  } catch (error) {
    errorText.value = fieldErrorText(error)
    diff.value = null
  } finally {
    previewing.value = false
  }
}

async function save(): Promise<void> {
  const roleId = roleIdOf(selectedRoleCode.value)
  if (roleId === null) return
  try {
    await ElMessageBox.confirm(
      `确认把「${currentMatrix.value?.roleName ?? selectedRoleCode.value}」的权限改为当前勾选？` +
        '改动会立刻影响该角色下次登录与接口判定。',
      '保存角色权限',
      { confirmButtonText: '确认保存', cancelButtonText: '再想想', type: 'warning' },
    )
  } catch {
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    diff.value = await roleApi.updateRolePermissions(roleId, {
      menus: selectedMenus.value,
      apis: selectedApis.value,
      preview: false,
    })
    ElMessage.success('已保存，新配置对该角色即时生效')
    await load()
  } catch (error) {
    errorText.value = fieldErrorText(error)
  } finally {
    saving.value = false
  }
}

function isEmptyDiff(value: PermissionDiff | null): boolean {
  if (!value) return true
  return !value.addedMenus.length && !value.removedMenus.length
    && !value.addedApis.length && !value.removedApis.length
}

/** 30301 的字段错误在 data 里，直接拼成一行给管理员看。 */
function fieldErrorText(error: unknown): string {
  if (error instanceof BizError) {
    const fields = error.fieldErrors.map((item) => item.reason).join('；')
    return fields ? `${error.message}：${fields}` : error.message
  }
  return '操作失败，请稍后重试'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div v-if="errorText" class="notice" style="margin-bottom: 12px">{{ errorText }}</div>

    <div class="notice" style="margin-bottom: 16px">
      系统管理员不具备业务审核权：<span class="mono">adoption:audit</span> 与
      <span class="mono">expense:review</span> 不在其权限集合内，审核类接口只对动物管理员开放。
      在线调整时系统管理员必须保留 <span class="mono">menu:admin</span> 与 <span class="mono">role:manage</span>，
      避免把管理员锁在门外。
    </div>

    <div class="card">
      <div class="card-head">
        <h2>在线配置</h2>
        <span class="more">先预览差异，确认后保存</span>
      </div>

      <div class="toolbar" style="margin-bottom: 12px">
        <label class="muted" style="font-size: 12px">角色</label>
        <select
          class="select"
          style="min-width: 180px"
          :value="selectedRoleCode"
          @change="selectRole(($event.target as HTMLSelectElement).value)"
        >
          <option v-for="role in editableRoles" :key="role.roleCode" :value="role.roleCode">
            {{ role.roleName }}（{{ role.roleCode }}）
          </option>
        </select>
        <span class="muted" style="font-size: 12px; margin-left: auto">
          系统管理员两套码（SYS_ADMIN / SYSTEM_ADMIN）会同步修改
        </span>
      </div>

      <div style="margin-bottom: 12px">
        <div class="muted" style="font-size: 12px; margin-bottom: 6px">菜单权限</div>
        <div style="display: flex; flex-wrap: wrap; gap: 8px">
          <label
            v-for="menu in data?.allMenus ?? []"
            :key="menu"
            class="tag"
            :class="{ gray: !selectedMenus.includes(menu) }"
            style="cursor: pointer"
          >
            <input v-model="selectedMenus" type="checkbox" :value="menu" style="margin-right: 4px" />
            {{ menuTitle(menu) }}
          </label>
        </div>
      </div>

      <div style="margin-bottom: 14px">
        <div class="muted" style="font-size: 12px; margin-bottom: 6px">接口权限</div>
        <div style="display: flex; flex-wrap: wrap; gap: 8px">
          <label
            v-for="api in apiOptions"
            :key="api"
            class="tag"
            :class="{ gray: !selectedApis.includes(api) }"
            style="cursor: pointer"
          >
            <input v-model="selectedApis" type="checkbox" :value="api" style="margin-right: 4px" />
            <span class="mono">{{ api }}</span>
          </label>
        </div>
      </div>

      <div style="display: flex; gap: 8px">
        <button class="btn ghost" type="button" :disabled="previewing || !changed()" @click="preview">
          {{ previewing ? '计算中…' : '预览差异' }}
        </button>
        <button class="btn" type="button" :disabled="saving || isEmptyDiff(diff) || !changed()" @click="save">
          {{ saving ? '保存中…' : '确认保存' }}
        </button>
        <button class="btn ghost" type="button" @click="selectRole(selectedRoleCode)">重置勾选</button>
      </div>

      <div v-if="diff" class="notice" style="margin-top: 14px">
        <div style="margin-bottom: 6px">
          <b>{{ diff.roleName }}</b> 的差异预览（{{ diff.applied ? '已落库' : '尚未落库' }}）
        </div>
        <div v-if="diff.addedMenus.length">新增菜单：{{ diff.addedMenus.map(menuTitle).join('、') }}</div>
        <div v-if="diff.removedMenus.length" style="color: var(--danger)">
          移除菜单：{{ diff.removedMenus.map(menuTitle).join('、') }}
        </div>
        <div v-if="diff.addedApis.length">新增接口：<span class="mono">{{ diff.addedApis.join('、') }}</span></div>
        <div v-if="diff.removedApis.length" style="color: var(--danger)">
          移除接口：<span class="mono">{{ diff.removedApis.join('、') }}</span>
        </div>
        <div v-if="isEmptyDiff(diff)">没有差异</div>
        <div v-for="warning in diff.warnings" :key="warning" class="muted" style="font-size: 12px; margin-top: 4px">
          {{ warning }}
        </div>
      </div>
    </div>

    <div class="grid2" style="grid-template-columns: 1fr 1fr">
      <div class="card" v-for="role in data?.matrix ?? []" :key="role.roleCode">
        <div class="card-head">
          <h2>{{ role.roleName }}</h2>
          <span class="mono">{{ role.roleCode }}</span>
        </div>
        <div style="display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px">
          <span v-for="menu in role.menus" :key="menu" class="tag">{{ menuTitle(menu) }}</span>
          <span v-if="!role.menus.length" class="muted">无菜单</span>
        </div>
        <div class="muted" style="font-size: 12px; line-height: 1.9">
          接口权限：{{ role.apis.join(' · ') || '—' }}
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h2>权限点清单</h2>
        <span class="more">共 {{ data?.permissions.length ?? 0 }} 个</span>
      </div>
      <table class="native">
        <thead>
          <tr>
            <th>编码</th>
            <th>名称</th>
            <th>类型</th>
            <th>路径</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="perm in data?.permissions ?? []" :key="perm.id">
            <td class="mono">{{ perm.permCode }}</td>
            <td>{{ perm.permName }}</td>
            <td>
              <span class="tag" :class="{ gray: perm.type === 'API' }">{{ perm.type === 'MENU' ? '菜单' : '接口' }}</span>
            </td>
            <td class="mono">{{ perm.path ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
