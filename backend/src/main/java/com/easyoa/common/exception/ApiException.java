package com.easyoa.common.exception;

/**
 * 业务异常：所有可预期的业务失败都应抛出该异常，由 {@link GlobalExceptionHandler} 统一转换为 API 响应。
 *
 * <p>禁止在业务代码中直接 throw {@code RuntimeException} 来表达可预期失败。
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND);
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }

    public static ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public static ApiException invalidRequest(String message) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message);
    }
}