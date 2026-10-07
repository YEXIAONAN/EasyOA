package com.easyoa.task.application;

import com.easyoa.task.domain.TaskPriority;

/**
 * 项目内任务列表查询条件（仅顶层任务，分页）。
 */
public record TaskQuery(String keyword, Long statusId, TaskPriority priority, int page, int size) {

    public static TaskQuery of(String keyword, Long statusId, TaskPriority priority, Integer page, Integer size) {
        String normalized = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
        return new TaskQuery(normalized, statusId, priority, page == null ? 1 : page,
                size == null ? 15 : Math.min(size, 100));
    }
}