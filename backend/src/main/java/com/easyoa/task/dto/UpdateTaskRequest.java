package com.easyoa.task.dto;

import java.time.Instant;

import com.easyoa.task.domain.ProgressMode;
import com.easyoa.task.domain.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 编辑任务基础信息（标题 / 描述 / 优先级 / 计划时间 / 进度模式）。
 *
 * <p>状态流转、进度、负责人、协作者、依赖各有独立接口，便于按权限与审计粒度控制。
 */
public record UpdateTaskRequest(
        @NotBlank(message = "请输入任务标题")
        @Size(max = 200, message = "任务标题过长")
        String title,

        @Size(max = 4000, message = "任务描述过长")
        String description,

        TaskPriority priority,

        Instant plannedStartAt,

        Instant plannedEndAt,

        ProgressMode progressMode) {
}