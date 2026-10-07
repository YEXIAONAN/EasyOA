import { apiRequest, type PageResult } from '@/api/client'
import type {
  CreateTaskPayload,
  TaskBoard,
  TaskCard,
  TaskDetail,
  TaskPriority,
  TaskStatusType,
  TaskStatusView,
  UpdateTaskPayload,
} from '@/api/types'

export interface ProjectTaskListParams {
  keyword?: string
  statusId?: number | null
  priority?: TaskPriority | null
  page?: number
  size?: number
}

export interface MyTaskListParams {
  filter?: 'OPEN' | 'DUE_SOON' | 'DONE' | 'ALL'
  keyword?: string
  page?: number
  size?: number
}

/**
 * 任务接口。
 *
 * 数据范围与角色权限由后端判定：非项目成员访问任务返回 404（不泄露任务是否存在），
 * 角色不足返回 403；依赖阻塞返回 409 + code TASK_BLOCKED_BY_DEPENDENCIES，
 * 前端据此弹出「忽略依赖并开始」流程。
 */
export const taskApi = {
  board(projectId: number): Promise<TaskBoard> {
    return apiRequest({ url: `/projects/${projectId}/tasks/board`, method: 'get' })
  },

  list(projectId: number, params: ProjectTaskListParams = {}): Promise<PageResult<TaskCard>> {
    return apiRequest({
      url: `/projects/${projectId}/tasks`,
      method: 'get',
      params: {
        keyword: params.keyword || undefined,
        statusId: params.statusId ?? undefined,
        priority: params.priority ?? undefined,
        page: params.page ?? 1,
        size: params.size ?? 15,
      },
    })
  },

  create(projectId: number, payload: CreateTaskPayload): Promise<TaskDetail> {
    return apiRequest({ url: `/projects/${projectId}/tasks`, method: 'post', data: payload })
  },

  statuses(projectId: number): Promise<TaskStatusView[]> {
    return apiRequest({ url: `/projects/${projectId}/task-statuses`, method: 'get' })
  },

  createStatus(projectId: number, name: string, systemType: TaskStatusType): Promise<TaskStatusView[]> {
    return apiRequest({ url: `/projects/${projectId}/task-statuses`, method: 'post', data: { name, systemType } })
  },

  my(params: MyTaskListParams = {}): Promise<PageResult<TaskCard>> {
    return apiRequest({
      url: '/tasks/my',
      method: 'get',
      params: {
        filter: params.filter ?? 'OPEN',
        keyword: params.keyword || undefined,
        page: params.page ?? 1,
        size: params.size ?? 15,
      },
    })
  },

  detail(id: number): Promise<TaskDetail> {
    return apiRequest({ url: `/tasks/${id}`, method: 'get' })
  },

  update(id: number, payload: UpdateTaskPayload): Promise<TaskDetail> {
    return apiRequest({ url: `/tasks/${id}`, method: 'put', data: payload })
  },

  changeStatus(id: number, statusId: number, overrideReason?: string): Promise<TaskDetail> {
    return apiRequest({
      url: `/tasks/${id}/status`,
      method: 'post',
      data: { statusId, overrideReason: overrideReason || undefined },
    })
  },

  changeProgress(id: number, progress: number): Promise<TaskDetail> {
    return apiRequest({ url: `/tasks/${id}/progress`, method: 'put', data: { progress } })
  },

  changeAssignees(id: number, primaryAssigneeId: number, deputyAssigneeId: number | null): Promise<TaskDetail> {
    return apiRequest({
      url: `/tasks/${id}/assignees`,
      method: 'put',
      data: { primaryAssigneeId, deputyAssigneeId },
    })
  },

  changeCollaborators(id: number, userIds: number[]): Promise<TaskDetail> {
    return apiRequest({ url: `/tasks/${id}/collaborators`, method: 'put', data: { userIds } })
  },

  createSubtask(
    parentId: number,
    payload: { title: string; description?: string; primaryAssigneeId?: number | null },
  ): Promise<TaskDetail> {
    return apiRequest({ url: `/tasks/${parentId}/subtasks`, method: 'post', data: payload })
  },

  addDependency(id: number, dependsOnTaskId: number): Promise<TaskDetail> {
    return apiRequest({ url: `/tasks/${id}/dependencies`, method: 'post', data: { dependsOnTaskId } })
  },

  removeDependency(id: number, dependsOnTaskId: number): Promise<TaskDetail> {
    return apiRequest({ url: `/tasks/${id}/dependencies/${dependsOnTaskId}`, method: 'delete' })
  },

  approveAssignment(id: number): Promise<TaskDetail> {
    return apiRequest({ url: `/tasks/${id}/assignment/approve`, method: 'post' })
  },

  rejectAssignment(id: number, reason?: string): Promise<TaskDetail> {
    return apiRequest({ url: `/tasks/${id}/assignment/reject`, method: 'post', data: { reason } })
  },
}