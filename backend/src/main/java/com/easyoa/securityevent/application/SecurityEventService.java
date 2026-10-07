package com.easyoa.securityevent.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.response.PageResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.securityevent.domain.SecurityEvent;
import com.easyoa.securityevent.domain.SecurityEventType;
import com.easyoa.securityevent.dto.SecurityEventQuery;
import com.easyoa.securityevent.dto.SecurityEventView;
import com.easyoa.securityevent.repository.SecurityEventRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 安全事件服务（永久追加）。
 *
 * <p>写入时计算哈希链：
 * {@code entryHash = SHA-256(previousHash + eventType + severity + description + detail + createdAt)}。
 * 首条事件的 previousHash 为 {@code GENESIS}。
 *
 * <p>已知限制：并发写入极端情况下可能出现链分叉，Phase 8 将引入串行化写入/校验工具。
 */
@Service
public class SecurityEventService {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventService.class);
    private static final String GENESIS = "GENESIS";
    private static final int MAX_PAGE_SIZE = 100;

    private final SecurityEventRepository securityEventRepository;
    private final ObjectMapper objectMapper;

    public SecurityEventService(SecurityEventRepository securityEventRepository, ObjectMapper objectMapper) {
        this.securityEventRepository = securityEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(SecurityEventType type, String severity, String description, Object detail) {
        Instant now = Instant.now();
        String detailJson = toJson(detail);
        String previousHash = securityEventRepository.findTopByOrderByIdDesc()
                .map(SecurityEvent::getEntryHash)
                .filter(hash -> hash != null && !hash.isBlank())
                .orElse(GENESIS);
        String entryHash = sha256(String.join("|",
                previousHash,
                type.name(),
                severity,
                description == null ? "" : description,
                detailJson == null ? "" : detailJson,
                now.toString()));

        SecurityUser current = RequestContext.currentUser();
        SecurityEvent event = new SecurityEvent(
                type,
                severity,
                description == null ? type.name() : truncate(description, 500),
                detailJson,
                current == null ? null : current.id(),
                current == null ? "system" : current.username(),
                RequestContext.clientIp(),
                RequestContext.userAgent(),
                RequestContext.requestId(),
                previousHash,
                entryHash,
                now);
        securityEventRepository.save(event);
        log.info("securityEvent type={} severity={} actor={}", type, severity, event.getActorUsername());
    }

    /** 安全事件检索（ROOT / ADMIN 只读视图）。 */
    @Transactional(readOnly = true)
    public PageResponse<SecurityEventView> query(SecurityEventQuery query) {
        int page = Math.max(query.page(), 1);
        int size = Math.min(Math.max(query.size(), 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<SecurityEvent> result = securityEventRepository.search(query, pageable);
        return PageResponse.from(result, SecurityEventView::from);
    }

    private String toJson(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            log.warn("安全事件 detail 序列化失败 type={}", detail.getClass().getName(), ex);
            return null;
        }
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}