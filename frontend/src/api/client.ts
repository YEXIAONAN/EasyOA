import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'

import { ApiError } from './errors'

/** 后端统一响应结构 */
export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data?: T
  requestId?: string
}

/** 统一分页结构 */
export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null

/** 注册全局 401 处理器（由 auth store 注入，避免循环依赖） */
export function onUnauthenticated(handler: UnauthorizedHandler): void {
  unauthorizedHandler = handler
}

export const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? '/api',
  timeout: 20000,
  withCredentials: true,
  // Session + CSRF：Cookie 由后端下发（非 HttpOnly），Axios 自动回填请求头
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiEnvelope<unknown>>) => {
    const response = error.response
    if (!response) {
      if (error.code === 'ECONNABORTED') {
        throw new ApiError('TIMEOUT', '请求超时，请稍后重试')
      }
      throw new ApiError('NETWORK_ERROR', '网络连接失败，请检查网络后重试')
    }

    const payload = response.data
    const apiError = new ApiError(
      payload?.code ?? fallbackCode(response.status),
      payload?.message ?? fallbackMessage(response.status),
      {
        status: response.status,
        requestId: payload?.requestId,
        fields: extractFields(payload?.data),
      },
    )

    if (apiError.isUnauthenticated) {
      unauthorizedHandler?.()
    }
    throw apiError
  },
)

/** 发起请求并解包 ApiResponse */
export async function apiRequest<T>(config: AxiosRequestConfig): Promise<T> {
  const { data } = await http.request<ApiEnvelope<T>>(config)
  return unwrap(data)
}

/**
 * 上传（multipart/form-data）。
 *
 * 显式将 Content-Type 置空：交给浏览器/axios 依据 FormData 自动生成带 boundary 的
 * multipart 请求头（实例默认的 application/json 不能用于文件上传）。
 */
export async function apiUpload<T>(url: string, form: FormData): Promise<T> {
  const { data } = await http.post<ApiEnvelope<T>>(url, form, {
    headers: { 'Content-Type': undefined },
  })
  return unwrap(data)
}

function unwrap<T>(data: ApiEnvelope<T>): T {
  if (!data || typeof data !== 'object' || !('success' in data)) {
    throw new ApiError('INVALID_RESPONSE', '服务返回了无法识别的数据')
  }
  if (!data.success) {
    throw new ApiError(data.code ?? 'UNKNOWN', data.message ?? '请求失败', {
      requestId: data.requestId,
      fields: extractFields(data.data),
    })
  }
  return data.data as T
}

function extractFields(data: unknown): Array<{ field: string; message: string }> | undefined {
  if (!data || typeof data !== 'object') return undefined
  const fields = (data as { fields?: unknown }).fields
  if (!Array.isArray(fields)) return undefined
  return fields
    .filter((item): item is { field: string; message: string } => {
      return !!item && typeof item === 'object' && 'field' in item && 'message' in item
    })
    .map((item) => ({ field: String(item.field), message: String(item.message) }))
}

function fallbackCode(status: number): string {
  switch (status) {
    case 400:
      return 'INVALID_REQUEST'
    case 401:
      return 'UNAUTHENTICATED'
    case 403:
      return 'FORBIDDEN'
    case 404:
      return 'NOT_FOUND'
    case 409:
      return 'CONFLICT'
    case 429:
      return 'RATE_LIMITED'
    default:
      return status >= 500 ? 'INTERNAL_ERROR' : 'UNKNOWN'
  }
}

function fallbackMessage(status: number): string {
  switch (status) {
    case 400:
      return '请求参数不合法'
    case 401:
      return '未登录或会话已失效'
    case 403:
      return '没有权限执行该操作'
    case 404:
      return '资源不存在或无权访问'
    case 409:
      return '操作与当前数据状态冲突'
    case 429:
      return '操作过于频繁，请稍后再试'
    default:
      return status >= 500 ? '服务器内部错误，请稍后再试' : '请求失败'
  }
}