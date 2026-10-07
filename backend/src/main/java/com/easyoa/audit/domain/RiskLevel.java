package com.easyoa.audit.domain;

/**
 * 审计风险级别。
 *
 * <ul>
 *   <li>{@link #NORMAL} — 常规业务操作；</li>
 *   <li>{@link #ELEVATED} — 需要关注的管理类操作（角色变化、成员移除等）；</li>
 *   <li>{@link #CRITICAL} — 高危操作（数据销毁、策略变更、ROOT 权限操作等）。</li>
 * </ul>
 */
public enum RiskLevel {

    NORMAL,
    ELEVATED,
    CRITICAL
}