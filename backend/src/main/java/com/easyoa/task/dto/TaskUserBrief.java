package com.easyoa.task.dto;

import com.easyoa.user.domain.User;

/**
 * 任务相关视图中使用的用户简要信息。
 */
public record TaskUserBrief(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        String jobTitle) {

    public static TaskUserBrief from(User user) {
        if (user == null) {
            return null;
        }
        return new TaskUserBrief(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getJobTitle());
    }
}