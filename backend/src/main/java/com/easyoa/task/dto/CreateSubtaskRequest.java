package com.easyoa.task.dto;

import java.time.Instant;

import com.easyoa.task.domain.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 添加一级子任务（负责人缺省为创建者本人）。
 */
public record CreateSubtaskRequest(
        @NotBlank(message = "请输入子任务标题")
        @Size(max = 200, message = "子任务标题过长")
        String title,

        @Size(max = 4000, message = "任务描述过长")
        String description,

        Long primaryAssigneeId,

        TaskPriority priority,

        Instant plannedStartAt,

        Instant plannedEndAt) {
}