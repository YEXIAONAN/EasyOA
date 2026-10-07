package com.easyoa.insights.dto;

import java.time.Instant;

/**
 * 项目健康度统计（数据层投影，健康度判定在应用层完成）。
 */
public record ProjectHealthStats(
        Long projectId,
        String name,
        String status,
        int progress,
        Instant plannedEndAt,
        int totalTasks,
        int doneTasks,
        int overdueTasks) {
}
