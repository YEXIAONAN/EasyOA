package com.easyoa.organization.dto;

import com.easyoa.user.dto.UserBrief;

/**
 * 组织单元详情（成员管理面板头部使用）。
 */
public record OrgUnitDetailResponse(
        Long id,
        Long parentId,
        String parentName,
        String name,
        String type,
        String status,
        int sortOrder,
        UserBrief manager,
        long memberCount) {
}