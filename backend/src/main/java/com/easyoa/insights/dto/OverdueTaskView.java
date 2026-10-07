package com.easyoa.insights.dto;

import java.time.Instant;

/**
 * 逾期任务条目。
 *
 * @param overdueDays 逾期天数（按自然日向上取整）
 */
public record OverdueTaskView(
        Long taskId,
        String title,
        Long projectId,
        String projectName,
        String primaryAssignee,
        Instant plannedEndAt,
        int progress,
        long overdueDays) {
}
