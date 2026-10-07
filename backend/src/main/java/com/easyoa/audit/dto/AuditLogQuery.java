package com.easyoa.audit.dto;

import java.time.Instant;

import com.easyoa.audit.domain.RiskLevel;

/**
 * 审计日志查询条件（全部可选，默认按时间倒序分页）。
 */
public record AuditLogQuery(
        Long actorUserId,
        String action,
        String resourceType,
        String resourceId,
        RiskLevel riskLevel,
        Instant from,
        Instant to,
        int page,
        int size) {

    public static AuditLogQuery of(Long actorUserId, String action, String resourceType, String resourceId,
            RiskLevel riskLevel, Instant from, Instant to, Integer page, Integer size) {
        return new AuditLogQuery(actorUserId, action, resourceType, resourceId, riskLevel, from, to,
                page == null ? 1 : page, size == null ? 20 : size);
    }
}