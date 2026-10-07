import { apiRequest, type PageResult } from '@/api/client'
import type {
  CreateUserPayload,
  MemberCard,
  MemberProfile,
  SystemRole,
  UpdateMyProfilePayload,
  UserStatus,
} from '@/api/types'

export interface DirectoryParams {
  keyword?: string
  orgUnitId?: number | null
  status?: UserStatus | null
  page?: number
  size?: number
}

/**
 * 成员目录、成员档案与账号管理接口。
 */
export const userApi = {
  directory(params: DirectoryParams = {}): Promise<PageResult<MemberCard>> {
    return apiRequest({
      url: '/users/directory',
      method: 'get',
      params: {
        keyword: params.keyword ?? undefined,
        orgUnitId: params.orgUnitId ?? undefined,
        status: params.status ?? undefined,
        page: params.page ?? 1,
        size: params.size ?? 24,
      },
    })
  },

  profile(id: number): Promise<MemberProfile> {
    return apiRequest({ url: `/users/${id}`, method: 'get' })
  },

  updateMyProfile(payload: UpdateMyProfilePayload): Promise<MemberProfile> {
    return apiRequest({ url: '/users/me', method: 'patch', data: payload })
  },

  create(payload: CreateUserPayload): Promise<MemberProfile> {
    return apiRequest({ url: '/users', method: 'post', data: payload })
  },

  changeStatus(id: number, status: UserStatus): Promise<MemberProfile> {
    return apiRequest({ url: `/users/${id}/status`, method: 'post', data: { status } })
  },

  changeRole(id: number, systemRole: SystemRole): Promise<MemberProfile> {
    return apiRequest({ url: `/users/${id}/role`, method: 'post', data: { systemRole } })
  },
}