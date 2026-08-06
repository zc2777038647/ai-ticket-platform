package com.xiaoyang.aiticketplatform.idempotency;

import java.util.Objects;

public record IdempotencyAcquireResult(
        IdempotencyAcquireStatus status,
        long retryAfterSeconds,
        String responsePayload
) {
    public IdempotencyAcquireResult {
        Objects.requireNonNull(status, "status 不能为空");
        if (status == IdempotencyAcquireStatus.IN_PROGRESS) {
            if (retryAfterSeconds < 1 || responsePayload != null) {
                throw new IllegalArgumentException("IN_PROGRESS 结果字段组合非法");
            }
        } else if (status == IdempotencyAcquireStatus.SUCCEEDED) {
            if (retryAfterSeconds != 0 || responsePayload == null || responsePayload.isBlank()) {
                throw new IllegalArgumentException("SUCCEEDED 结果字段组合非法");
            }
        } else if (retryAfterSeconds != 0 || responsePayload != null) {
            throw new IllegalArgumentException(status + " 结果字段组合非法");
        }
    }
}
