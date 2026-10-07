package com.easyoa.audit.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.audit.dto.AuditLogQuery;
import com.easyoa.audit.dto.AuditLogView;
import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.response.PageResponse;

/**
 * 审计日志查询（只读；仅 ADMIN / ROOT 可访问）。
 *
 * <p>审计日志不提供任何修改 / 删除接口，这是产品级安全约束。
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN')")
    public ApiResponse<PageResponse<AuditLogView>> list(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ApiResponse.ok(auditService.query(
                AuditLogQuery.of(actorUserId, action, resourceType, resourceId, riskLevel, from, to, page, size)));
    }
}