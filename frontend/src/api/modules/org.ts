import { apiRequest } from '@/api/client'
import type {
  CreateOrgUnitPayload,
  OrgMember,
  OrgUnitDetail,
  OrgUnitTreeNode,
  UpdateOrgUnitPayload,
  UserOrgMembershipView,
} from '@/api/types'

/**
 * 组织架构接口。
 *
 * 权限由后端判定（ROOT / ADMIN 调整结构；单元负责人可管理其成员），
 * 前端只负责按角色展示操作入口。
 */
export const orgApi = {
  tree(includeArchived = false): Promise<OrgUnitTreeNode[]> {
    return apiRequest({ url: '/org-units', method: 'get', params: { includeArchived } })
  },

  detail(id: number): Promise<OrgUnitDetail> {
    return apiRequest({ url: `/org-units/${id}`, method: 'get' })
  },

  create(payload: CreateOrgUnitPayload): Promise<OrgUnitDetail> {
    return apiRequest({ url: '/org-units', method: 'post', data: payload })
  },

  update(id: number, payload: UpdateOrgUnitPayload): Promise<OrgUnitDetail> {
    return apiRequest({ url: `/org-units/${id}`, method: 'put', data: payload })
  },

  move(id: number, newParentId: number | null): Promise<OrgUnitDetail> {
    return apiRequest({ url: `/org-units/${id}/move`, method: 'post', data: { newParentId } })
  },

  archive(id: number): Promise<OrgUnitDetail> {
    return apiRequest({ url: `/org-units/${id}/archive`, method: 'post' })
  },

  restore(id: number): Promise<OrgUnitDetail> {
    return apiRequest({ url: `/org-units/${id}/restore`, method: 'post' })
  },

  members(id: number): Promise<OrgMember[]> {
    return apiRequest({ url: `/org-units/${id}/members`, method: 'get' })
  },

  addMember(id: number, userId: number): Promise<OrgMember[]> {
    return apiRequest({ url: `/org-units/${id}/members`, method: 'post', data: { userId } })
  },

  removeMember(id: number, userId: number): Promise<OrgMember[]> {
    return apiRequest({ url: `/org-units/${id}/members/${userId}`, method: 'delete' })
  },

  setPrimary(id: number, userId: number): Promise<UserOrgMembershipView[]> {
    return apiRequest({ url: `/org-units/${id}/members/${userId}/primary`, method: 'put' })
  },
}