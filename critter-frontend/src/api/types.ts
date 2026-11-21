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

/** 认养方案（GET /plans，公开） */
export interface AdoptionPlan {
  planId: number
  planName: string
  period: 'MONTH' | 'QUARTER'
  amount: number
  benefits: string
}

/** 认养单列表行 */
export interface AdoptionSummary {
  adoptionNo: string
  userId: number
  userName: string
  animalCode: string
  animalName: string | null
  planName: string
  amount: number
  period: 'MONTH' | 'QUARTER'
  status: string
  contactMasked: string
  startDate: string | null
  endDate: string | null
  certificateNo: string | null
  createdAt: string
}

export interface AdoptionPaymentRecord {
  paymentNo: string
  paidAmount: number
  paidAt: string
  voucherFileIds: string[]
  note: string | null
  verifiedAt: string | null
}

export interface AdoptionDetail extends AdoptionSummary {
  auditReason: string | null
  payDeadline: string | null
  payments: AdoptionPaymentRecord[]
}

export interface AdoptionCertificate {
  adoptionNo: string
  certificateNo: string
  adopterName: string
  animalCode: string
  animalName: string | null
  planName: string
  startDate: string
  endDate: string
}

export interface AuditedAdoption {
  adoptionNo: string
  status: string
  payDeadline: string | null
}

export interface AdoptionPaymentResult {
  adoptionNo: string
  status: string
  startDate: string
  endDate: string
  certificateNo: string
  animalStatus: string
}

export interface TerminatedAdoption {
  adoptionNo: string
  status: string
  endDate: string
  animalStatus: string
}

export type MessageType =
  | 'ADOPTION_EXPIRY'
  | 'DONATION_PROGRESS'
  | 'ORDER_PROGRESS'
  | 'APPROVAL_RESULT'

export interface MessageItem {
  id: number
  type: MessageType
  title: string
  bizType: string | null
  bizId: string | null
  readFlag: boolean
  readAt: string | null
  createdAt: string
}

export interface MessageUnreadCount {
  adoptionExpiry: number
  donationProgress: number
  orderProgress: number
  approvalResult: number
}

export interface MessageReadFailure {
  id: string
  code: number
  message: string
}

export interface MessageReadResult {
  success: number
  failed: MessageReadFailure[]
}

/* ==========================================================================
 * D2 域（档案 / 群体 / 物种 / 上报 / 工单 / 项目 / 捐赠）
 * 字段与后端 service 层 record 一一对应；金额一律整数分。
 * ========================================================================== */

export type AnimalCategory = 'CAT' | 'DOG' | 'OTHER'
export type AnimalGender = 'MALE' | 'FEMALE' | 'UNKNOWN'
export type AnimalStatusCode =
  | 'ON_CAMPUS'
  | 'TREATING'
  | 'HOSPITALIZED'
  | 'TO_RELEASE'
  | 'RELEASED'
  | 'ADOPTED'
  | 'MISSING'
  | 'DECEASED'

export interface PointRef {
  id: number
  name: string
}

export interface AnimalSummary {
  code: string
  category: AnimalCategory
  name: string | null
  gender: AnimalGender
  color: string
  estimatedAge: number | null
  point: PointRef | null
  status: AnimalStatusCode
  neutered: boolean
  adopted: boolean
  coverUrl: string | null
  updatedAt: string
}

export interface AnimalDetail {
  code: string
  category: AnimalCategory
  name: string | null
  gender: AnimalGender
  color: string
  estimatedAge: number | null
  features: string
  point: PointRef | null
  status: AnimalStatusCode
  neutered: boolean
  neuteredAt: string | null
  adopted: boolean
  coverUrl: string | null
  /** 仅管理员视图返回，公开视图不出现该字段 */
  internalNote?: string | null
  createdAt: string
  updatedAt: string
}

export interface CreatedAnimal {
  code: string
  status: AnimalStatusCode
  createdAt: string
}

export interface AnimalChangeLogView {
  field: string
  beforeValue: string | null
  afterValue: string | null
  reason: string
  operatorId: number | null
  createdAt: string
}

