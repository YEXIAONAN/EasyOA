import type { SensitiveOperationType } from '@/api/types'

/**
 * 安全相关的展示映射（仅用于 UI 呈现，不参与任何权限判断）。
 */

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'brand'

const riskLevelLabels: Record<string, string> = {
  NORMAL: '常规',
  ELEVATED: '重要',
  CRITICAL: '高危',
}

const riskLevelTones: Record<string, Tone> = {
  NORMAL: 'neutral',
  ELEVATED: 'warning',
  CRITICAL: 'danger',
}

export function riskLevelLabel(level?: string | null): string {
  if (!level) return '—'
  return riskLevelLabels[level] ?? level
}

export function riskLevelTone(level?: string | null): Tone {
  return riskLevelTones[level ?? 'NORMAL'] ?? 'neutral'
}

const severityLabels: Record<string, string> = {
  INFO: '提示',
  WARNING: '警告',
  CRITICAL: '严重',
}

const severityTones: Record<string, Tone> = {
  INFO: 'neutral',
  WARNING: 'warning',
  CRITICAL: 'danger',
}

export function severityLabel(severity?: string | null): string {
  if (!severity) return '—'
  return severityLabels[severity] ?? severity
}

export function severityTone(severity?: string | null): Tone {
  return severityTones[severity ?? 'INFO'] ?? 'neutral'
}

const securityEventTypeLabels: Record<string, string> = {
  SETUP_COMPLETED: '系统初始化完成',
  LOGIN_BLOCKED: '登录被锁定',
  BRUTE_FORCE_SUSPECTED: '疑似暴力破解',
  ROOT_SENSITIVE_OPERATION: 'ROOT 高危操作',
  PRIVILEGED_OPERATION: '超级权限操作',
  MFA_RESET: '动态口令重置',
  AUDIT_DATA_PURGE: '审计数据清理',
  SECURITY_POLICY_CHANGED: '安全策略变更',
  SENSITIVE_DATA_EXPORT: '敏感数据导出',
  DATA_DESTROYED: '系统数据销毁',
}

export function securityEventTypeLabel(type?: string | null): string {
  if (!type) return '—'
  return securityEventTypeLabels[type] ?? type
}

/** 常见审计动作的中文说明（未收录的动作直接展示原始 Action 码，便于排查） */
const auditActionLabels: Record<string, string> = {
  AUTH_LOGIN_SUCCEEDED: '登录成功',
  AUTH_LOGIN_FAILED: '登录失败',
  AUTH_LOGIN_BLOCKED: '登录被锁定',
  AUTH_LOGOUT: '退出登录',
  AUTH_PASSWORD_CHANGED: '修改密码',
  AUTH_SESSION_REVOKED: '撤销会话',
  SETUP_INITIALIZED: '系统初始化',
  SYSTEM_SETTING_UPDATED: '系统设置更新',
  USER_CREATED: '创建成员',
  USER_ROLE_CHANGED: '变更系统角色',
  USER_DISABLED: '禁用账号',
  USER_ENABLED: '启用账号',
  USER_PROFILE_UPDATED: '更新个人资料',
  ORG_UNIT_CREATED: '创建组织单元',
  ORG_UNIT_UPDATED: '更新组织单元',
  ORG_UNIT_ARCHIVED: '归档组织单元',
  ORG_UNIT_RESTORED: '恢复组织单元',
  ORG_MEMBERSHIP_CHANGED: '调整组织成员',
  ORG_PRIMARY_CHANGED: '变更主组织',
  PROJECT_CREATED: '创建项目',
  PROJECT_STATUS_CHANGED: '变更项目状态',
  PROJECT_OWNER_TRANSFERRED: '转让项目负责人',
  PROJECT_MEMBER_ADDED: '添加项目成员',
  PROJECT_MEMBER_REMOVED: '移除项目成员',
  PROJECT_ARCHIVED: '归档项目',
  TASK_CREATED: '创建任务',
  TASK_ASSIGNEE_CHANGED: '调整任务负责人',
  TASK_STATUS_CHANGED: '变更任务状态',
  TASK_PROGRESS_CHANGED: '更新任务进度',
  TASK_COMPLETED: '完成任务',
  TASK_OVERRIDE_DEPENDENCY: '忽略依赖开始任务',
  TASK_ASSIGNMENT_APPROVED: '通过任务派发',
  TASK_ASSIGNMENT_REJECTED: '驳回任务派发',
  COMMENT_EDITED: '编辑评论',
  COMMENT_WITHDRAWN: '撤回评论',
  FILE_UPLOADED: '上传附件',
  FILE_DOWNLOADED_SENSITIVE: '下载敏感附件',
  FILE_DELETED: '删除附件',
  APPROVAL_TEMPLATE_CREATED: '创建审批模板',
  APPROVAL_TEMPLATE_VERSION_CREATED: '发布模板版本',
  APPROVAL_SUBMITTED: '提交审批',
  APPROVAL_APPROVED: '审批通过',
  APPROVAL_REJECTED: '审批拒绝',
  APPROVAL_RETURNED: '审批退回',
  APPROVAL_TRANSFERRED: '转交审批',
  APPROVAL_CANCELLED: '撤回审批',
  SECURITY_TOTP_BOUND: '绑定动态口令',
  SECURITY_TOTP_RESET: '重置动态口令',
  SECURITY_SENSITIVE_OPERATION: 'ROOT 高危操作',
  SECURITY_AUDIT_EXPORT: '导出审计数据',
  SECURITY_DATA_DESTROYED: '销毁系统数据',
  SECURITY_POLICY_CHANGED: '修改安全策略',
}

export function auditActionLabel(action?: string | null): string {
  if (!action) return '—'
  return auditActionLabels[action] ?? action
}

/** 高危操作中文名（危险区域卡片使用） */
export const sensitiveOperationLabels: Record<SensitiveOperationType, string> = {
  AUDIT_LOG_PURGE: '审计日志清理',
  SENSITIVE_DATA_EXPORT: '敏感数据导出',
  MFA_RESET: '管理员 MFA 重置',
  SECURITY_POLICY_CHANGE: '安全策略修改',
  DATA_DESTRUCTION: '系统核心数据销毁',
}

/** JSON 字符串美化（审计 before/after 与安全事件 detail 展示） */
export function prettyJson(value?: string | null): string {
  if (!value) return '—'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}
