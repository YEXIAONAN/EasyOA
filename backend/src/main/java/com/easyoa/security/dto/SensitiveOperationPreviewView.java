package com.easyoa.security.dto;

import java.util.List;

import com.easyoa.security.domain.SensitiveOperationType;

/**
 * 高危操作影响范围预览。
 *
 * @param confirmationPhrase 最终确认步骤要求逐字输入的短语
 * @param requiresTarget     是否必须指定操作目标（例如 MFA 重置的账号）
 */
public record SensitiveOperationPreviewView(
        SensitiveOperationType type,
        String title,
        String description,
        List<String> impacts,
        String confirmationPhrase,
        boolean requiresTarget,
        String targetLabel) {
}
