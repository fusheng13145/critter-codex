import { del, get, post, put } from './http'
import type {
  CommentCreatePayload,
  CommentReplyPayload,
  CommentView,
  FollowRemoval,
  FollowView,
  InteractionBizType,
  PageData,
} from './types'

/** 留言与关注（D3 P1，接口 35–39）。 */

export interface CommentQuery extends Record<string, unknown> {
  bizType: InteractionBizType
  bizId: string
  page?: number
  size?: number
}

export function fetchComments(params: CommentQuery): Promise<PageData<CommentView>> {
  return get<PageData<CommentView>>('/comments', params)
}

export function createComment(payload: CommentCreatePayload): Promise<CommentView> {
  return post<CommentView>('/comments', payload)
}

export function replyComment(commentId: number, payload: CommentReplyPayload): Promise<CommentView> {
  return put<CommentView>(`/comments/${commentId}/reply`, payload)
}

export function followTarget(bizType: InteractionBizType, bizId: string): Promise<FollowView> {
  return post<FollowView>('/follows', { bizType, bizId })
}

export function unfollowTarget(followId: number): Promise<FollowRemoval> {
  return del<FollowRemoval>(`/follows/${followId}`)
}
