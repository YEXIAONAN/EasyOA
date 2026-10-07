package com.easyoa.organization.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.organization.domain.OrgUnit;
import com.easyoa.organization.domain.OrgUnitStatus;
import com.easyoa.organization.domain.OrgUnitType;
import com.easyoa.organization.dto.CreateOrgUnitRequest;
import com.easyoa.organization.dto.OrgUnitDetailResponse;
import com.easyoa.organization.dto.OrgUnitTreeNode;
import com.easyoa.organization.dto.UpdateOrgUnitRequest;
import com.easyoa.organization.repository.OrgMembershipRepository;
import com.easyoa.organization.repository.OrgUnitRepository;
import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.User;
import com.easyoa.user.dto.UserBrief;

/**
 * 组织单元管理（组织结构）。
 *
 * <p>关键规则：
 * <ul>
 *   <li>组织树为邻接表；移动时使用 {@code WITH RECURSIVE} 取子树，禁止移动到自身或下级（成环）；</li>
 *   <li>归档需先归档下级：不允许存在 ACTIVE 的子单元；</li>
 *   <li>同级同名（未归档）被数据库唯一索引兜底拦截；</li>
 *   <li>所有结构变更写入审计日志。</li>
 * </ul>
 */
@Service
public class OrgUnitService {

    private final OrgUnitRepository orgUnitRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final UserService userService;
    private final AuditService auditService;
    private final OrganizationPermissionService permissionService;

    public OrgUnitService(OrgUnitRepository orgUnitRepository, OrgMembershipRepository orgMembershipRepository,
            UserService userService, AuditService auditService, OrganizationPermissionService permissionService) {
        this.orgUnitRepository = orgUnitRepository;
        this.orgMembershipRepository = orgMembershipRepository;
        this.userService = userService;
        this.auditService = auditService;
        this.permissionService = permissionService;
    }

    // --- 查询 -----------------------------------------------------------------

    /** 组织树（默认隐藏已归档单元；已归档单元的子树不会包含 ACTIVE 单元，见归档规则）。 */
    @Transactional(readOnly = true)
    public List<OrgUnitTreeNode> tree(boolean includeArchived) {
        List<OrgUnit> units = orgUnitRepository.findAllByOrderByParentIdAscSortOrderAscNameAsc();
        Map<Long, Long> memberCounts = new HashMap<>();
        for (var row : orgMembershipRepository.countGroupedByOrgUnit()) {
            memberCounts.put(row.orgUnitId(), row.memberCount());
        }
        Map<Long, UserBrief> managers = loadManagers(units);

        Map<Long, List<OrgUnit>> childrenIndex = new HashMap<>();
        List<OrgUnit> roots = new ArrayList<>();
        for (OrgUnit unit : units) {
            if (unit.isRoot()) {
                roots.add(unit);
            } else {
                childrenIndex.computeIfAbsent(unit.getParentId(), key -> new ArrayList<>()).add(unit);
            }
        }
        return buildNodes(roots, childrenIndex, 0, memberCounts, managers, includeArchived);
    }

    @Transactional(readOnly = true)
    public OrgUnitDetailResponse detail(Long id) {
        OrgUnit unit = requireUnit(id);
        String parentName = unit.getParentId() == null ? null
                : orgUnitRepository.findById(unit.getParentId()).map(OrgUnit::getName).orElse(null);
        return new OrgUnitDetailResponse(
                unit.getId(),
                unit.getParentId(),
                parentName,
                unit.getName(),
                unit.getType().name(),
                unit.getStatus().name(),
                unit.getSortOrder(),
                unit.getManagerUserId() == null ? null : userService.findBrief(unit.getManagerUserId()).orElse(null),
                orgMembershipRepository.countByOrgUnitId(unit.getId()));
    }

    /** 子树 ID（含自身）。 */
    @Transactional(readOnly = true)
    public List<Long> subtreeIds(Long id) {
        requireUnit(id);
        return orgUnitRepository.findSubtreeIds(id);
    }

    /** 上级链（从父到根，不含自身）；自带环保护。 */
    @Transactional(readOnly = true)
    public List<Long> ancestorIds(Long id) {
        OrgUnit unit = requireUnit(id);
        List<Long> ancestors = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long cursor = unit.getParentId();
        while (cursor != null && visited.add(cursor)) {
            ancestors.add(cursor);
            cursor = orgUnitRepository.findById(cursor).map(OrgUnit::getParentId).orElse(null);
        }
        return ancestors;
    }

