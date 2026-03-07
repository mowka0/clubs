import { useAuthStore } from '../store/authStore'
import { getInitData } from '../telegram/sdk'

const BASE_URL = '/api'

type RequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown
  skipAuth?: boolean
}

class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public data?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

async function authenticate(): Promise<string | null> {
  const initData = getInitData()
  if (!initData) return null
  try {
    const response = await fetch(`${BASE_URL}/auth/telegram`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ initData }),
    })
    if (!response.ok) return null
    const data = await response.json() as { token: string; user: { id: string; telegramId: number; username: string | null; firstName: string | null; lastName: string | null; avatarUrl: string | null } }
    const { token, user } = data
    if (token && user) {
      useAuthStore.getState().setAuth(user, token)
      return token
    }
    return null
  } catch {
    return null
  }
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, skipAuth = false, ...init } = options

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string>),
  }

  if (!skipAuth) {
    const token = useAuthStore.getState().token
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
  }

  const fetchOptions: RequestInit = {
    ...init,
    headers,
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  }

  let response = await fetch(`${BASE_URL}${path}`, fetchOptions)

  if (response.status === 401 && !skipAuth) {
    const newToken = await authenticate()
    if (newToken) {
      headers['Authorization'] = `Bearer ${newToken}`
      response = await fetch(`${BASE_URL}${path}`, { ...fetchOptions, headers })
    }
  }

  if (!response.ok) {
    let errorData: unknown
    try {
      errorData = await response.json()
    } catch {
      errorData = null
    }
    const message =
      (errorData as { message?: string })?.message ?? `HTTP ${response.status}`
    throw new ApiError(response.status, message, errorData)
  }

  if (response.status === 204) {
    return undefined as unknown as T
  }

  return response.json() as Promise<T>
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'GET' }),

  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'POST', body }),

  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'PUT', body }),

  delete: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'DELETE' }),
}

export { ApiError, authenticate }
