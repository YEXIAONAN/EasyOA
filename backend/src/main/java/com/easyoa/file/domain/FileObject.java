package com.easyoa.file.domain;

import java.time.Instant;

import com.easyoa.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 附件元数据（磁盘文件与数据库记录分离）。
 *
 * <p>磁盘使用 UUID 随机存储名（{@link #storedName}），用户原始文件名只作为元数据保存，
 * 绝不参与真实路径拼接；下载必须经过 {@code GET /api/files/{fileId}} 的权限校验。
 *
 * <p>软删除优先：{@link #deletedAt} 标记删除，记录保留（审计与追溯）。
 */
@Entity
@Table(name = "files")
public class FileObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 120)
    private String storedName;

    @Column(name = "mime_type", nullable = false, length = 150)
    private String mimeType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false, length = 64)
    private String sha256;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false, updatable = false)
    private User uploader;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private FileResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FileObject() {
        // JPA
    }

    public FileObject(String originalName, String storedName, String mimeType, long size, String sha256,
            User uploader, FileResourceType resourceType, Long resourceId) {
        this.originalName = originalName;
        this.storedName = storedName;
        this.mimeType = mimeType;
        this.size = size;
        this.sha256 = sha256;
        this.uploader = uploader;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** 重新归属资源（例如先上传到任务、评论发布时挂载到评论）。 */
    public void relink(FileResourceType resourceType, Long resourceId) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    // --- getters ---

    public Long getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSize() {
        return size;
    }

    public String getSha256() {
        return sha256;
    }

    public User getUploader() {
        return uploader;
    }

    public FileResourceType getResourceType() {
        return resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}