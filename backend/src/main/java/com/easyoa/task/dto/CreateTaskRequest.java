package com.easyoa.task.dto;

import java.time.Instant;
import java.util.List;

import com.easyoa.task.domain.ProgressMode;
import com.easyoa.task.domain.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建任务 / 子任务（parentId 为空表示顶层任务）。
 *
 * <p>派发规则（服务层判定）：项目 OWNER / DEPUTY_OWNER 派发立即生效；
 * 普通成员把任务派给自己立即生效，派给他人则进入 PENDING_ASSIGNMENT 等待审核。
 */
public record CreateTaskRequest(
        @NotBlank(message = "请输入任务标题")
        @Size(max = 200, message = "任务标题过长")
        String title,

        @Size(max = 4000, message = "任务描述过长")
        String description,

        /** 主负责人（必须）；子任务可为空，默认创建者本人。 */
        Long primaryAssigneeId,

        /** 副负责人（0~1）。 */
        Long deputyAssigneeId,

        /** 协作成员（× N）。 */
        List<Long> collaboratorUserIds,

        TaskPriority priority,

        /** 初始状态；为空时使用项目的默认「待处理」状态。 */
        Long statusId,

        Instant plannedStartAt,

        Instant plannedEndAt,

        ProgressMode progressMode) {
}