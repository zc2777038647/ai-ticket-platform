package com.xiaoyang.aiticketplatform.exception;

import com.xiaoyang.aiticketplatform.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusinessExceptionTest {

    @Test
    void shouldExposeErrorCodeAndPublicMessage() {
        BusinessException exception = new BusinessException(ErrorCode.TICKET_NOT_FOUND);

        assertSame(ErrorCode.TICKET_NOT_FOUND, exception.getErrorCode());
        assertEquals("工单不存在", exception.getMessage());
    }

    @Test
    void shouldRejectNullErrorCode() {
        assertThrows(NullPointerException.class, () -> new BusinessException(null));
    }
}
