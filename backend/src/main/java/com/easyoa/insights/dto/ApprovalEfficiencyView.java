package com.easyoa.insights.dto;

import java.time.Instant;

/**
 * 审批效率。
 *
 * @param averageHours      平均审批时长（小时，基于已完结实例的 submitted_at → finished_at）
 * @param approvedRate      通过率（通过 / (通过 + 拒绝)）
 * @param averageHoursScope 平均时长的统计口径说明（已完结实例数）
 */
public record ApprovalEfficiencyView(
        int pending,
        int approved,
        int rejected,
        int returned,
        int finishedCount,
        Double averageHours,
        Double approvedRate,
        Instant lastFinishedAt) {
}
