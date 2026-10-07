import { apiRequest, apiUpload, type PageResult } from '@/api/client'
import type { CommentVersionView, CommentView, CreateCommentPayload, FileMeta } from '@/api/types'

/**
 * 评论接口。
 *
 * 数据范围与任务一致（非项目成员 404）；编辑与撤回仅作者本人；
 * 撤回不做物理删除，原始评论与编辑历史保留供审计追踪。
 */
export const commentApi = {
  list(taskId: number, page = 1, size = 20): Promise<PageResult<CommentView>> {
    return apiRequest({ url: `/tasks/${taskId}/comments`, method: 'get', params: { page, size } })
  },

  create(taskId: number, payload: CreateCommentPayload): Promise<CommentView> {
    return apiRequest({ url: `/tasks/${taskId}/comments`, method: 'post', data: payload })
  },

  update(commentId: number, payload: { content: string; mentionUserIds?: number[] }): Promise<CommentView> {
    return apiRequest({ url: `/comments/${commentId}`, method: 'put', data: payload })
  },

  withdraw(commentId: number): Promise<CommentView> {
    return apiRequest({ url: `/comments/${commentId}/withdraw`, method: 'post' })
  },

  versions(commentId: number): Promise<CommentVersionView[]> {
    return apiRequest({ url: `/comments/${commentId}/versions`, method: 'get' })
  },
}

/**
 * 附件接口。
 *
 * 上传使用 multipart/form-data；下载统一走 `/api/files/{id}`（受控入口，禁止静态直链）。
 */
export const fileApi = {
  listTaskFiles(taskId: number): Promise<FileMeta[]> {
    return apiRequest({ url: `/tasks/${taskId}/files`, method: 'get' })
  },

  upload(taskId: number, file: File): Promise<FileMeta> {
    const form = new FormData()
    form.append('file', file)
    return apiUpload(`/tasks/${taskId}/files`, form)
  },

  remove(fileId: number): Promise<void> {
    return apiRequest({ url: `/files/${fileId}`, method: 'delete' })
  },
}