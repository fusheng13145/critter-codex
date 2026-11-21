/**
 * 访问令牌的内存持有者。
 *
 * 为什么单独一个模块：令牌按要求不落 localStorage（避免 XSS 窃取），
 * 而 http 层与 store 层互相引用会形成循环依赖，用这个极小的中间层解开。
 */
let accessToken: string | null = null
let unauthorizedHandler: (() => void) | null = null

export function getToken(): string | null {
  return accessToken
}

export function setToken(token: string | null): void {
  accessToken = token
}

export function onUnauthorized(handler: () => void): void {
  unauthorizedHandler = handler
}

export function notifyUnauthorized(): void {
  if (unauthorizedHandler) {
    unauthorizedHandler()
  }
}
