package com.easyoa.audit.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 审计日志（Append Only）。
 *
 * <p>该实体刻意不提供任何 setter 与 {@code @PreUpdate} 回调：
 * 审计记录一旦写入即不可修改；仓储层同样不暴露 update/delete 能力
 * （见 {@code AuditLogRepository}）。
 */
@Entity
@Table(name = "audit_logs")
public class AuditLogRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    /** 操作者用户名快照（用户改名 / 删除后仍可追溯）。 */
    @Column(name = "actor_username", nullable = false, length = 64)
    private String actorUsername;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "resource_type", length = 60)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb")
    private String beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data", columnDefinition = "jsonb")
    private String afterData;

    @Column(length = 500)
    private String reason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLogRecord() {
        // JPA
    }

    public AuditLogRecord(Long actorUserId, String actorUsername, String action, String resourceType, String resourceId,
            String beforeData, String afterData, String reason, String ipAddress, String userAgent, String requestId,
            RiskLevel riskLevel) {
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.reason = reason;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.requestId = requestId;
        this.riskLevel = riskLevel == null ? RiskLevel.NORMAL : riskLevel;
        this.createdAt = Instant.now();
    }

    // --- getters only ---

    public Long getId() {
        return id;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getBeforeData() {
        return beforeData;
    }

    public String getAfterData() {
        return afterData;
    }

    public String getReason() {
        return reason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getRequestId() {
        return requestId;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}