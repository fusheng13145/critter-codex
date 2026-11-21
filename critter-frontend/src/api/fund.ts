import { get, post, put } from './http'
import type {
  BatchConfirmResult,
  CreatedDonation,
  CreatedProject,
  DonationConfirmation,
  DonationSummary,
  PageData,
  ProjectAuditResult,
  ProjectDetail,
  ProjectSummary,
} from './types'

/** 项目与捐赠接口：T3.1 已落地，支出/台账接口仍待 T3.2/T3.3/T3.5。 */

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
