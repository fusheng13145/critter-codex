<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

/**
 * 全站外壳：侧栏 + 顶栏（原型"青绿自然"）。
 * 侧栏菜单由后端 /users/me 的 menus 决定，前端不再写死角色判断；
 * B/C 的页面接进来后菜单会自动出现，无需改这里。
 */

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const ICONS: Record<string, string> = {
  home: '◫',
  archive: '▤',
  adopt: '♡',
  report: '＋',
  fund: '¥',
  medical: '✚',
  volunteer: '✿',
  content: '✉',
  stats: '◔',
  admin: '⚙',
  me: '◎',
}

const menus = computed(() => auth.menus)
const crumb = computed(() => (route.meta.title as string | undefined) ?? '工作台')
const today = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
})

const collapsed = ref(false)

function isActive(path: string): boolean {
  return route.path === path || (path !== '/' && route.path.startsWith(path))
}

function go(path: string): void {
  if (route.path !== path) {
    router.push(path)
  }
}

async function handleCommand(command: string): Promise<void> {
  if (command === 'profile') {
    router.push('/me')
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('退出后需要重新登录，确定退出吗？', '退出登录', {
        confirmButtonText: '退出',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }
    await auth.logout()
    ElMessage.success('已退出登录')
    router.replace({ name: 'login' })
  }
}

onMounted(() => {
  // 窄屏自动收起侧栏文字，避免遮挡内容
  collapsed.value = window.innerWidth <= 1000
})
</script>

<template>
  <div class="app" :class="{ collapsed }">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">青</div>
        <div>
          <b>青檐万灵册</b>
          <small>校园生物档案与救助平台</small>
        </div>
      </div>
      <div class="theme-tag">青绿自然 · 统一风格</div>
      <nav>
        <button
          v-for="menu in menus"
          :key="menu.code"
          class="nav-item"
          :class="{ active: isActive(menu.path) }"
          type="button"
          @click="go(menu.path)"
        >
          <span>{{ ICONS[menu.code] ?? '·' }}</span>
          <span>{{ menu.title }}</span>
        </button>
      </nav>
      <div class="side-foot">
        三人小组 · A 身份与看板<br />
        B 档案与资金 · C 认养与医疗
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="crumb">青檐万灵册 / <b>{{ crumb }}</b></div>
        <div class="top-actions">
          <span class="pill">{{ auth.user?.primaryRoleLabel ?? '游客' }}</span>
          <span class="muted" style="font-size: 12px">{{ today }}</span>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="avatar" :title="auth.displayName">{{ auth.avatarText }}</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>
