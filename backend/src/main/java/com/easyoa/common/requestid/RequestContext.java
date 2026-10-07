package com.easyoa.common.requestid;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.easyoa.common.security.SecurityUser;
import com.easyoa.common.util.IpUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前请求上下文（requestId / 客户端 IP / User-Agent / 当前用户）。
 *
 * <p>用于审计、日志与安全事件记录，避免在每个业务方法中层层传递这些参数。
 */
public final class RequestContext {

    private RequestContext() {
    }

    public static String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }

    public static String clientIp() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : IpUtils.resolveClientIp(request);
    }

    public static String userAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : truncate(request.getHeader("User-Agent"), 400);
    }

    public static String path() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getMethod() + " " + request.getRequestURI();
    }

    /** 当前登录用户；未登录返回 null。 */
    public static SecurityUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser user) {
            return user;
        }
        return null;
    }

    /** 当前登录用户名（审计 actor 快照）；未登录返回 "anonymous"。 */
    public static String username() {
        SecurityUser user = currentUser();
        return user == null ? "anonymous" : user.username();
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}