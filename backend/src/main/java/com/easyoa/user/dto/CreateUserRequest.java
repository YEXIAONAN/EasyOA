package com.easyoa.user.dto;

import com.easyoa.user.domain.SystemRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建成员（管理员操作）。
 *
 * <p>权限规则：ADMIN 只能创建 MEMBER；创建 ADMIN / ROOT 需要 ROOT 身份。
 */
public record CreateUserRequest(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,31}$",
                message = "用户名需以字母开头，仅含字母 / 数字 / 下划线，长度 3~32")
        String username,

        @NotBlank(message = "请输入显示名称")
        @Size(max = 64, message = "显示名称过长")
        String displayName,

        @Size(max = 64, message = "职位过长")
        String jobTitle,

        @Email(message = "邮箱格式不正确")
        @Size(max = 190, message = "邮箱过长")
        String email,

        @Size(max = 32, message = "手机号过长")
        String phone,

        @NotNull(message = "请选择系统角色")
        SystemRole systemRole,

        @NotBlank(message = "请设置初始密码")
        @Size(max = 128, message = "密码过长")
        String initialPassword,

        /** 可选：同时加入的组织单元（首个组织自动成为主部门）。 */
        Long orgUnitId) {
}