package com.easyoa.auth.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.auth.domain.LoginAttempt;
import com.easyoa.auth.repository.LoginAttemptRepository;
import com.easyoa.common.config.EasyOaProperties;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;

/**
 * 登录失败限制（按用户名 + IP 双维度）。
 *
 * <p>策略：窗口期内（锁定时长）连续失败达到阈值后拒绝登录，
 * 任何一次成功登录都会清除该用户的失败计数（成功记录参与统计窗口）。
 */
@Service
public class LoginAttemptService {

    private final LoginAttemptRepository loginAttemptRepository;
    private final EasyOaProperties properties;

    public LoginAttemptService(LoginAttemptRepository loginAttemptRepository, EasyOaProperties properties) {
        this.loginAttemptRepository = loginAttemptRepository;
        this.properties = properties;
    }

    /** 失败次数达到阈值 → 抛出 LOGIN_BLOCKED。 */
    @Transactional(readOnly = true)
    public void assertNotBlocked(String username, String ipAddress) {
        Instant windowStart = lockWindowStart();
        if (failuresForUsername(username, windowStart) >= maxFailures()) {
            throw new ApiException(ErrorCode.LOGIN_BLOCKED, "登录失败次数过多，账号已被临时锁定，请 "
                    + properties.getSecurity().getLoginLockMinutes() + " 分钟后重试");
        }
        if (ipAddress != null && failuresForIp(ipAddress, windowStart) >= maxFailures() * 3L) {
            throw new ApiException(ErrorCode.LOGIN_BLOCKED, "当前网络环境登录失败次数过多，请稍后再试");
        }
    }

    /**
     * 记录一次失败尝试。
     *
     * @return 该用户名在窗口期内的累计失败次数（用于判断是否需要写入安全事件）
     */
    @Transactional
    public long recordFailure(String username, String ipAddress, String reason) {
        loginAttemptRepository.save(LoginAttempt.failure(username, ipAddress, reason));
        return failuresForUsername(username, lockWindowStart());
    }

    @Transactional
    public void recordSuccess(String username, String ipAddress) {
        loginAttemptRepository.save(LoginAttempt.success(username, ipAddress));
    }

    @Transactional(readOnly = true)
    public boolean hasReachedThreshold(long failureCount) {
        return failureCount >= maxFailures();
    }

    private long failuresForUsername(String username, Instant windowStart) {
        return loginAttemptRepository.countByUsernameIgnoreCaseAndSuccessfulIsFalseAndCreatedAtAfter(username,
                windowStart);
    }

    private long failuresForIp(String ipAddress, Instant windowStart) {
        return loginAttemptRepository.countByIpAddressAndSuccessfulIsFalseAndCreatedAtAfter(ipAddress, windowStart);
    }

    private Instant lockWindowStart() {
        return Instant.now().minus(properties.getSecurity().getLoginLockMinutes(), ChronoUnit.MINUTES);
    }

    private int maxFailures() {
        return properties.getSecurity().getLoginMaxFailures();
    }
}