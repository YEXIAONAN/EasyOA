package com.easyoa.task.dto;

import com.easyoa.task.domain.TaskStatus;

/**
 * 任务状态视图：自定义名称 + 系统统一类型。
 */
public record TaskStatusView(
        Long id,
        String name,
        String systemType,
        int sortOrder) {

    public static TaskStatusView from(TaskStatus status) {
        return new TaskStatusView(
                status.getId(),
                status.getName(),
                status.getSystemType().name(),
                status.getSortOrder());
    }
}