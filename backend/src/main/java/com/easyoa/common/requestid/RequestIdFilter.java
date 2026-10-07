package com.easyoa.common.requestid;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 请求链路 ID 过滤器。
 *
 * <p>为每个请求生成 {@code requestId} 并写入 MDC，贯穿：
 * 访问日志 → 业务日志 → 审计日志 → API 响应 → 错误响应。
 *
 * <p>同时接受来自可信反向代理的 {@code X-Request-Id}，便于跨 Nginx / 后端追踪。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MDC_KEY, requestId);
        request.setAttribute(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String provided = request.getHeader(HEADER);
        if (StringUtils.hasText(provided) && provided.length() <= MAX_LENGTH
                && provided.chars().allMatch(ch -> ch >= 0x21 && ch <= 0x7E)) {
            return provided;
        }
        return UUID.randomUUID().toString();
    }
}