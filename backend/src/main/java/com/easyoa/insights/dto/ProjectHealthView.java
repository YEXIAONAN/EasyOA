package com.easyoa.insights.dto;

import java.time.Instant;

/**
 * 项目健康度视图。
 *
 * @param health HEALTHY（正常）/ AT_RISK（有逾期）/ BLOCKED（已过期未完成）
 */
public record ProjectHealthView(
        Long projectId,
        String name,
        String status,
        int progress,
        Instant plannedEndAt,
        int totalTasks,
        int doneTasks,
        int overdueTasks,
        String health) {
}
