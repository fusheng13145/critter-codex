import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { onUnauthorized } from '@/api/token'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/layouts/AppLayout.vue'

/**
 * 路由表：A 的四个页面 + B/C 的占位页。
 * 占位页不是"空壳"：它带着归属人与契约路径，B/C 接手时只换组件、不动路由与权限。
 */

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** 需要的菜单码，与后端 LoginUser.menus 对应 */
    menu?: string
    public?: boolean
    owner?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录 / 注册', public: true },
  },
  {
    path: '/',
    component: AppLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { title: '工作台', menu: 'home' },
      },
      {
        path: 'stats',
        name: 'stats',
        component: () => import('@/views/StatsView.vue'),
        meta: { title: '数据统计', menu: 'stats' },
      },
      {
        path: 'admin',
        name: 'admin',
        component: () => import('@/views/AdminView.vue'),
        meta: { title: '后台管理', menu: 'admin' },
      },
      {
        path: 'me',
        name: 'profile',
        component: () => import('@/views/ProfileView.vue'),
        meta: { title: '个人中心', menu: 'me' },
      },
      // ---- 以下五个由 B/C 实现，先落占位页，保证侧栏、面包屑与权限一致 ----
      {
        path: 'animals',
        name: 'animals',
        component: () => import('@/views/PlaceholderView.vue'),
        meta: { title: '生物档案', menu: 'archive', owner: 'B' },
      },
      {
        path: 'adopt',
        name: 'adopt',
        component: () => import('@/views/PlaceholderView.vue'),
        meta: { title: '认养专区', menu: 'adopt', owner: 'C' },
      },
      {
        path: 'report',
        name: 'report',
        component: () => import('@/views/PlaceholderView.vue'),
        meta: { title: '救助上报', menu: 'report', owner: 'B' },
      },
      {
        path: 'fund',
        name: 'fund',
        component: () => import('@/views/PlaceholderView.vue'),
        meta: { title: '资金透明', menu: 'fund', owner: 'B' },
      },
      {
        path: 'medical',
        name: 'medical',
        component: () => import('@/views/PlaceholderView.vue'),
        meta: { title: '医疗协作', menu: 'medical', owner: 'C' },
      },
      {
        path: 'tasks',
        name: 'tasks',
        component: () => import('@/views/PlaceholderView.vue'),
        meta: { title: '志愿任务', menu: 'volunteer', owner: 'C' },
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/views/PlaceholderView.vue'),
        meta: { title: '消息中心', menu: 'content', owner: 'C' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在', public: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    if (to.name === 'login' && auth.isLoggedIn) {
      return { name: 'home' }
    }
    return true
  }

  if (!auth.bootstrapped) {
    const restored = await auth.restore()
    if (!restored) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }

  const menu = to.meta.menu
  if (menu && !auth.hasMenu(menu)) {
    ElMessage.warning('当前身份没有该菜单权限')
    return { name: 'home' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 青檐万灵册` : '青檐万灵册'
})

// 刷新令牌也失效时，统一回登录页并保留原路径（在这里注册，避免 store 与 router 循环依赖）
onUnauthorized(() => {
  const auth = useAuthStore()
  auth.clear()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

export default router
