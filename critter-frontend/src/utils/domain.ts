import type {
  AnimalCategory,
  AnimalGender,
  DonationSummary,
  GroupSummary,
  OrderStatusCode,
  ReportSummary,
  ReportDetail,
} from '@/api/types'

/**
 * D2 域的展示口径。
 *
 * 为什么不在 format.ts 里复用 STATUS_LABELS：PENDING 在账号语境是"待验证"，
 * 在上报/工单语境是"待受理"，同一个枚举名不能共用一张表。这里按对象族拆开。
 */

export interface SelectOption {
  value: string
  label: string
}

export const ANIMAL_CATEGORY_OPTIONS: SelectOption[] = [
  { value: 'CAT', label: '猫' },
  { value: 'DOG', label: '犬' },
  { value: 'OTHER', label: '其他' },
]

export const ANIMAL_GENDER_OPTIONS: SelectOption[] = [
  { value: 'MALE', label: '公' },
  { value: 'FEMALE', label: '母' },
  { value: 'UNKNOWN', label: '未知' },
]

export const ANIMAL_STATUS_OPTIONS: SelectOption[] = [
  { value: 'ON_CAMPUS', label: '在校园活动' },
  { value: 'TREATING', label: '治疗中' },
  { value: 'HOSPITALIZED', label: '住院观察' },
  { value: 'TO_RELEASE', label: '待放归' },
  { value: 'RELEASED', label: '已放归' },
  { value: 'ADOPTED', label: '已领养' },
  { value: 'MISSING', label: '失踪' },
  { value: 'DECEASED', label: '已离世' },
]

export const GROUP_STATUS_OPTIONS: SelectOption[] = [
  { value: 'ACTIVE', label: '活跃' },
  { value: 'UNCONFIRMED', label: '未确认' },
  { value: 'GONE', label: '已消失' },
]

export const REPORT_TYPE_OPTIONS: SelectOption[] = [
  { value: 'INJURED', label: '受伤' },
  { value: 'NEW_ARRIVAL', label: '新出现' },
  { value: 'ABANDONED', label: '疑似遗弃' },
  { value: 'DISTURB', label: '扰民' },
  { value: 'DEAD', label: '死亡' },
]

export const REPORT_STATUS_OPTIONS: SelectOption[] = [
  { value: 'PENDING', label: '待受理' },
  { value: 'PROCESSING', label: '已受理' },
  { value: 'CLOSED', label: '已关闭' },
]

export const ORDER_STATUS_OPTIONS: SelectOption[] = [
  { value: 'PENDING', label: '待受理' },
  { value: 'PROCESSING', label: '处置中' },
  { value: 'TO_REVISIT', label: '待回访' },
  { value: 'TIMEOUT', label: '已超时' },
  { value: 'CLOSED', label: '已结案' },
]

export const PROJECT_STATUS_OPTIONS: SelectOption[] = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_AUDIT', label: '待审核' },
  { value: 'FUNDING', label: '募捐中' },
  { value: 'PENDING_SETTLE', label: '待结项' },
  { value: 'SETTLED', label: '已结项' },
  { value: 'ENDED', label: '已结束' },
  { value: 'REJECTED', label: '已驳回' },
]

export const TARGET_TYPE_OPTIONS: SelectOption[] = [
  { value: 'ANIMAL', label: '个体' },
  { value: 'GROUP', label: '群体' },
  { value: 'MIXED', label: '混合' },
]

export const DONATION_STATUS_OPTIONS: SelectOption[] = [
  { value: 'PENDING_VERIFY', label: '待核对' },
  { value: 'VERIFIED', label: '已入账' },
  { value: 'ABNORMAL', label: '凭证异常' },
]

export const HEALTH_TYPE_OPTIONS: SelectOption[] = [
  { value: 'VACCINE', label: '疫苗' },
  { value: 'DEWORM', label: '驱虫' },
  { value: 'WEIGHT', label: '体重' },
  { value: 'HISTORY', label: '病史' },
]

export const SPECIES_CATEGORY_OPTIONS: SelectOption[] = [
  { value: '哺乳纲', label: '哺乳纲' },
  { value: '鸟纲', label: '鸟纲' },
  { value: '两栖纲', label: '两栖纲' },
]

const ANIMAL_CATEGORY_LABELS: Record<string, string> = {
  CAT: '猫',
  DOG: '犬',
  OTHER: '其他',
}

const ANIMAL_GENDER_LABELS: Record<string, string> = {
  MALE: '公',
  FEMALE: '母',
  UNKNOWN: '未知',
}

const ANIMAL_STATUS_LABELS: Record<string, string> = {
  ON_CAMPUS: '在校园活动',
  TREATING: '治疗中',
  HOSPITALIZED: '住院观察',
  TO_RELEASE: '待放归',
  RELEASED: '已放归',
  ADOPTED: '已领养',
  MISSING: '失踪',
  DECEASED: '已离世',
}

const GROUP_STATUS_LABELS: Record<string, string> = {
  ACTIVE: '活跃',
  UNCONFIRMED: '未确认',
  GONE: '已消失',
}

const REPORT_TYPE_LABELS: Record<string, string> = {
  INJURED: '受伤',
  NEW_ARRIVAL: '新出现',
  ABANDONED: '疑似遗弃',
  DISTURB: '扰民',
  DEAD: '死亡',
}

