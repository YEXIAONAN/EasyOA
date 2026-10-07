package com.easyoa.insights.dto;

/**
 * 任务趋势的一个时间桶（按周）。
 *
 * @param weekStart      周起始日期（周一，ISO yyyy-MM-dd）
 * @param createdCount   该周新建任务数
 * @param completedCount 该周完成任务数
 */
public record TaskTrendPoint(String weekStart, int createdCount, int completedCount) {
}
