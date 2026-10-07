package com.easyoa.organization.dto;

import java.util.List;

import com.easyoa.user.dto.UserBrief;

/**
 * 组织树节点。
 */
public record OrgUnitTreeNode(
        Long id,
        Long parentId,
        String name,
        String type,
        String status,
        int sortOrder,
        int depth,
        UserBrief manager,
        long memberCount,
        List<OrgUnitTreeNode> children) {
}