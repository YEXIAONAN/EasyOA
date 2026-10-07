package com.easyoa.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 确认绑定动态口令。 */
public record ConfirmMfaRequest(
        @NotBlank(message = "请输入动态验证码")
        @Pattern(regexp = "\\d{6}", message = "动态验证码为 6 位数字")
        String code) {
}
