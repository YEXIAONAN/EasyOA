package com.easyoa.auth.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.auth.application.AuthService;
import com.easyoa.auth.application.SessionService;
import com.easyoa.auth.dto.ChangePasswordRequest;
import com.easyoa.auth.dto.CurrentUserResponse;
import com.easyoa.auth.dto.LoginRequest;
import com.easyoa.auth.dto.SessionSummaryResponse;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.user.application.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 认证接口。
 *
 * <p>Controller 只负责参数绑定与响应包装，业务逻辑全部在应用层。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final UserService userService;
    private final AuditService auditService;

    public AuthController(AuthService authService, SessionService sessionService, UserService userService,
            AuditService auditService) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.userService = userService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ApiResponse<CurrentUserResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return ApiResponse.ok(authService.login(request, httpRequest, httpResponse));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok(CurrentUserResponse.from(userService.getProfile(principal.id())));
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        authService.changePassword(request, httpRequest);
        return ApiResponse.ok();
    }

    /** 当前用户的活动会话列表（登录设备管理）。 */
    @GetMapping("/sessions")
    public ApiResponse<List<SessionSummaryResponse>> sessions(@AuthenticationPrincipal SecurityUser principal,
            HttpServletRequest request) {
        var session = request.getSession(false);
        return ApiResponse.ok(sessionService.listActiveSessions(principal.id(),
                session == null ? null : session.getId()));
    }

    /** 撤销指定会话（仅能撤销自己的会话）。 */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> revokeSession(@AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long sessionId) {
        sessionService.revokeById(sessionId, principal.id(), "USER_REVOKED");
        auditService.record(AuditEntry.action(AuditActions.AUTH_SESSION_REVOKED, RiskLevel.ELEVATED)
                .resource("USER_SESSION", sessionId));
        return ApiResponse.ok();
    }
}