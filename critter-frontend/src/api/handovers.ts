import { get, post, put } from './http'
import type { HandoverDetail, HandoverView, FollowupView } from './p2types'
import type { PageData } from './types'

export function fetchHandovers(params: {
  status?: string
  page?: number
  size?: number
}): Promise<PageData<HandoverView>> {
  return get<PageData<HandoverView>>('/handovers', params)
}

export function fetchHandover(no: string): Promise<HandoverDetail> {
  return get<HandoverDetail>(`/handovers/${no}`)
}

export function createHandover(payload: {
  animalCode: string
  adopterName: string
  contact: string
  guardianConsent: boolean
  residenceNote: string
}): Promise<HandoverDetail> {
  return post<HandoverDetail>('/handovers', payload)
}

export function completeFollowup(
  no: string,
  seqNo: number,
  payload: { result: 'NORMAL' | 'ABNORMAL'; note?: string },
): Promise<FollowupView> {
  return put<FollowupView>(`/handovers/${no}/followups/${seqNo}/complete`, payload)
}
