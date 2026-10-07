import { apiRequest, apiUpload, type PageResult } from '@/api/client'
import type {
  ApprovalCard,
  ApprovalDetail,
  ApprovalScope,
  ApprovalTemplateDetail,
  ApprovalTemplateSummary,
  CreateApprovalPayload,
  FileMeta,
  TemplatePayload,
} from '@/api/types'

/**
 * 审批接口。
 *
 * 数据范围：申请人、参与过审批的人与系统管理员可见（其余 404）。
 * 同意 / 拒绝 / 退回仅当前节点待审批人；撤回仅申请人；转交仅管理员。
 */
export const approvalApi = {
  list(scope: ApprovalScope, page = 1, size = 15): Promise<PageResult<ApprovalCard>> {
    return apiRequest({ url: '/approvals', method: 'get', params: { scope, page, size } })
  },

  detail(id: number): Promise<ApprovalDetail> {
    return apiRequest({ url: `/approvals/${id}`, method: 'get' })
  },

  create(payload: CreateApprovalPayload): Promise<ApprovalDetail> {
    return apiRequest({ url: '/approvals', method: 'post', data: payload })
  },

  updateForm(id: number, values: Record<string, unknown>): Promise<ApprovalDetail> {
    return apiRequest({ url: `/approvals/${id}/form`, method: 'put', data: { values } })
  },

  submit(id: number): Promise<ApprovalDetail> {
    return apiRequest({ url: `/approvals/${id}/submit`, method: 'post' })
  },

  approve(id: number, comment?: string): Promise<ApprovalDetail> {
    return apiRequest({ url: `/approvals/${id}/approve`, method: 'post', data: { comment } })
  },

  reject(id: number, comment: string): Promise<ApprovalDetail> {
    return apiRequest({ url: `/approvals/${id}/reject`, method: 'post', data: { comment } })
  },

  returnForReview(id: number, comment: string): Promise<ApprovalDetail> {
    return apiRequest({ url: `/approvals/${id}/return`, method: 'post', data: { comment } })
  },

  withdraw(id: number): Promise<ApprovalDetail> {
    return apiRequest({ url: `/approvals/${id}/withdraw`, method: 'post' })
  },

  transfer(id: number, fromUserId: number, toUserId: number, comment?: string): Promise<ApprovalDetail> {
    return apiRequest({
      url: `/approvals/${id}/transfer`,
      method: 'post',
      data: { fromUserId, toUserId, comment },
    })
  },

  /** 审批表单附件上传（草稿阶段，提交时挂载到实例） */
  uploadAttachment(file: File): Promise<FileMeta> {
    const form = new FormData()
    form.append('file', file)
    return apiUpload('/approvals/attachments', form)
  },

  // --- 模板（仅管理员可写） -----------------------------------------------------

  templates(keyword = '', size = 50): Promise<PageResult<ApprovalTemplateSummary>> {
    return apiRequest({ url: '/approval-templates', method: 'get', params: { keyword: keyword || undefined, page: 1, size } })
  },

  templateDetail(id: number): Promise<ApprovalTemplateDetail> {
    return apiRequest({ url: `/approval-templates/${id}`, method: 'get' })
  },

  createTemplate(payload: TemplatePayload): Promise<ApprovalTemplateDetail> {
    return apiRequest({ url: '/approval-templates', method: 'post', data: payload })
  },

  updateTemplate(
    id: number,
    payload: { name: string; description?: string; enabled: boolean },
  ): Promise<ApprovalTemplateDetail> {
    return apiRequest({ url: `/approval-templates/${id}`, method: 'put', data: payload })
  },

  publishVersion(id: number, payload: { formFields: TemplatePayload['formFields']; nodes: TemplatePayload['nodes'] }): Promise<ApprovalTemplateDetail> {
    return apiRequest({ url: `/approval-templates/${id}/versions`, method: 'post', data: payload })
  },
}