package com.easyoa.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 自助解绑动态口令：需要当前密码 + 动态验证码双重确认。
 */
public record DisableMfaRequest(
        @NotBlank(message = "请输入当前密码")
        String currentPassword,

        @NotBlank(message = "请输入动态验证码")
        @Pattern(regexp = "\\d{6}", message = "动态验证码为 6 位数字")
        String code) {
}
