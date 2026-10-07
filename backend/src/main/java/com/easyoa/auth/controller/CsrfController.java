package com.easyoa.auth.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * CSRF Token 引导接口。
 *
 * <p>SPA 启动时调用一次，确保拿到 XSRF-TOKEN Cookie（非 HttpOnly，供 Axios 读取）；
 * 后续所有写请求由 Axios 自动附加 X-XSRF-TOKEN 头。
 */
@RestController
public class CsrfController {

    public record CsrfTokenResponse(String headerName, String parameterName) {
    }

    @GetMapping("/api/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            // 触发延迟加载，确保 Cookie 写入响应
            token.getToken();
        }
        return ApiResponse.ok(new CsrfTokenResponse(
                token == null ? "X-XSRF-TOKEN" : token.getHeaderName(),
                token == null ? "_csrf" : token.getParameterName()));
    }
}