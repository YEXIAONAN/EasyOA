package com.easyoa.organization.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 设置主部门（必须是该用户已有的组织归属）。
 */
public record SetPrimaryOrgUnitRequest(@NotNull(message = "请选择主部门") Long orgUnitId) {
}