import { get, post, put } from './http'
import type {
  AdoptionBenefitView,
  AdoptionPostView,
  AdoptionVisitView,
  VisitAdminView,
} from './p2types'

export function fetchAdoptionBenefits(no: string): Promise<AdoptionBenefitView> {
  return get<AdoptionBenefitView>(`/adoptions/${no}/benefits`)
}

export function createAdoptionPost(
  no: string,
  payload: { title: string; content: string },
): Promise<AdoptionPostView> {
  return post<AdoptionPostView>(`/adoptions/${no}/posts`, payload)
}

export function bookAdoptionVisit(
  no: string,
  payload: { visitDate: string; timeSlot: string; note?: string },
): Promise<AdoptionVisitView> {
  return post<AdoptionVisitView>(`/adoptions/${no}/visits`, payload)
}

export function cancelAdoptionVisit(no: string, visitId: number): Promise<AdoptionVisitView> {
  return put<AdoptionVisitView>(`/adoptions/${no}/visits/${visitId}/cancel`)
}

export function fetchPendingVisits(limit = 20): Promise<VisitAdminView[]> {
  return get<VisitAdminView[]>('/adoptions/visits/pending', { limit })
}

export function reviewAdoptionVisit(
  no: string,
  visitId: number,
  payload: { decision: 'APPROVE' | 'REJECT'; reason?: string },
): Promise<AdoptionVisitView> {
  return put<AdoptionVisitView>(`/adoptions/${no}/visits/${visitId}/review`, payload)
}
