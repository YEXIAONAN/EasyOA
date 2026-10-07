import { apiRequest, type PageResult } from '@/api/client'
import type { NotificationItem } from '@/api/types'

/**
 * 通知中心接口（只能查看与操作自己的通知；支持 Deep Link）。
 */
export const notificationApi = {
  list(params: { unreadOnly?: boolean; page?: number; size?: number } = {}): Promise<PageResult<NotificationItem>> {
    return apiRequest({
      url: '/notifications',
      method: 'get',
      params: {
        unreadOnly: params.unreadOnly ?? false,
        page: params.page ?? 1,
        size: params.size ?? 20,
      },
    })
  },

  unreadCount(): Promise<{ count: number }> {
    return apiRequest({ url: '/notifications/unread-count', method: 'get' })
  },

  markRead(id: number): Promise<void> {
    return apiRequest({ url: `/notifications/${id}/read`, method: 'post' })
  },

  markAllRead(): Promise<void> {
    return apiRequest({ url: '/notifications/read-all', method: 'post' })
  },
}