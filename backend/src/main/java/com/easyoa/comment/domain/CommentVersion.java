package com.easyoa.comment.domain;

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
 * 评论编辑历史（version_no = 1 为原始内容，每次编辑递增）。
 *
 * <p>只追加、不修改、不删除：撤回后原始评论仍保留在数据库中，供管理员审计追踪。
 */
@Entity
@Table(name = "comment_versions")
public class CommentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false, updatable = false)
    private Comment comment;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 4000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editor_id")
    private User editor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CommentVersion() {
        // JPA
    }

    public CommentVersion(Comment comment, int versionNo, String content, User editor) {
        this.comment = comment;
        this.versionNo = versionNo;
        this.content = content;
        this.editor = editor;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Comment getComment() {
        return comment;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getContent() {
        return content;
    }

    public User getEditor() {
        return editor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}