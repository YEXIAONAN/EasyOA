package com.easyoa.notification.domain;

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
 * 业务通知：任务 / 审批 / 评论 / 项目事件的站内通知（支持 Deep Link）。
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String body;

    /** 前端深链（点击通知直达对应内容，不跳回首页）。 */
    @Column(nullable = false, length = 300)
    private String link;

    @Column(name = "resource_type", length = 30)
    private String resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // JPA
    }

    public Notification(User recipient, NotificationType type, String title, String body, String link,
            String resourceType, Long resourceId, User actor) {
        this.recipient = recipient;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.actor = actor;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void markRead(Instant now) {
        this.readAt = now;
    }

    public boolean isRead() {
        return this.readAt != null;
    }

    public Long getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getLink() {
        return link;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public User getActor() {
        return actor;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}