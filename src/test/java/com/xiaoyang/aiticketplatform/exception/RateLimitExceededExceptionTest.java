package com.xiaoyang.aiticketplatform.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitExceededExceptionTest {

    @Test
    void shouldExposeSafeMessageAndRetryAfterSeconds() {
        RateLimitExceededException exception = new RateLimitExceededException(17);

        assertEquals("请求过于频繁，请稍后重试", exception.getMessage());
        assertEquals(17, exception.getRetryAfterSeconds());
    }

    @Test
    void shouldRejectRetryAfterBelowOneSecond() {
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimitExceededException(0));
    }
}
