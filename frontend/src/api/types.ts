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
  /** 「我的任务」区块（Phase 4 起为真实数据） */
  myTasks: TaskCard[]
  /** 当前用户参与的项目（工作台「项目进度」区块，Phase 3 起为真实数据） */
  projectProgress: ProjectCard[]
}

// ---------------------------------------------------------------------------
// 项目协作（Phase 3）
// ---------------------------------------------------------------------------

export type ProjectStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED'
export type ProjectRole = 'OWNER' | 'DEPUTY_OWNER' | 'MEMBER'

export interface ProjectMemberView {
  userId: number
  username: string
  displayName: string
  avatarUrl?: string
  jobTitle?: string
  role: ProjectRole
  joinedAt: string
}

export interface ProjectCard {
  id: number
  name: string
  description?: string
  status: ProjectStatus
  progress: number
  plannedStartAt?: string
  plannedEndAt?: string
  owner?: ProjectMemberView
  deputyOwner?: ProjectMemberView
  memberCount: number
  myRole?: ProjectRole
  updatedAt: string
}

export interface ProjectPermissions {
  canEditInfo: boolean
  canChangeStatus: boolean
  canManageMembers: boolean
  canSetDeputy: boolean
  canTransferOwner: boolean
  canArchive: boolean
}

export interface ProjectDetail {
  id: number
  name: string
  description?: string
  status: ProjectStatus
  progress: number
  plannedStartAt?: string
  plannedEndAt?: string
  archivedAt?: string
  createdAt: string
  updatedAt: string
  owner?: ProjectMemberView
  deputyOwner?: ProjectMemberView
  members: ProjectMemberView[]
  myRole?: ProjectRole
  permissions: ProjectPermissions
}

export interface CreateProjectPayload {
  name: string
  description?: string
  plannedStartAt?: string
  plannedEndAt?: string
  memberUserIds?: number[]
  progress?: number
  status?: ProjectStatus
}

export interface UpdateProjectPayload {
  name: string
  description?: string
  plannedStartAt?: string
  plannedEndAt?: string
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

// ---------------------------------------------------------------------------
// 任务执行（Phase 4）
// ---------------------------------------------------------------------------

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
/** 系统统一类型：项目可自定义状态名称，但必须映射到这里（统计不依赖自定义名称） */
export type TaskStatusType = 'TODO' | 'ACTIVE' | 'REVIEW' | 'DONE' | 'CLOSED'
export type ProgressMode = 'MANUAL' | 'AUTO'
export type AssignmentState = 'ACTIVE' | 'PENDING_ASSIGNMENT' | 'REJECTED'

export interface TaskUserBrief {
  id: number
  username: string
  displayName: string
  avatarUrl?: string | null
  jobTitle?: string | null
}

export interface TaskStatusView {
  id: number
  name: string
  systemType: TaskStatusType
  sortOrder: number
}

/** 任务卡片：看板 / 任务列表 / 我的任务 / 子任务列表共用 */
export interface TaskCard {
  id: number
  projectId: number
  projectName: string
  parentId?: number | null
  title: string
  priority: TaskPriority
  status: TaskStatusView
  primaryAssignee: TaskUserBrief
  deputyAssignee?: TaskUserBrief | null
  progress: number
  progressMode: ProgressMode
  plannedStartAt?: string | null
  plannedEndAt?: string | null
  completedAt?: string | null
  blocked: boolean
  blockerCount: number
  overdue: boolean
  canManage: boolean
  canFullControl: boolean
  updatedAt: string
}

export interface TaskDependencyView {
  id: number
  dependsOnTaskId: number
  title: string
  statusName: string
  statusType: TaskStatusType
  finished: boolean
  primaryAssignee?: TaskUserBrief | null
}

export interface TaskDetailPermissions {
  canManage: boolean
  canFullControl: boolean
  canEditProgress: boolean
  canManageDependencies: boolean
  canManageCollaborators: boolean
  canReviewAssignment: boolean
}

export interface TaskDetail {
  id: number
  projectId: number
  projectName: string
  parentId?: number | null
  parentTitle?: string | null
  title: string
  description?: string | null
  priority: TaskPriority
  status: TaskStatusView
  primaryAssignee: TaskUserBrief
  deputyAssignee?: TaskUserBrief | null
  collaborators: TaskUserBrief[]
  progress: number
  progressMode: ProgressMode
  plannedStartAt?: string | null
  plannedEndAt?: string | null
  actualStartAt?: string | null
  completedAt?: string | null
  assignmentState: AssignmentState
  blocked: boolean
  blockerCount: number
  dependencies: TaskDependencyView[]
  subtasks: TaskCard[]
  createdAt: string
  updatedAt: string
  permissions: TaskDetailPermissions
}

export interface TaskBoard {
  projectId: number
  statuses: TaskStatusView[]
  tasks: TaskCard[]
  /** 待派发审核任务（仅项目负责人可见，成员看到空数组） */
  pendingAssignments: TaskCard[]
}

export interface CreateTaskPayload {
  title: string
  description?: string
  primaryAssigneeId?: number | null
  deputyAssigneeId?: number | null
  collaboratorUserIds?: number[]
  priority?: TaskPriority
  statusId?: number | null
  plannedStartAt?: string
  plannedEndAt?: string
  progressMode?: ProgressMode
}

export interface UpdateTaskPayload {
  title: string
  description?: string
  priority?: TaskPriority
  plannedStartAt?: string
  plannedEndAt?: string
  progressMode?: ProgressMode
}