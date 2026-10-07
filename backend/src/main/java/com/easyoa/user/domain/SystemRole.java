package com.easyoa.user.domain;

/**
 * 系统角色（与项目角色彻底分离）。
 *
 * <ul>
 *   <li>{@link #ROOT} — 系统最高权限，高危操作需要密码 + TOTP + 原因 + 审计；</li>
 *   <li>{@link #ADMIN} — 组织与系统管理角色；</li>
 *   <li>{@link #MEMBER} — 普通成员。</li>
 * </ul>
 */
public enum SystemRole {

    ROOT,
    ADMIN,
    MEMBER;

    /** ROOT 或 ADMIN 视为系统管理角色。 */
    public boolean isAdminLike() {
        return this == ROOT || this == ADMIN;
    }
}