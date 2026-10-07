package com.easyoa.auth.dto;

import java.time.Instant;

import com.easyoa.auth.domain.UserSession;

/**
 * 会话摘要（用于「登录设备管理」，不暴露任何 Hash 值）。
 */
public record SessionSummaryResponse(
        Long id,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        boolean current) {

    public static SessionSummaryResponse from(UserSession session, String currentSessionKeyHash) {
        return new SessionSummaryResponse(
                session.getId(),
                session.getIpAddress(),
                session.getUserAgent(),
                session.getCreatedAt(),
                session.getLastSeenAt(),
                session.getExpiresAt(),
                currentSessionKeyHash != null && currentSessionKeyHash.equals(session.getSessionKeyHash()));
    }
}