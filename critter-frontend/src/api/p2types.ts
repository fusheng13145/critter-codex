/** 领养（动物离校）交接单 */
export interface HandoverView {
  id: number
  handoverNo: string
  animalId: number
  animalCode: string
  adopterName: string
  contact: string
  guardianConsent: boolean
  residenceNote: string
  status: 'ACTIVE' | 'RETURNED' | 'CLOSED'
  adoptedAt: string
  completedFollowups: number
  totalFollowups: number
}

export interface FollowupView {
  id: number
  seqNo: number
  dueDate: string
  status: 'PENDING' | 'DONE' | 'MISSED'
  result: 'NORMAL' | 'ABNORMAL' | null
  note: string | null
  completedAt: string | null
  overdue: boolean
}

export interface HandoverDetail {
  handover: HandoverView
  followups: FollowupView[]
}

/** 疫苗与用药计划 */
export interface CarePlanView {
  id: number
  planNo: string
  animalCode: string
  type: 'VACCINE' | 'MEDICATION'
  itemName: string
  dosage: string | null
  planDate: string
  status: 'PLANNED' | 'DONE' | 'CANCELED'
  sourceVisitId: number | null
  result: string | null
  completedAt: string | null
  note: string | null
  overdue: boolean
}

/** 志愿排班 */
export interface ShiftView {
  id: number
  shiftNo: string
  title: string
  pointId: number | null
  shiftDate: string
  startTime: string
  endTime: string
  capacity: number
  booked: number
  status: 'OPEN' | 'CLOSED' | 'CANCELED'
  signed: boolean
  checkedIn: boolean
  absent: boolean
}

export interface SuspensionView {
  status: string
  absenceStreak: number
  suspendedAt: string | null
  canAppeal: boolean
  pendingAppeal: boolean
}

export interface AppealView {
  id: number
  userName: string
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  reviewReason: string | null
  decidedAt: string | null
  createdAt: string
}

export interface ShiftSignupView {
  id: number
  userId: number
  userName: string
  signedAt: string
  checkinAt: string | null
  absent: boolean
  hours: number | null
}

export interface AbsenceResult {
  absenceStreak: number
  suspended: boolean
  message: string
}

/** 认养人权益 */
export interface AdoptionPostView {
  id: number
  title: string
  content: string
  authorName: string | null
  createdAt: string
}

export interface AdoptionVisitView {
  id: number
  visitDate: string
  timeSlot: string
  note: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELED'
  reviewReason: string | null
  createdAt: string
}

export interface AdoptionBenefitView {
  adoptionNo: string
  animalCode: string
  animalName: string | null
  startDate: string | null
  endDate: string | null
  status: string
  posts: AdoptionPostView[]
  visits: AdoptionVisitView[]
}

export interface VisitAdminView {
  id: number
  adoptionNo: string
  animalCode: string
  animalName: string | null
  visitDate: string
  timeSlot: string
  note: string | null
  status: string
  adopterName: string | null
  createdAt: string
}