const REPORT_STATUS_LABELS: Record<string, string> = {
  PENDING: '待受理',
  PROCESSING: '已受理',
  CLOSED: '已关闭',
}

const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING: '待受理',
  PROCESSING: '处置中',
  TO_REVISIT: '待回访',
  TIMEOUT: '已超时',
  CLOSED: '已结案',
}

const PROJECT_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_AUDIT: '待审核',
  FUNDING: '募捐中',
  PENDING_SETTLE: '待结项',
  SETTLED: '已结项',
  ENDED: '已结束',
  REJECTED: '已驳回',
}

const TARGET_TYPE_LABELS: Record<string, string> = {
  ANIMAL: '个体',
  GROUP: '群体',
  MIXED: '混合',
}

const DONATION_STATUS_LABELS: Record<string, string> = {
  PENDING_VERIFY: '待核对',
  VERIFIED: '已入账',
  ABNORMAL: '凭证异常',
}

const HEALTH_TYPE_LABELS: Record<string, string> = {
  VACCINE: '疫苗',
  DEWORM: '驱虫',
  WEIGHT: '体重',
  HISTORY: '病史',
}

const SCALE_LABELS: Record<string, string> = {
  FEW: '零星',
  SMALL_HERD: '小群',
  LARGE_HERD: '大群',
}

function pick(map: Record<string, string>, code: string | null | undefined): string {
  if (!code) return '—'
  return map[code] ?? code
}

export function categoryLabel(code: string | null | undefined): string {
  return pick(ANIMAL_CATEGORY_LABELS, code)
}

export function genderLabel(code: string | null | undefined): string {
  return pick(ANIMAL_GENDER_LABELS, code)
}

export function animalStatusLabel(code: string | null | undefined): string {
  return pick(ANIMAL_STATUS_LABELS, code)
}

export function groupStatusLabel(code: string | null | undefined, fallback?: string | null): string {
  return fallback || pick(GROUP_STATUS_LABELS, code)
}

export function reportTypeLabel(code: string | null | undefined): string {
  return pick(REPORT_TYPE_LABELS, code)
}

export function reportStatusLabel(code: string | null | undefined): string {
  return pick(REPORT_STATUS_LABELS, code)
}

export function orderStatusLabel(code: string | null | undefined): string {
  return pick(ORDER_STATUS_LABELS, code)
}

export function projectStatusLabel(code: string | null | undefined): string {
  return pick(PROJECT_STATUS_LABELS, code)
}

export function targetTypeLabel(code: string | null | undefined): string {
  return pick(TARGET_TYPE_LABELS, code)
}

export function donationStatusLabel(code: string | null | undefined): string {
  return pick(DONATION_STATUS_LABELS, code)
}

export function healthTypeLabel(code: string | null | undefined): string {
  return pick(HEALTH_TYPE_LABELS, code)
}

export function occurrenceScaleLabel(code: string | null | undefined): string {
  return pick(SCALE_LABELS, code)
}

/** 标签色只表达风险，不表达审美：danger 终止/异常，warn 待办/超时，gray 中性终态。 */
export function statusTone(
  code:
    | string
    | AnimalCategory
    | AnimalGender
    | GroupSummary['status']
    | ReportSummary['status']
    | ReportDetail['status']
    | OrderStatusCode
    | DonationSummary['status']
    | null
    | undefined,
): '' | 'warn' | 'danger' | 'gray' {
  if (!code) return 'gray'
  if (['DECEASED', 'ABNORMAL', 'CLOSED', 'GONE', 'REJECTED', 'ENDED', 'TIMEOUT'].includes(code)) {
    return 'danger'
  }
  if (['PENDING_VERIFY', 'PENDING_AUDIT', 'PENDING', 'TREATING', 'HOSPITALIZED', 'TO_REVISIT', 'PENDING_SETTLE'].includes(code)) {
    return 'warn'
  }
  if (['VERIFIED', 'FUNDING', 'ACTIVE', 'PROCESSING', 'SETTLED', 'ADOPTED', 'RELEASED', 'ON_CAMPUS'].includes(code)) {
    return ''
  }
  return 'gray'
}

/** 元转分：表单输入保留两位小数，提交时转整数分，避免浮点误差。 */
export function yuanToCents(value: number | string | null | undefined): number {
  if (value === null || value === undefined || value === '') return 0
  const amount = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(amount)) return 0
  return Math.round(amount * 100)
}

/** 分转元，用于金额输入框的初始值。 */
export function centsToYuan(cents: number | null | undefined): number {
  if (cents === null || cents === undefined) return 0
  return Number((cents / 100).toFixed(2))
}

/** 表单里的日期时间转带时区的 ISO；后端 OffsetDateTime 可直接接收。 */
export function toOffsetDateTime(value: string): string | undefined {
  if (!value) return undefined
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return undefined
  return date.toISOString()
}

/**
 * 后端 coverUrl 返回的是元信息地址 `/api/v1/files/F...`，不是 fileId。
 * 展示层需要 fileId 才能带鉴权取二进制，这里统一剥掉路径。
 */
export function fileIdFromUrl(value: string | null | undefined): string | null {
  if (!value) return null
  const trimmed = value.trim()
  if (!trimmed) return null
  const segments = trimmed.split('/').filter(Boolean)
  return segments.length ? segments[segments.length - 1] : null
}
