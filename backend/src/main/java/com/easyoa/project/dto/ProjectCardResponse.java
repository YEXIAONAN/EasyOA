package com.easyoa.project.dto;

import java.time.Instant;

/**
 * 项目列表卡片。
 */
public record ProjectCardResponse(
        Long id,
        String name,
        String description,
        String status,
        int progress,
        Instant plannedStartAt,
        Instant plannedEndAt,
        ProjectMemberView owner,
        ProjectMemberView deputyOwner,
        long memberCount,
        /** 当前登录用户在该项目中的角色；管理员未参与项目时为 null。 */
        String myRole,
        Instant updatedAt) {
}