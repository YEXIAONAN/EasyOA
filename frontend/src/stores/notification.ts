import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { defineStore } from 'pinia'

import { ApiError } from '@/api/errors'
import { notificationApi } from '@/api/modules/notifications'

/**
 * 通知中心状态。
 *
 * Phase 7 起：unreadCount 为真实未读数（顶部铃铛轮询刷新）；Toast 反馈保持不变。
 */
export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)

  /** 拉取未读数（任务分配 / 审批待办 / @ 提及等真实通知）。 */
  async function refreshUnread(): Promise<void> {
    try {
      const result = await notificationApi.unreadCount()
      unreadCount.value = result.count
    } catch {
      // 未登录或网络异常时保持原值，不打扰用户
    }
  }

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

  return { unreadCount, refreshUnread, success, info, warning, error }
})