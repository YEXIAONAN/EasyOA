package com.easyoa.notification.dto;

import java.time.Instant;

import com.easyoa.notification.domain.Notification;

/**
 * 通知视图（含 Deep Link：点击直达任务 / 审批 / 项目，不跳回首页）。
 */
public record NotificationView(
        Long id,
        String type,
        String title,
        String body,
        String link,
        String resourceType,
        Long resourceId,
        String actorName,
        boolean read,
        Instant createdAt) {

    public static NotificationView from(Notification notification) {
        return new NotificationView(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLink(),
                notification.getResourceType(),
                notification.getResourceId(),
                notification.getActor() == null ? null : notification.getActor().getDisplayName(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}