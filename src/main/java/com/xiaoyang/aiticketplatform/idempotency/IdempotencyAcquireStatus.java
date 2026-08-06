package com.xiaoyang.aiticketplatform.idempotency;

public enum IdempotencyAcquireStatus {
    ACQUIRED,
    IN_PROGRESS,
    SUCCEEDED,
    PAYLOAD_MISMATCH,
    DISABLED
}
