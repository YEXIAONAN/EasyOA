/**
 * 统一 API 错误。
 *
 * 前端只依赖后端返回的 `code` 做逻辑判断，绝不依赖 Java 异常文本。
 */
export class ApiError extends Error {
  readonly code: string
  readonly requestId?: string
  readonly status?: number
  /** 校验类错误（VALIDATION_FAILED）的字段级提示 */
  readonly fields?: Array<{ field: string; message: string }>

  constructor(
    code: string,
    message: string,
    options: { status?: number; requestId?: string; fields?: Array<{ field: string; message: string }> } = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = options.status
    this.requestId = options.requestId
    this.fields = options.fields
  }

  get isUnauthenticated(): boolean {
    return this.code === 'UNAUTHENTICATED' || this.code === 'SESSION_EXPIRED' || this.status === 401
  }

  get isForbidden(): boolean {
    return this.code === 'FORBIDDEN' || this.status === 403
  }

  get isNotFound(): boolean {
    return this.code === 'NOT_FOUND' || this.status === 404
  }

  get isConflict(): boolean {
    return this.status === 409
  }

  get isRateLimited(): boolean {
    return this.status === 429
  }
}