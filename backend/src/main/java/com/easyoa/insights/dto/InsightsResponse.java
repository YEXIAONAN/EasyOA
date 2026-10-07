package com.easyoa.insights.dto;

import java.time.Instant;
import java.util.List;

/**
 * 数据中心总览（Phase 9）。
 *
 * <p>刻意保持克制：只提供项目健康度、任务趋势、逾期任务、成员负载与审批效率五组数据，
 * 不引入图表库，也不堆砌「看起来专业」但无决策价值的指标。
 *
 * @param scope ALL（管理员全局）或 MY_PROJECTS（仅我所在项目）
 */
public record InsightsResponse(
        String scope,
        Instant generatedAt,
        List<ProjectHealthView> projectHealth,
        List<TaskTrendPoint> taskTrend,
        List<OverdueTaskView> overdueTasks,
        List<WorkloadView> workload,
        ApprovalEfficiencyView approvalEfficiency) {
}
