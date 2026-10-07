package com.easyoa.workspace.dto;

import java.time.Instant;
import java.util.List;

import com.easyoa.approval.dto.ApprovalCardView;
import com.easyoa.project.dto.ProjectCardResponse;
import com.easyoa.task.dto.TaskCardResponse;

/**
 * 工作台摘要。
 *
 * <p>Phase 6 起「待我审批」KPI 与「待我审批」区块接入真实审批数据（不允许一键批准，
 * 必须进入详情页处理）。
 */
public record WorkspaceSummaryResponse(
        UserBrief me,
        KpiSummary kpis,
        List<TaskCardResponse> myTasks,
        List<ApprovalCardView> pendingApprovals,
        List<ProjectCardResponse> projectProgress) {

    public record UserBrief(
            Long id,
            String username,
            String displayName,
            String systemRole,
            String avatarUrl,
            Instant lastLoginAt) {
    }

    public record KpiSummary(
            long myOpenTasks,
            long pendingApprovals,
            long activeProjects,
            long dueSoonTasks) {
    }
}