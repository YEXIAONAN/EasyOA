package com.easyoa.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 手工更新任务进度（0~100）。
 *
 * <p>进度 AUTO 模式且存在子任务时不允许手工更新（由子任务完成比例自动计算）。
 */
public record ChangeTaskProgressRequest(
        @NotNull(message = "请输入进度")
        @Min(value = 0, message = "进度不能小于 0")
        @Max(value = 100, message = "进度不能大于 100")
        Integer progress) {
}