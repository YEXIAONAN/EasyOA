package com.easyoa.organization.domain;

/**
 * 组织单元状态。
 *
 * <p>组织不使用物理删除：撤销组织通过 {@link #ARCHIVED} 归档，历史归属与审计保持完整。
 */
public enum OrgUnitStatus {

    ACTIVE,
    ARCHIVED
}