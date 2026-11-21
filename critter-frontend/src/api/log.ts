import { get } from './http'
import type { LogItem, PageData } from './types'

/** 操作日志检索（P10 日志页，仅系统管理员） */

export function fetchLogs(params: {
  page?: number
  size?: number
  actorId?: number
  action?: string
  bizType?: string
  bizId?: string
  from?: string
  to?: string
  result?: string
}): Promise<PageData<LogItem>> {
  return get<PageData<LogItem>>('/logs', params)
}
