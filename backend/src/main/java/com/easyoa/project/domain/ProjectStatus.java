package com.easyoa.project.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 项目生命周期状态。
 *
 * <pre>
 * DRAFT ──► ACTIVE ◄──► PAUSED
 *              │  ▲
 *              ▼  │
 *          COMPLETED
 *              │
 *              ▼
 *          ARCHIVED（只读，终态）
 * </pre>
 *
 * <p>产品规范明确允许：PAUSED → ACTIVE、COMPLETED → ACTIVE（项目重新激活）。
 * ARCHIVED 为只读终态：正常业务逻辑不提供删除项目，结束使用归档。
 */
public enum ProjectStatus {

    DRAFT,
    ACTIVE,
    PAUSED,
    COMPLETED,
    ARCHIVED;

    private static final Map<ProjectStatus, Set<ProjectStatus>> ALLOWED_TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(ACTIVE, ARCHIVED),
            ACTIVE, EnumSet.of(PAUSED, COMPLETED, ARCHIVED),
            PAUSED, EnumSet.of(ACTIVE, ARCHIVED),
            COMPLETED, EnumSet.of(ACTIVE, ARCHIVED),
            ARCHIVED, EnumSet.noneOf(ProjectStatus.class));

    public boolean canTransitionTo(ProjectStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** 归档后项目只读：信息、成员、状态均不可再变更。 */
    public boolean isReadOnly() {
        return this == ARCHIVED;
    }

    public Set<ProjectStatus> allowedTargets() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of());
    }
}