import { del, get, getBlob, post, put } from './http'
import type {
  AnimalChangeLogView,
  AnimalDetail,
  AnimalMergeResult,
  AnimalStatusLogView,
  AnimalStatusResult,
  AnimalSummary,
  AnimalStatusCode,
  CreatedAnimal,
  CreatedGroup,
  CreatedHealthRecord,
  CreatedOccurrence,
  CreatedSighting,
  GroupDetail,
  GroupMergeResult,
  GroupSplitResult,
  GroupSummary,
  HealthRecordView,
  OccurrenceView,
  PageData,
  PatrolView,
  PointAdminView,
  PointView,
  SightingView,
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

export interface UpdateGroupPayload {
  features: string
  pointIds: number[]
  minSize: number
  maxSize: number
  sizeBasis: string
}

export interface CreateSightingPayload {
  pointId: number
  seenAt: string
  countEstimate?: number
  behavior?: string
  fileIds?: string[]
}

export interface CreateOccurrencePayload {
  speciesId: number
  pointId: number
  occurredAt: string
  scale: 'FEW' | 'SMALL_HERD' | 'LARGE_HERD'
  habitat?: string
  fileIds?: string[]
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

export function changeAnimalStatus(
  code: string,
  payload: { targetStatus: AnimalStatusCode; reason: string },
): Promise<AnimalStatusResult> {
  return put<AnimalStatusResult>(`/animals/${code}/status`, payload)
}

export function fetchAnimalStatusHistory(code: string): Promise<AnimalStatusLogView[]> {
  return get<AnimalStatusLogView[]>(`/animals/${code}/status-history`)
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

export function updateGroup(code: string, payload: UpdateGroupPayload): Promise<GroupDetail> {
  return put<GroupDetail>(`/groups/${code}`, payload)
}

export function createSighting(code: string, payload: CreateSightingPayload): Promise<CreatedSighting> {
  return post<CreatedSighting>(`/groups/${code}/sightings`, payload)
}

/** 先登记待归属目击，再由后台指派到具体群体 */
export function createUnassignedSighting(payload: CreateSightingPayload): Promise<CreatedSighting> {
  return post<CreatedSighting>('/sightings', payload)
}

export function assignSighting(
  id: number,
  payload: { groupCode?: string; pending?: boolean; reason: string },
): Promise<SightingView> {
  return put<SightingView>(`/sightings/${id}`, payload)
}

export function fetchSightings(params: {
  groupCode?: string
  assignStatus?: string
  pointId?: number
  page?: number
  size?: number
}): Promise<PageData<SightingView>> {
  return get<PageData<SightingView>>('/sightings', params)
}

export function fetchSpecies(params?: { keyword?: string; category?: string }): Promise<SpeciesView[]> {
  return get<SpeciesView[]>('/species', params)
}

export interface SpeciesPayload {
  commonName?: string
  scientificName?: string | null
  category?: string | null
  protectionLevel?: string | null
  note?: string | null
}

export function createSpecies(payload: SpeciesPayload): Promise<SpeciesView> {
  return post<SpeciesView>('/species', payload)
}

export function updateSpecies(id: number, payload: SpeciesPayload): Promise<SpeciesView> {
  return put<SpeciesView>(`/species/${id}`, payload)
}

export function fetchPoints(): Promise<PointView[]> {
  return get<PointView[]>('/points')
}

export function fetchAdminPoints(): Promise<PointAdminView[]> {
  return get<PointAdminView[]>('/points/admin')
}

export interface PointPayload {
  pointName?: string
  area?: string
  x?: number | null
  y?: number | null
  enabled?: boolean
  note?: string | null
}

export function createPoint(payload: PointPayload): Promise<PointView> {
  return post<PointView>('/points', payload)
}

export function updatePoint(id: number, payload: PointPayload): Promise<PointView> {
  return put<PointView>(`/points/${id}`, payload)
}

export function deletePoint(id: number): Promise<void> {
  return del<void>(`/points/${id}`)
}

export function mergeAnimal(
  mainCode: string,
  payload: { sourceCodes: string[]; reason: string },
): Promise<AnimalMergeResult> {
  return post<AnimalMergeResult>(`/animals/${mainCode}/merge`, payload)
}

/** 二维码 PNG 需要带鉴权请求，直接用浏览器打开会 401 */
export function fetchAnimalQrCode(code: string): Promise<Blob> {
  return getBlob(`/animals/${code}/qrcode`)
}

export interface GroupMergePayload {
  mainCode: string
  sourceCodes: string[]
  reason: string
}

export function mergeGroups(payload: GroupMergePayload): Promise<GroupMergeResult> {
  return post<GroupMergeResult>('/groups/merge', payload)
}

export interface GroupSplitPayload {
  newFeatures: string
  newMinSize: number
  newMaxSize: number
  newSizeBasis: string
  pointIds: number[]
  sourceMinSize: number
  sourceMaxSize: number
  sourceSizeBasis: string
  moveSightingIds?: number[]
  reason: string
}

export function splitGroup(code: string, payload: GroupSplitPayload): Promise<GroupSplitResult> {
  return post<GroupSplitResult>(`/groups/${code}/split`, payload)
}

export interface PatrolPayload {
  pointId: number
  patrolAt: string
  countEstimate?: number | null
  hasAnomaly?: boolean
  anomalyNote?: string | null
  fileIds?: string[]
}

export function fetchPatrols(params: {
  pointId?: number
  from?: string
  to?: string
  page?: number
  size?: number
}): Promise<PageData<PatrolView>> {
  return get<PageData<PatrolView>>('/patrols', params)
}

export function createPatrol(payload: PatrolPayload): Promise<PatrolView> {
  return post<PatrolView>('/patrols', payload)
}

export function convertPatrol(id: number, groupCode?: string | null): Promise<SightingView> {
  return post<SightingView>(`/patrols/${id}/convert`, { groupCode: groupCode ?? null })
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

export function createOccurrence(payload: CreateOccurrencePayload): Promise<CreatedOccurrence> {
  return post<CreatedOccurrence>('/occurrences', payload)
}
