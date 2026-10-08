package com.easyoa.task.dto;

import java.time.Instant;

/**
 * 成员近期任务（团队页面成员档案「近期任务」区块）。
 *
 * <p>这是「他人视角」的只读摘要：不含 canManage / canFullControl 等权限位，
 * 避免前端误把它当成可操作的任务卡片。
 */
public record MemberTaskBrief(
        Long taskId,
        Long projectId,
        String projectName,
        String title,
        String statusName,
        String statusType,
        String priority,
        int progress,
        Instant plannedEndAt,
        boolean overdue) {
}