import { get, post, put } from './http'
import type { CarePlanView } from './p2types'
import type { PageData } from './types'

export function fetchCarePlans(params: {
  animalCode?: string
  type?: string
  status?: string
  page?: number
  size?: number
}): Promise<PageData<CarePlanView>> {
  return get<PageData<CarePlanView>>('/care/plans', params)
}

export function fetchOverduePlans(limit = 3): Promise<CarePlanView[]> {
  return get<CarePlanView[]>('/care/plans/overdue', { limit })
}

export function createCarePlan(payload: {
  animalCode?: string
  visitId?: number
  type: 'VACCINE' | 'MEDICATION'
  itemName: string
  dosage?: string
  planDate: string
  note?: string
}): Promise<CarePlanView> {
  return post<CarePlanView>('/care/plans', payload)
}

export function completeCarePlan(
  id: number,
  payload: { result: string; note?: string },
): Promise<CarePlanView> {
  return put<CarePlanView>(`/care/plans/${id}/complete`, payload)
}

export function cancelCarePlan(id: number, note?: string): Promise<CarePlanView> {
  return put<CarePlanView>(`/care/plans/${id}/cancel`, { note })
}
