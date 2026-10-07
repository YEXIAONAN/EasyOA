/**
 * 领域类型定义（与后端 DTO 一一对应，禁止散落 any）。
 */

export type SystemRole = 'ROOT' | 'ADMIN' | 'MEMBER'

export interface CurrentUser {
  id: number
  username: string
  displayName: string
  systemRole: SystemRole
  avatarUrl?: string
  email?: string
  totpEnabled: boolean
  setupRequired: boolean
  lastLoginAt?: string
}

export interface SessionSummary {
  id: number
  ipAddress?: string
  userAgent?: string
  createdAt: string
  lastSeenAt: string
  expiresAt: string
  current: boolean
}

export interface SetupStatus {
  required: boolean
  organizationName: string
}

export interface SetupInitializePayload {
  organizationName: string
  username: string
  displayName: string
  password: string
}

export interface SetupInitializeResult {
  organizationName: string
  rootUsername: string
}

export interface LoginPayload {
  username: string
  password: string
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
}

export interface WorkspaceSummary {
  me: {
    id: number
    username: string
    displayName: string
    systemRole: SystemRole
    avatarUrl?: string
    lastLoginAt?: string
  }
  kpis: {
    myOpenTasks: number
    pendingApprovals: number
    activeProjects: number
    dueSoonTasks: number
  }
}