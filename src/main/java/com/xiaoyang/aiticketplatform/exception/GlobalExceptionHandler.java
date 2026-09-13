package com.xiaoyang.aiticketplatform.exception;

import com.xiaoyang.aiticketplatform.common.ApiResponse;
import com.xiaoyang.aiticketplatform.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String DEFAULT_VALIDATION_MESSAGE = "请求参数不合法";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        boolean hasBindingFailure = exception.getBindingResult().getFieldErrors().stream()
                .anyMatch(FieldError::isBindingFailure);
        if (hasBindingFailure) {
            LOGGER.warn("请求参数格式错误");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure(ErrorCode.REQUEST_PARAMETER_INVALID));
        }

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            String message = fieldError.getDefaultMessage();
            if (message == null || message.isBlank()) {
                message = DEFAULT_VALIDATION_MESSAGE;
            }
            fieldErrors.putIfAbsent(fieldError.getField(), message);
        }

        LOGGER.warn("请求参数校验失败: {}", fieldErrors);
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception
    ) {
        LOGGER.warn("请求体无法读取: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.MESSAGE_NOT_READABLE));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleHandlerMethodValidation(
            HandlerMethodValidationException exception
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getParameterValidationResults().stream()
                .filter(ParameterErrors.class::isInstance)
                .map(ParameterErrors.class::cast)
                .flatMap(errors -> errors.getFieldErrors().stream())
                .forEach(fieldError -> {
                    String message = fieldError.getDefaultMessage();
                    if (message == null || message.isBlank()) {
                        message = DEFAULT_VALIDATION_MESSAGE;
                    }
                    fieldErrors.putIfAbsent(fieldError.getField(), message);
                });

        LOGGER.warn("请求参数校验失败: {}", fieldErrors);
        if (fieldErrors.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR));
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR, fieldErrors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        LOGGER.warn("请求参数格式错误");
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.REQUEST_PARAMETER_INVALID));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        HttpStatus httpStatus = resolveBusinessHttpStatus(errorCode);
        if (httpStatus == null) {
            LOGGER.error("业务错误缺少 HTTP 状态映射，错误码: {}", errorCode.getCode());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR));
        }

        LOGGER.warn("业务操作失败，错误码: {}", errorCode.getCode());
        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.failure(errorCode));
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidIdempotencyKey(
            InvalidIdempotencyKeyException exception
    ) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.INVALID_IDEMPOTENCY_KEY));
    }

    @ExceptionHandler(IdempotencyRequestInProgressException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyRequestInProgress(
            IdempotencyRequestInProgressException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(ApiResponse.failure(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(
            RateLimitExceededException exception
    ) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(ApiResponse.failure(ErrorCode.RATE_LIMIT_EXCEEDED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        LOGGER.error("未处理的服务器异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR));
    }

    private HttpStatus resolveBusinessHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_CREDENTIALS, AUTHENTICATION_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case AUTHORIZATION_DENIED -> HttpStatus.FORBIDDEN;
            case TICKET_NOT_FOUND, ASSIGNEE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_TICKET_STATUS_TRANSITION,
                 TICKET_STATUS_CONFLICT,
                 USERNAME_ALREADY_EXISTS,
                 INVALID_ASSIGNEE_ROLE,
                 TICKET_ALREADY_ASSIGNED,
                 TICKET_ASSIGNMENT_CONFLICT,
                 IDEMPOTENCY_KEY_REUSED -> HttpStatus.CONFLICT;
            case AI_RESPONSE_INVALID -> HttpStatus.BAD_GATEWAY;
            case AI_SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> null;
        };
    }
}
