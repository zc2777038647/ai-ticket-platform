package com.xiaoyang.aiticketplatform.common;

import java.util.Objects;

public record ApiResponse<T>(
        int code,
        String message,
        T data
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return failure(errorCode, null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, T data) {
        ErrorCode managedErrorCode = Objects.requireNonNull(errorCode, "errorCode 不能为空");
        return new ApiResponse<>(managedErrorCode.getCode(), managedErrorCode.getMessage(), data);
    }
}
