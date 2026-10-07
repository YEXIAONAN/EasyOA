package com.easyoa.project.dto;

import java.time.Instant;
import java.util.List;

/**
 * 项目详情（概览页）。
 */
public record ProjectDetailResponse(
        Long id,
        String name,
        String description,
        String status,
        int progress,
        Instant plannedStartAt,
        Instant plannedEndAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        ProjectMemberView owner,
        ProjectMemberView deputyOwner,
        List<ProjectMemberView> members,
        /** 当前登录用户在该项目中的角色；管理员未参与时为 null。 */
        String myRole,
        /** 当前登录用户可执行的操作（前端仅用于展示入口，后端仍独立鉴权）。 */
        Permissions permissions) {

    public record Permissions(
            boolean canEditInfo,
            boolean canChangeStatus,
            boolean canManageMembers,
            boolean canSetDeputy,
            boolean canTransferOwner,
            boolean canArchive) {
    }
}