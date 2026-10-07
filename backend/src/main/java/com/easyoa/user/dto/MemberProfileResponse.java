package com.easyoa.user.dto;

import java.util.List;

import com.easyoa.organization.dto.OrgUnitBrief;
import com.easyoa.organization.dto.UserOrgMembershipView;

/**
 * 成员档案（团队页面点击成员后的侧栏详情）。
 *
 * <p>敏感字段按权限过滤：{@code contact} 仅在「本人 / ROOT / ADMIN / 其组织负责人」
 * 的情况下返回，其余情况为 null，且 {@code contactVisible=false}。
 */
public record MemberProfileResponse(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        String jobTitle,
        String systemRole,
        String status,
        String bio,
        OrgUnitBrief primaryOrgUnit,
        List<UserOrgMembershipView> orgUnits,
        boolean contactVisible,
        ContactInfo contact,
        java.time.Instant lastLoginAt,
        java.time.Instant createdAt) {

    public record ContactInfo(String email, String phone) {
    }
}