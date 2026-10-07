package com.easyoa.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码目录。
 *
 * <p>约定：
 * <ul>
 *   <li>每个错误码映射一个明确的 HTTP 状态；</li>
 *   <li>message 为可直接返回给最终用户的中文提示，不包含任何内部实现细节；</li>
 *   <li>新增错误码必须同时被前端错误处理覆盖（见前端 api/errors.ts）。</li>
 * </ul>
 */
public enum ErrorCode {

    // --- 通用 ---
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "字段校验未通过"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "未登录或会话已失效"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有权限执行该操作"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "账号已被禁用，请联系管理员"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在或无权访问"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不被支持"),
    CONFLICT(HttpStatus.CONFLICT, "操作与当前数据状态冲突"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "请求体过大"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的内容类型"),
    PASSWORD_POLICY_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "密码强度不符合安全策略"),
    UNPROCESSABLE(HttpStatus.UNPROCESSABLE_ENTITY, "请求无法被处理"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试"),
    LOGIN_BLOCKED(HttpStatus.TOO_MANY_REQUESTS, "失败次数过多，账号已被临时锁定，请稍后再试"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后再试"),

    // --- 认证 / 会话 ---
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "会话已失效，请重新登录"),
    PASSWORD_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "当前密码不正确"),

    // --- 首次初始化 ---
    SETUP_ALREADY_COMPLETED(HttpStatus.CONFLICT, "系统已完成初始化，无法重复执行"),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "用户名已被占用"),

    // --- 任务（Phase 4）：依赖阻塞需要前端触发「忽略依赖并开始」流程 ---
    TASK_BLOCKED_BY_DEPENDENCIES(HttpStatus.CONFLICT, "当前任务仍有未完成的前置依赖");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}