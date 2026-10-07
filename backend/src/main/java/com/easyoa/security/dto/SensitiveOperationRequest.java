package com.easyoa.security.dto;

import java.util.Map;

import com.easyoa.security.domain.SensitiveOperationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 高危操作执行入参：完整承载「密码 → TOTP → Reason → Final Confirm」四要素。
 *
 * <p>{@code reason} 的长度校验在应用层执行，以便返回语义明确的 {@code REASON_REQUIRED}
 * 而不是笼统的字段校验失败。
 */
public record SensitiveOperationRequest(
        @NotNull(message = "缺少操作类型") SensitiveOperationType type,
        Long targetId,

        @NotBlank(message = "请输入当前登录密码")
        String currentPassword,

        @NotBlank(message = "请输入动态验证码")
        String totpCode,

        String reason,

        @NotBlank(message = "请输入最终确认口令")
        String confirmation,

        Map<String, Object> payload) {
}
