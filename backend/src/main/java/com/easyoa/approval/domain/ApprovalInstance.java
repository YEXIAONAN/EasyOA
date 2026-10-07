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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 审批实例。
 *
 * <p>发起后绑定模板版本并保存表单快照（schema + values）与审批人快照；
 * 进入 PENDING 后申请人不得修改表单（只能撤回或等待退回）。
 */
@Entity
@Table(name = "approval_instances")
public class ApprovalInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private ApprovalTemplate template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_version_id", nullable = false, updatable = false)
    private ApprovalTemplateVersion templateVersion;

    /** 发起时的模板版本号（冗余，便于列表展示与审计）。 */
    @Column(name = "template_version_no", nullable = false, updatable = false)
    private int templateVersionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false, updatable = false)
    private User applicant;

    /** JSON：{ schema: [...], values: {...} } */
    @Column(name = "form_snapshot", nullable = false, columnDefinition = "text")
    private String formSnapshot;

    /** PENDING 时的当前节点序号（0 基），终态或退回后为 null。 */
    @Column(name = "current_node_index")
    private Integer currentNodeIndex;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ApprovalInstance() {
        // JPA
    }

    public ApprovalInstance(ApprovalTemplate template, ApprovalTemplateVersion templateVersion, User applicant,
            String title, String formSnapshot) {
        this.template = template;
        this.templateVersion = templateVersion;
        this.templateVersionNo = templateVersion.getVersionNo();
        this.applicant = applicant;
        this.title = title;
        this.formSnapshot = formSnapshot;
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

    /** 提交 / 重新提交：从第一个节点开始审批。 */
    public void start(int firstNodeIndex, Instant now) {
        this.status = ApprovalStatus.PENDING;
        this.currentNodeIndex = firstNodeIndex;
        this.finishedAt = null;
        if (this.submittedAt == null) {
            this.submittedAt = now;
        }
    }

    public void advanceTo(int nextNodeIndex) {
        this.currentNodeIndex = nextNodeIndex;
    }

    public void approve(Instant now) {
        this.status = ApprovalStatus.APPROVED;
        this.currentNodeIndex = null;
        this.finishedAt = now;
    }

    public void reject(Instant now) {
        this.status = ApprovalStatus.REJECTED;
        this.currentNodeIndex = null;
        this.finishedAt = now;
    }

    public void returnToApplicant() {
        this.status = ApprovalStatus.RETURNED;
        this.currentNodeIndex = null;
    }

    public void cancel(Instant now) {
        this.status = ApprovalStatus.CANCELLED;
        this.currentNodeIndex = null;
        this.finishedAt = now;
    }

    public void updateFormSnapshot(String formSnapshot) {
        this.formSnapshot = formSnapshot;
    }

    public boolean isPending() {
        return this.status == ApprovalStatus.PENDING;
    }

    // --- getters ---

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public ApprovalTemplate getTemplate() {
        return template;
    }

    public ApprovalTemplateVersion getTemplateVersion() {
        return templateVersion;
    }

    public int getTemplateVersionNo() {
        return templateVersionNo;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public User getApplicant() {
        return applicant;
    }

    public String getFormSnapshot() {
        return formSnapshot;
    }

    public Integer getCurrentNodeIndex() {
        return currentNodeIndex;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
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