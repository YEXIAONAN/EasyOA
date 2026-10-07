package com.easyoa.organization.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 添加组织成员。
 */
public record AddOrgMemberRequest(@NotNull(message = "请选择成员") Long userId) {
}