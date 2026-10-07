package com.easyoa.project.domain;

import java.time.Instant;

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
 * 项目。
 *
 * <p>项目角色不在此实体上冗余：唯一事实来源是 {@link ProjectMember}，
 * 「单一 OWNER / 单一副负责人」由数据库 Partial Unique Index 兜底。
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.DRAFT;

    /** 0~100 总体进度（Phase 3 手工维护；Phase 4 起可由任务聚合）。 */
    @Column(nullable = false)
    private int progress;

    @Column(name = "planned_start_at")
    private Instant plannedStartAt;

    @Column(name = "planned_end_at")
    private Instant plannedEndAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Project() {
        // JPA
    }

    public Project(String name, String description, Long createdBy) {
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
        this.status = ProjectStatus.DRAFT;
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

    public void updateInfo(String name, String description, Instant plannedStartAt, Instant plannedEndAt) {
        this.name = name;
        this.description = description;
        this.plannedStartAt = plannedStartAt;
        this.plannedEndAt = plannedEndAt;
    }

    public void changeStatus(ProjectStatus target) {
        this.status = target;
        if (target == ProjectStatus.ARCHIVED) {
            this.archivedAt = Instant.now();
        } else {
            this.archivedAt = null;
        }
    }

    public void changeProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
    }

    // --- getters ---

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public Instant getPlannedStartAt() {
        return plannedStartAt;
    }

    public Instant getPlannedEndAt() {
        return plannedEndAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
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