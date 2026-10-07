package com.easyoa.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 用户会话记录。
 *
 * <p>安全设计：
 * <ul>
 *   <li>数据库不保存原始 Session ID，只保存 {@code HMAC-SHA256(主密钥, sessionId)}；</li>
 *   <li>会话可被撤销（登出、密码修改、管理员强制下线），撤销后立即失效；</li>
 *   <li>记录 IP 与 User-Agent 快照，用于安全审计与异常登录判断。</li>
 * </ul>
 */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_key_hash", nullable = false, length = 64, unique = true)
    private String sessionKeyHash;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 64)
    private String revokedReason;

    protected UserSession() {
        // JPA
    }

    public UserSession(Long userId, String sessionKeyHash, String ipAddress, String userAgent, Instant expiresAt) {
        this.userId = userId;
        this.sessionKeyHash = sessionKeyHash;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
        this.lastSeenAt = this.createdAt;
        this.expiresAt = expiresAt;
    }

    public void touch(Instant at, Instant expiresAt) {
        this.lastSeenAt = at;
        this.expiresAt = expiresAt;
    }

    public void revoke(String reason) {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
            this.revokedReason = reason;
        }
    }

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSessionKeyHash() {
        return sessionKeyHash;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }
}