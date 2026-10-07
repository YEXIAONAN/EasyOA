package com.easyoa.organization.dto;

import java.time.Instant;

import com.easyoa.organization.domain.OrgMembership;

/**
 * 组织成员条目。
 */
public record OrgMemberResponse(
        Long userId,
        String username,
        String displayName,
        String avatarUrl,
        String jobTitle,
        String systemRole,
        String status,
        boolean primary,
        Instant joinedAt) {

    public static OrgMemberResponse from(OrgMembership membership) {
        var user = membership.getUser();
        return new OrgMemberResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getJobTitle(),
                user.getSystemRole().name(),
                user.getStatus().name(),
                membership.isPrimary(),
                membership.getJoinedAt());
    }
}