package com.easyoa.audit.application;

import com.easyoa.audit.domain.RiskLevel;

/**
 * 审计条目（写入审计日志的入参）。
 *
 * <p>使用方式：
 * <pre>{@code
 * auditService.record(AuditEntry.action(AuditActions.PROJECT_CREATED)
 *         .resource("PROJECT", project.getId())
 *         .after(summary)
 *         .reason("..."));
 * }</pre>
 *
 * <p>未显式指定 actor 时，自动使用当前登录用户（来自 SecurityContext）。
 */
public final class AuditEntry {

    private final String action;
    private final RiskLevel riskLevel;
    private Long actorUserId;
    private String actorUsername;
    private String resourceType;
    private String resourceId;
    private Object beforeData;
    private Object afterData;
    private String reason;

    private AuditEntry(String action, RiskLevel riskLevel) {
        this.action = action;
        this.riskLevel = riskLevel;
    }

    public static AuditEntry action(String action) {
        return new AuditEntry(action, RiskLevel.NORMAL);
    }

    public static AuditEntry action(String action, RiskLevel riskLevel) {
        return new AuditEntry(action, riskLevel);
    }

    public AuditEntry actor(Long userId, String username) {
        this.actorUserId = userId;
        this.actorUsername = username;
        return this;
    }

    public AuditEntry resource(String type, Object id) {
        this.resourceType = type;
        this.resourceId = id == null ? null : String.valueOf(id);
        return this;
    }

    public AuditEntry before(Object data) {
        this.beforeData = data;
        return this;
    }

    public AuditEntry after(Object data) {
        this.afterData = data;
        return this;
    }

    public AuditEntry reason(String reason) {
        this.reason = reason;
        return this;
    }

    public String action() {
        return action;
    }

    public RiskLevel riskLevel() {
        return riskLevel;
    }

    public Long actorUserId() {
        return actorUserId;
    }

    public String actorUsername() {
        return actorUsername;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    public Object beforeData() {
        return beforeData;
    }

    public Object afterData() {
        return afterData;
    }

    public String reason() {
        return reason;
    }
}