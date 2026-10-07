package com.easyoa.user.domain;

import java.time.Instant;

import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 用户。
 *
 * <p>安全约定：
 * <ul>
 *   <li>{@code passwordHash} 只保存 BCrypt 哈希，任何情况下不得出现在 DTO / 日志中；</li>
 *   <li>{@code totpSecretEncrypted} 为加密存储，API 永远不返回已绑定的明文 Secret；</li>
 *   <li>软删除（{@code deletedAt}），历史数据保持完整。</li>
 * </ul>
 */
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at is null")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名，唯一，创建后不可修改。 */
    @Column(nullable = false, length = 64)
    private String username;

    @Column(length = 190)
    private String email;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false, length = 20)
    private SystemRole systemRole = SystemRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(length = 32)
    private String phone;

    @Column(length = 500)
    private String bio;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    @Column(name = "totp_secret_encrypted", length = 255)
    private String totpSecretEncrypted;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {
        // JPA
    }

    public User(String username, String displayName, String passwordHash, SystemRole systemRole) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.systemRole = systemRole;
        this.status = UserStatus.ACTIVE;
        this.passwordChangedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    public void changePassword(String newPasswordHash, Instant at) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = at;
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    public void enable() {
        this.status = UserStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    // --- getters ---

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public SystemRole getSystemRole() {
        return systemRole;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getPhone() {
        return phone;
    }

    public String getBio() {
        return bio;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    // --- setters（仅允许修改安全字段；身份字段不可变） ---

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void setSystemRole(SystemRole systemRole) {
        this.systemRole = systemRole;
    }
}