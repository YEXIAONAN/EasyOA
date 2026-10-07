package com.easyoa.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "请输入当前密码")
        String currentPassword,

        @NotBlank(message = "请输入新密码")
        @Size(max = 128, message = "新密码过长")
        String newPassword) {
}