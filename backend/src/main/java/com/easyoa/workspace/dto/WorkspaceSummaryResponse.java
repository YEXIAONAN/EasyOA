package com.easyoa.workspace.dto;

import java.time.Instant;

/**
 * 工作台摘要。
 *
 * <p>KPI 中暂未交付的模块统一返回 0，前端按空状态渲染；
 * Phase 3（项目）/ Phase 4（任务）/ Phase 6（审批）交付后替换为真实统计查询。
 */
public record WorkspaceSummaryResponse(UserBrief me, KpiSummary kpis) {

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