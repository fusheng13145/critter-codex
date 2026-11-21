import { get, put } from './http'
import type { RolesResponse } from './types'

/** 角色与权限清单（后台角色页） */

export function fetchRoles(): Promise<RolesResponse> {
  return get<RolesResponse>('/roles')
}

/**
 * 在线配置角色权限。
 * preview=true 只回差异不落库：后台页要求先看"会删掉什么"，确认后再调一次 preview=false。
 */
export function updateRolePermissions(
  roleId: number,
  payload: { menus: string[]; apis: string[]; preview: boolean },
): Promise<import('./types').PermissionDiff> {
  return put(`/roles/${roleId}/permissions`, payload)
}
