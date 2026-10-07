package com.easyoa.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 首次初始化请求：创建组织 + 第一个 ROOT 用户。
 */
public record SetupInitializeRequest(
        @NotBlank(message = "请输入组织名称")
        @Size(max = 80, message = "组织名称过长")
        String organizationName,

        @NotBlank(message = "请输入 ROOT 用户名")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,31}$",
                message = "用户名需以字母开头，仅含字母/数字/下划线，长度 3~32")
        String username,

        @NotBlank(message = "请输入显示名称")
        @Size(max = 64, message = "显示名称过长")
        String displayName,

        @NotBlank(message = "请输入密码")
        @Size(max = 128, message = "密码过长")
        String password) {
}