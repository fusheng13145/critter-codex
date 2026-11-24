<script setup lang="ts">
import { ref } from 'vue'
import PageHero from '@/components/PageHero.vue'
import LogPanel from '@/views/admin/LogPanel.vue'
import UserPanel from '@/views/admin/UserPanel.vue'
import DictPanel from '@/views/admin/DictPanel.vue'
import RolePanel from '@/views/admin/RolePanel.vue'

/**
 * P10 后台：日志 / 用户 / 字典 / 角色四个页签。
 * 只有系统管理员能进（路由 meta.menu=admin，后端接口再按角色拦一次）。
 */

const active = ref('logs')
</script>

<template>
  <div>
    <PageHero
      title="后台管理"
    />

    <div class="card">
      <el-tabs v-model="active">
        <el-tab-pane label="操作日志" name="logs" lazy>
          <LogPanel />
        </el-tab-pane>
        <el-tab-pane label="用户与申请" name="users" lazy>
          <UserPanel />
        </el-tab-pane>
        <el-tab-pane label="数据字典" name="dicts" lazy>
          <DictPanel />
        </el-tab-pane>
        <el-tab-pane label="角色权限" name="roles" lazy>
          <RolePanel />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
:deep(.el-tabs__header) {
  margin-bottom: 18px;
}
</style>