    // --- 结构变更 -------------------------------------------------------------

    @Transactional
    public OrgUnitDetailResponse create(CreateOrgUnitRequest request) {
        permissionService.requireManageStructure();
        String name = request.name().trim();
        OrgUnit parent = resolveParent(request.parentId());
        assertNameAvailable(parent == null ? null : parent.getId(), name, null);
        validateManager(request.managerUserId());

        OrgUnit unit = new OrgUnit(parent == null ? null : parent.getId(), name, request.type(),
                request.sortOrder() == null ? 0 : request.sortOrder(), request.managerUserId());
        orgUnitRepository.save(unit);

        auditService.record(AuditEntry.action(AuditActions.ORG_UNIT_CREATED, RiskLevel.ELEVATED)
                .resource("ORG_UNIT", unit.getId())
                .after(snapshot(unit)));
        return detail(unit.getId());
    }

    @Transactional
    public OrgUnitDetailResponse update(Long id, UpdateOrgUnitRequest request) {
        permissionService.requireManageStructure();
        OrgUnit unit = requireUnit(id);
        String name = request.name().trim();
        assertNameAvailable(unit.getParentId(), name, unit.getId());
        validateManager(request.managerUserId());

        Map<String, Object> before = snapshot(unit);
        unit.rename(name);
        unit.changeType(request.type());
        if (request.sortOrder() != null) {
            unit.reorder(request.sortOrder());
        }
        unit.assignManager(request.managerUserId());
        orgUnitRepository.save(unit);

        auditService.record(AuditEntry.action(AuditActions.ORG_UNIT_UPDATED, RiskLevel.ELEVATED)
                .resource("ORG_UNIT", unit.getId())
                .before(before)
                .after(snapshot(unit)));
        return detail(unit.getId());
    }

    /**
     * 移动组织单元：禁止移动到自身或自己的下级（成环）。
     */
    @Transactional
    public OrgUnitDetailResponse move(Long id, Long newParentId) {
        permissionService.requireManageStructure();
        OrgUnit unit = requireUnit(id);

        if (newParentId != null) {
            if (newParentId.equals(id)) {
                throw ApiException.conflict("不能将组织单元移动到自身之下");
            }
            OrgUnit newParent = requireUnit(newParentId);
            if (!newParent.isActive()) {
                throw ApiException.conflict("目标上级单元已归档，无法作为上级");
            }
            Set<Long> subtree = new HashSet<>(orgUnitRepository.findSubtreeIds(id));
            if (subtree.contains(newParentId)) {
                throw ApiException.conflict("不能将组织单元移动到自己的下级，否则会形成循环");
            }
            assertNameAvailable(newParentId, unit.getName(), unit.getId());
        } else {
            assertNameAvailable(null, unit.getName(), unit.getId());
        }

        Map<String, Object> before = Map.of("parentId", String.valueOf(unit.getParentId()));
        unit.moveTo(newParentId);
        orgUnitRepository.save(unit);

        auditService.record(AuditEntry.action(AuditActions.ORG_UNIT_UPDATED, RiskLevel.ELEVATED)
                .resource("ORG_UNIT", unit.getId())
                .before(before)
                .after(Map.of("parentId", String.valueOf(newParentId)))
                .reason("移动组织单元"));
        return detail(unit.getId());
    }

    /** 归档：使用归档替代删除，历史归属与审计保持完整。 */
    @Transactional
    public OrgUnitDetailResponse archive(Long id) {
        permissionService.requireManageStructure();
        OrgUnit unit = requireUnit(id);
        if (!unit.isActive()) {
            throw ApiException.conflict("该组织单元已归档");
        }
        long activeChildren = orgUnitRepository.countByParentIdAndStatus(id, OrgUnitStatus.ACTIVE);
        if (activeChildren > 0) {
            throw ApiException.conflict("存在未归档的下级单元，请先归档下级组织");
        }
        unit.archive();
        orgUnitRepository.save(unit);
        auditService.record(AuditEntry.action(AuditActions.ORG_UNIT_ARCHIVED, RiskLevel.ELEVATED)
                .resource("ORG_UNIT", unit.getId())
                .before(Map.of("status", OrgUnitStatus.ACTIVE.name()))
                .after(Map.of("status", OrgUnitStatus.ARCHIVED.name())));
        return detail(unit.getId());
    }

