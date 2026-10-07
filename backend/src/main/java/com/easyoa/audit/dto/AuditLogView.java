package com.easyoa.audit.dto;

import java.time.Instant;

import com.easyoa.audit.domain.AuditLogRecord;

/**
 * 审计日志视图。before/after 以 JSON 字符串返回，由前端按需解析展示。
 */
public record AuditLogView(
        Long id,
        Long actorUserId,
        String actorUsername,
        String action,
        String resourceType,
        String resourceId,
        String beforeData,
        String afterData,
        String reason,
        String ipAddress,
        String requestId,
        String riskLevel,
        Instant createdAt) {

    public static AuditLogView from(AuditLogRecord record) {
        return new AuditLogView(
                record.getId(),
                record.getActorUserId(),
                record.getActorUsername(),
                record.getAction(),
                record.getResourceType(),
                record.getResourceId(),
                record.getBeforeData(),
                record.getAfterData(),
                record.getReason(),
                record.getIpAddress(),
                record.getRequestId(),
                record.getRiskLevel().name(),
                record.getCreatedAt());
    }
}