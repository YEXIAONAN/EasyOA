import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { defineStore } from 'pinia'

import { ApiError } from '@/api/errors'

/**
 * 通知中心状态。
 *
 * v0.1.0 Phase 1：完成 UI 反馈（Toast）与错误归一化。
 * 真实通知（任务/审批/评论）在 Phase 7 Workspace 阶段接入后端，届时 unreadCount 变为真实值。
 */
export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)

  function success(message: string): void {
    ElMessage({ type: 'success', message, duration: 2400 })
  }

  function info(message: string): void {
    ElMessage({ type: 'info', message, duration: 2400 })
  }

  function warning(message: string): void {
    ElMessage({ type: 'warning', message, duration: 3200 })
  }

  /** 统一错误提示：携带 requestId，便于用户向管理员反馈问题 */
  function error(error: unknown): void {
    const message = toMessage(error)
    ElMessage({ type: 'error', message, duration: 4000, showClose: true })
  }

  function toMessage(error: unknown): string {
    if (error instanceof ApiError) {
      return error.requestId ? `${error.message}（请求编号 ${error.requestId}）` : error.message
    }
    if (error instanceof Error) return error.message
    return '操作失败，请稍后再试'
  }

  return { unreadCount, success, info, warning, error }
})