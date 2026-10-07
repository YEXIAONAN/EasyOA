package com.easyoa.auth.dto;

import java.time.Instant;

import com.easyoa.user.dto.UserProfileResponse;

/**
 * 当前登录用户（/api/auth/me 与登录响应）。
 *
 * <p>只包含前端渲染所需字段，敏感字段一律不返回。
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String displayName,
        String systemRole,
        String avatarUrl,
        String email,
        boolean totpEnabled,
        boolean setupRequired,
        Instant lastLoginAt) {

    public static CurrentUserResponse from(UserProfileResponse profile) {
        return new CurrentUserResponse(
                profile.id(),
                profile.username(),
                profile.displayName(),
                profile.systemRole(),
                profile.avatarUrl(),
                profile.email(),
                profile.totpEnabled(),
                false,
                profile.lastLoginAt());
    }
}