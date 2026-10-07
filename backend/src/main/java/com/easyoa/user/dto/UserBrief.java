package com.easyoa.user.dto;

import com.easyoa.user.domain.User;

/**
 * 用户简要信息（跨模块使用：组织成员列表、负责人展示、成员目录等）。
 *
 * <p>不包含任何敏感字段（密码哈希、TOTP、联系方式按场景另行授权返回）。
 */
public record UserBrief(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        String jobTitle,
        String systemRole,
        String status) {

    public static UserBrief from(User user) {
        return new UserBrief(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getJobTitle(),
                user.getSystemRole().name(),
                user.getStatus().name());
    }
}