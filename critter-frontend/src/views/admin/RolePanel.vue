<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as roleApi from '@/api/role'
import { BizError } from '@/api/http'
import { menuTitle } from '@/utils/menus'
import type { RolesResponse } from '@/api/types'

/**
 * 后台 · 角色与权限。
 * 展示的是"服务端当前生效的矩阵"（GET /roles），前端不另存一份：
 * 这样评审时看到的页面就是真正的鉴权依据，不会出现"页面说有权限、接口却 403"。
 */

const data = ref<RolesResponse | null>(null)
const loading = ref(false)
const errorText = ref('')

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    data.value = await roleApi.fetchRoles()
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '角色清单加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div v-if="errorText" class="notice" style="margin-bottom: 12px">{{ errorText }}</div>

    <div class="notice" style="margin-bottom: 16px">
      系统管理员不具备业务审核权：<span class="mono">adoption:audit</span> 与
      <span class="mono">expense:review</span> 不在其权限集合内，审核类接口只对动物管理员开放。
      在线调整权限（<span class="mono">PUT /roles/{{ '{id}' }}/permissions</span>）列入三期，当前矩阵由服务端常量下发。
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
