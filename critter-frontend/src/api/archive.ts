import { get, post, put } from './http'
import type {
  AnimalChangeLogView,
  AnimalDetail,
  AnimalSummary,
  CreatedAnimal,
  CreatedGroup,
  CreatedHealthRecord,
  GroupDetail,
  GroupSummary,
  HealthRecordView,
  OccurrenceView,
  PageData,
  PointView,
  SpeciesView,
} from './types'

/** 档案域接口：列表/详情公开，写操作由路由与后端双重限制。 */

export interface AnimalQuery extends Record<string, unknown> {
  keyword?: string
  status?: string
  pointId?: number
  neutered?: boolean
  adopted?: boolean
  page?: number
  size?: number
}

export interface CreateAnimalPayload {
  category: string
  gender: string
  color: string
  estimatedAge?: number
  features: string
  pointId: number
  photos: string[]
  healthNote?: string
}

export interface UpdateAnimalPayload {
  name?: string
  gender?: string
  color?: string
  estimatedAge?: number
  features?: string
  pointId?: number
  internalNote?: string
  reason: string
}

export interface GroupQuery extends Record<string, unknown> {
  keyword?: string
  speciesId?: number
  status?: string
  pointId?: number
  page?: number
  size?: number
}

export interface CreateGroupPayload {
  speciesId?: number
  features: string
  pointIds: number[]
  minSize: number
  maxSize: number
  sizeBasis: string
}

export function fetchAnimals(params: AnimalQuery): Promise<PageData<AnimalSummary>> {
  return get<PageData<AnimalSummary>>('/animals', params)
}

export function fetchAnimal(code: string): Promise<AnimalDetail> {
  return get<AnimalDetail>(`/animals/${code}`)
}

export function createAnimal(payload: CreateAnimalPayload): Promise<CreatedAnimal> {
  return post<CreatedAnimal>('/animals', payload)
}

export function updateAnimal(code: string, payload: UpdateAnimalPayload): Promise<AnimalDetail> {
  return put<AnimalDetail>(`/animals/${code}`, payload)
}

export function fetchAnimalHistory(code: string): Promise<AnimalChangeLogView[]> {
  return get<AnimalChangeLogView[]>(`/animals/${code}/history`)
}

export function fetchHealthRecords(code: string): Promise<HealthRecordView[]> {
  return get<HealthRecordView[]>(`/animals/${code}/health`)
}

export function createHealthRecord(
  code: string,
  payload: { type: string; recordedAt?: string; result: string; nextDate?: string },
): Promise<CreatedHealthRecord> {
  return post<CreatedHealthRecord>(`/animals/${code}/health`, payload)
}

export function fetchGroups(params: GroupQuery): Promise<PageData<GroupSummary>> {
  return get<PageData<GroupSummary>>('/groups', params)
}

export function fetchGroup(code: string): Promise<GroupDetail> {
  return get<GroupDetail>(`/groups/${code}`)
}

export function createGroup(payload: CreateGroupPayload): Promise<CreatedGroup> {
  return post<CreatedGroup>('/groups', payload)
}

export function fetchSpecies(params?: { keyword?: string; category?: string }): Promise<SpeciesView[]> {
  return get<SpeciesView[]>('/species', params)
}

export function fetchPoints(): Promise<PointView[]> {
  return get<PointView[]>('/points')
}

export function fetchOccurrences(params: {
  speciesId?: number
  pointId?: number
  season?: string
  page?: number
  size?: number
}): Promise<PageData<OccurrenceView>> {
  return get<PageData<OccurrenceView>>('/occurrences', params)
}
