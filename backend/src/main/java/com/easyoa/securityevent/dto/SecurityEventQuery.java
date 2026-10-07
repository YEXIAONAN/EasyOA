package com.easyoa.securityevent.dto;

import java.time.Instant;

import com.easyoa.securityevent.domain.SecurityEventType;

/**
 * 安全事件查询条件（全部可选，默认按时间倒序分页）。
 */
public record SecurityEventQuery(
        SecurityEventType eventType,
        String severity,
        Long actorUserId,
        Instant from,
        Instant to,
        int page,
        int size) {

    public static SecurityEventQuery of(SecurityEventType eventType, String severity, Long actorUserId, Instant from,
            Instant to, Integer page, Integer size) {
        return new SecurityEventQuery(eventType, severity, actorUserId, from, to,
                page == null ? 1 : page, size == null ? 20 : size);
    }
}
