import { get, post, put } from './http'
import type {
  AcceptedReport,
  ClosedReport,
  CreatedReport,
  OrderDetail,
  OrderSummary,
  OrderTransitionResult,
  PageData,
  ReportDetail,
  ReportSummary,
} from './types'

/** 上报与工单接口：列表/受理/流转均为管理员权限，前端只负责把可见入口摆对。 */

export interface ReportQuery extends Record<string, unknown> {
  status?: string
  type?: string
  pointId?: number
  overdue?: boolean
  page?: number
  size?: number
}

export interface ReportPayload {
  type: string
  pointId: number
  photos: string[]
  description: string
  animalCode?: string
  speciesId?: number
  contact?: string
}

export interface OrderQuery extends Record<string, unknown> {
  status?: string
  type?: string
  overdue?: boolean
  page?: number
  size?: number
}

export function createReport(payload: ReportPayload): Promise<CreatedReport> {
  return post<CreatedReport>('/reports', payload)
}

export function fetchReports(params: ReportQuery): Promise<PageData<ReportSummary>> {
  return get<PageData<ReportSummary>>('/reports', params)
}

export function fetchMyReports(params: { page?: number; size?: number } = {}): Promise<PageData<ReportSummary>> {
  return get<PageData<ReportSummary>>('/reports/mine', params)
}

export function fetchReport(id: number): Promise<ReportDetail> {
  return get<ReportDetail>(`/reports/${id}`)
}

export function acceptReport(
  id: number,
  payload: { handlerId?: number; animalCode?: string; note?: string },
): Promise<AcceptedReport> {
  return post<AcceptedReport>(`/reports/${id}/accept`, payload)
}

export function closeReport(id: number, reason: string, invalid = false): Promise<ClosedReport> {
  return put<ClosedReport>(`/reports/${id}/close`, { reason, invalid })
}

export function confirmReportClose(
  id: number,
): Promise<{ reportNo: string; status: string; confirmedAt: string; autoConfirmed: boolean }> {
  return put<{ reportNo: string; status: string; confirmedAt: string; autoConfirmed: boolean }>(
    `/reports/${id}/confirm`,
  )
}

export function fetchOrders(params: OrderQuery): Promise<PageData<OrderSummary>> {
  return get<PageData<OrderSummary>>('/orders', params)
}

export function fetchOrder(orderNo: string): Promise<OrderDetail> {
  return get<OrderDetail>(`/orders/${orderNo}`)
}

export function transitionOrder(
  orderNo: string,
  payload: { targetStatus: string; note?: string; reason?: string; handlerId?: number },
): Promise<OrderTransitionResult> {
  return put<OrderTransitionResult>(`/orders/${orderNo}/status`, payload)
}
