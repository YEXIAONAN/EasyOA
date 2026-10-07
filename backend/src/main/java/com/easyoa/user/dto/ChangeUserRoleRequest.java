package com.easyoa.user.dto;

import com.easyoa.user.domain.SystemRole;

import jakarta.validation.constraints.NotNull;

/**
 * 变更系统角色（仅 ROOT 可执行；且不允许移除最后一个 ROOT）。
 */
public record ChangeUserRoleRequest(@NotNull(message = "请选择系统角色") SystemRole systemRole) {
}