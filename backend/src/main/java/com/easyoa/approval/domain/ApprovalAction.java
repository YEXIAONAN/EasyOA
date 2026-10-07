package com.easyoa.approval.domain;

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
 * 审批历史动作（只追加，审批详情按时间线展示）。
 */
@Entity
@Table(name = "approval_actions")
public class ApprovalAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instance_id", nullable = false, updatable = false)
    private ApprovalInstance instance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", updatable = false)
    private ApprovalNode node;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApprovalActionType action;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApprovalAction() {
        // JPA
    }

    public ApprovalAction(ApprovalInstance instance, ApprovalNode node, User actor, ApprovalActionType action,
            String comment) {
        this.instance = instance;
        this.node = node;
        this.actor = actor;
        this.action = action;
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ApprovalInstance getInstance() {
        return instance;
    }

    public ApprovalNode getNode() {
        return node;
    }

    public User getActor() {
        return actor;
    }

    public ApprovalActionType getAction() {
        return action;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}