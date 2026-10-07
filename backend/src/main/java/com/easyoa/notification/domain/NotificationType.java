package com.easyoa.notification.domain;

/**
 * 业务通知类型（与安全事件、审计日志分离）。
 */
public enum NotificationType {

    // 任务
    TASK_ASSIGNED,
    TASK_STATUS_CHANGED,
    TASK_DUE_SOON,
    TASK_OVERDUE,

    // 评论
    MENTION,
    COMMENT_REPLY,

    // 审批
    APPROVAL_PENDING,
    APPROVAL_APPROVED,
    APPROVAL_REJECTED,
    APPROVAL_RETURNED,

    // 项目
    PROJECT_MEMBER_ADDED,
    PROJECT_ROLE_CHANGED
}