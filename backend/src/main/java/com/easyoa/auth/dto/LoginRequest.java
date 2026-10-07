package com.easyoa.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 *
 * <p>{@code totpCode} 仅在账号已绑定动态口令时必填：
 * 首次请求可以不带，服务端返回 {@code TOTP_REQUIRED} 后由前端引导用户补填。
 */
public record LoginRequest(
        @NotBlank(message = "请输入用户名")
        @Size(max = 64, message = "用户名过长")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(max = 128, message = "密码过长")
        String password,

        @Pattern(regexp = "^$|\\d{6}", message = "动态验证码为 6 位数字")
        String totpCode) {
}