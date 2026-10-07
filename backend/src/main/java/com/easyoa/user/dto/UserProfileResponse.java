package com.easyoa.user.dto;

import java.time.Instant;

import com.easyoa.user.domain.User;

/**
 * 用户档案响应。
 *
 * <p>敏感字段（passwordHash / totpSecret / 登录失败计数等）永远不会出现在该结构中。
 */
public record UserProfileResponse(
        Long id,
        String username,
        String displayName,
        String systemRole,
        String status,
        String email,
        String phone,
        String bio,
        String avatarUrl,
        boolean totpEnabled,
        Instant lastLoginAt,
        Instant createdAt) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getSystemRole().name(),
                user.getStatus().name(),
                user.getEmail(),
                user.getPhone(),
                user.getBio(),
                user.getAvatarUrl(),
                user.isTotpEnabled(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}