export interface HealthRecordView {
  id: number
  type: 'VACCINE' | 'DEWORM' | 'WEIGHT' | 'HISTORY'
  typeLabel: string
  recordedAt: string
  result: string
  nextDate: string | null
  reminderDue: boolean
  daysUntilNext: number | null
  operatorId: number | null
  createdAt: string
}

export interface CreatedHealthRecord {
  healthRecordId: number
}

export interface SpeciesRef {
  id: number
  commonName: string
  category: string
}

export interface GroupSummary {
  groupCode: string
  species: SpeciesRef
  features: string
  minSize: number
  maxSize: number
  sizeBasis: string
  status: 'ACTIVE' | 'UNCONFIRMED' | 'GONE'
  statusLabel: string
  points: PointRef[]
  lastSeenAt: string | null
}

export interface GroupDetail extends GroupSummary {
  createdAt: string
  updatedAt: string
}

export interface CreatedGroup {
  groupCode: string
  status: GroupSummary['status']
}

export interface SpeciesView {
  id: number
  commonName: string
  scientificName: string
  category: string
  protectionLevel: string
  note: string | null
}

export interface PointView {
  id: number
  name: string
  area: string
  x: number
  y: number
  enabled: boolean
  note: string | null
}

export interface OccurrenceView {
  id: number
  speciesId: number
  speciesName: string
  point: PointRef | null
  occurredAt: string
  scale: 'FEW' | 'SMALL_HERD' | 'LARGE_HERD'
  scaleLabel: string
  habitat: string | null
  fileIds: string[]
  recorderId: number | null
  createdAt: string
}

export interface ProjectSummary {
  projectNo: string
  name: string
  targetType: 'ANIMAL' | 'GROUP' | 'MIXED'
  targetAmount: number
  raisedAmount: number
  spentAmount: number
  progressPercent: number
  startDate: string
  endDate: string
  daysRemaining: number
  status: string
  donationCount: number
  coverFileId: string | null
  createdAt: string
}

export interface ProjectBudgetItem {
  subject: string
  amount: number
  note: string | null
}

export interface ProjectDonation {
  donationNo: string
  donorDisplay: string
  amount: number
  message: string | null
  verifiedAt: string | null
}

export interface ProjectDetail {
  projectNo: string
  name: string
  purpose: string
  targetType: 'ANIMAL' | 'GROUP' | 'MIXED'
  targetAmount: number
  raisedAmount: number
  spentAmount: number
  progressPercent: number
  startDate: string
  endDate: string
  daysRemaining: number
  status: string
  coverFileId: string | null
  auditReason: string | null
  auditorId: number | null
  donationCount: number
  budgetTotal: number
  budget: ProjectBudgetItem[]
  donations: ProjectDonation[]
  createdAt: string
  updatedAt: string
  allowedActions: string[]
}

export interface CreatedProject {
  projectNo: string
  status: string
  createdAt: string
}

export interface ProjectAuditResult {
  projectNo: string
  status: string
  auditReason: string | null
  auditorId: number | null
  auditedAt: string
  allowedActions: string[]
}

