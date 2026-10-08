import { apiRequest } from '@/api/client'
import type { ActivityItem, MemberCollaboration, WorkspaceSummary } from '@/api/types'

export const workspaceApi = {
  summary(): Promise<WorkspaceSummary> {
    return apiRequest({ url: '/workspace/summary', method: 'get' })
  },

  /** Activity Feed（业务动态，含深链；数据范围：我参与的项目 + 我的审批） */
  activity(limit = 10): Promise<ActivityItem[]> {
    return apiRequest({ url: '/workspace/activity', method: 'get', params: { limit } })
  },

  /**
   * 成员协作概览（团队页面成员档案的「参与项目 / 近期任务」）。
   *
   * 服务端按查看者的数据范围过滤：非管理员只能看到自己同样可见的项目与任务。
   */
  memberCollaboration(userId: number): Promise<MemberCollaboration> {
    return apiRequest({ url: `/workspace/members/${userId}/collaboration`, method: 'get' })
  },
}