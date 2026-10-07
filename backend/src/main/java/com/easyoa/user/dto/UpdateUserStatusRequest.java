package com.easyoa.user.dto;

import com.easyoa.user.domain.UserStatus;

import jakarta.validation.constraints.NotNull;

/**
 * 启用 / 禁用成员。
 */
public record UpdateUserStatusRequest(@NotNull(message = "请选择账号状态") UserStatus status) {
}