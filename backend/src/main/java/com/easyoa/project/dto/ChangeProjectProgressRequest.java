package com.easyoa.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 更新项目总体进度（OWNER / DEPUTY_OWNER）。
 */
public record ChangeProjectProgressRequest(
        @NotNull(message = "请输入进度")
        @Min(value = 0, message = "进度不能小于 0")
        @Max(value = 100, message = "进度不能大于 100")
        Integer progress) {
}