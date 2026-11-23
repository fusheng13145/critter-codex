import { get, post, put } from './http'
import type { AbsenceResult, AppealView, ShiftSignupView, ShiftView, SuspensionView } from './p2types'
import type { PageData } from './types'

export function fetchShifts(params: {
  from?: string
  to?: string
  mine?: boolean
}): Promise<ShiftView[]> {
  return get<ShiftView[]>('/shifts', params)
}

export function fetchSuspension(): Promise<SuspensionView> {
  return get<SuspensionView>('/shifts/me')
}

export function createShift(payload: {
  title: string
  pointId?: number
  shiftDate: string
  startTime: string
  endTime: string
  capacity: number
}): Promise<ShiftView> {
  return post<ShiftView>('/shifts', payload)
}

export function signupShift(id: number): Promise<ShiftView> {
  return post<ShiftView>(`/shifts/${id}/signup`)
}

export function checkinShift(id: number): Promise<ShiftView> {
  return put<ShiftView>(`/shifts/${id}/checkin`)
}

export function closeShift(id: number, action: 'CLOSED' | 'CANCELED'): Promise<ShiftView> {
  return put<ShiftView>(`/shifts/${id}/close`, { action })
}

export function markShiftAbsent(id: number, userId: number): Promise<AbsenceResult> {
  return put<AbsenceResult>(`/shifts/${id}/signups/${userId}/absent`)
}

export function fetchShiftSignups(id: number): Promise<ShiftSignupView[]> {
  return get<ShiftSignupView[]>(`/shifts/${id}/signups`)
}

export function submitAppeal(reason: string): Promise<AppealView> {
  return post<AppealView>('/shifts/appeals', { reason })
}

export function fetchMyAppeals(): Promise<AppealView[]> {
  return get<AppealView[]>('/shifts/appeals/mine')
}

export function fetchAppeals(params: {
  status?: string
  page?: number
  size?: number
}): Promise<PageData<AppealView>> {
  return get<PageData<AppealView>>('/shifts/appeals', params)
}

export function reviewAppeal(
  id: number,
  payload: { decision: 'APPROVE' | 'REJECT'; reason?: string },
): Promise<AppealView> {
  return put<AppealView>(`/shifts/appeals/${id}/review`, payload)
}
