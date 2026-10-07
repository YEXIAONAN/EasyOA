package com.easyoa.user.dto;

import java.time.Instant;

import com.easyoa.organization.dto.OrgUnitBrief;

/**
 * 成员目录卡片（团队页面）。
 *
 * <p>故意不包含联系方式等敏感字段：这些字段只在成员档案中按权限返回。
 */
public record MemberCardResponse(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        String jobTitle,
        String systemRole,
        String status,
        OrgUnitBrief primaryOrgUnit,
        long orgUnitCount,
        Instant joinedAt) {
}