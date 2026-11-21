import { get, post, put } from './http'
import type { DictBundle, DictView } from './types'

/** 字典接口：所有表单下拉的口径来源 */

export function fetchDicts(types: string[]): Promise<DictBundle> {
  return get<DictBundle>('/dicts', { type: types.join(',') })
}

export function fetchDictsForAdmin(type?: string): Promise<DictView[]> {
  return get<DictView[]>('/dicts/manage', type ? { type } : undefined)
}

export function createDict(payload: {
  type: string
  key: string
  label: string
  sortNo?: number
}): Promise<DictView> {
  return post<DictView>('/dicts', payload)
}

export function updateDict(
  id: number,
  payload: { label?: string; sortNo?: number; enabled?: boolean },
): Promise<DictView> {
  return put<DictView>(`/dicts/${id}`, payload)
}
