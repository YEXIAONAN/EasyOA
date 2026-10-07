package com.easyoa.auth.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.auth.domain.UserSession;
import com.easyoa.auth.dto.SessionSummaryResponse;
import com.easyoa.auth.repository.UserSessionRepository;
import com.easyoa.common.config.EasyOaProperties;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.util.HmacUtils;

/**
 * 会话管理：注册、校验、撤销。
 *
 * <p>数据库只保存 {@code HMAC-SHA256(会话密钥, sessionId)}，即使数据库泄露也无法伪造会话。
 */
@Service
public class SessionService {

    /** lastSeenAt 写入节流：避免每个请求都产生一次更新。 */
    private static final Duration TOUCH_INTERVAL = Duration.ofSeconds(60);

    private final UserSessionRepository userSessionRepository;
    private final EasyOaProperties properties;

    public SessionService(UserSessionRepository userSessionRepository, EasyOaProperties properties) {
        this.userSessionRepository = userSessionRepository;
        this.properties = properties;
    }

    /** 登录成功后登记会话。 */
    @Transactional
    public void register(String sessionId, Long userId) {
        Instant now = Instant.now();
        userSessionRepository.save(new UserSession(
                userId,
                hash(sessionId),
                RequestContext.clientIp(),
                RequestContext.userAgent(),
                now.plus(properties.getSecurity().getSessionTimeout())));
    }

    /**
     * 校验会话是否仍然有效（未被撤销、未过期、属于该用户），并在节流窗口外刷新活跃时间。
     *
     * @return true 表示会话有效
     */
    @Transactional
    public boolean validateAndTouch(String sessionId, Long userId) {
        return userSessionRepository.findBySessionKeyHash(hash(sessionId))
                .map(session -> {
                    if (!session.isActive() || !session.getUserId().equals(userId)) {
                        return false;
                    }
                    Instant now = Instant.now();
                    if (session.getLastSeenAt().isBefore(now.minus(TOUCH_INTERVAL))) {
                        session.touch(now, now.plus(properties.getSecurity().getSessionTimeout()));
                    }
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void revoke(String sessionId, String reason) {
        userSessionRepository.findBySessionKeyHash(hash(sessionId)).ifPresent(session -> session.revoke(reason));
    }

    @Transactional
    public void revokeById(Long sessionId, Long userId, String reason) {
        userSessionRepository.findById(sessionId)
                .filter(session -> session.getUserId().equals(userId))
                .ifPresent(session -> session.revoke(reason));
    }

    /**
     * 撤销某用户的全部会话（可保留当前会话，例如修改密码后强制其他设备下线）。
     *
     * @return 被撤销的会话数量
     */
    @Transactional
    public int revokeAllForUser(Long userId, String reason, String keepSessionId) {
        String keepHash = keepSessionId == null ? null : hash(keepSessionId);
        List<UserSession> sessions = userSessionRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(userId, Instant.now());
        int revoked = 0;
        for (UserSession session : sessions) {
            if (keepHash != null && keepHash.equals(session.getSessionKeyHash())) {
                continue;
            }
            session.revoke(reason);
            revoked++;
        }
        return revoked;
    }

    @Transactional(readOnly = true)
    public List<SessionSummaryResponse> listActiveSessions(Long userId, String currentSessionId) {
        String currentHash = currentSessionId == null ? null : hash(currentSessionId);
        return userSessionRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(userId, Instant.now())
                .stream()
                .map(session -> SessionSummaryResponse.from(session, currentHash))
                .toList();
    }

    private String hash(String sessionId) {
        return HmacUtils.hmacSha256Hex(properties.getSecurity().getSessionSecret(), sessionId);
    }
}