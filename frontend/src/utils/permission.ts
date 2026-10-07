import type { SystemRole } from '@/api/types'

/**
 * 系统角色展示信息（仅用于 UI 呈现，不代表任何权限判断）。
 */
const roleLabels: Record<SystemRole, string> = {
  ROOT: '系统所有者',
  ADMIN: '管理员',
  MEMBER: '成员',
}

export function systemRoleLabel(role?: SystemRole | null): string {
  if (!role) return ''
  return roleLabels[role] ?? role
}