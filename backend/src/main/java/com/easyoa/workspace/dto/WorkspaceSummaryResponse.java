package com.easyoa.workspace.dto;

import java.time.Instant;
import java.util.List;

import com.easyoa.approval.dto.ApprovalCardView;
import com.easyoa.project.dto.ProjectCardResponse;
import com.easyoa.task.dto.TaskCardResponse;

/**
 * 工作台摘要。
 *
 * <p>Phase 7 起「项目动态」区块接入真实 Activity Feed（业务动态，含深链）；
 * 全部 KPI 与区块均为真实数据。
 */
public record WorkspaceSummaryResponse(
        UserBrief me,
        KpiSummary kpis,
        List<TaskCardResponse> myTasks,
        List<ApprovalCardView> pendingApprovals,
        List<ActivityView> activity,
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