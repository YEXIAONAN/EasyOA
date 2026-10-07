package com.easyoa.comment.domain;

import java.time.Instant;

import com.easyoa.task.domain.Task;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 任务评论（parent 非空时为一级回复）。
 *
 * <p>评论不得物理删除：撤回只记录 {@code withdrawnAt}，内容与编辑历史完整保留，
 * 管理员审计可以追踪（配合 comment_versions）。
 */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;

    /** 回复的顶层评论（v0.1.0 只支持一级回复）。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id", updatable = false)
    private Comment parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(nullable = false)
    private boolean edited;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Comment() {
        // JPA
    }

    public Comment(Task task, Comment parent, User author, String content) {
        this.task = task;
        this.parent = parent;
        this.author = author;
        this.content = content;
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

    public void edit(String content) {
        this.content = content;
        this.edited = true;
    }

    public void withdraw() {
        this.withdrawnAt = Instant.now();
    }

    public boolean isWithdrawn() {
        return this.withdrawnAt != null;
    }

    public boolean isReply() {
        return this.parent != null;
    }

    // --- getters ---

    public Long getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public Comment getParent() {
        return parent;
    }

    public User getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public boolean isEdited() {
        return edited;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
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