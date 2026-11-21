import { get, getBlob } from './http'
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

/**
 * 统计导出（xlsx）。5 万行以内直接回二进制；超过阈值时后端回 JSON（{taskId}），
 * 由调用方轮询 fetchExportTask 取下载地址。两者都以 Blob 返回，按 blob.type 分流。
 */
export async function exportStats(params?: { from?: string; to?: string }): Promise<Blob> {
  return getBlob('/stats/export', params)
}

/** 轮询异步导出任务（GET /tasks/export/{taskId}） */
export function fetchExportTask(taskId: string): Promise<import('./types').ExportTask> {
  return get(`/tasks/export/${taskId}`)
}

/** 异步任务完成后的下载（带令牌，所以不能直接用 <a href>） */
export async function downloadExport(taskId: string): Promise<Blob> {
  return getBlob(`/stats/export/${taskId}/download`)
}
