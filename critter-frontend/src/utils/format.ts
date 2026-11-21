/** 展示层格式化：金额一律"分 → 元"，时间统一 yyyy-MM-dd HH:mm。 */

export const STATUS_LABELS: Record<string, string> = {
  // 账号
  ACTIVE: '正常',
  PENDING: '待验证',
  LOCKED: '已锁定',
  DISABLED: '已禁用',
  // 字典/申请
  GRANTED: '已通过',
  REJECTED: '已驳回',
  // 档案
  ON_CAMPUS: '在校园活动',
  TREATING: '治疗中',
  HOSPITALIZED: '住院观察',
  TO_RELEASE: '待放归',
  RELEASED: '已放归',
  ADOPTED: '已领养',
  MISSING: '失踪',
  DECEASED: '已离世',
  // 项目/资金
  DRAFT: '草稿',
  PENDING_AUDIT: '待审核',
  FUNDING: '募捐中',
  PENDING_SETTLE: '待结项',
  SETTLED: '已结项',
  ENDED: '已结束',
  // 支出
  PENDING_REVIEW: '待复核',
  FIRST_APPROVED: '待二签',
  APPROVED: '已复核',
  // 工单/上报
  PROCESSING: '处置中',
  TO_REVISIT: '待回访',
  CLOSED: '已关闭',
  TIMEOUT: '已超时',
  // 附件
  PASSED: '已通过',
}

export const ACTION_LABELS: Record<string, string> = {
  LOGIN: '登录',
  LOGOUT: '退出登录',
  REGISTER: '注册',
  REGISTER_VERIFY: '邮箱验证',
  PASSWORD_CHANGE: '修改密码',
  PROFILE_UPDATE: '修改资料',
  ROLE_APPLY: '身份申请',
  ROLE_APPROVE: '身份审批通过',
  ROLE_REJECT: '身份审批驳回',
  DICT_CREATE: '新增字典',
  DICT_UPDATE: '修改字典',
  FILE_UPLOAD: '上传附件',
  FILE_AUDIT: '审核附件',
  USER_STATUS_UPDATE: '账号状态变更',
}

export const ROLE_LABELS: Record<string, string> = {
  GUEST: '游客',
  STUDENT: '注册学生',
  ADOPTER: '认养人',
  VOLUNTEER: '志愿者',
  ANIMAL_ADMIN: '动物管理员',
  SYS_ADMIN: '系统管理员',
  SYSTEM_ADMIN: '系统管理员',
}

export function statusLabel(code: string | null | undefined): string {
  if (!code) return '—'
  return STATUS_LABELS[code] ?? code
}

/** 认养状态与账号/档案的 ACTIVE 不同义，必须单独翻译为“生效中”。 */
export const ADOPTION_STATUS_LABELS: Record<string, string> = {
  PENDING_AUDIT: '待审核',
  PENDING_PAYMENT: '待缴费',
  ACTIVE: '生效中',
  EXPIRED: '已到期',
  CLOSED: '已关闭',
  REJECTED: '已驳回',
  TERMINATED: '已终止',
}

export function adoptionStatusLabel(code: string | null | undefined): string {
  if (!code) return '—'
  return ADOPTION_STATUS_LABELS[code] ?? code
}

export function adoptionStatusType(code: string | null | undefined): 'success' | 'warning' | 'danger' | 'info' {
  if (code === 'ACTIVE') return 'success'
  if (code === 'PENDING_AUDIT' || code === 'PENDING_PAYMENT') return 'warning'
  if (code === 'REJECTED' || code === 'TERMINATED' || code === 'EXPIRED') return 'danger'
  return 'info'
}

export function periodLabel(code: string | null | undefined): string {
  if (code === 'MONTH') return '按月'
  if (code === 'QUARTER') return '按季'
  return code ?? '—'
}

export const MESSAGE_TYPE_LABELS: Record<string, string> = {
  ADOPTION_EXPIRY: '认养到期',
  DONATION_PROGRESS: '募捐进度',
  ORDER_PROGRESS: '工单进展',
  APPROVAL_RESULT: '审批结果',
  HEALTH_REMINDER: '健康提醒',
}

export function messageTypeLabel(code: string | null | undefined): string {
  if (!code) return '—'
  return MESSAGE_TYPE_LABELS[code] ?? code
}

export function messageTypeTag(code: string | null | undefined): 'success' | 'warning' | 'info' | 'primary' {
  if (code === 'APPROVAL_RESULT') return 'success'
  if (code === 'ADOPTION_EXPIRY') return 'warning'
  if (code === 'DONATION_PROGRESS') return 'primary'
  if (code === 'HEALTH_REMINDER') return 'warning'
  return 'info'
}

export const NOTICE_CATEGORY_LABELS: Record<string, string> = {
  PROGRESS: '进展',
  PUBLICITY: '公示',
  SCIENCE: '科普',
}

export function noticeCategoryLabel(code: string | null | undefined): string {
  if (!code) return '—'
  return NOTICE_CATEGORY_LABELS[code] ?? code
}

export function noticeCategoryTag(code: string | null | undefined): 'success' | 'warning' | 'info' {
  if (code === 'PROGRESS') return 'success'
  if (code === 'PUBLICITY') return 'warning'
  return 'info'
}

export function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action
}

export function roleLabel(code: string): string {
  return ROLE_LABELS[code] ?? code
}

/** 分 → 元，保留两位；金额字段在接口里都是整数分（接口设计 §1）。 */
export function money(cents: number | null | undefined): string {
  if (cents === null || cents === undefined) return '¥0.00'
  return `¥${(cents / 100).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

export function moneyShort(cents: number | null | undefined): string {
  if (!cents) return '¥0'
  const yuan = cents / 100
  if (yuan >= 10000) {
    return `¥${(yuan / 10000).toFixed(1)} 万`
  }
  return `¥${yuan.toLocaleString('zh-CN')}`
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function dateOnly(value: string | null | undefined): string {
  if (!value) return '—'
  return dateTime(value).slice(0, 10)
}

export function percent(value: number | null | undefined): string {
  if (value === null || value === undefined) return '0%'
  return `${value.toFixed(1).replace(/\.0$/, '')}%`
}

/** 相对时间：列表里的"10 分钟前"，超过 7 天回落到日期。 */
export function relativeTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const diff = Date.now() - date.getTime()
  const minute = 60 * 1000
  if (diff < minute) return '刚刚'
  if (diff < 60 * minute) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < 24 * 60 * minute) return `${Math.floor(diff / (60 * minute))} 小时前`
  if (diff < 7 * 24 * 60 * minute) return `${Math.floor(diff / (24 * 60 * minute))} 天前`
  return dateOnly(value)
}
