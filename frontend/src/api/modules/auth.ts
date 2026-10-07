import { apiRequest } from '@/api/client'
import type {
  ChangePasswordPayload,
  CurrentUser,
  LoginPayload,
  SessionSummary,
  SetupInitializePayload,
  SetupInitializeResult,
  SetupStatus,
} from '@/api/types'

/**
 * 认证相关接口。
 *
 * 说明：baseURL 已包含 /api，因此此处路径均为相对路径。
 */
export const authApi = {
  /** 引导 CSRF Token（确保 XSRF-TOKEN Cookie 存在） */
  csrf(): Promise<{ headerName: string; parameterName: string }> {
    return apiRequest({ url: '/csrf', method: 'get' })
  },

  login(payload: LoginPayload): Promise<CurrentUser> {
    return apiRequest({ url: '/auth/login', method: 'post', data: payload })
  },

  logout(): Promise<void> {
    return apiRequest({ url: '/auth/logout', method: 'post' })
  },

  me(): Promise<CurrentUser> {
    return apiRequest({ url: '/auth/me', method: 'get' })
  },

  changePassword(payload: ChangePasswordPayload): Promise<void> {
    return apiRequest({ url: '/auth/password', method: 'post', data: payload })
  },

  sessions(): Promise<SessionSummary[]> {
    return apiRequest({ url: '/auth/sessions', method: 'get' })
  },

  revokeSession(sessionId: number): Promise<void> {
    return apiRequest({ url: `/auth/sessions/${sessionId}`, method: 'delete' })
  },

  setupStatus(): Promise<SetupStatus> {
    return apiRequest({ url: '/setup/status', method: 'get' })
  },

  initializeSetup(payload: SetupInitializePayload): Promise<SetupInitializeResult> {
    return apiRequest({ url: '/setup/initialize', method: 'post', data: payload })
  },
}