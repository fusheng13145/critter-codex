import { get, post, put } from './http'
import type {
  ClinicCreatePayload,
  ClinicView,
  VisitCreatePayload,
  VisitDetail,
  VisitRegistration,
  VisitUpdatePayload,
} from './types'

/** 医疗协作（D3）：合作医院、登记/纠错就诊、按编号查询就诊记录。 */

export function fetchClinics(): Promise<ClinicView[]> {
  return get<ClinicView[]>('/clinics')
}

/** 管理员新增合作医院。 */
export function createClinic(payload: ClinicCreatePayload): Promise<ClinicView> {
  return post<ClinicView>('/clinics', payload)
}

export function createVisit(payload: VisitCreatePayload): Promise<VisitRegistration> {
  return post<VisitRegistration>('/visits', payload)
}

export function fetchVisit(visitNo: string): Promise<VisitDetail> {
  return get<VisitDetail>(`/visits/${encodeURIComponent(visitNo)}`)
}

/** 管理员纠错就诊；reason 用于审计留痕。 */
export function updateVisit(visitNo: string, payload: VisitUpdatePayload): Promise<VisitDetail> {
  return put<VisitDetail>(`/visits/${encodeURIComponent(visitNo)}`, payload)
}

/** 档案详情反查就诊：按动物编号返回该个体的全部就诊记录。 */
export function fetchAnimalVisits(animalCode: string): Promise<VisitDetail[]> {
  return get<VisitDetail[]>('/visits', { animalCode })
}
