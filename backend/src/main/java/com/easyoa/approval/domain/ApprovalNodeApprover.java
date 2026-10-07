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
 * 审批人快照：提交时解析动态规则生成，之后组织变化不会自动修改。
 *
 * <p>管理员转交时：原审批人标记 {@code TRANSFERRED_OUT}，新增一条
 * {@code transferredFromUserId} 指向原审批人的记录（完整审计）。
 */
@Entity
@Table(name = "approval_node_approvers")
public class ApprovalNodeApprover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false, updatable = false)
    private ApprovalNode node;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 解析来源规则（快照），便于审计「为什么是这个人」。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 40)
    private ApproverRuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApproverStatus status = ApproverStatus.PENDING;

    @Column(length = 1000)
    private String comment;

    @Column(name = "acted_at")
    private Instant actedAt;

    @Column(name = "transferred_from_user_id")
    private Long transferredFromUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApprovalNodeApprover() {
        // JPA
    }

    public ApprovalNodeApprover(ApprovalNode node, User user, ApproverRuleType ruleType) {
        this.node = node;
        this.user = user;
        this.ruleType = ruleType;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void approve(String comment, Instant now) {
        this.status = ApproverStatus.APPROVED;
        this.comment = comment;
        this.actedAt = now;
    }

    public void reject(String comment, Instant now) {
        this.status = ApproverStatus.REJECTED;
        this.comment = comment;
        this.actedAt = now;
    }

    public void markReturned(String comment, Instant now) {
        this.status = ApproverStatus.RETURNED;
        this.comment = comment;
        this.actedAt = now;
    }

    public void markTransferredOut() {
        this.status = ApproverStatus.TRANSFERRED_OUT;
    }

    /** 退回后重新提交：重置为待审批（被转交退出的记录保持原状）。 */
    public void reset() {
        if (this.status == ApproverStatus.TRANSFERRED_OUT) {
            return;
        }
        this.status = ApproverStatus.PENDING;
        this.comment = null;
        this.actedAt = null;
    }

    public boolean isPending() {
        return this.status == ApproverStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public ApprovalNode getNode() {
        return node;
    }

    public User getUser() {
        return user;
    }

    public ApproverRuleType getRuleType() {
        return ruleType;
    }

    public ApproverStatus getStatus() {
        return status;
    }

    public String getComment() {
        return comment;
    }

    public Instant getActedAt() {
        return actedAt;
    }

    public Long getTransferredFromUserId() {
        return transferredFromUserId;
    }

    public void setTransferredFromUserId(Long transferredFromUserId) {
        this.transferredFromUserId = transferredFromUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}