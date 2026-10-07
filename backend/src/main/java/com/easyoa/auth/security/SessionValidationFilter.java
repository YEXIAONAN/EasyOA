package com.easyoa.auth.security;

import java.io.IOException;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.easyoa.auth.application.SessionService;
import com.easyoa.common.security.RestAuthenticationEntryPoint;
import com.easyoa.common.security.SecurityUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 会话有效性校验过滤器。
 *
 * <p>Spring Security 的 HttpSession 只表明「浏览器持有 Cookie」，
 * 真正的信任源是数据库中未被撤销的会话记录。
 * 该过滤器保证：会话被撤销（登出其他设备 / 修改密码 / 管理员强制下线）后，
 * 即使攻击者继续持有旧 Cookie，也会立即收到 401。
 */
@Component
public class SessionValidationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public SessionValidationFilter(SessionService sessionService,
            RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.sessionService = sessionService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (session != null && authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SecurityUser user) {
            if (!sessionService.validateAndTouch(session.getId(), user.id())) {
                try {
                    session.invalidate();
                } catch (IllegalStateException ignored) {
                    // 会话可能已被容器回收
                }
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(request, response,
                        new InsufficientAuthenticationException("会话已失效"));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 无需登录即可访问的端点跳过校验，避免无谓的数据库查询。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/auth/login") || path.equals("/api/auth/logout") || path.equals("/api/csrf")
                || path.startsWith("/api/setup/") || path.startsWith("/actuator/health");
    }
}