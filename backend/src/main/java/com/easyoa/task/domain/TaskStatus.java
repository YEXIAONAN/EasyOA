package com.easyoa.task.domain;

import java.time.Instant;

import com.easyoa.project.domain.Project;

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
 * 项目自定义任务状态。
 *
 * <p>名称可以自定义（如「开发中」），但 {@link #systemType} 必须映射到系统统一类型，
 * 统计与流程判断只依赖系统类型。
 */
@Entity
@Table(name = "task_statuses")
public class TaskStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(nullable = false, length = 40)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_type", nullable = false, length = 20)
    private TaskStatusType systemType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskStatus() {
        // JPA
    }

    public TaskStatus(Project project, String name, TaskStatusType systemType, int sortOrder) {
        this.project = project;
        this.name = name;
        this.systemType = systemType;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getName() {
        return name;
    }

    public TaskStatusType getSystemType() {
        return systemType;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}