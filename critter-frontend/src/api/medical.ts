import { get, post } from './http'
import type { ClinicView, VisitCreatePayload, VisitDetail, VisitRegistration } from './types'

/** 医疗协作（D3）：合作医院、登记就诊、按编号查询就诊记录。 */

export function fetchClinics(): Promise<ClinicView[]> {
  return get<ClinicView[]>('/clinics')
}

export function createVisit(payload: VisitCreatePayload): Promise<VisitRegistration> {
  return post<VisitRegistration>('/visits', payload)
}

export function fetchVisit(visitNo: string): Promise<VisitDetail> {
  return get<VisitDetail>(`/visits/${encodeURIComponent(visitNo)}`)
}

/** 档案详情反查就诊：按动物编号返回该个体的全部就诊记录。 */
export function fetchAnimalVisits(animalCode: string): Promise<VisitDetail[]> {
  return get<VisitDetail[]>('/visits', { animalCode })
}
