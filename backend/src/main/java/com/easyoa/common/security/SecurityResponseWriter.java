package com.easyoa.common.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 安全过滤器链中的 JSON 响应输出（401/403 等），保证未认证 / 无权限请求
 * 也返回统一 ApiResponse 结构，而不是默认的 HTML 错误页。
 */
@Component
public class SecurityResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(errorCode.name(), errorCode.defaultMessage());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}