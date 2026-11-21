<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as userApi from '@/api/user'
import { BizError } from '@/api/http'
import { dateTime, roleLabel, statusLabel } from '@/utils/format'
import type { ApplicationItem, UserListItem } from '@/api/types'

/**
 * 后台 · 用户与身份申请。
 * 禁用必须填不少于 5 字原因（服务端也会再校验一次），禁用后该用户所有已签发令牌立即失效。
 */

const rows = ref<UserListItem[]>([])
const applications = ref<ApplicationItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const roleFilter = ref('')
const errorText = ref('')

const statusDialog = reactive({
  visible: false,
  userId: 0,
  target: 'DISABLED',
  nickname: '',
  reason: '',
})

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const data = await userApi.fetchUsers({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined,
      role: roleFilter.value || undefined,
    })
    rows.value = data.list
    total.value = data.page.total
  } catch (error) {
    errorText.value = error instanceof BizError ? error.message : '用户列表加载失败'
  } finally {
    loading.value = false
  }
}

async function loadApplications(): Promise<void> {
  try {
    applications.value = await userApi.fetchPendingApplications()
  } catch {
    applications.value = []
  }
}

function search(): void {
  page.value = 1
  load()
}

function openStatusDialog(row: UserListItem, target: string): void {
  statusDialog.visible = true
  statusDialog.userId = row.id
  statusDialog.nickname = row.nickname
  statusDialog.target = target
  statusDialog.reason = ''
}

async function submitStatus(): Promise<void> {
  if (statusDialog.target === 'DISABLED' && statusDialog.reason.trim().length < 5) {
    ElMessage.warning('禁用原因不少于 5 个字')
    return
  }
  try {
    await userApi.updateUserStatus(statusDialog.userId, statusDialog.target, statusDialog.reason.trim() || undefined)
    ElMessage.success(statusDialog.target === 'DISABLED' ? '账号已禁用，令牌已作废' : '账号已启用')
    statusDialog.visible = false
    load()
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '操作失败')
  }
}

async function review(item: ApplicationItem, decision: 'APPROVE' | 'REJECT'): Promise<void> {
  let reason = ''
  if (decision === 'REJECT') {
    try {
      const result = await ElMessageBox.prompt('请填写驳回理由（不少于 5 字）', '驳回身份申请', {
        confirmButtonText: '驳回',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：可参与时段与任务安排不匹配',
      })
      reason = result.value ?? ''
    } catch {
      return
    }
    if (reason.trim().length < 5) {
      ElMessage.warning('驳回理由不少于 5 个字')
      return
    }
  }
  try {
    await userApi.reviewApplication(item.id, decision, reason || undefined)
    ElMessage.success(decision === 'APPROVE' ? '已通过，该身份立即生效' : '已驳回')
    await Promise.all([loadApplications(), load()])
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : '审核失败')
  }
}

onMounted(() => {
  load()
  loadApplications()
})
</script>

<template>
  <div>
    <div class="toolbar">
      <input v-model="keyword" class="input" style="min-width: 200px" placeholder="学号 / 昵称" @keyup.enter="search" />
      <select v-model="statusFilter" class="select" style="min-width: 130px">
        <option value="">全部状态</option>
        <option value="ACTIVE">正常</option>
        <option value="PENDING">待验证</option>
        <option value="DISABLED">已禁用</option>
      </select>
      <select v-model="roleFilter" class="select" style="min-width: 150px">
        <option value="">全部角色</option>
        <option value="STUDENT">注册学生</option>
        <option value="ADOPTER">认养人</option>
        <option value="VOLUNTEER">志愿者</option>
        <option value="ANIMAL_ADMIN">动物管理员</option>
        <option value="SYS_ADMIN">系统管理员</option>
      </select>
      <button class="btn" type="button" @click="search">检索</button>
    </div>

    <div v-if="errorText" class="notice" style="margin-bottom: 12px">{{ errorText }}</div>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="userNo" label="学号" width="130" />
      <el-table-column prop="nickname" label="昵称" width="120" />
      <el-table-column prop="dept" label="院系" min-width="150" />
      <el-table-column label="角色" min-width="170">
        <template #default="{ row }">
          <span v-for="role in row.roles" :key="role" class="tag gray" style="margin-right: 4px">
            {{ roleLabel(role) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="联系方式" width="130">
        <template #default="{ row }">{{ row.phoneMasked ?? '未填写' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span class="tag" :class="{ warn: row.status === 'PENDING', danger: row.status === 'DISABLED', gray: row.status === 'ACTIVE' }">
            {{ statusLabel(row.status) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="160">
        <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <button
            v-if="row.status !== 'DISABLED'"
            class="btn sm ghost"
            type="button"
            @click="openStatusDialog(row, 'DISABLED')"
          >
            禁用
          </button>
          <button v-else class="btn sm ghost" type="button" @click="openStatusDialog(row, 'ACTIVE')">启用</button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="load"
      @size-change="search"
    />

    <h2 style="margin: 24px 0 10px">待审身份申请</h2>
    <el-table :data="applications" stripe>
      <el-table-column prop="id" label="#" width="70" />
      <el-table-column label="申请身份" width="130">
        <template #default="{ row }">{{ row.roleLabel }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="申请理由" min-width="240" />
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ dateTime(row.appliedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <button class="btn sm" type="button" @click="review(row, 'APPROVE')">通过</button>
          <button class="btn sm ghost" type="button" style="margin-left: 6px" @click="review(row, 'REJECT')">驳回</button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="!applications.length" class="notice" style="margin-top: 12px">当前没有待审申请。</div>

    <el-dialog v-model="statusDialog.visible" :title="statusDialog.target === 'DISABLED' ? '禁用账号' : '启用账号'" width="460px">
      <p class="muted" style="margin-top: 0">
        {{ statusDialog.target === 'DISABLED' ? '禁用后该用户所有已签发令牌立即失效，登录返回 30104。' : '启用后用户可重新登录。' }}
      </p>
      <div class="field">
        <label>{{ statusDialog.target === 'DISABLED' ? '禁用原因（不少于 5 字，写入操作日志）' : '备注（可选）' }}</label>
        <textarea v-model="statusDialog.reason" class="textarea" :placeholder="`账号：${statusDialog.nickname}`" />
      </div>
      <template #footer>
        <button class="btn ghost" type="button" @click="statusDialog.visible = false">取消</button>
        <button class="btn" type="button" style="margin-left: 8px" @click="submitStatus">确定</button>
      </template>
    </el-dialog>
  </div>
</template>
