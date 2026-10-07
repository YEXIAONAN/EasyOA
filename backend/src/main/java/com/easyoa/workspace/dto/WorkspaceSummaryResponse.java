package com.easyoa.workspace.dto;

import java.time.Instant;
import java.util.List;

import com.easyoa.project.dto.ProjectCardResponse;
import com.easyoa.task.dto.TaskCardResponse;

/**
 * 工作台摘要。
 *
 * <p>Phase 4 起「我的任务」「即将到期」KPI 与「我的任务」区块接入真实任务数据；
 * 审批（Phase 6）相关统计暂为 0，前端按空状态渲染。
 */
public record WorkspaceSummaryResponse(
        UserBrief me,
        KpiSummary kpis,
        List<TaskCardResponse> myTasks,
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