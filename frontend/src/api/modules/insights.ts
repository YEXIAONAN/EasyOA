import { apiRequest } from '@/api/client'
import type { InsightsOverview } from '@/api/types'

/**
 * 数据中心接口（只读；数据范围由后端按角色收敛）。
 */
export const insightsApi = {
  overview(): Promise<InsightsOverview> {
    return apiRequest({ url: '/insights', method: 'get' })
  },
}
