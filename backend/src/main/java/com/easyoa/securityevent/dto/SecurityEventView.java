package com.easyoa.securityevent.dto;

import java.time.Instant;

import com.easyoa.securityevent.domain.SecurityEvent;

/**
 * 安全事件视图。
 *
 * <p>{@code detail} 以原始 JSON 字符串返回（前端按需展开），
 * {@code previousHash} / {@code entryHash} 用于说明哈希链完整性。
 */
public record SecurityEventView(
        Long id,
        String eventType,
        String severity,
        String description,
        String detail,
        Long actorUserId,
        String actorUsername,
        String ipAddress,
        String requestId,
        String previousHash,
        String entryHash,
        Instant createdAt) {

    public static SecurityEventView from(SecurityEvent event) {
        return new SecurityEventView(
                event.getId(),
                event.getEventType().name(),
                event.getSeverity(),
                event.getDescription(),
                event.getDetail(),
                event.getActorUserId(),
                event.getActorUsername(),
                event.getIpAddress(),
                event.getRequestId(),
                event.getPreviousHash(),
                event.getEntryHash(),
                event.getCreatedAt());
    }
}
