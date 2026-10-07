package com.easyoa.common.exception;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.response.ApiResponse;

import jakarta.validation.ConstraintViolationException;

/**
 * 全局异常处理：所有异常统一转换为 {@link ApiResponse}。
 *
 * <p>安全要求：
 * <ul>
 *   <li>5xx 响应绝不携带异常堆栈、SQL 或内部类名；</li>
 *   <li>请求体校验失败时只返回字段级提示，不返回 Java 异常信息；</li>
 *   <li>异常日志中绝不记录密码、TOTP、Session 等敏感内容。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        ErrorCode code = ex.errorCode();
        if (code.httpStatus().is5xxServerError()) {
            log.error("业务异常（服务端） code={} message={}", code.name(), ex.getMessage(), ex);
        } else {
            log.warn("业务异常 code={} message={} path={}", code.name(), ex.getMessage(), RequestContext.path());
        }
        return ResponseEntity.status(code.httpStatus()).body(ApiResponse.error(code.name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "字段不合法" : error.getDefaultMessage()))
                .toList();
        log.warn("字段校验失败 path={} fields={}", RequestContext.path(), fields);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.name(), ErrorCode.VALIDATION_FAILED.defaultMessage(),
                        Map.of("fields", fields)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("参数校验失败 path={} message={}", RequestContext.path(), ex.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.name(), ErrorCode.VALIDATION_FAILED.defaultMessage()));
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex) {
        log.warn("请求格式错误 path={} type={}", RequestContext.path(), ex.getClass().getSimpleName());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.httpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.name(), ErrorCode.INVALID_REQUEST.defaultMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.httpStatus())
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED.name(), ErrorCode.METHOD_NOT_ALLOWED.defaultMessage()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.httpStatus())
                .body(ApiResponse.error(ErrorCode.UNSUPPORTED_MEDIA_TYPE.name(),
                        ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(ErrorCode.PAYLOAD_TOO_LARGE.httpStatus())
                .body(ApiResponse.error(ErrorCode.PAYLOAD_TOO_LARGE.name(), ErrorCode.PAYLOAD_TOO_LARGE.defaultMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND.name(), ErrorCode.NOT_FOUND.defaultMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("访问被拒绝 path={} actor={}", RequestContext.path(), RequestContext.username());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.httpStatus())
                .body(ApiResponse.error(ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.defaultMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.httpStatus())
                .body(ApiResponse.error(ErrorCode.UNAUTHENTICATED.name(), ErrorCode.UNAUTHENTICATED.defaultMessage()));
    }

    /** 唯一约束 / 外键约束冲突：转换为 409，避免把数据库错误暴露为 500。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("数据完整性约束冲突 path={} message={}", RequestContext.path(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.CONFLICT.httpStatus())
                .body(ApiResponse.error(ErrorCode.CONFLICT.name(), "操作与当前数据状态冲突，请刷新后重试"));
    }

    /** 并发修改冲突（乐观锁 / 悲观锁）。 */
    @ExceptionHandler({ OptimisticLockingFailureException.class, PessimisticLockingFailureException.class })
    public ResponseEntity<ApiResponse<Void>> handleConcurrentModification(Exception ex) {
        log.warn("并发修改冲突 path={} type={}", RequestContext.path(), ex.getClass().getSimpleName());
        return ResponseEntity.status(ErrorCode.CONFLICT.httpStatus())
                .body(ApiResponse.error(ErrorCode.CONFLICT.name(), "数据已被其他人修改，请刷新后重试"));
    }

    /** 兜底：未预期异常。对外只返回统一文案，内部记录完整堆栈用于排查。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("未处理异常 type={} path={}", ex.getClass().getName(), RequestContext.path(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }
}