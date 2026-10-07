import { apiRequest } from '@/api/client'
import type { ActivityItem, WorkspaceSummary } from '@/api/types'

export const workspaceApi = {
  summary(): Promise<WorkspaceSummary> {
    return apiRequest({ url: '/workspace/summary', method: 'get' })
  },

  /** Activity Feed（业务动态，含深链；数据范围：我参与的项目 + 我的审批） */
  activity(limit = 10): Promise<ActivityItem[]> {
    return apiRequest({ url: '/workspace/activity', method: 'get', params: { limit } })
  },
}