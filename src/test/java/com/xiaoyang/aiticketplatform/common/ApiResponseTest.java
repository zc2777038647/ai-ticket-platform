package com.xiaoyang.aiticketplatform.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiResponseTest {

    @Test
    void shouldCreateSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("created-ticket");

        assertEquals(0, response.code());
        assertEquals("success", response.message());
        assertEquals("created-ticket", response.data());
    }

    @Test
    void shouldCreateFailureResponseWithoutData() {
        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.INTERNAL_ERROR);

        assertEquals(50000, response.code());
        assertEquals("服务器内部错误", response.message());
        assertNull(response.data());
    }

    @Test
    void shouldCreateTicketNotFoundResponseWithoutData() {
        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.TICKET_NOT_FOUND);

        assertEquals(40400, response.code());
        assertEquals("工单不存在", response.message());
        assertNull(response.data());
    }
}
