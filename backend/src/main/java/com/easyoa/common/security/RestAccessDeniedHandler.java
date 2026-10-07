package com.easyoa.common.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.easyoa.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 已认证但权限不足 → 403（JSON）。
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityResponseWriter writer;

    public RestAccessDeniedHandler(SecurityResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        writer.write(response, ErrorCode.FORBIDDEN);
    }
}