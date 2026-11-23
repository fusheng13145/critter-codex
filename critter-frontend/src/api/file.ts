import { get, post, put } from './http'
import type { UploadResult } from './types'

/** 附件：两步上传的第一步（先拿 fileId，再挂业务对象） */

export interface FileAuditItem {
  fileId: string
  bizType: string
  bizId: string | null
  name: string
  mime: string
  size: number
  auditStatus: string
  url: string
  contentUrl: string
  createdAt: string
}

export interface FileAuditPage {
  list: FileAuditItem[]
  page: { page: number; size: number; total: number; pages: number }
}

export async function uploadFile(file: File, bizType: string): Promise<UploadResult> {
  const form = new FormData()
  form.append('file', file)
  form.append('bizType', bizType)
  // 让浏览器自己带 multipart boundary，手写 Content-Type 会丢 boundary
  return post<UploadResult>('/files', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export function fetchFileMeta(fileId: string): Promise<unknown> {
  return get(`/files/${fileId}`)
}

/** 附件审核工作台列表（管理员）：auditStatus=PENDING 是"先审后发"的待办池 */
export function fetchFilesForAudit(params: {
  page?: number
  size?: number
  auditStatus?: string
  bizType?: string
}): Promise<FileAuditPage> {
  return get<FileAuditPage>('/files', params)
}

/** 审核动作：PASS 在公开页上架，REJECT 立即下架 */
export function auditFile(fileId: string, decision: 'PASS' | 'REJECT'): Promise<FileAuditItem> {
  return put<FileAuditItem>(`/files/${fileId}/audit`, null, { decision })
}

/** 公开照片可直接给 <img src>，私有附件需带令牌（前端用 blob 方式取） */
export function fileContentUrl(fileId: string): string {
  return `/api/v1/files/${fileId}/content`
}

