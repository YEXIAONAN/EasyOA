package com.easyoa.organization.application;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.organization.domain.OrgUnit;
import com.easyoa.organization.repository.OrgUnitRepository;

/**
 * 组织权限：组织域的唯一权限入口。
 *
 * <p>禁止在业务代码中散落 {@code if (user.getSystemRole() == ROOT)} 之类的判断，
 * 所有组织相关授权必须经过本服务。
 *
 * <p>角色模型（v0.1.0）：
 * <ul>
 *   <li>组织结构的创建 / 修改 / 移动 / 归档：仅 ROOT / ADMIN；</li>
 *   <li>组织成员管理：ROOT / ADMIN，或该单元（含其上级链）的负责人。</li>
 * </ul>
 */
@Service
public class OrganizationPermissionService {

    private final OrgUnitRepository orgUnitRepository;

    public OrganizationPermissionService(OrgUnitRepository orgUnitRepository) {
        this.orgUnitRepository = orgUnitRepository;
    }

    /** 组织结构管理（ROOT / ADMIN）。 */
    public void requireManageStructure() {
        SecurityUser actor = requireAuthenticated();
        if (!actor.systemRole().isAdminLike()) {
            throw ApiException.forbidden("只有系统管理员可以调整组织架构");
        }
    }

    /** 组织成员管理（ROOT / ADMIN 或该单元及其上级链的负责人）。 */
    public void requireManageMembers(Long orgUnitId) {
        SecurityUser actor = requireAuthenticated();
        if (actor.systemRole().isAdminLike()) {
            return;
        }
        if (!isManagerOfSubtree(actor.id(), orgUnitId)) {
            throw ApiException.forbidden("你不是该组织单元的负责人，无法管理其成员");
        }
    }

    /** 是否为该单元（含其上级链）的负责人。 */
    @Transactional(readOnly = true)
    public boolean isManagerOfSubtree(Long actorUserId, Long orgUnitId) {
        if (actorUserId == null || orgUnitId == null) {
            return false;
        }
        Long cursor = orgUnitId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            OrgUnit unit = orgUnitRepository.findById(cursor).orElse(null);
            if (unit == null) {
                return false;
            }
            if (actorUserId.equals(unit.getManagerUserId())) {
                return true;
            }
            cursor = unit.getParentId();
        }
        return false;
    }

    private SecurityUser requireAuthenticated() {
        SecurityUser actor = RequestContext.currentUser();
        if (actor == null) {
            throw new ApiException(com.easyoa.common.exception.ErrorCode.UNAUTHENTICATED);
        }
        return actor;
    }
}