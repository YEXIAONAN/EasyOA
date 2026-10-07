package com.easyoa.task.domain;

import java.time.Instant;

import com.easyoa.user.domain.User;

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
 * 任务协作成员（× N）。
 *
 * <p>协作成员可以查看任务、评论、上传附件、@成员，并完成自己负责的子任务；
 * 不能修改任务状态 / 进度（权限判定统一走 {@code TaskPermissionService}）。
 */
@Entity
@Table(name = "task_collaborators")
public class TaskCollaborator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected TaskCollaborator() {
        // JPA
    }

    public TaskCollaborator(Task task, User user) {
        this.task = task;
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        this.addedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public User getUser() {
        return user;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}