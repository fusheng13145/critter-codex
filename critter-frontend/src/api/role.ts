import { get } from './http'
import type { RolesResponse } from './types'

/** 角色与权限清单（后台角色页） */

export function fetchRoles(): Promise<RolesResponse> {
  return get<RolesResponse>('/roles')
}
