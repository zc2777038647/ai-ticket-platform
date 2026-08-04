package com.xiaoyang.aiticketplatform.common;

public enum ErrorCode {
    VALIDATION_ERROR(40000, "请求参数校验失败"),
    MESSAGE_NOT_READABLE(40001, "请求体格式错误"),
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
