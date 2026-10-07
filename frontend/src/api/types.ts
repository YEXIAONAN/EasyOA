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
  /** 「待我审批」区块（Phase 6 起为真实数据；必须进入详情处理，不支持一键批准） */
  pendingApprovals: ApprovalCard[]
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
  /** 是否可查看任务下所有评论的编辑历史（作者本人、项目负责人或系统管理员） */
  canViewCommentHistory: boolean
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

// ---------------------------------------------------------------------------
// 评论与文件（Phase 5）
// ---------------------------------------------------------------------------

export type FileResourceType = 'TASK' | 'COMMENT'

/** 附件元数据（统一下载入口 /api/files/{id}，禁止作为公开静态资源） */
export interface FileMeta {
  id: number
  originalName: string
  mimeType: string
  size: number
  sha256: string
  uploaderId: number
  uploaderName: string
  resourceType: FileResourceType
  resourceId: number
  downloadUrl: string
  createdAt: string
}

/** 评论（顶层评论携带一级回复；撤回时 content 不返回，显示撤回占位） */
export interface CommentView {
  id: number
  taskId: number
  parentId?: number | null
  author: TaskUserBrief
  content?: string | null
  withdrawn: boolean
  edited: boolean
  mentions: TaskUserBrief[]
  attachments: FileMeta[]
  replies: CommentView[]
  createdAt: string
  updatedAt: string
}

export interface CommentVersionView {
  versionNo: number
  content: string
  editor?: TaskUserBrief | null
  createdAt: string
}

export interface CreateCommentPayload {
  content: string
  parentId?: number | null
  mentionUserIds?: number[]
  attachmentFileIds?: number[]
}

// ---------------------------------------------------------------------------
// 审批（Phase 6）
// ---------------------------------------------------------------------------

export type ApprovalStatusType = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'RETURNED' | 'CANCELLED'
export type ApprovalScope = 'PENDING' | 'MINE' | 'FINISHED'
export type NodeModeType = 'ANY_ONE' | 'ALL'
export type ApprovalNodeStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type ApprovalApproverStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'RETURNED' | 'TRANSFERRED_OUT'
export type ApproverRuleType =
  | 'FIXED_USER'
  | 'DIRECT_MANAGER'
  | 'PRIMARY_DEPT_MANAGER'
  | 'ORG_UNIT_MANAGER'
  | 'PROJECT_OWNER'
  | 'PROJECT_DEPUTY'
  | 'SYSTEM_ROLE'

export type FormFieldType =
  | 'TEXT'
  | 'TEXTAREA'
  | 'NUMBER'
  | 'MONEY'
  | 'DATE'
  | 'DATETIME'
  | 'SELECT'
  | 'MULTI_SELECT'
  | 'USER'
  | 'ATTACHMENT'

/** 审批表单字段定义（模板与实例快照共用） */
export interface FormFieldDef {
  key: string
  label: string
  type: FormFieldType
  required: boolean
  options?: string[] | null
  placeholder?: string | null
}

/** 动态审批人规则（含可选备用规则） */
export interface ApproverRule {
  type: ApproverRuleType
  userId?: number | null
  systemRole?: string | null
  projectField?: string | null
  fallback?: ApproverRule[] | null
}

export interface NodeDefinition {
  name: string
  mode: NodeModeType
  approvers: ApproverRule[]
}

export interface ApprovalTemplateSummary {
  id: number
  name: string
  description?: string | null
  enabled: boolean
  latestVersionNo: number
  updatedAt: string
}

export interface ApprovalTemplateVersion {
  versionNo: number
  name: string
  description?: string | null
  formFields: FormFieldDef[]
  nodes: NodeDefinition[]
  createdAt: string
}

export interface ApprovalTemplateDetail extends ApprovalTemplateSummary {
  latestVersion: ApprovalTemplateVersion
}

/** 审批卡片：类型 / 申请人 / 申请时间 / 当前节点 / 状态 */
export interface ApprovalCard {
  id: number
  title: string
  templateName: string
  status: ApprovalStatusType
  applicant: TaskUserBrief
  currentNodeName?: string | null
  createdAt: string
  submittedAt?: string | null
  updatedAt: string
}

export interface ApprovalApproverView {
  user: TaskUserBrief
  ruleType: ApproverRuleType
  status: ApprovalApproverStatus
  comment?: string | null
  actedAt?: string | null
  transferredIn: boolean
}

export interface ApprovalNodeView {
  index: number
  name: string
  mode: NodeModeType
  status: ApprovalNodeStatus
  current: boolean
  approvers: ApprovalApproverView[]
}

export interface ApprovalActionView {
  action: string
  actor: TaskUserBrief
  comment?: string | null
  createdAt: string
}

export interface ApprovalPermissions {
  canSubmit: boolean
  canEditForm: boolean
  canWithdraw: boolean
  canApprove: boolean
  canReject: boolean
  canReturn: boolean
  canTransfer: boolean
}

export interface ApprovalDetail {
  id: number
  title: string
  templateName: string
  templateVersionNo: number
  status: ApprovalStatusType
  applicant: TaskUserBrief
  formFields: FormFieldDef[]
  formValues: Record<string, unknown>
  attachments: FileMeta[]
  nodes: ApprovalNodeView[]
  actions: ApprovalActionView[]
  permissions: ApprovalPermissions
  createdAt: string
  submittedAt?: string | null
  finishedAt?: string | null
  updatedAt: string
}

export interface CreateApprovalPayload {
  templateId: number
  title: string
  values: Record<string, unknown>
}

export interface TemplatePayload {
  name: string
  description?: string
  formFields: FormFieldDef[]
  nodes: NodeDefinition[]
}