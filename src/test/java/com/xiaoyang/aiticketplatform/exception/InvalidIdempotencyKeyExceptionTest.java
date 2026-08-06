package com.xiaoyang.aiticketplatform.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvalidIdempotencyKeyExceptionTest {

    @Test
    void shouldExposeOnlySafeMessage() {
        InvalidIdempotencyKeyException exception = new InvalidIdempotencyKeyException();

        assertEquals("幂等键不合法", exception.getMessage());
    }
}
