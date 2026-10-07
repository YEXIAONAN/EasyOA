package com.easyoa.workspace.dto;

import java.time.Instant;

/**
 * Activity Feed 条目（业务动态，不是审计日志）。
 *
 * <p>由业务表（评论 / 任务 / 审批）与状态变更记录实时聚合，展示
 * 「谁完成了任务 / 谁评论了任务 / 谁创建了审批 / 谁调整了状态」等业务语言。
 */
public record ActivityView(
        String type,
        Long actorId,
        String actorName,
        String avatarUrl,
        /** 动作短语，例如「完成了任务」「评论了任务」。 */
        String action,
        /** 对象描述，例如任务标题或审批标题。 */
        String target,
        String link,
        Instant time) {
}