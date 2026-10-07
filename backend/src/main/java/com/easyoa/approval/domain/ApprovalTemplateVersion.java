package com.easyoa.approval.domain;

import java.time.Instant;

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
 * 审批模板历史版本（只追加）。
 *
 * <p>form_schema / node_schema 为 JSON 文档：字段定义与审批节点（含审批人规则）。
 * 运行中的审批实例绑定发起时的版本，模板更新不影响历史审批。
 */
@Entity
@Table(name = "approval_template_versions")
public class ApprovalTemplateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private ApprovalTemplate template;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "form_schema", nullable = false, columnDefinition = "text")
    private String formSchema;

    @Column(name = "node_schema", nullable = false, columnDefinition = "text")
    private String nodeSchema;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApprovalTemplateVersion() {
        // JPA
    }

    public ApprovalTemplateVersion(ApprovalTemplate template, int versionNo, String name, String description,
            String formSchema, String nodeSchema, Long createdBy) {
        this.template = template;
        this.versionNo = versionNo;
        this.name = name;
        this.description = description;
        this.formSchema = formSchema;
        this.nodeSchema = nodeSchema;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ApprovalTemplate getTemplate() {
        return template;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getFormSchema() {
        return formSchema;
    }

    public String getNodeSchema() {
        return nodeSchema;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}