import { apiRequest, type PageResult } from '@/api/client'
import type {
  AuditLogItem,
  MfaEnrollment,
  MfaStatus,
  SecurityEventItem,
  SecurityPolicy,
  SensitiveOperationPreview,
  SensitiveOperationResult,
  SensitiveOperationType,
} from '@/api/types'

/**
 * 安全中心接口（Phase 8）。
 *
 * 路径约定（后端 baseURL 已包含 /api）：
 *   - 动态口令自助管理：任何登录用户；
 *   - 安全策略读取：ROOT / ADMIN；
 *   - 高危操作预览与执行：仅 ROOT（认证仪式在后端统一实现）。
 */
export const securityApi = {
  // --- 动态口令 ---
  mfaStatus(): Promise<MfaStatus> {
    return apiRequest({ url: '/security/mfa', method: 'get' })
  },

  startEnrollment(): Promise<MfaEnrollment> {
    return apiRequest({ url: '/security/mfa/enrollment', method: 'post' })
  },

  confirmEnrollment(code: string): Promise<MfaStatus> {
    return apiRequest({ url: '/security/mfa/enrollment/confirm', method: 'post', data: { code } })
  },

  cancelEnrollment(): Promise<void> {
    return apiRequest({ url: '/security/mfa/enrollment', method: 'delete' })
  },

  disableMfa(payload: { currentPassword: string; code: string }): Promise<MfaStatus> {
    return apiRequest({ url: '/security/mfa/disable', method: 'post', data: payload })
  },

  // --- 安全策略 ---
  policy(): Promise<SecurityPolicy> {
    return apiRequest({ url: '/security/settings', method: 'get' })
  },

  // --- 高危操作 ---
  previewOperation(payload: {
    type: SensitiveOperationType
    targetId?: number | null
    payload?: Record<string, unknown>
  }): Promise<SensitiveOperationPreview> {
    return apiRequest({ url: '/security/sensitive-operations/preview', method: 'post', data: payload })
  },

  executeOperation(payload: {
    type: SensitiveOperationType
    targetId?: number | null
    currentPassword: string
    totpCode: string
    reason: string
    confirmation: string
    payload?: Record<string, unknown>
  }): Promise<SensitiveOperationResult> {
    return apiRequest({ url: '/security/sensitive-operations/execute', method: 'post', data: payload })
  },

  // --- 安全事件（只读） ---
  securityEvents(params: {
    eventType?: string
    severity?: string
    page?: number
    size?: number
  } = {}): Promise<PageResult<SecurityEventItem>> {
    return apiRequest({ url: '/security-events', method: 'get', params })
  },
}

/** 审计日志（只读、Append Only） */
export const auditLogApi = {
  list(params: {
    action?: string
    actorUserId?: number
    resourceType?: string
    riskLevel?: string
    from?: string
    to?: string
    page?: number
    size?: number
  } = {}): Promise<PageResult<AuditLogItem>> {
    return apiRequest({ url: '/audit-logs', method: 'get', params })
  },
}
