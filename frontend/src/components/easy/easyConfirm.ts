import { ElMessageBox } from 'element-plus'

export interface ConfirmOptions {
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  /** 危险 / 不可逆操作（归档、禁用、移除成员等） */
  danger?: boolean
}

/**
 * EasyConfirm — 统一确认弹窗。
 *
 * 交互原则：只有危险或不可逆操作才二次确认；普通操作直接执行并用 Toast 反馈。
 */
export async function confirmAction(options: ConfirmOptions): Promise<boolean> {
  try {
    await ElMessageBox.confirm(options.message, options.title, {
      confirmButtonText: options.confirmText ?? '确认',
      cancelButtonText: options.cancelText ?? '取消',
      type: options.danger ? 'warning' : 'info',
      confirmButtonClass: options.danger ? 'el-button--danger' : undefined,
      autofocus: false,
      draggable: false,
    })
    return true
  } catch {
    return false
  }
}