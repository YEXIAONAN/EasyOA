package com.easyoa.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 登录尝试记录（追加写入，用于登录失败限制与安全分析）。
 *
 * <p>只记录用户名、IP、结果与失败原因枚举，绝不记录密码等凭据。
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private boolean successful;

    /** 失败原因编码：BAD_CREDENTIALS / ACCOUNT_DISABLED / BLOCKED / UNKNOWN。 */
    @Column(name = "failure_reason", length = 64)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LoginAttempt() {
        // JPA
    }

    private LoginAttempt(String username, String ipAddress, boolean successful, String failureReason) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.successful = successful;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
    }

    public static LoginAttempt failure(String username, String ipAddress, String reason) {
        return new LoginAttempt(username, ipAddress, false, reason);
    }

    public static LoginAttempt success(String username, String ipAddress) {
        return new LoginAttempt(username, ipAddress, true, null);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}