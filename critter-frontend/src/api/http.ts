import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { getToken, notifyUnauthorized, setToken } from './token'
import type { TokenResponse } from './types'

/**
 * 统一请求层（接口设计 §9）：
 * 1) 单实例，baseURL 走 /api/v1（开发环境由 Vite 代理到 8080），超时 15 秒；
 * 2) 请求注入 Bearer 与 X-Request-Id；
 * 3) 响应统一解包 data；code !== 0 抛 BizError，页面只需 try/catch；
 * 4) 401 + 30102 时静默刷新一次，失败才跳登录。
 */

interface Envelope<T> {
  code: number
  message: string
  data: T
  requestId: string | null
  timestamp: string
}

export interface FieldError {
  field: string
  reason: string
}

/** 业务异常：页面按 code 分支，30301 时用 fieldErrors 标红具体输入框。 */
export class BizError extends Error {
  code: number
  data: unknown

  constructor(code: number, message: string, data: unknown = null) {
    super(message)
    this.name = 'BizError'
    this.code = code
    this.data = data
  }

  get fieldErrors(): FieldError[] {
    return Array.isArray(this.data) ? (this.data as FieldError[]) : []
  }
}

const service: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  config.headers.set('X-Request-Id', createRequestId())
  return config
})

let refreshPromise: Promise<string | null> | null = null

/** 静默刷新：同一时刻只发一次，避免并发请求把刷新令牌轮换掉两次。 */
async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = axios
      .post<Envelope<TokenResponse>>('/api/v1/auth/refresh', null, { withCredentials: true, timeout: 10000 })
      .then((response) => {
        const body = response.data
        if (body.code === 0 && body.data?.accessToken) {
          setToken(body.data.accessToken)
          return body.data.accessToken
        }
        return null
      })
      .catch(() => null)
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

service.interceptors.response.use(
  (response) => {
    const body = response.data as Envelope<unknown>
    if (body && typeof body.code === 'number') {
      if (body.code === 0) {
        return body.data
      }
      throw new BizError(body.code, body.message, body.data)
    }
    return response.data
  },
  async (error) => {
    const response = error?.response
    const original = error?.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined
    let body = response?.data as Envelope<unknown> | undefined
    // 二进制下载（导出）失败时后端仍回 JSON 信封，这里把 Blob 还原成信封再取 code
    if (body instanceof Blob) {
      body = await readBlobEnvelope(body)
    }

    if (response?.status === 401 && original && !original._retried && !isAuthEndpoint(original.url)) {
      const token = await refreshAccessToken()
      if (token) {
        original._retried = true
        original.headers = { ...(original.headers ?? {}), Authorization: `Bearer ${token}` }
        return service.request(original)
      }
      notifyUnauthorized()
    }

    if (body && typeof body.code === 'number') {
      throw new BizError(body.code, body.message, body.data)
    }
    if (response?.status === 401) {
      notifyUnauthorized()
      throw new BizError(20001, '未登录或登录已过期')
    }
    throw new BizError(50001, '网络异常，请检查后端是否已启动')
  },
)

function isAuthEndpoint(url?: string): boolean {  if (!url) return false
  return url.includes('/auth/login') || url.includes('/auth/refresh') || url.includes('/auth/register')
}

function createRequestId(): string {
  const cryptoObj = globalThis.crypto
  if (cryptoObj && typeof cryptoObj.randomUUID === 'function') {
    return cryptoObj.randomUUID().replace(/-/g, '')
  }
  return `${Date.now()}${Math.random().toString(16).slice(2, 10)}`
}

async function readBlobEnvelope(blob: Blob): Promise<Envelope<unknown> | undefined> {
  try {
    const parsed = JSON.parse(await blob.text())
    return typeof parsed?.code === 'number' ? (parsed as Envelope<unknown>) : undefined
  } catch {
    return undefined
  }
}

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return (await service.get(url, { params })) as unknown as T
}

/** 二进制下载（统计导出）：xlsx 或"异步受理"的 JSON 都以 Blob 返回，调用方按 blob.type 分流。 */
export async function getBlob(url: string, params?: Record<string, unknown>): Promise<Blob> {
  return (await service.get(url, { params, responseType: 'blob' })) as unknown as Blob
}

export async function post<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return (await service.post(url, body, config)) as unknown as T
}

export async function put<T>(url: string, body?: unknown, params?: Record<string, unknown>): Promise<T> {
  return (await service.put(url, body, { params })) as unknown as T
}

export async function del<T>(url: string): Promise<T> {
  return (await service.delete(url)) as unknown as T
}

export default service
