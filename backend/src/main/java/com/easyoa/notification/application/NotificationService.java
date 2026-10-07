package com.easyoa.notification.application;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.response.PageResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.notification.domain.Notification;
import com.easyoa.notification.domain.NotificationType;
import com.easyoa.notification.dto.NotificationView;
import com.easyoa.notification.repository.NotificationRepository;
import com.easyoa.task.application.TaskPermissionService;
import com.easyoa.user.application.UserService;

/**
 * 通知中心服务。
 *
 * <p>业务通知与安全审计分离：这里只承载任务 / 审批 / 评论 / 项目事件；
 * 通知不支持跨用户读取（只能看自己的）。
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TaskPermissionService permissionService;
    private final UserService userService;

    public NotificationService(NotificationRepository notificationRepository,
            TaskPermissionService permissionService, UserService userService) {
        this.notificationRepository = notificationRepository;
        this.permissionService = permissionService;
        this.userService = userService;
    }

    /**
     * 发送通知（收件人 == 触发者时自动跳过，避免给自己发通知）。
     */
    @Transactional
    public void notify(Long recipientId, NotificationType type, String title, String body, String link,
            String resourceType, Long resourceId, Long actorId) {
        if (recipientId == null || recipientId.equals(actorId)) {
            return;
        }
        notificationRepository.save(new Notification(
                userService.getById(recipientId), type, title, body, link, resourceType, resourceId,
                actorId == null ? null : userService.getById(actorId)));
    }

    /** 批量通知（自行去重，自动跳过触发者）。 */
    @Transactional
    public void notifyAll(List<Long> recipientIds, NotificationType type, String title, String body, String link,
            String resourceType, Long resourceId, Long actorId) {
        for (Long recipientId : recipientIds.stream().distinct().toList()) {
            notify(recipientId, type, title, body, link, resourceType, resourceId, actorId);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(Long userId, boolean unreadOnly, int page, int size) {
        SecurityUser actor = permissionService.requireAuthenticated();
        if (!actor.id().equals(userId)) {
            throw ApiException.forbidden("只能查看自己的通知");
        }
        Page<Notification> result = notificationRepository.search(userId, unreadOnly,
                PageRequest.of(Math.max(page, 1) - 1, Math.max(size, 1)));
        return PageResponse.from(result, NotificationView::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        SecurityUser actor = permissionService.requireAuthenticated();
        if (!actor.id().equals(userId)) {
            throw ApiException.forbidden("只能查看自己的通知");
        }
        return notificationRepository.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("通知不存在或无权访问"));
        if (!notification.isRead()) {
            notification.markRead(Instant.now());
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }
}