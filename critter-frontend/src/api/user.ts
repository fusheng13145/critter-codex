import { get, post, put } from './http'
import type { ApplicationItem, LoginLogItem, PageData, Profile, UserListItem } from './types'

/** 用户域（个人中心 P11 + 后台用户页 P10） */

export function fetchProfile(): Promise<Profile> {
  return get<Profile>('/users/me')
}

export function updateProfile(payload: {
  nickname: string
  dept?: string | null
  phone?: string | null
}): Promise<Profile> {
  return put<Profile>('/users/me', payload)
}

export function changePassword(oldPassword: string, newPassword: string): Promise<null> {
  return put<null>('/users/me/password', { oldPassword, newPassword })
}

export function fetchLoginLogs(limit = 20): Promise<LoginLogItem[]> {
  return get<LoginLogItem[]>('/users/me/login-logs', { limit })
}

export function applyRole(payload: {
  roleCode: string
  reason: string
  availableSlots?: string
}): Promise<{ applicationId: number; roleCode: string; status: string }> {
  return post('/users/me/applications', payload)
}

export function fetchMyApplications(): Promise<ApplicationItem[]> {
  return get<ApplicationItem[]>('/users/me/applications')
}

export function fetchPendingApplications(): Promise<ApplicationItem[]> {
  return get<ApplicationItem[]>('/users/applications')
}

export function reviewApplication(
  id: number,
  decision: 'APPROVE' | 'REJECT',
  reason?: string,
  /** 可选临时授权（小时，1—720）；不传即永久授予 */
  expireHours?: number,
): Promise<ApplicationItem> {
  return put<ApplicationItem>(`/users/applications/${id}`, {
    decision,
    reason,
    expireHours: expireHours ?? null,
  })
}

export function fetchUsers(params: {
  page?: number
  size?: number
  status?: string
  role?: string
  dept?: string
  keyword?: string
}): Promise<PageData<UserListItem>> {
  return get<PageData<UserListItem>>('/users', params)
}

export function updateUserStatus(id: number, status: string, reason?: string): Promise<null> {
  return put<null>(`/users/${id}/status`, { status, reason })
}

/**
 * 账号禁用申诉（公开接口）：被禁用的账号登不进来，只能凭学号从登录页提交。
 * 同一学号已有待处理申诉时后端幂等返回原记录。
 */
export function submitAppeal(payload: {
  userNo: string
  reason: string
  contact?: string
}): Promise<import('./types').AppealResponse> {
  return post('/users/me/appeals', payload)
}
