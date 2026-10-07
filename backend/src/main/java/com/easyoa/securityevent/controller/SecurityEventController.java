package com.easyoa.securityevent.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.response.PageResponse;
import com.easyoa.securityevent.application.SecurityEventService;
import com.easyoa.securityevent.domain.SecurityEventType;
import com.easyoa.securityevent.dto.SecurityEventQuery;
import com.easyoa.securityevent.dto.SecurityEventView;

/**
 * 安全事件检索（只读；仅 ROOT / ADMIN 可访问）。
 *
 * <p>安全事件永久追加，不存在任何修改 / 删除接口。
 */
@RestController
@RequestMapping("/api/security-events")
public class SecurityEventController {

    private final SecurityEventService securityEventService;

    public SecurityEventController(SecurityEventService securityEventService) {
        this.securityEventService = securityEventService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN')")
    public ApiResponse<PageResponse<SecurityEventView>> list(
            @RequestParam(required = false) SecurityEventType eventType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ApiResponse.ok(securityEventService.query(
                SecurityEventQuery.of(eventType, severity, actorUserId, from, to, page, size)));
    }
}
