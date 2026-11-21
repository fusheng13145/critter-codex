import { get, post, put } from './http'
import type {
  AdoptionCertificate,
  AdoptionDetail,
  AdoptionPaymentResult,
  AdoptionPlan,
  AdoptionPlanPayload,
  AdoptionSummary,
  AuditedAdoption,
  PageData,
  TerminatedAdoption,
} from './types'

/** 认养域接口（D3）：方案、申请、审核、缴费、我的认养、终止、证书。 */

export function fetchPlans(includeDisabled = false): Promise<AdoptionPlan[]> {
  return get<AdoptionPlan[]>('/plans', includeDisabled ? { includeDisabled: true } : undefined)
}

export function createPlan(payload: AdoptionPlanPayload): Promise<AdoptionPlan> {
  return post<AdoptionPlan>('/plans', payload)
}

export function updatePlan(planId: number, payload: AdoptionPlanPayload): Promise<AdoptionPlan> {
  return put<AdoptionPlan>(`/plans/${planId}`, payload)
}

export function applyAdoption(payload: {
  animalCode: string
  planId: number
  promiseAccepted: boolean
  contact: string
  remark?: string
}): Promise<{ adoptionNo: string; status: string; amount: number; period: string }> {
  return post('/adoptions', payload)
}

export function fetchAdoptions(params: {
  status?: string
  page?: number
  size?: number
}): Promise<PageData<AdoptionSummary>> {
  return get<PageData<AdoptionSummary>>('/adoptions', params)
}

export function fetchMyAdoptions(params: {
  status?: string
  page?: number
  size?: number
}): Promise<PageData<AdoptionSummary>> {
  return get<PageData<AdoptionSummary>>('/adoptions/mine', params)
}

export function fetchAdoptionDetail(no: string): Promise<AdoptionDetail> {
  return get<AdoptionDetail>(`/adoptions/${no}`)
}

export function auditAdoption(
  no: string,
  payload: { decision: 'APPROVE' | 'REJECT'; reason?: string; amount?: number },
): Promise<AuditedAdoption> {
  return put<AuditedAdoption>(`/adoptions/${no}/audit`, payload)
}

export function confirmAdoptionPayment(
  no: string,
  payload: {
    paidAmount: number
    paidAt: string
    voucherFileIds: string[]
    note?: string
  },
): Promise<AdoptionPaymentResult> {
  return put<AdoptionPaymentResult>(`/adoptions/${no}/payment`, payload)
}

export function terminateAdoption(no: string, reason: string): Promise<TerminatedAdoption> {
  return put<TerminatedAdoption>(`/adoptions/${no}/terminate`, { reason })
}

export function fetchAdoptionCertificate(no: string): Promise<AdoptionCertificate> {
  return get<AdoptionCertificate>(`/adoptions/${no}/certificate`)
}
