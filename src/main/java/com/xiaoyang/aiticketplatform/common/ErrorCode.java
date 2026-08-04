package com.xiaoyang.aiticketplatform.common;

public enum ErrorCode {
    VALIDATION_ERROR(40000, "请求参数校验失败"),
    MESSAGE_NOT_READABLE(40001, "请求体格式错误"),
    REQUEST_PARAMETER_INVALID(40002, "请求参数格式错误"),
    TICKET_NOT_FOUND(40400, "工单不存在"),
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
