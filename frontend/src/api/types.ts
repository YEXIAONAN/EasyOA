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

// ---------------------------------------------------------------------------
// 组织与成员（Phase 2）
// ---------------------------------------------------------------------------

export type OrgUnitType = 'DEPARTMENT' | 'TEAM'
export type OrgUnitStatus = 'ACTIVE' | 'ARCHIVED'
export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface UserBrief {
  id: number
  username: string
  displayName: string
  avatarUrl?: string
  jobTitle?: string
  systemRole: SystemRole
  status: UserStatus
}

export interface OrgUnitBrief {
  id: number
  name: string
  type: OrgUnitType
  status: OrgUnitStatus
}

export interface OrgUnitTreeNode extends OrgUnitBrief {
  parentId: number | null
  sortOrder: number
  depth: number
  manager?: UserBrief
  memberCount: number
  children: OrgUnitTreeNode[]
}

export interface OrgUnitDetail {
  id: number
  parentId: number | null
  parentName?: string
  name: string
  type: OrgUnitType
  status: OrgUnitStatus
  sortOrder: number
  manager?: UserBrief
  memberCount: number
}

export interface OrgMember {
  userId: number
  username: string
  displayName: string
  avatarUrl?: string
  jobTitle?: string
  systemRole: SystemRole
  status: UserStatus
  primary: boolean
  joinedAt: string
}

export interface UserOrgMembershipView {
  orgUnit: OrgUnitBrief
  primary: boolean
  joinedAt: string
}

export interface MemberCard {
  id: number
  username: string
  displayName: string
  avatarUrl?: string
  jobTitle?: string
  systemRole: SystemRole
  status: UserStatus
  primaryOrgUnit?: OrgUnitBrief
  orgUnitCount: number
  joinedAt: string
}

export interface MemberContact {
  email?: string
  phone?: string
}

export interface MemberProfile {
  id: number
  username: string
  displayName: string
  avatarUrl?: string
  jobTitle?: string
  systemRole: SystemRole
  status: UserStatus
  bio?: string
  primaryOrgUnit?: OrgUnitBrief
  orgUnits: UserOrgMembershipView[]
  contactVisible: boolean
  contact?: MemberContact
  lastLoginAt?: string
  createdAt: string
}

export interface CreateOrgUnitPayload {
  name: string
  type: OrgUnitType
  parentId?: number | null
  sortOrder?: number
  managerUserId?: number | null
}

export interface UpdateOrgUnitPayload {
  name: string
  type: OrgUnitType
  sortOrder?: number
  managerUserId?: number | null
}

export interface CreateUserPayload {
  username: string
  displayName: string
  jobTitle?: string
  email?: string
  phone?: string
  systemRole: SystemRole
  initialPassword: string
  orgUnitId?: number | null
}

export interface UpdateMyProfilePayload {
  displayName: string
  email?: string
  phone?: string
  bio?: string
  avatarUrl?: string
}