package com.easyoa.task.domain;

import java.time.Instant;

import com.easyoa.project.domain.Project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 任务前置依赖：{@code task} 依赖 {@code dependsOnTask}（B depends on A）。
 *
 * <p>v0.1.0 只支持同项目内依赖（project 列 + 复合外键在数据库层保证），
 * 且只有顶层任务可以参与依赖；循环依赖在服务层检测。
 */
@Entity
@Table(name = "task_dependencies")
public class TaskDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    /** 依赖方（B）。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;

    /** 前置任务（A）。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depends_on_task_id", nullable = false, updatable = false)
    private Task dependsOnTask;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskDependency() {
        // JPA
    }

    public TaskDependency(Task task, Task dependsOnTask, Long createdBy) {
        this.project = task.getProject();
        this.task = task;
        this.dependsOnTask = dependsOnTask;
        this.createdBy = createdBy;
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

    public Task getTask() {
        return task;
    }

    public Task getDependsOnTask() {
        return dependsOnTask;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}