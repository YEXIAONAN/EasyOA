package com.easyoa.organization.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 组织单元（部门 / 团队）。
 *
 * <p>采用邻接表（{@code parentId}）表达组织树；几十人规模的团队下
 * PostgreSQL {@code WITH RECURSIVE} 完全足够，无需 Closure Table / Nested Set。
 *
 * <p>不使用 JPA 关联映射父子关系：树形结构通过显式仓库查询（递归 CTE）加载，
 * 避免隐式懒加载与 N+1。
 */
@Entity
@Table(name = "org_units")
public class OrgUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrgUnitType type;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrgUnitStatus status = OrgUnitStatus.ACTIVE;

    /** 组织负责人（用于展示与 Phase 6 审批人解析：DIRECT_MANAGER / ORG_UNIT_MANAGER）。 */
    @Column(name = "manager_user_id")
    private Long managerUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OrgUnit() {
        // JPA
    }

    public OrgUnit(Long parentId, String name, OrgUnitType type, int sortOrder, Long managerUserId) {
        this.parentId = parentId;
        this.name = name;
        this.type = type;
        this.sortOrder = sortOrder;
        this.managerUserId = managerUserId;
        this.status = OrgUnitStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeType(OrgUnitType type) {
        this.type = type;
    }

    public void reorder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void moveTo(Long newParentId) {
        this.parentId = newParentId;
    }

    public void assignManager(Long managerUserId) {
        this.managerUserId = managerUserId;
    }

    public void archive() {
        this.status = OrgUnitStatus.ARCHIVED;
    }

    public void restore() {
        this.status = OrgUnitStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == OrgUnitStatus.ACTIVE;
    }

    public boolean isRoot() {
        return parentId == null;
    }

    // --- getters ---

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public OrgUnitType getType() {
        return type;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public OrgUnitStatus getStatus() {
        return status;
    }

    public Long getManagerUserId() {
        return managerUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}