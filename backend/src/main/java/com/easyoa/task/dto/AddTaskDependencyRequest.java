package com.easyoa.task.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 添加前置依赖（同项目内；循环依赖会被拒绝）。
 */
public record AddTaskDependencyRequest(
        @NotNull(message = "请选择前置任务")
        Long dependsOnTaskId) {
}