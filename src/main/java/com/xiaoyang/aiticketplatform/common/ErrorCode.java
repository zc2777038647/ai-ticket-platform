package com.xiaoyang.aiticketplatform.common;

public enum ErrorCode {
    VALIDATION_ERROR(40000, "请求参数校验失败"),
    MESSAGE_NOT_READABLE(40001, "请求体格式错误"),
    REQUEST_PARAMETER_INVALID(40002, "请求参数格式错误"),
    INVALID_IDEMPOTENCY_KEY(40003, "幂等键不合法"),
    INVALID_CREDENTIALS(40100, "用户名或密码错误"),
    AUTHENTICATION_REQUIRED(40101, "请先登录或提供有效访问令牌"),
    AUTHORIZATION_DENIED(40300, "权限不足，无法执行此操作"),
    TICKET_NOT_FOUND(40400, "工单不存在"),
    ASSIGNEE_NOT_FOUND(40401, "处理人不存在"),
    INVALID_TICKET_STATUS_TRANSITION(40900, "工单状态流转不合法"),
    TICKET_STATUS_CONFLICT(40901, "工单状态已发生变化，请刷新后重试"),
    USERNAME_ALREADY_EXISTS(40902, "用户名已存在"),
    INVALID_ASSIGNEE_ROLE(40903, "目标用户不是可指派的处理人"),
    TICKET_ALREADY_ASSIGNED(40904, "工单已指派给该处理人"),
    TICKET_ASSIGNMENT_CONFLICT(40905, "工单指派状态已发生变化，请刷新后重试"),
    IDEMPOTENCY_REQUEST_IN_PROGRESS(40906, "相同请求正在处理中，请稍后重试"),
    IDEMPOTENCY_KEY_REUSED(40907, "幂等键已用于不同请求"),
    RATE_LIMIT_EXCEEDED(42900, "请求过于频繁，请稍后重试"),
    AI_RESPONSE_INVALID(50200, "AI 服务响应格式无效"),
    AI_SERVICE_UNAVAILABLE(50300, "AI 服务暂时不可用"),
    INTERNAL_ERROR(50000, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
