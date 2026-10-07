package com.easyoa.project.dto;

import java.time.Instant;

import com.easyoa.project.domain.ProjectMember;

/**
 * 项目成员视图（含项目角色）。
 */
public record ProjectMemberView(
        Long userId,
        String username,
        String displayName,
        String avatarUrl,
        String jobTitle,
        String role,
        Instant joinedAt) {

    public static ProjectMemberView from(ProjectMember member) {
        var user = member.getUser();
        return new ProjectMemberView(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getJobTitle(),
                member.getRole().name(),
                member.getJoinedAt());
    }
}