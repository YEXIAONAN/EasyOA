package com.easyoa.security.dto;

import java.util.Map;

import com.easyoa.security.domain.SensitiveOperationType;

import jakarta.validation.constraints.NotNull;

/**
 * 高危操作影响范围预览入参。
 *
 * <p>预览不执行任何写操作，只计算「如果执行会发生什么」，供操作者在最终确认前评估。
 */
public record SensitiveOperationPreviewRequest(
        @NotNull(message = "缺少操作类型") SensitiveOperationType type,
        Long targetId,
        Map<String, Object> payload) {
}
