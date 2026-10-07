package com.easyoa.approval.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.easyoa.file.dto.FileView;
import com.easyoa.task.dto.TaskUserBrief;

/**
 * 审批详情（流程时间线 + 表单快照 + 审批历史 + 当前用户可执行操作）。
 *
 * <p>流程展示面向业务用户：只暴露节点名称与审批人，不暴露底层 Node ID 等技术概念。
 */
public record ApprovalDetailView(
        Long id,
        String title,
        String templateName,
        int templateVersionNo,
        String status,
        TaskUserBrief applicant,
        List<FormFieldView> formFields,
        Map<String, Object> formValues,
        List<FileView> attachments,
        List<NodeView> nodes,
        List<ActionView> actions,
        Permissions permissions,
        Instant createdAt,
        Instant submittedAt,
        Instant finishedAt,
        Instant updatedAt) {

    /** 流程节点：current = 当前节点。 */
    public record NodeView(
            int index,
            String name,
            String mode,
            String status,
            boolean current,
            List<ApproverView> approvers) {
    }

    /** 审批人快照（transferredIn 表示由管理员转交加入）。 */
    public record ApproverView(
            TaskUserBrief user,
            String ruleType,
            String status,
            String comment,
            Instant actedAt,
            boolean transferredIn) {
    }

    /** 审批历史动作。 */
    public record ActionView(
            String action,
            TaskUserBrief actor,
            String comment,
            Instant createdAt) {
    }

    /** 当前用户可执行操作（前端只做体验控制，后端独立鉴权）。 */
    public record Permissions(
            boolean canSubmit,
            boolean canEditForm,
            boolean canWithdraw,
            boolean canApprove,
            boolean canReject,
            boolean canReturn,
            boolean canTransfer) {
    }
}