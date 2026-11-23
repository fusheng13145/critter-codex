import { get, getBlob, post, put } from './http'
import type {
  BatchConfirmResult,
  CreatedDonation,
  CreatedExpense,
  CreatedProject,
  DonationConfirmation,
  DonationSummary,
  ExpenseReviewResult,
  ExpenseSummary,
  ExportAccepted,
  ExportTask,
  LedgerResult,
  PageData,
  ProjectAuditResult,
  ProjectDetail,
  ProjectSettlementView,
  ProjectSummary,
} from './types'

/** 项目、捐赠、支出与台账接口。金额统一整数分。 */

export interface ProjectQuery extends Record<string, unknown> {
  status?: string
  targetType?: string
  keyword?: string
  page?: number
  size?: number
}

export interface ProjectDraftPayload {
  name: string
  purpose: string
  targetType: string
  targetAmount: number
  startDate: string
  endDate: string
  budget: { subject: string; amount: number; note?: string }[]
  coverFileId?: string
  submit: boolean
}

export interface DonationPayload {
  projectNo: string
  amount: number
  anonymous: boolean
  message?: string
  voucherFileIds: string[]
}

export interface DonationQuery extends Record<string, unknown> {
  status?: string
  projectNo?: string
  page?: number
  size?: number
}

export function fetchProjects(params: ProjectQuery): Promise<PageData<ProjectSummary>> {
  return get<PageData<ProjectSummary>>('/projects', params)
}

export function fetchProject(projectNo: string): Promise<ProjectDetail> {
  return get<ProjectDetail>(`/projects/${projectNo}`)
}

export function createProject(payload: ProjectDraftPayload): Promise<CreatedProject> {
  return post<CreatedProject>('/projects', payload)
}

export function updateProject(projectNo: string, payload: ProjectDraftPayload): Promise<ProjectDetail> {
  return put<ProjectDetail>(`/projects/${projectNo}`, payload)
}

export function auditProject(
  projectNo: string,
  payload: { decision: 'APPROVE' | 'REJECT'; reason?: string },
): Promise<ProjectAuditResult> {
  return put<ProjectAuditResult>(`/projects/${projectNo}/audit`, payload)
}

export function fetchDonations(params: DonationQuery): Promise<PageData<DonationSummary>> {
  return get<PageData<DonationSummary>>('/donations', params)
}

export function fetchMyDonations(params: { page?: number; size?: number } = {}): Promise<PageData<DonationSummary>> {
  return get<PageData<DonationSummary>>('/donations/mine', params)
}

export function flagDonation(
  donationNo: string,
  payload: { reason: string; voucherFileIds?: string[] },
): Promise<{ donationNo: string; status: string; abnormalReason: string; voucherFileIds: string[]; flaggedAt: string }> {
  return put<{ donationNo: string; status: string; abnormalReason: string; voucherFileIds: string[]; flaggedAt: string }>(
    `/donations/${donationNo}/flag`,
    payload,
  )
}

export function fetchDonationCertificate(donationNo: string): Promise<Blob> {
  return getBlob(`/donations/${donationNo}/certificate`)
}

export function createDonation(payload: DonationPayload): Promise<CreatedDonation> {
  return post<CreatedDonation>('/donations', payload)
}

export function confirmDonation(
  donationNo: string,
  payload: { amount?: number; paidAt?: string; voucherFileIds?: string[]; note?: string },
): Promise<DonationConfirmation> {
  return put<DonationConfirmation>(`/donations/${donationNo}/confirm`, payload)
}

export function confirmDonations(ids: string[]): Promise<BatchConfirmResult> {
  return put<BatchConfirmResult>('/donations/batch', { ids })
}

export interface ExpensePayload {
  projectNo: string
  animalCode: string
  purpose: string
  amount: number
  payee: string
  occurredAt: string
  voucherFileIds: string[]
}

export interface ExpenseQuery extends Record<string, unknown> {
  status?: string
  projectNo?: string
  page?: number
  size?: number
}

export interface LedgerQuery extends Record<string, unknown> {
  projectNo?: string
  from?: string
  to?: string
}

export function fetchExpenses(params: ExpenseQuery): Promise<PageData<ExpenseSummary>> {
  return get<PageData<ExpenseSummary>>('/expenses', params)
}

export function createExpense(payload: ExpensePayload): Promise<CreatedExpense> {
  return post<CreatedExpense>('/expenses', payload)
}

export function reviewExpense(
  expenseNo: string,
  payload: { decision: 'APPROVE' | 'REJECT'; reason?: string },
): Promise<ExpenseReviewResult> {
  return put<ExpenseReviewResult>(`/expenses/${expenseNo}/review`, payload)
}

export function fetchLedger(params: LedgerQuery = {}): Promise<LedgerResult> {
  return get<LedgerResult>('/ledger', params)
}

/** 同步导出返回 xlsx；超 5 万行返回 JSON 信封（同步被拒、异步受理） */
export function exportLedger(params: LedgerQuery = {}): Promise<Blob> {
  return getBlob('/ledger/export', params)
}

export function fetchLedgerExportTask(taskId: string): Promise<ExportTask> {
  return get<ExportTask>(`/ledger/export/${taskId}`)
}

export function downloadLedgerExport(taskId: string): Promise<Blob> {
  return getBlob(`/ledger/export/${taskId}/download`)
}

export type { ExportAccepted }

export interface SettleProjectPayload {
  reportText: string
  balanceTo: string
  attachments: string[]
}

export function settleProject(
  projectNo: string,
  payload: SettleProjectPayload,
): Promise<ProjectSettlementView> {
  return put<ProjectSettlementView>(`/projects/${projectNo}/settle`, payload)
}

export function fetchProjectSettlement(projectNo: string): Promise<ProjectSettlementView> {
  return get<ProjectSettlementView>(`/projects/${projectNo}/settlement`)
}

export function appendSettlementNote(
  projectNo: string,
  note: string,
): Promise<ProjectSettlementView> {
  return post<ProjectSettlementView>(`/projects/${projectNo}/settlement/notes`, { note })
}
