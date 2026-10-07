package com.easyoa.common.logging;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 结构化访问日志。
 *
 * <p>每个请求记录一条 key=value 结构日志，字段：timestamp / level / requestId / userId /
 * method / path / status / durationMs / ip。禁止记录请求体与敏感字段。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger accessLog = LoggerFactory.getLogger("EASYOA_ACCESS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            SecurityUser user = RequestContext.currentUser();
            accessLog.info(
                    "requestId={} userId={} username={} method={} path={} status={} durationMs={} ip={}",
                    safe(RequestContext.requestId()),
                    user == null ? "-" : user.id(),
                    safe(RequestContext.username()),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    safe(RequestContext.clientIp()));
        }
    }

    /** 静态资源与健康检查不产生访问日志噪音。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") || path.startsWith("/assets/") || path.equals("/favicon.ico");
    }

    private String safe(String value) {
        return value == null ? "-" : value.replaceAll("[\\r\\n\\t]", "_");
    }
}