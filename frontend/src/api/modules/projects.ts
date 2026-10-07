import { apiRequest, type PageResult } from '@/api/client'
import type {
  CreateProjectPayload,
  ProjectCard,
  ProjectDetail,
  ProjectMemberView,
  ProjectStatus,
  UpdateProjectPayload,
} from '@/api/types'

export interface ProjectListParams {
  keyword?: string
  status?: ProjectStatus | null
  page?: number
  size?: number
}

/**
 * 项目接口。
 *
 * 数据范围与角色权限由后端判定：非成员访问返回 404（不泄露项目是否存在），
 * 角色不足返回 403。
 */
export const projectApi = {
  list(params: ProjectListParams = {}): Promise<PageResult<ProjectCard>> {
    return apiRequest({
      url: '/projects',
      method: 'get',
      params: {
        keyword: params.keyword ?? undefined,
        status: params.status ?? undefined,
        page: params.page ?? 1,
        size: params.size ?? 12,
      },
    })
  },

  detail(id: number): Promise<ProjectDetail> {
    return apiRequest({ url: `/projects/${id}`, method: 'get' })
  },

  create(payload: CreateProjectPayload): Promise<ProjectDetail> {
    return apiRequest({ url: '/projects', method: 'post', data: payload })
  },

  updateInfo(id: number, payload: UpdateProjectPayload): Promise<ProjectDetail> {
    return apiRequest({ url: `/projects/${id}`, method: 'put', data: payload })
  },

  changeProgress(id: number, progress: number): Promise<ProjectDetail> {
    return apiRequest({ url: `/projects/${id}/progress`, method: 'put', data: { progress } })
  },

  changeStatus(id: number, status: ProjectStatus): Promise<ProjectDetail> {
    return apiRequest({ url: `/projects/${id}/status`, method: 'post', data: { status } })
  },

  archive(id: number): Promise<ProjectDetail> {
    return apiRequest({ url: `/projects/${id}/archive`, method: 'post' })
  },

  members(id: number): Promise<ProjectMemberView[]> {
    return apiRequest({ url: `/projects/${id}/members`, method: 'get' })
  },

  addMember(id: number, userId: number): Promise<ProjectMemberView[]> {
    return apiRequest({ url: `/projects/${id}/members`, method: 'post', data: { userId } })
  },

  removeMember(id: number, userId: number): Promise<ProjectMemberView[]> {
    return apiRequest({ url: `/projects/${id}/members/${userId}`, method: 'delete' })
  },

  setDeputy(id: number, userId: number | null): Promise<ProjectDetail> {
    return apiRequest({ url: `/projects/${id}/deputy`, method: 'put', data: { userId } })
  },

  transferOwner(id: number, userId: number): Promise<ProjectDetail> {
    return apiRequest({ url: `/projects/${id}/transfer-owner`, method: 'post', data: { userId } })
  },
}