    @Transactional
    public OrgUnitDetailResponse restore(Long id) {
        permissionService.requireManageStructure();
        OrgUnit unit = requireUnit(id);
        if (unit.isActive()) {
            throw ApiException.conflict("该组织单元未归档，无需恢复");
        }
        if (unit.getParentId() != null) {
            OrgUnit parent = orgUnitRepository.findById(unit.getParentId()).orElse(null);
            if (parent != null && !parent.isActive()) {
                throw ApiException.conflict("上级单元仍处于归档状态，请先恢复上级组织");
            }
        }
        unit.restore();
        orgUnitRepository.save(unit);
        auditService.record(AuditEntry.action(AuditActions.ORG_UNIT_RESTORED, RiskLevel.ELEVATED)
                .resource("ORG_UNIT", unit.getId())
                .before(Map.of("status", OrgUnitStatus.ARCHIVED.name()))
                .after(Map.of("status", OrgUnitStatus.ACTIVE.name())));
        return detail(unit.getId());
    }

    // --- 内部方法 -------------------------------------------------------------

    @Transactional(readOnly = true)
    public OrgUnit requireUnit(Long id) {
        return orgUnitRepository.findById(id).orElseThrow(() -> ApiException.notFound("组织单元不存在"));
    }

    private OrgUnit resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        OrgUnit parent = requireUnit(parentId);
        if (!parent.isActive()) {
            throw ApiException.conflict("上级单元已归档，无法在其下创建组织");
        }
        return parent;
    }

    private void assertNameAvailable(Long parentId, String name, Long excludeId) {
        boolean conflict = orgUnitRepository.findByParentIdOrderBySortOrderAscNameAsc(parentId).stream()
                .anyMatch(unit -> unit.isActive()
                        && unit.getName().equalsIgnoreCase(name)
                        && (excludeId == null || !unit.getId().equals(excludeId)));
        if (conflict) {
            throw ApiException.conflict("同一层级下已存在同名组织单元：" + name);
        }
    }

    private void validateManager(Long managerUserId) {
        if (managerUserId == null) {
            return;
        }
        User manager = userService.getById(managerUserId);
        if (!manager.isActive()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "负责人账号已被禁用，不能作为组织负责人");
        }
    }

    private Map<Long, UserBrief> loadManagers(List<OrgUnit> units) {
        Set<Long> managerIds = new HashSet<>();
        for (OrgUnit unit : units) {
            if (unit.getManagerUserId() != null) {
                managerIds.add(unit.getManagerUserId());
            }
        }
        return userService.findBriefs(managerIds);
    }

    private List<OrgUnitTreeNode> buildNodes(List<OrgUnit> current, Map<Long, List<OrgUnit>> childrenIndex, int depth,
            Map<Long, Long> memberCounts, Map<Long, UserBrief> managers, boolean includeArchived) {
        List<OrgUnitTreeNode> nodes = new ArrayList<>();
        for (OrgUnit unit : current) {
            if (!includeArchived && !unit.isActive()) {
                continue;
            }
            List<OrgUnit> children = childrenIndex.getOrDefault(unit.getId(), List.of());
            nodes.add(new OrgUnitTreeNode(
                    unit.getId(),
                    unit.getParentId(),
                    unit.getName(),
                    unit.getType().name(),
                    unit.getStatus().name(),
                    unit.getSortOrder(),
                    depth,
                    unit.getManagerUserId() == null ? null : managers.get(unit.getManagerUserId()),
                    memberCounts.getOrDefault(unit.getId(), 0L),
                    buildNodes(children, childrenIndex, depth + 1, memberCounts, managers, includeArchived)));
        }
        return nodes;
    }

    private Map<String, Object> snapshot(OrgUnit unit) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("name", unit.getName());
        snapshot.put("type", unit.getType().name());
        snapshot.put("parentId", String.valueOf(unit.getParentId()));
        snapshot.put("sortOrder", unit.getSortOrder());
        snapshot.put("status", unit.getStatus().name());
        snapshot.put("managerUserId", String.valueOf(unit.getManagerUserId()));
        return snapshot;
    }
}