export interface DonationSummary {
  donationNo: string
  projectNo: string
  projectName: string
  userId: number
  donorDisplay: string
  amount: number
  anonymous: boolean
  message: string | null
  voucherFileIds: string[]
  status: 'PENDING_VERIFY' | 'VERIFIED' | 'ABNORMAL'
  verifierId: number | null
  verifiedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CreatedDonation {
  donationNo: string
  status: 'PENDING_VERIFY'
  note: string
}

export interface ProjectProgress {
  projectNo: string
  raisedAmount: number
  targetAmount: number
  progressPercent: number
}

export interface DonationConfirmation {
  donationNo: string
  status: 'VERIFIED' | 'ABNORMAL'
  amount: number
  verifiedAt: string | null
  progress: ProjectProgress | null
  note: string
}

export interface BatchFailure {
  id: string
  code: number
  message: string
}

export interface BatchConfirmResult {
  success: number
  failed: BatchFailure[]
}

export interface ReportSummary {
  id: number
  reportNo: string
  type: 'INJURED' | 'NEW_ARRIVAL' | 'ABANDONED' | 'DISTURB' | 'DEAD'
  pointId: number
  pointName: string
  description: string
  photos: string[]
  status: 'PENDING' | 'PROCESSING' | 'CLOSED'
  overdue: boolean
  createdAt: string
  deadline: string
  reporterId: number
}

export interface ReportDetail extends ReportSummary {
  animalId: number | null
  animalCode: string | null
  contact: string | null
  orderNo: string | null
}

export interface CreatedReport {
  reportNo: string
  status: 'PENDING'
  deadline: string
  duplicated: boolean
  duplicateOf: string | null
}

export interface AcceptedReport {
  orderNo: string
  reportNo: string
  reportStatus: 'PROCESSING'
  orderStatus: string
  deadline: string
}

export interface ClosedReport {
  reportNo: string
  status: 'CLOSED'
  closeReason: string
}

export type OrderStatusCode =
  | 'PENDING'
  | 'PROCESSING'
  | 'TO_REVISIT'
  | 'TIMEOUT'
  | 'CLOSED'

export interface OrderSummary {
  orderNo: string
  reportId: number
  reportNo: string
  type: ReportSummary['type']
  pointId: number
  pointName: string
  status: OrderStatusCode
  overdue: boolean
  createdAt: string
  deadlineAt: string
  acceptedAt: string | null
  handlerId: number | null
}

export interface OrderTimelineItem {
  action: string
  actionLabel: string
  at: string
  actorId: number | null
  actorRole: string | null
  result: string
  fromStatus: OrderStatusCode | null
  toStatus: OrderStatusCode | null
  note: string | null
}

export interface OrderDetail extends OrderSummary {
  description: string
  photos: string[]
  animalId: number | null
  animalCode: string | null
  acceptorId: number | null
  disposeNote: string | null
  closeReason: string | null
  allowed: OrderStatusCode[]
  timeline: OrderTimelineItem[]
}

export interface OrderTransitionResult {
  orderNo: string
  status: OrderStatusCode
  allowed: OrderStatusCode[]
  acceptedAt: string | null
  deadlineAt: string
  handlerId: number | null
  disposeNote: string | null
  closeReason: string | null
}

/** 合作医院（GET /clinics）；priceNote 为示例报价，不作真实价格承诺 */
export interface ClinicView {
  clinicId: number
  clinicName: string
  contact: string | null
  phone: string | null
  address: string | null
  priceNote: string | null
}

/** 新增合作医院（POST /clinics），仅管理员可用 */
export interface ClinicCreatePayload {
  clinicName: string
  contact?: string
  phone?: string
  address?: string
  priceNote?: string
}

export interface VisitCreatePayload {
  animalId: number
  orderId?: number | null
  clinicId: number
  visitedAt: string
  complaint?: string
  diagnosis?: string
  treatment?: string
  cost: number
  fileIds?: string[]
  sourceNote?: string
}

export interface VisitRegistration {
  visitNo: string
  animalId: number
  animalCode: string
  animalName: string | null
  clinicId: number
  clinicName: string
  visitedAt: string
  cost: number
}

/** 纠错就诊（PUT /visits/{no}），reason 只写审计日志 */
export interface VisitUpdatePayload {
  complaint?: string
  diagnosis?: string
  treatment?: string
  cost: number
  fileIds?: string[]
  reason: string
}

/** 就诊详情（GET /visits/{no}）；cost 为整数分 */
export interface VisitDetail {
  visitNo: string
  animalId: number
  animalCode: string
  animalName: string | null
  orderId: number | null
  orderNo: string | null
  clinicId: number
  clinicName: string
  visitedAt: string
  complaint: string | null
  diagnosis: string | null
  treatment: string | null
  cost: number
  fileIds: string[]
  sourceNote: string | null
  createdAt: string
}

/** 公告分类英文码：进展 / 公示 / 科普 */
export type NoticeCategory = 'PROGRESS' | 'PUBLICITY' | 'SCIENCE'

/** 公告列表项（GET /notices），不含正文 */
export interface NoticeItem {
  noticeId: number
  noticeNo: string
  category: NoticeCategory
  title: string
  pinned: boolean
  publishedAt: string
}

/** 公告详情（GET /notices/{id}） */
export interface NoticeDetail extends NoticeItem {
  content: string
  authorId: number | null
  authorName: string | null
  createdAt: string
}

export interface NoticeCreatePayload {
  category: NoticeCategory
  title: string
  content: string
  pinned?: boolean
}
