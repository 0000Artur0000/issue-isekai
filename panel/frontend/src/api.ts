export type Role = 'ADMIN' | 'OPERATOR'

export type Me = {
  authenticated: boolean
  username: string | null
  role: Role | null
  csrfHeaderName: string
  csrfToken: string
}

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

// ponytail: module-level csrf so api() stays a plain function; refreshed by every fetchMe()
let csrf: { header: string; token: string } | null = null

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

export async function api<T = unknown>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!SAFE_METHODS.has(method) && csrf) {
    headers.set(csrf.header, csrf.token)
  }
  if (typeof init.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(path, { ...init, method, headers, credentials: 'same-origin' })
  if (response.status === 401 && path !== '/login') {
    window.location.assign('/login')
    throw new ApiError('Требуется вход', 401)
  }
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null
    throw new ApiError(body?.message ?? `Ошибка ${response.status}`, response.status)
  }
  return response.status === 204 ? (undefined as T) : (response.json() as Promise<T>)
}

export async function fetchMe(): Promise<Me> {
  const me = await api<Me>('/api/me')
  csrf = { header: me.csrfHeaderName, token: me.csrfToken }
  return me
}

export async function login(username: string, password: string): Promise<Me> {
  await api('/login', { method: 'POST', body: new URLSearchParams({ username, password }) })
  return fetchMe()
}

export async function logout(): Promise<Me> {
  await api('/logout', { method: 'POST' })
  return fetchMe()
}
