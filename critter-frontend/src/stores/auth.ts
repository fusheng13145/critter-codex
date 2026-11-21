import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import * as userApi from '@/api/user'
import { setToken } from '@/api/token'
import type { LoginUser, MenuView, Profile } from '@/api/types'

interface AuthState {
  user: LoginUser | null
  profile: Profile | null
  /** 首次进入是否已尝试过恢复会话，避免守卫重复刷新 */
  bootstrapped: boolean
  restoring: boolean
}

/**
 * 会话状态：访问令牌只在内存（api/token.ts），这里存用户与菜单。
 * 刷新页面后靠 httpOnly 刷新令牌恢复，恢复失败则由路由守卫送去登录页。
 */
export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    profile: null,
    bootstrapped: false,
    restoring: false,
  }),

  getters: {
    isLoggedIn(state): boolean {
      return state.user !== null
    },
    displayName(state): string {
      return state.user?.nickname ?? '未登录'
    },
    roles(state): string[] {
      return state.user?.roles ?? []
    },
    menuCodes(state): string[] {
      return state.user?.menus ?? []
    },
    menus(state): MenuView[] {
      return state.profile?.menus ?? []
    },
    isAdmin(state): boolean {
      const roles = state.user?.roles ?? []
      return roles.includes('ANIMAL_ADMIN') || roles.includes('SYS_ADMIN') || roles.includes('SYSTEM_ADMIN')
    },
    isSystemAdmin(state): boolean {
      const roles = state.user?.roles ?? []
      // 接口文档用 SYS_ADMIN，库里历史种子用 SYSTEM_ADMIN，两种都认
      return roles.includes('SYS_ADMIN') || roles.includes('SYSTEM_ADMIN')
    },
    avatarText(state): string {
      const name = state.user?.nickname ?? '客'
      return name.slice(0, 1)
    },
  },

  actions: {
    async login(account: string, password: string): Promise<void> {
      const result = await authApi.login(account, password)
      setToken(result.accessToken)
      this.user = result.user
      await this.loadProfile()
      this.bootstrapped = true
    },

    async loadProfile(): Promise<Profile> {
      const profile = await userApi.fetchProfile()
      this.profile = profile
      if (this.user) {
        this.user.roles = profile.roles
        this.user.menus = profile.menus.map((item) => item.code)
        this.user.primaryRoleLabel = profile.primaryRoleLabel
      }
      return profile
    },

    /** 页面刷新后用 Cookie 恢复会话；失败静默，不弹错误。 */
    async restore(): Promise<boolean> {
      if (this.bootstrapped || this.restoring) {
        return this.isLoggedIn
      }
      this.restoring = true
      try {
        const token = await authApi.refreshToken()
        setToken(token.accessToken)
        const profile = await this.loadProfile()
        this.user = {
          id: profile.id,
          userNo: profile.userNo,
          nickname: profile.nickname,
          dept: profile.dept,
          primaryRole: profile.primaryRole,
          primaryRoleLabel: profile.primaryRoleLabel,
          roles: profile.roles,
          menus: profile.menus.map((item) => item.code),
        }
        return true
      } catch {
        setToken(null)
        this.user = null
        this.profile = null
        return false
      } finally {
        this.restoring = false
        this.bootstrapped = true
      }
    },

    async logout(): Promise<void> {
      try {
        await authApi.logout()
      } catch {
        // 令牌已失效时注销失败不影响前端清理
      }
      this.clear()
    },

    clear(): void {
      setToken(null)
      this.user = null
      this.profile = null
    },

    hasRole(role: string): boolean {
      return (this.user?.roles ?? []).includes(role)
    },

    hasMenu(code: string): boolean {
      return (this.user?.menus ?? []).includes(code)
    },
  },
})
