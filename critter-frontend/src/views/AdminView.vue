<script setup lang="ts">
import { ref } from 'vue'
import PageHero from '@/components/PageHero.vue'
import LogPanel from '@/views/admin/LogPanel.vue'
import UserPanel from '@/views/admin/UserPanel.vue'
import DictPanel from '@/views/admin/DictPanel.vue'
import RolePanel from '@/views/admin/RolePanel.vue'
import FileAuditPanel from '@/views/admin/FileAuditPanel.vue'

/**
 * P10 后台：日志 / 用户 / 字典 / 角色 / 附件审核五个页签。
 * 附件审核对动物管理员与系统管理员开放（先审后发的照片在这里上架/下架），
 * 其余治理页签仅系统管理员（路由 meta.menu=admin，后端接口再按角色拦一次）。
 */

const active = ref('logs')
</script>

<template>
  <div>
    <PageHero
      title="后台管理"
      desc="系统管理员负责治理：账号状态、身份审批、字典口径、日志审计与角色权限配置。业务审核（认养、支出复核）不在这里，公开照片审核除外。"
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
        <el-tab-pane label="附件审核" name="files" lazy>
          <FileAuditPanel />
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
