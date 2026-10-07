import type { ApprovalApproverStatus, ApprovalStatusType, ApproverRuleType, FormFieldDef, NodeModeType } from '@/api/types'

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'brand'

/** 审批实例状态展示 */
const statusLabels: Record<ApprovalStatusType, string> = {
  DRAFT: '草稿',
  PENDING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
  RETURNED: '已退回',
  CANCELLED: '已撤回',
}

const statusTones: Record<ApprovalStatusType, Tone> = {
  DRAFT: 'neutral',
  PENDING: 'brand',
  APPROVED: 'success',
  REJECTED: 'danger',
  RETURNED: 'warning',
  CANCELLED: 'neutral',
}

export function approvalStatusLabel(status?: ApprovalStatusType | null): string {
  return status ? (statusLabels[status] ?? status) : '—'
}

export function approvalStatusTone(status?: ApprovalStatusType | null): Tone {
  return status ? (statusTones[status] ?? 'neutral') : 'neutral'
}

const approverStatusLabels: Record<ApprovalApproverStatus, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
  RETURNED: '已退回',
  TRANSFERRED_OUT: '已转交',
}

export function approverStatusLabel(status?: ApprovalApproverStatus | null): string {
  return status ? (approverStatusLabels[status] ?? status) : '—'
}

const ruleTypeLabels: Record<ApproverRuleType, string> = {
  FIXED_USER: '指定成员',
  DIRECT_MANAGER: '直属主管',
  PRIMARY_DEPT_MANAGER: '主部门负责人',
  ORG_UNIT_MANAGER: '组织负责人',
  PROJECT_OWNER: '项目负责人',
  PROJECT_DEPUTY: '项目副负责人',
  SYSTEM_ROLE: '系统角色',
}

export function approverRuleLabel(type?: ApproverRuleType | null): string {
  return type ? (ruleTypeLabels[type] ?? type) : '—'
}

export function nodeModeLabel(mode?: NodeModeType | null): string {
  if (mode === 'ANY_ONE') return '任一通过'
  if (mode === 'ALL') return '全部通过'
  return '—'
}

const actionLabels: Record<string, string> = {
  SUBMIT: '提交申请',
  APPROVE: '同意',
  REJECT: '拒绝',
  RETURN: '退回修改',
  WITHDRAW: '撤回申请',
  TRANSFER: '转交',
  UPDATE_FORM: '修改表单',
}

export function approvalActionLabel(action?: string | null): string {
  return action ? (actionLabels[action] ?? action) : '—'
}

/**
 * 把表单输入转换为后端快照值：
 * NUMBER / MONEY → 数字；DATE / DATETIME → ISO 字符串；USER / ATTACHMENT → 数字 id；
 * 空值直接省略（由后端按 required 校验并给出字段级提示）。
 */
export function buildFormValues(
  fields: FormFieldDef[],
  raw: Record<string, unknown>,
): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  for (const field of fields) {
    const value = raw[field.key]
    if (value === null || value === undefined || value === '') continue
    if (Array.isArray(value) && value.length === 0) continue
    switch (field.type) {
      case 'NUMBER':
      case 'MONEY':
        result[field.key] = Number(value)
        break
      case 'DATE': {
        const date = new Date(`${String(value)}T00:00:00Z`)
        result[field.key] = Number.isNaN(date.getTime()) ? String(value) : date.toISOString()
        break
      }
      case 'DATETIME': {
        const date = new Date(String(value))
        result[field.key] = Number.isNaN(date.getTime()) ? String(value) : date.toISOString()
        break
      }
      case 'USER':
        result[field.key] = Number(value)
        break
      case 'ATTACHMENT':
      case 'MULTI_SELECT':
        result[field.key] = (value as unknown[]).map((item) => (field.type === 'ATTACHMENT' ? Number(item) : String(item)))
        break
      default:
        result[field.key] = String(value).trim()
    }
  }
  return result
}

/** ISO 时间 → 表单控件值（DATE：yyyy-MM-dd；DATETIME：yyyy-MM-ddTHH:mm） */
export function toFormInputValue(type: FormFieldDef['type'], value: unknown): unknown {
  if (value === null || value === undefined) return ''
  if (type === 'DATE' && typeof value === 'string') {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    const pad = (num: number) => String(num).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  }
  if (type === 'DATETIME' && typeof value === 'string') {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    const pad = (num: number) => String(num).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
  return value
}