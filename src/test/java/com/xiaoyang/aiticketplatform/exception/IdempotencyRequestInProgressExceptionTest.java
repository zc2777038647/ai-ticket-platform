package com.xiaoyang.aiticketplatform.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdempotencyRequestInProgressExceptionTest {

    @Test
    void shouldExposeSafeMessageAndPositiveRetryAfter() {
        IdempotencyRequestInProgressException exception =
                new IdempotencyRequestInProgressException(17);

        assertEquals("相同请求正在处理中，请稍后重试", exception.getMessage());
        assertEquals(17, exception.getRetryAfterSeconds());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void shouldRejectNonPositiveRetryAfter(long retryAfterSeconds) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IdempotencyRequestInProgressException(retryAfterSeconds)
        );
    }
}
