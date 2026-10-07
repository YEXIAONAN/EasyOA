package com.easyoa.task.dto;

import com.easyoa.task.domain.TaskStatusType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增项目自定义任务状态（必须映射系统统一类型，追加到列尾）。
 */
public record CreateTaskStatusRequest(
        @NotBlank(message = "请输入状态名称")
        @Size(max = 40, message = "状态名称过长")
        String name,

        @NotNull(message = "请选择系统类型")
        TaskStatusType systemType) {
}