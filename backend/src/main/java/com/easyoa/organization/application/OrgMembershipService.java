package com.easyoa.organization.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.organization.domain.OrgMembership;
import com.easyoa.organization.domain.OrgUnit;
import com.easyoa.organization.dto.OrgMemberResponse;
import com.easyoa.organization.dto.OrgUnitBrief;
import com.easyoa.organization.dto.UserOrgMembershipView;
import com.easyoa.organization.repository.OrgMembershipRepository;
import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.User;

/**
 * 组织归属（成员多组织归属 + 主部门）。
 *
 * <p>不变式：
 * <ul>
 *   <li>同一用户在同一组织单元下最多一条归属；</li>
 *   <li>用户只要存在组织归属，就恰好有一个主部门；</li>
 *   <li>首个归属自动成为主部门；移除主部门时自动递补最早的归属。</li>
 * </ul>
 */
@Service
public class OrgMembershipService {

    private final OrgMembershipRepository orgMembershipRepository;
    private final OrgUnitService orgUnitService;
    private final UserService userService;
    private final AuditService auditService;
    private final OrganizationPermissionService permissionService;

    public OrgMembershipService(OrgMembershipRepository orgMembershipRepository, OrgUnitService orgUnitService,
            UserService userService, AuditService auditService, OrganizationPermissionService permissionService) {
        this.orgMembershipRepository = orgMembershipRepository;
        this.orgUnitService = orgUnitService;
        this.userService = userService;
        this.auditService = auditService;
        this.permissionService = permissionService;
    }

