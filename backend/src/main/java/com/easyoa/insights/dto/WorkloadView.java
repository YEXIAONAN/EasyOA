package com.easyoa.insights.dto;

/**
 * 成员任务负载。
 *
 * @param openTasks    未完成（非 DONE / CLOSED）任务数
 * @param overdueTasks 其中已逾期的数量
 */
public record WorkloadView(Long userId, String displayName, int openTasks, int overdueTasks) {
}
