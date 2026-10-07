package com.easyoa.approval.dto;

import java.time.Instant;

import com.easyoa.task.dto.TaskUserBrief;

/**
 * 审批卡片：类型（模板名）/ 申请人 / 申请时间 / 当前节点 / 状态。
 */
public record ApprovalCardView(
        Long id,
        String title,
        String templateName,
        String status,
        TaskUserBrief applicant,
        String currentNodeName,
        Instant createdAt,
        Instant submittedAt,
        Instant updatedAt) {
}