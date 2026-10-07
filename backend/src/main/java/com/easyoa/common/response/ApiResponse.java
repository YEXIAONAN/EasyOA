package com.easyoa.common.response;

import com.easyoa.common.requestid.RequestContext;

/**
 * 统一 API 响应结构。
 *
 * <pre>
 * 成功：{ "success": true,  "code": "OK",         "message": "success", "data": {...}, "requestId": "..." }
 * 失败：{ "success": false, "code": "TASK_NOT_FOUND", "message": "...",               "requestId": "..." }
 * </pre>
 *
 * <p>前端只依赖 {@code code} 做逻辑判断，禁止依赖后端异常文本。
 *
 * @param <T> 数据类型
 */
public record ApiResponse<T>(boolean success, String code, String message, T data, String requestId) {

    public static final String CODE_OK = "OK";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, CODE_OK, "success", data, RequestContext.requestId());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null, RequestContext.requestId());
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data, RequestContext.requestId());
    }
}