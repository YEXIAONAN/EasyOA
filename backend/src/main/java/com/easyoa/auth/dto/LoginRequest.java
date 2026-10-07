package com.easyoa.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 */
public record LoginRequest(
        @NotBlank(message = "请输入用户名")
        @Size(max = 64, message = "用户名过长")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(max = 128, message = "密码过长")
        String password) {
}