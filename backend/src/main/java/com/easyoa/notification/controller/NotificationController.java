package com.easyoa.notification.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.response.PageResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.notification.application.NotificationService;
import com.easyoa.notification.dto.NotificationView;
import com.easyoa.task.application.TaskPermissionService;

/**
 * 通知中心接口（只能查看与操作自己的通知）。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final TaskPermissionService permissionService;

    public NotificationController(NotificationService notificationService, TaskPermissionService permissionService) {
        this.notificationService = notificationService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationView>> list(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        SecurityUser actor = permissionService.requireAuthenticated();
        return ApiResponse.ok(notificationService.list(actor.id(), unreadOnly, page, Math.min(size, 100)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        SecurityUser actor = permissionService.requireAuthenticated();
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount(actor.id())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        SecurityUser actor = permissionService.requireAuthenticated();
        notificationService.markRead(actor.id(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        SecurityUser actor = permissionService.requireAuthenticated();
        notificationService.markAllRead(actor.id());
        return ApiResponse.ok();
    }
}