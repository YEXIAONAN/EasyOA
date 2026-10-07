package com.easyoa.user.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.organization.application.OrgMembershipService;
import com.easyoa.organization.application.OrganizationPermissionService;
import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;

/**
 * 用户域权限：成员档案可见性、账号管理与角色变更的唯一判定入口。
 *
 * <p>规则（v0.1.0）：
 * <ul>
 *   <li>成员档案基础信息：所有登录用户可见；</li>
 *   <li>联系方式（邮箱 / 手机）：仅本人、ROOT / ADMIN、或其所在组织的负责人可见；</li>
 *   <li>创建成员：ADMIN 只能创建 MEMBER；创建 ADMIN / ROOT 需要 ROOT；</li>
 *   <li>启用 / 禁用：ROOT / ADMIN；ADMIN 不得操作 ROOT / ADMIN 账号；</li>
 *   <li>角色变更：仅 ROOT，且不允许移除最后一个 ROOT。</li>
 * </ul>
 */
@Service
public class UserPermissionService {

    private final OrgMembershipService orgMembershipService;
    private final OrganizationPermissionService organizationPermissionService;

    public UserPermissionService(OrgMembershipService orgMembershipService,
            OrganizationPermissionService organizationPermissionService) {
        this.orgMembershipService = orgMembershipService;
        this.organizationPermissionService = organizationPermissionService;
    }

    @Transactional(readOnly = true)
    public boolean canViewContact(SecurityUser viewer, Long targetUserId) {
        if (viewer == null) {
            return false;
        }
        if (viewer.id().equals(targetUserId) || viewer.systemRole().isAdminLike()) {
            return true;
        }
        // 目标成员所在组织（含上级链）的负责人可以看到联系方式
        return orgMembershipService.orgUnitIdsOf(targetUserId).stream()
                .anyMatch(orgUnitId -> organizationPermissionService.isManagerOfSubtree(viewer.id(), orgUnitId));
    }

    public void requireCreateUser(SecurityUser actor, SystemRole requestedRole) {
        requireAdminLike(actor, "只有系统管理员可以创建成员");
        if (requestedRole != SystemRole.MEMBER && actor.systemRole() != SystemRole.ROOT) {
            throw ApiException.forbidden("只有 ROOT 可以创建管理员账号");
        }
    }

    public void requireManageAccount(SecurityUser actor, User target) {
        requireAdminLike(actor, "只有系统管理员可以启用或禁用账号");
        // 先判定「操作自己」：给出更准确的原因，而不是笼统的权限不足
        if (actor.id().equals(target.getId())) {
            throw ApiException.conflict("不能修改自己的账号状态");
        }
        if (actor.systemRole() == SystemRole.ADMIN && target.getSystemRole().isAdminLike()) {
            throw ApiException.forbidden("ADMIN 不能操作 ROOT / ADMIN 账号");
        }
    }

    public void requireChangeRole(SecurityUser actor) {
        SecurityUser current = actor == null ? RequestContext.currentUser() : actor;
        if (current == null || current.systemRole() != SystemRole.ROOT) {
            throw ApiException.forbidden("只有 ROOT 可以变更系统角色");
        }
    }

    private void requireAdminLike(SecurityUser actor, String message) {
        SecurityUser current = actor == null ? RequestContext.currentUser() : actor;
        if (current == null) {
            throw new ApiException(com.easyoa.common.exception.ErrorCode.UNAUTHENTICATED);
        }
        if (!current.systemRole().isAdminLike()) {
            throw ApiException.forbidden(message);
        }
    }
}