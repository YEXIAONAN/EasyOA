package com.easyoa.project.domain;

/**
 * 项目角色（与系统角色彻底分离）。
 *
 * <ul>
 *   <li>{@link #OWNER} — 项目最终负责人，每项目恰好 1 人；</li>
 *   <li>{@link #DEPUTY_OWNER} — 副负责人，每项目 0~1 人；</li>
 *   <li>{@link #MEMBER} — 普通成员。</li>
 * </ul>
 *
 * <p>注意：这里只描述角色能力，实际授权判断统一走 {@code ProjectPermissionService}。
 */
public enum ProjectRole {

    OWNER,
    DEPUTY_OWNER,
    MEMBER;

    public boolean isOwner() {
        return this == OWNER;
    }

    /** 是否具备项目管理能力（OWNER / DEPUTY_OWNER）。 */
    public boolean isManagement() {
        return this == OWNER || this == DEPUTY_OWNER;
    }
}