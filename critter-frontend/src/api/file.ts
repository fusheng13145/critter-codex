import { get, post } from './http'
import type { UploadResult } from './types'

/** 附件：两步上传的第一步（先拿 fileId，再挂业务对象） */

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

/** 公开照片可直接给 <img src>，私有附件需带令牌（前端用 blob 方式取） */
export function fileContentUrl(fileId: string): string {
  return `/api/v1/files/${fileId}/content`
}
