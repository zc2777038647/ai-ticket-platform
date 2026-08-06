package com.xiaoyang.aiticketplatform.exception;

import com.xiaoyang.aiticketplatform.common.ErrorCode;

public class IdempotencyRequestInProgressException extends RuntimeException {

    private final long retryAfterSeconds;

    public IdempotencyRequestInProgressException(long retryAfterSeconds) {
        super(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS.getMessage());
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("retryAfterSeconds 必须大于等于 1");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
