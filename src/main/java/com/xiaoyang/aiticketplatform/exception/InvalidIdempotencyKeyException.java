package com.xiaoyang.aiticketplatform.exception;

import com.xiaoyang.aiticketplatform.common.ErrorCode;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super(ErrorCode.INVALID_IDEMPOTENCY_KEY.getMessage());
    }
}