    // --- 查询 -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrgMemberResponse> membersOf(Long orgUnitId) {
        orgUnitService.requireUnit(orgUnitId);
        return orgMembershipRepository.findByOrgUnitIdWithUser(orgUnitId).stream()
                .map(OrgMemberResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserOrgMembershipView> membershipsOf(Long userId) {
        return orgMembershipRepository.findByUserIdWithOrgUnit(userId).stream()
                .map(membership -> new UserOrgMembershipView(
                        OrgUnitBrief.from(membership.getOrgUnit()),
                        membership.isPrimary(),
                        membership.getJoinedAt()))
                .toList();
    }

    /** 批量获取多个用户的归属（成员目录避免 N+1）。 */
    @Transactional(readOnly = true)
    public Map<Long, List<UserOrgMembershipView>> membershipsOfUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<UserOrgMembershipView>> result = new HashMap<>();
        for (OrgMembership membership : orgMembershipRepository.findByUserIdsWithOrgUnit(userIds)) {
            result.computeIfAbsent(membership.getUser().getId(), key -> new ArrayList<>())
                    .add(new UserOrgMembershipView(
                            OrgUnitBrief.from(membership.getOrgUnit()),
                            membership.isPrimary(),
                            membership.getJoinedAt()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<OrgUnitBrief> primaryOrgUnitOf(Long userId) {
        return orgMembershipRepository.findFirstByUserIdAndPrimaryTrue(userId)
                .map(membership -> OrgUnitBrief.from(membership.getOrgUnit()));
    }

    @Transactional(readOnly = true)
    public List<Long> orgUnitIdsOf(Long userId) {
        return orgMembershipRepository.findByUserId(userId).stream()
                .map(membership -> membership.getOrgUnit().getId())
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> userIdsInUnits(Collection<Long> orgUnitIds) {
        if (orgUnitIds == null || orgUnitIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(orgMembershipRepository.findUserIdsByOrgUnitIds(orgUnitIds));
    }

    @Transactional(readOnly = true)
    public long memberCount(Long orgUnitId) {
        return orgMembershipRepository.countByOrgUnitId(orgUnitId);
    }

    // --- 变更 -----------------------------------------------------------------

    @Transactional
    public List<OrgMemberResponse> addMember(Long orgUnitId, Long userId) {
        permissionService.requireManageMembers(orgUnitId);
        OrgUnit unit = orgUnitService.requireUnit(orgUnitId);
        if (!unit.isActive()) {
            throw ApiException.conflict("已归档的组织单元不能再添加成员");
        }
        User user = userService.getById(userId);
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "该账号已被禁用，无法加入组织");
        }
        if (orgMembershipRepository.findByUserIdAndOrgUnitId(userId, orgUnitId).isPresent()) {
            throw ApiException.conflict("该成员已在此组织单元中");
        }

        List<OrgMembership> existing = orgMembershipRepository.findByUserId(userId);
        boolean becomePrimary = existing.stream().noneMatch(OrgMembership::isPrimary);
        orgMembershipRepository.save(new OrgMembership(user, unit, becomePrimary));

        auditService.record(AuditEntry.action(AuditActions.ORG_MEMBERSHIP_CHANGED, RiskLevel.ELEVATED)
                .resource("ORG_UNIT", orgUnitId)
                .after(java.util.Map.of("userId", userId, "orgUnitId", orgUnitId, "primary", becomePrimary))
                .reason("添加组织成员"));
        return membersOf(orgUnitId);
    }

    @Transactional
    public List<OrgMemberResponse> removeMember(Long orgUnitId, Long userId) {
        permissionService.requireManageMembers(orgUnitId);
        OrgMembership membership = orgMembershipRepository.findByUserIdAndOrgUnitId(userId, orgUnitId)
                .orElseThrow(() -> ApiException.notFound("该成员不在此组织单元中"));

        boolean wasPrimary = membership.isPrimary();
        orgMembershipRepository.delete(membership);

        // 主部门被移除 → 自动递补（保持「有归属则必有主部门」不变式）
        if (wasPrimary) {
            List<OrgMembership> remaining = new ArrayList<>(orgMembershipRepository.findByUserId(userId));
            remaining.stream()
                    .filter(candidate -> !candidate.getOrgUnit().getId().equals(orgUnitId))
                    .min(java.util.Comparator.comparing(OrgMembership::getJoinedAt))
                    .ifPresent(candidate -> {
                        candidate.markPrimary();
                        orgMembershipRepository.save(candidate);
                    });
        }

        auditService.record(AuditEntry.action(AuditActions.ORG_MEMBERSHIP_CHANGED, RiskLevel.ELEVATED)
                .resource("ORG_UNIT", orgUnitId)
                .before(java.util.Map.of("userId", userId, "orgUnitId", orgUnitId, "primary", wasPrimary))
                .reason("移除组织成员"));
        return membersOf(orgUnitId);
    }

    /**
     * 设置主部门（必须已是该用户的组织归属）。
     *
     * <p>主部门唯一性由数据库 Partial Unique Index 保证；这里先清除旧主部门并 flush，
     * 避免同一事务内出现两条 primary 造成的唯一约束冲突。
     */
    @Transactional
    public List<UserOrgMembershipView> setPrimary(Long userId, Long orgUnitId) {
        permissionService.requireManageMembers(orgUnitId);
        OrgUnit unit = orgUnitService.requireUnit(orgUnitId);
        if (!unit.isActive()) {
            throw ApiException.conflict("已归档的组织单元不能设置为主部门");
        }
        OrgMembership target = orgMembershipRepository.findByUserIdAndOrgUnitId(userId, orgUnitId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNPROCESSABLE, "该成员尚未加入此组织单元，无法设为主部门"));

        List<OrgMembership> memberships = orgMembershipRepository.findByUserId(userId);
        boolean changed = memberships.stream()
                .filter(OrgMembership::isPrimary)
                .noneMatch(primary -> primary.getId().equals(target.getId()));
        if (!changed) {
            return membershipsOf(userId);
        }

        memberships.stream()
                .filter(OrgMembership::isPrimary)
                .filter(primary -> !primary.getId().equals(target.getId()))
                .forEach(OrgMembership::clearPrimary);
        orgMembershipRepository.flush();

        target.markPrimary();
        orgMembershipRepository.save(target);

        auditService.record(AuditEntry.action(AuditActions.ORG_PRIMARY_CHANGED, RiskLevel.ELEVATED)
                .resource("USER", userId)
                .after(java.util.Map.of("primaryOrgUnitId", orgUnitId))
                .reason("调整主部门"));
        return membershipsOf(userId);
    }
}