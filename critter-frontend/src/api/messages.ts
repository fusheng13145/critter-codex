import { get, put } from './http'
import type {
  MessageItem,
  MessageReadResult,
  MessageUnreadCount,
  PageData,
} from './types'

/** 消息中心（D3）：列表、四类未读、批量已读。 */

export function fetchMessages(params: {
  readFlag?: boolean
  type?: string
  page?: number
  size?: number
}): Promise<PageData<MessageItem>> {
  return get<PageData<MessageItem>>('/messages', params)
}

export function fetchUnreadCounts(): Promise<MessageUnreadCount> {
  return get<MessageUnreadCount>('/messages/unread')
}

export function markMessagesRead(ids: number[]): Promise<MessageReadResult> {
  return put<MessageReadResult>('/messages/read', { ids })
}
