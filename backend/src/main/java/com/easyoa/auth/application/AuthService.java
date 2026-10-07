package com.easyoa.auth.application;

import java.time.Instant;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.auth.dto.ChangePasswordRequest;
import com.easyoa.auth.dto.CurrentUserResponse;
import com.easyoa.auth.dto.LoginRequest;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.common.util.IpUtils;
import com.easyoa.securityevent.application.SecurityEventService;
import com.easyoa.securityevent.domain.SecurityEventType;
import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.User;
import com.easyoa.user.dto.UserProfileResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 认证能力：登录、登出、修改密码、当前用户。
 *
 * <p>安全要点：
 * <ul>
 *   <li>登录成功先做 Session Fixation 防护（changeSessionId）再落库会话；</li>
 *   <li>登录失败按用户名 + IP 双维度计数，达阈值写入安全事件；</li>
 *   <li>修改密码后强制撤销其他设备会话；</li>
 *   <li>所有认证动作写入审计日志。</li>
 * </ul>
 *
 * <p>事务策略：本服务的公开方法<b>不</b>开启外层事务，认证失败必须留下
 * 登录尝试、审计与安全事件记录（抛出异常不应导致它们被回滚）。
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final UserService userService;
    private final SessionService sessionService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;

    public AuthService(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
            PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, UserService userService,
            SessionService sessionService, LoginAttemptService loginAttemptService, AuditService auditService,
            SecurityEventService securityEventService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.userService = userService;
        this.sessionService = sessionService;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.securityEventService = securityEventService;
    }

    /**
     * 登录。
     *
     * <p>刻意<b>不</b>使用外层事务：登录失败会抛异常，若整个过程处于同一事务中，
     * 登录失败尝试记录（login_attempts）、审计与安全事件都会随事务一起回滚，
     * 导致失败限制与安全审计失效。因此这里采用「每步各自事务」的编排方式：
     * 失败记录、审计、会话登记分别由各自服务保证事务。
     */
    public CurrentUserResponse login(LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String username = request.username().trim();
        String ip = IpUtils.resolveClientIp(httpRequest);

        loginAttemptService.assertNotBlocked(username, ip);

        Authentication authentication;
        try {
            authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, request.password()));
        } catch (DisabledException ex) {
            loginAttemptService.recordFailure(username, ip, "ACCOUNT_DISABLED");
            auditService.record(AuditEntry.action(AuditActions.AUTH_LOGIN_FAILED, RiskLevel.ELEVATED)
                    .actor(null, username)
                    .reason("账号已禁用"));
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        } catch (AuthenticationException ex) {
            long failures = loginAttemptService.recordFailure(username, ip, "BAD_CREDENTIALS");
            if (loginAttemptService.hasReachedThreshold(failures)) {
                securityEventService.record(SecurityEventType.LOGIN_BLOCKED, "WARNING",
                        "账号连续登录失败达到阈值，已被临时锁定：" + username,
                        java.util.Map.of("failures", failures, "ip", ip == null ? "unknown" : ip));
            }
            auditService.record(AuditEntry.action(AuditActions.AUTH_LOGIN_FAILED, RiskLevel.ELEVATED)
                    .actor(null, username)
                    .reason("凭据错误"));
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        SecurityUser principal = (SecurityUser) authentication.getPrincipal();
        establishSession(authentication, httpRequest, httpResponse, principal);
        loginAttemptService.recordSuccess(principal.username(), ip);
        userService.recordLogin(principal.id(), Instant.now());
        auditService.record(AuditEntry.action(AuditActions.AUTH_LOGIN_SUCCEEDED).actor(principal.id(),
                principal.username()));

        UserProfileResponse profile = userService.getProfile(principal.id());
        return CurrentUserResponse.from(profile);
    }

    /** 登出：撤销会话记录、清理安全上下文并审计。 */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityUser user = RequestContext.currentUser();
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionService.revoke(session.getId(), "USER_LOGOUT");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new CookieClearingLogoutHandler("EASYOA_SESSION", "XSRF-TOKEN").logout(request, response, authentication);
        SecurityContextHolder.clearContext();
        if (user != null) {
            auditService.record(AuditEntry.action(AuditActions.AUTH_LOGOUT).actor(user.id(), user.username()));
        }
    }

    /**
     * 修改密码：校验当前密码 → 强度策略 → 更新哈希 → 撤销其他设备会话 → 审计。
     *
     * <p>同样不使用外层事务：校验失败需要留下审计记录，不能因为抛异常而回滚。
     */
    public void changePassword(ChangePasswordRequest request, HttpServletRequest httpRequest) {
        SecurityUser principal = RequestContext.currentUser();
        if (principal == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        User user = userService.getById(principal.id());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.record(AuditEntry.action(AuditActions.AUTH_PASSWORD_CHANGED, RiskLevel.ELEVATED)
                    .reason("当前密码校验失败"));
            throw new ApiException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY_VIOLATION, "新密码不得与当前密码相同");
        }
        passwordPolicy.validate(user.getUsername(), request.newPassword());
        userService.changePassword(principal.id(), passwordEncoder.encode(request.newPassword()));

        HttpSession session = httpRequest.getSession(false);
        int revoked = sessionService.revokeAllForUser(principal.id(), "PASSWORD_CHANGED",
                session == null ? null : session.getId());
        auditService.record(AuditEntry.action(AuditActions.AUTH_PASSWORD_CHANGED, RiskLevel.ELEVATED)
                .reason("密码已更新，撤销其他设备会话 " + revoked + " 个"));
    }

    /** 建立登录态：Session Fixation 防护 → 保存 SecurityContext → 登记会话记录。 */
    private void establishSession(Authentication authentication, HttpServletRequest request,
            HttpServletResponse response, SecurityUser principal) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            // Session Fixation 防护：登录前后更换 Session ID
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new IllegalStateException("登录后未能创建会话");
        }
        sessionService.register(session.getId(), principal.id());
    }
}