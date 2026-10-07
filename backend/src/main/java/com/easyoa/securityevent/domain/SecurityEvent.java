package com.easyoa.securityevent.domain;

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
 * 安全事件（永久追加，应用层无 delete）。
 *
 * <p>{@code previousHash} / {@code entryHash} 组成哈希链：
 * 每条事件的 entryHash = SHA-256(previousHash + 规范化内容)。
 * v0.1.0 已写入哈希链字段，为后续完整性校验与审计取证预留。
 */
@Entity
@Table(name = "security_events")
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    private SecurityEventType eventType;

    /** 事件级别：INFO / WARNING / CRITICAL。 */
    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String detail;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", length = 64)
    private String actorUsername;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "previous_hash", length = 128)
    private String previousHash;

    @Column(name = "entry_hash", length = 128)
    private String entryHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SecurityEvent() {
        // JPA
    }

    public SecurityEvent(SecurityEventType eventType, String severity, String description, String detail,
            Long actorUserId, String actorUsername, String ipAddress, String userAgent, String requestId,
            String previousHash, String entryHash, Instant createdAt) {
        this.eventType = eventType;
        this.severity = severity;
        this.description = description;
        this.detail = detail;
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.requestId = requestId;
        this.previousHash = previousHash;
        this.entryHash = entryHash;
        this.createdAt = createdAt;
    }

    // --- getters only ---

    public Long getId() {
        return id;
    }

    public SecurityEventType getEventType() {
        return eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public String getDetail() {
        return detail;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
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

    public String getPreviousHash() {
        return previousHash;
    }

    public String getEntryHash() {
        return entryHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}