import { get, post, put } from './http'
import type {
  PageData,
  TnrCreatePayload,
  TnrNodePayload,
  TnrTaskView,
} from './types'

/** TNR 任务看板与五节点推进（D3 P1）。登录可看，管理员可发起与推进。 */

export interface TnrQuery extends Record<string, unknown> {
  stage?: string
  animalCode?: string
  page?: number
  size?: number
}

export function fetchTnrTasks(params: TnrQuery): Promise<PageData<TnrTaskView>> {
  return get<PageData<TnrTaskView>>('/tnr', params)
}

export function createTnr(payload: TnrCreatePayload): Promise<TnrTaskView> {
  return post<TnrTaskView>('/tnr', payload)
}

export function advanceTnr(tnrNo: string, payload: TnrNodePayload): Promise<TnrTaskView> {
  return put<TnrTaskView>(`/tnr/${encodeURIComponent(tnrNo)}/node`, payload)
}
