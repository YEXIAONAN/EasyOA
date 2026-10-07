package com.easyoa.organization.dto;

/**
 * 移动组织单元（为空表示移动到顶层）。
 */
public record MoveOrgUnitRequest(Long newParentId) {
}