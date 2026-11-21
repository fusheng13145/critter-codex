/**
 * 与后端 DTO 一一对应的类型（字段名严格照《接口设计说明书》v1.0-frozen）。
 * 后端只允许新增字段，这里出现"多出来的字段"说明契约被改过，应回文档核对。
 */

export interface PageMeta {
  page: number
  size: number
  total: number
  pages: number
}

export interface PageData<T> {
  list: T[]
  page: PageMeta
}

export interface MenuView {
  code: string
  title: string
  path: string
  /** 页面归属：A/B/C，占位页据此提示由谁实现 */
  owner: string
}

export interface LoginUser {
  id: number
  userNo: string
  nickname: string
  dept: string | null
  primaryRole: string
  primaryRoleLabel: string
  roles: string[]
  menus: string[]
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string | null
  expiresIn: number
  user: LoginUser
}

export interface TokenResponse {
  accessToken: string
  expiresIn: number
}

export interface RegisterPayload {
  account: string
  email: string
  password: string
  nickname: string
  dept?: string
  phone?: string
}

export interface RegisterResponse {
  userNo: string
  status: string
  email: string
  codeTtlSeconds: number
  /** 课程演示环境没有邮件通道，验证码回传在这里；生产环境为 null */
  devCode: string | null
}

export interface Profile {
  id: number
  userNo: string
  email: string
  nickname: string
  dept: string | null
  phone: string | null
  phoneMasked: string | null
  primaryRole: string
  primaryRoleLabel: string
  roles: string[]
  menus: MenuView[]
  status: string
  createdAt: string
}

export interface ApplicationItem {
  id: number
  roleCode: string
  roleLabel: string
  status: string
  reason: string | null
  appliedAt: string
  grantedBy: number | null
}

export interface LoginLogItem {
  id: number
  action: string
  result: string
  ip: string | null
  requestId: string | null
  createdAt: string
}

export interface UserListItem {
  id: number
  userNo: string
  nickname: string
  dept: string | null
  email: string
  phoneMasked: string | null
  primaryRole: string
  roles: string[]
  status: string
  createdAt: string
}

export interface DictItem {
  id: number
  key: string
  label: string
  sortNo: number
  enabled: boolean
}

export interface DictBundle {
  dicts: Record<string, DictItem[]>
  types: string[]
}

export interface DictView {
  id: number
  type: string
  key: string
  label: string
  sortNo: number
  enabled: boolean
}

export interface UploadResult {
  fileId: string
  url: string
  contentUrl: string
  name: string
  size: number
  mime: string
  auditStatus: string
}

export interface LogItem {
  id: number
  actorId: number
  actorRole: string | null
  action: string
  bizType: string | null
  bizId: string | null
  beforeJson: string | null
  afterJson: string | null
  ip: string | null
  result: string
  failCode: number | null
  requestId: string | null
  createdAt: string
}

export interface StatusSlice {
  status: string
  label: string
  count: number
}

export interface MonthPoint {
  month: string
  newAnimals: number
}

export interface BiologyStats {
  total: number
  deceased: number
  byStatus: StatusSlice[]
  neuteredCount: number
  neuteredRate: number
  activeAdoptions: number
  adoptionCoverage: number
  groupCount: number
  groupSizeMin: number
  groupSizeMax: number
  occurrenceCount: number
  months: MonthPoint[]
}

export interface FundSummary {
  raisedAmount: number
  spentAmount: number
  balanceAmount: number
  donationCount: number
  expenseCount: number
}

export interface FundMonth {
  month: string
  raisedAmount: number
  spentAmount: number
}

export interface FundProject {
  no: string
  name: string
  status: string
  targetAmount: number
  raisedAmount: number
  spentAmount: number
  balanceAmount: number
  progress: number
}

export interface FundStats {
  summary: FundSummary
  byMonth: FundMonth[]
  byProject: FundProject[]
  unsettled: FundProject[]
}

export interface EfficiencyStats {
  reports: { total: number; pending: number; processing: number; closed: number }
  orders: {
    total: number
    closed: number
    timeout: number
    open: number
    avgAcceptMinutes: number
    closeRate: number
  }
  pendingAdoptions: number
  pendingExpenses: number
}

export interface RoleView {
  id: number
  roleCode: string
  roleName: string
  description: string | null
}

export interface PermissionView {
  id: number
  permCode: string
  permName: string
  type: string
  path: string | null
}

export interface RoleMatrix {
  roleCode: string
  roleName: string
  menus: string[]
  apis: string[]
}

export interface RolesResponse {
  roles: RoleView[]
  permissions: PermissionView[]
  matrix: RoleMatrix[]
  allMenus: string[]
}

/** 找回密码（GET /auth/password/code 的返回） */
export interface ResetCodeResponse {
  userNo: string
  emailMasked: string
  codeTtlSeconds: number
  devCode?: string | null
}

/** 账号禁用申诉（POST /users/me/appeals） */
export interface AppealResponse {
  id: number
  userNo: string
  status: string
  statusLabel: string
  message: string
  createdAt: string
}

/** 角色权限在线配置的差异预览（PUT /roles/{id}/permissions） */
export interface PermissionDiff {
  roleCode: string
  roleName: string
  addedMenus: string[]
  removedMenus: string[]
  addedApis: string[]
  removedApis: string[]
  warnings: string[]
  applied: boolean
}

/** 异步导出任务（超过 5 万行时才出现） */
export interface ExportTask {
  taskId: string
  status: 'RUNNING' | 'DONE' | 'FAILED'
  rows: number
  fileName: string
  downloadUrl: string | null
  error: string | null
}

/** 同步导出被拒（异步受理）时的信封体 */
export interface ExportAccepted {
  taskId: string
  status: string
  rows: number
  pollUrl: string
}
