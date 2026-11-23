import { get, post } from './http'
import type { NoticeCreatePayload, NoticeDetail, NoticeItem, PageData } from './types'

/** 公告（D3）：公开列表 / 公开详情 / 管理员发布。 */

export function fetchNotices(params: {
  category?: string
  page?: number
  size?: number
}): Promise<PageData<NoticeItem>> {
  return get<PageData<NoticeItem>>('/notices', params)
}

export function fetchNotice(id: number): Promise<NoticeDetail> {
  return get<NoticeDetail>(`/notices/${id}`)
}

export function fetchNoticeHighlights(limit = 5): Promise<NoticeItem[]> {
  return get<NoticeItem[]>('/notices/highlights', { limit })
}

export function publishNotice(payload: NoticeCreatePayload): Promise<NoticeItem> {
  return post<NoticeItem>('/notices', payload)
}
