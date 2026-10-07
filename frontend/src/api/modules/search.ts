import { apiRequest } from '@/api/client'
import type { SearchResponse } from '@/api/types'

/**
 * 全局搜索接口（顶部搜索 / 命令面板共用）。
 *
 * 结果按类别分组（项目 / 任务 / 成员 / 审批），每组最多 5 条并携带深链；
 * 后端使用 PostgreSQL 能力实现，不引入 Elasticsearch。
 */
export const searchApi = {
  search(query: string): Promise<SearchResponse> {
    return apiRequest({ url: '/search', method: 'get', params: { q: query } })
  },
}