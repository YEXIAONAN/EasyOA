package com.easyoa.approval.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 审批模板（主表）。
 *
 * <p>模板内容全部版本化存储：每次修改表单 / 节点定义都会发布新版本，
 * 已运行实例始终使用发起时的版本（历史审批不受模板更新影响）。
 */
@Entity
@Table(name = "approval_templates")
public class ApprovalTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    /** 当前最新版本号（新申请使用该版本）。 */
    @Column(name = "latest_version_no", nullable = false)
    private int latestVersionNo;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ApprovalTemplate() {
        // JPA
    }

    public ApprovalTemplate(String name, String description, Long createdBy) {
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
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

    public void updateInfo(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void publishVersion(int versionNo) {
        this.latestVersionNo = versionNo;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getLatestVersionNo() {
        return latestVersionNo;
    }

    public Long getCreatedBy() {
        return createdBy;
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
}