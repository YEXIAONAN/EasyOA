package com.easyoa.approval.domain;

import java.time.Instant;

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
 * 审批实例节点快照（节点定义在提交时固化，模板后续变更不影响本实例）。
 */
@Entity
@Table(name = "approval_nodes")
public class ApprovalNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instance_id", nullable = false, updatable = false)
    private ApprovalInstance instance;

    @Column(name = "node_index", nullable = false)
    private int nodeIndex;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NodeMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NodeStatus status = NodeStatus.PENDING;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApprovalNode() {
        // JPA
    }

    public ApprovalNode(ApprovalInstance instance, int nodeIndex, String name, NodeMode mode) {
        this.instance = instance;
        this.nodeIndex = nodeIndex;
        this.name = name;
        this.mode = mode;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void approve(Instant now) {
        this.status = NodeStatus.APPROVED;
        this.finishedAt = now;
    }

    public void reject(Instant now) {
        this.status = NodeStatus.REJECTED;
        this.finishedAt = now;
    }

    /** 退回后重新提交：节点回到待审批（从第一个节点重新审批）。 */
    public void reset() {
        this.status = NodeStatus.PENDING;
        this.finishedAt = null;
    }

    public Long getId() {
        return id;
    }

    public ApprovalInstance getInstance() {
        return instance;
    }

    public int getNodeIndex() {
        return nodeIndex;
    }

    public String getName() {
        return name;
    }

    public NodeMode getMode() {
        return mode;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}