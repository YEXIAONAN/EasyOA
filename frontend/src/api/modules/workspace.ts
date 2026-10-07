import { apiRequest } from '@/api/client'
import type { WorkspaceSummary } from '@/api/types'

export const workspaceApi = {
  summary(): Promise<WorkspaceSummary> {
    return apiRequest({ url: '/workspace/summary', method: 'get' })
  },
}