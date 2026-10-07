package com.easyoa.task.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 变更任务状态（看板拖拽 / 侧栏切换）。
 *
 * <p>当目标状态需要「开始工作」但存在未完成前置依赖时，后端返回
 * {@code TASK_BLOCKED_BY_DEPENDENCIES}；用户明确选择「忽略依赖并开始」并填写原因后，
 * 携带 {@link #overrideReason} 重试，后端复查权限后放行并写入审计。
 */
public record ChangeTaskStatusRequest(
        @NotNull(message = "请选择目标状态")
        Long statusId,

        /** 「忽略依赖并开始」时必填的原因（普通流转为空）。 */
        @Size(max = 500, message = "原因过长")
        String overrideReason) {
}