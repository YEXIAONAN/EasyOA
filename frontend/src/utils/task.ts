import type { ProgressMode, TaskPriority, TaskStatusType } from '@/api/types'

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'brand'

/** 优先级展示（与后端 TaskPriority 对应） */
const priorityLabels: Record<TaskPriority, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  URGENT: '紧急',
}

const priorityTones: Record<TaskPriority, Tone> = {
  LOW: 'neutral',
  MEDIUM: 'neutral',
  HIGH: 'warning',
  URGENT: 'danger',
}

export function taskPriorityLabel(priority?: TaskPriority | null): string {
  return priority ? (priorityLabels[priority] ?? priority) : '—'
}

export function taskPriorityTone(priority?: TaskPriority | null): Tone {
  return priority ? (priorityTones[priority] ?? 'neutral') : 'neutral'
}

/** 系统统一类型展示：自定义状态名称由 status.name 提供，这里用于统计语义 */
const statusTypeLabels: Record<TaskStatusType, string> = {
  TODO: '待处理',
  ACTIVE: '进行中',
  REVIEW: '待审核',
  DONE: '已完成',
  CLOSED: '已关闭',
}

const statusTypeTones: Record<TaskStatusType, Tone> = {
  TODO: 'neutral',
  ACTIVE: 'brand',
  REVIEW: 'warning',
  DONE: 'success',
  CLOSED: 'neutral',
}

export function taskStatusTypeLabel(type?: TaskStatusType | null): string {
  return type ? (statusTypeLabels[type] ?? type) : '—'
}

export function taskStatusTypeTone(type?: TaskStatusType | null): Tone {
  return type ? (statusTypeTones[type] ?? 'neutral') : 'neutral'
}

export function progressModeLabel(mode?: ProgressMode | null): string {
  if (mode === 'AUTO') return '自动（按子任务完成比例）'
  if (mode === 'MANUAL') return '手工'
  return '—'
}

/** CLOSED / DONE 视为已结束 */
export function isFinishedStatus(type?: TaskStatusType | null): boolean {
  return type === 'DONE' || type === 'CLOSED'
}