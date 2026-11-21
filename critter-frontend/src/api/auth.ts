import { post, put } from './http'
import type { LoginResponse, RegisterPayload, RegisterResponse } from './types'

/** 认证接口（接口设计 §7.1、§6.1 第 1—5 项） */

export function login(account: string, password: string): Promise<LoginResponse> {
  return post<LoginResponse>('/auth/login', { account, password })
}

export function register(payload: RegisterPayload): Promise<RegisterResponse> {
  return post<RegisterResponse>('/auth/register', payload)
}

export function verifyRegister(email: string, code: string): Promise<{ userNo: string; status: string }> {
  return post('/auth/register/verify', { email, code })
}

/** 用 httpOnly Cookie 里的刷新令牌换新的访问令牌（页面刷新后恢复会话用） */
export function refreshToken(): Promise<{ accessToken: string; expiresIn: number }> {
  return post('/auth/refresh')
}

export function logout(): Promise<null> {
  return post<null>('/auth/logout')
}

/** 找回密码：发验证码（5 分钟有效；演示环境回传 devCode） */
export function requestPasswordCode(account: string): Promise<import('./types').ResetCodeResponse> {
  return post('/auth/password/code', { account })
}

/** 找回密码：凭验证码重置（错误 5 次锁定，规则与登录一致） */
export function resetPassword(account: string, code: string, newPassword: string): Promise<null> {
  return put<null>('/auth/password', { account, code, newPassword })
}
