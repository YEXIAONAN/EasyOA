package com.easyoa.organization.dto;

import java.time.Instant;

/**
 * 单个用户的组织归属视图（含主部门标记）。
 */
public record UserOrgMembershipView(
        OrgUnitBrief orgUnit,
        boolean primary,
        Instant joinedAt) {
}