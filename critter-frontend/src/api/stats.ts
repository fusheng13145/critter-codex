import { get } from './http'
import type { BiologyStats, EfficiencyStats, FundStats } from './types'

/** 统计接口：只读 B/C 维护的表与视图，前端不做二次口径换算 */

export function fetchBiology(): Promise<BiologyStats> {
  return get<BiologyStats>('/stats/biology')
}

export function fetchFund(params?: { from?: string; to?: string }): Promise<FundStats> {
  return get<FundStats>('/stats/fund', params)
}

export function fetchEfficiency(params?: { from?: string; to?: string }): Promise<EfficiencyStats> {
  return get<EfficiencyStats>('/stats/efficiency', params)
}
