import type { ProjectRole, ProjectStatus } from '@/api/types'

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'brand'

/** 项目状态展示（与后端 ProjectStatus 对应） */
const statusLabels: Record<ProjectStatus, string> = {
  DRAFT: '草稿',
  ACTIVE: '进行中',
  PAUSED: '已暂停',
  COMPLETED: '已完成',
  ARCHIVED: '已归档',
}

const statusTones: Record<ProjectStatus, Tone> = {
  DRAFT: 'neutral',
  ACTIVE: 'brand',
  PAUSED: 'warning',
  COMPLETED: 'success',
  ARCHIVED: 'neutral',
}

export function projectStatusLabel(status?: ProjectStatus | null): string {
  return status ? (statusLabels[status] ?? status) : '—'
}

export function projectStatusTone(status?: ProjectStatus | null): Tone {
  return status ? (statusTones[status] ?? 'neutral') : 'neutral'
}

/** 项目角色展示（仅用于 UI 呈现，权限判定在后端） */
const roleLabels: Record<ProjectRole, string> = {
  OWNER: '项目负责人',
  DEPUTY_OWNER: '副负责人',
  MEMBER: '项目成员',
}

export function projectRoleLabel(role?: ProjectRole | null): string {
  return role ? (roleLabels[role] ?? role) : '—'
}

export function projectRoleTone(role?: ProjectRole | null): Tone {
  if (role === 'OWNER') return 'brand'
  if (role === 'DEPUTY_OWNER') return 'warning'
  return 'neutral'
}