package com.easyoa.security.dto;

import java.time.Instant;
import java.util.Map;

import com.easyoa.security.domain.SensitiveOperationType;

/**
 * 高危操作执行结果。{@code result} 内容随操作类型不同（例如导出操作返回文件名与行数）。
 */
public record SensitiveOperationResultView(
        SensitiveOperationType type,
        String title,
        String message,
        Map<String, Object> result,
        Instant executedAt) {
}
