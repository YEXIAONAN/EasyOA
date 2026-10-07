package com.easyoa.workspace.dto;

import java.time.Instant;
import java.util.List;

import com.easyoa.project.dto.ProjectCardResponse;

/**
 * 工作台摘要。
 *
 * <p>Phase 3 起「进行中项目」KPI 与「项目进度」区块接入真实项目数据；
 * 任务（Phase 4）与审批（Phase 6）相关统计暂为 0，前端按空状态渲染。
 */
public record WorkspaceSummaryResponse(
        UserBrief me,
        KpiSummary kpis,
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