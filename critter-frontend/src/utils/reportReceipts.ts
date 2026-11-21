/**
 * 上报回执（本机留存）。
 *
 * 为什么需要本地回执：后端当前只开放管理员上报列表，没有 /reports/mine，
 * 而创建响应只返回 reportNo、不返回数据库 id，前端无法按编号反查详情。
 * 因此提交成功后把回执写入 localStorage，仅作为“我交过什么”的凭证，
 * 状态以管理员受理结果为准；等后端补上报人自查接口后再替换为真实列表。
 */

export interface ReportReceipt {
  reportNo: string
  type: string
  typeLabel: string
  pointId: number
  pointName: string
  description: string
  photos: string[]
  animalCode: string | null
  contact: string | null
  status: string
  deadline: string
  duplicated: boolean
  duplicateOf: string | null
  createdAt: string
}

const KEY_PREFIX = 'critter.report.receipts.'
const MAX_RECEIPTS = 30

function storageKey(owner: string): string {
  return `${KEY_PREFIX}${owner || 'anonymous'}`
}

export function loadReportReceipts(owner: string): ReportReceipt[] {
  try {
    const raw = localStorage.getItem(storageKey(owner))
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as ReportReceipt[]) : []
  } catch {
    return []
  }
}

export function saveReportReceipt(owner: string, receipt: ReportReceipt): void {
  const list = loadReportReceipts(owner)
  const next = [receipt, ...list.filter((item) => item.reportNo !== receipt.reportNo)].slice(0, MAX_RECEIPTS)
  localStorage.setItem(storageKey(owner), JSON.stringify(next))
}

export function clearReportReceipts(owner: string): void {
  localStorage.removeItem(storageKey(owner))
}
