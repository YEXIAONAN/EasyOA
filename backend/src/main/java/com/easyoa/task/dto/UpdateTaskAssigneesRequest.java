package com.easyoa.task.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 调整负责人（主负责人 × 1、副负责人 × 0~1）。
 *
 * <p>仅主负责人或项目负责人 / 副负责人（full control）可修改主负责人；
 * 副负责人不能修改主负责人。
 */
public record UpdateTaskAssigneesRequest(
        @NotNull(message = "请选择主负责人")
        Long primaryAssigneeId,

        /** null 表示不设置副负责人。 */
        Long deputyAssigneeId) {
}