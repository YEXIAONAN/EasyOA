package com.easyoa.organization.domain;

import java.time.Instant;

import com.easyoa.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 组织归属（用户 N:N 组织单元）。
 *
 * <p>约束：
 * <ul>
 *   <li>同一用户在同一组织单元下最多一条记录（数据库唯一索引）；</li>
 *   <li>每个用户最多一个主部门（Partial Unique Index 保障）；</li>
 *   <li>组织归属与项目成员关系完全独立（Org Membership != Project Membership）。</li>
 * </ul>
 */
@Entity
@Table(name = "user_org_memberships")
public class OrgMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_unit_id", nullable = false)
    private OrgUnit orgUnit;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected OrgMembership() {
        // JPA
    }

    public OrgMembership(User user, OrgUnit orgUnit, boolean primary) {
        this.user = user;
        this.orgUnit = orgUnit;
        this.primary = primary;
        this.joinedAt = Instant.now();
    }

    public void markPrimary() {
        this.primary = true;
    }

    public void clearPrimary() {
        this.primary = false;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public OrgUnit getOrgUnit() {
        return orgUnit;
    }

    public boolean isPrimary() {
        return primary;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}